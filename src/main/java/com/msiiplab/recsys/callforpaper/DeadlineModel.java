package com.msiiplab.recsys.callforpaper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.mahout.cf.taste.impl.common.FastByIDMap;
import org.apache.mahout.cf.taste.impl.common.LongPrimitiveIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeadlineModel {
	
	private FastByIDMap<List<Double>> mTimeSeriesData;
	private Double mSigmaSquare;
	
	private static final double SCALE = 365 * 24 * 3600;
	
	public static final Logger log = LoggerFactory.getLogger(DeadlineModel.class);
	
	public DeadlineModel(FastByIDMap<List<Double>> timeSeriesData) {
		mTimeSeriesData = timeSeriesData;
		inference(timeSeriesData);
		// gridSearch();
		// inference(mTimeSeriesData);
	}
	
	public void gridSearch() {
		double minSigmaSquare = -1;
		double minLogL = 0.0;
		for (double time = 0.01; time < 1; time += 0.01) {
			mSigmaSquare = time * time;
			double logL = logLikelihood();
			log.info("\\sigma^2 = {}, logLikelihood = {}", mSigmaSquare * SCALE * SCALE, logL);
			if (minSigmaSquare < 0) {
				minSigmaSquare = mSigmaSquare;
				minLogL = logL;
			} else {
				if (minLogL < logL) {
					minLogL = logL;
					minSigmaSquare = mSigmaSquare;
				}
			}
		}
		mSigmaSquare = minSigmaSquare;
		log.info("final: \\sigma^2 = {}, logLikelihood = {}", mSigmaSquare * SCALE * SCALE, minLogL);
	}
	
	public void inference(FastByIDMap<List<Double>> timeSeriesData) {
		mSigmaSquare = 1.0;
		
		double logLikelihood = logLikelihood();
		double oldLogLikelihood = logLikelihood;
		int count = 0;
		while (count < 10 || Math.abs((oldLogLikelihood-logLikelihood)/oldLogLikelihood) > 0.0001) {
			log.info("Likelihood: {}, mSigmaSquare: {}", 
					logLikelihood, mSigmaSquare);
			oldLogLikelihood = logLikelihood;
			mSigmaSquare = newSigma();
			logLikelihood = logLikelihood();
			count ++;
		}
		log.info("Inference done, Likelihood: {}, mSigmaSquare: {}", logLikelihood, mSigmaSquare);
	}
	
	public Double newSigma() {
		double sum = 0.0;
		long itemNum = 0;
		ArrayList<Callable<Double>> callableList = new ArrayList<Callable<Double>>();
		LongPrimitiveIterator it_user = mTimeSeriesData.keySetIterator();
		while (it_user.hasNext()) {
			long userID = it_user.nextLong();
			itemNum += mTimeSeriesData.get(userID).size();
			callableList.add(new ParameterCallable<Double, Long>(userID) {
				@Override
				public Double call() throws Exception {
					long userID = mParameter;
					List<Double> timeSeries = mTimeSeriesData.get(userID);
					if (timeSeries.size() > 1) {
						double sum = 0.0;
						for (int i=0; i<timeSeries.size(); i++) {
							double weightedProb = 0.0;
							double prob = 0.0;
							for (int j=0; j<timeSeries.size(); j++) {
								if (i != j) {
									double delta = ( timeSeries.get(i) - timeSeries.get(j) ) / SCALE;
									double p = Math.exp(-delta*delta/2/mSigmaSquare);
									prob += p;
									weightedProb += p * (delta * delta);
								}
							}
							if (prob < Double.MIN_NORMAL) {
								if (weightedProb > Double.MIN_NORMAL) {
									log.error("timeSeries.size = {}", timeSeries.size());
								}
							} else {
								sum += weightedProb / prob;
							}
						}
						return sum;
					} else {
						return 0.0;
					}
				}
			});
		}
		List<Double> resultList = runCallableList(callableList);
		for (double comp: resultList) {
			sum += comp;
		}
		sum /= itemNum;
		return sum;
	}
	
	private Double logLikelihood() {
		double logL = 0.0;
		ArrayList<Callable<Double>> callableList = new ArrayList<Callable<Double>>();
		LongPrimitiveIterator it_user = mTimeSeriesData.keySetIterator();
		while (it_user.hasNext()) {
			long userID = it_user.nextLong();
			callableList.add(new ParameterCallable<Double, Long>(userID) {
				@Override
				public Double call() throws Exception {
					long userID = mParameter;
					List<Double> timeSeries = mTimeSeriesData.get(userID);
					if (timeSeries.size() > 1) {
						double sum = 0.0;
						for (int i=0; i<timeSeries.size(); i++) {
							double prob = 0.0;
							for (int j=0; j<timeSeries.size(); j++) {
								if (i != j) {
									double delta = (timeSeries.get(i) - timeSeries.get(j)) / SCALE;
									prob += Math.exp(-delta*delta/2/mSigmaSquare) /  Math.sqrt(2*Math.PI*mSigmaSquare);
								}
							}
							prob = prob / (timeSeries.size() - 1);
							if (prob < Double.MIN_NORMAL) {
								prob = Double.MIN_NORMAL;
							}
							double logprob = Math.log(prob);
							sum += logprob;
						}
						return sum;
					} else {
						return 0.0;
					}
				}
			});
		}
		List<Double> resultList = runCallableList(callableList);
		for (double comp: resultList) {
			logL += comp;
		}
		return logL;
	}
	
	private <T> List<T> runCallableList(ArrayList<Callable<T>> callableList) {
		ArrayList<T> resultList = new ArrayList<T>();
		
		int N_CPUS = Runtime.getRuntime().availableProcessors();
		ExecutorService pool = Executors.newFixedThreadPool(N_CPUS-1);
		try {
			List<Future<T>> futureList = pool.invokeAll(callableList);
			for (Future<T> future: futureList) {
				try {
					resultList.add(future.get());
				} catch (InterruptedException e) {
					e.printStackTrace();
				} catch (ExecutionException e) {
					e.printStackTrace();
					e.getCause().printStackTrace();
					System.exit(1);
				}
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		} finally {
			pool.shutdown();
		}
		return resultList;
	}
	
	public abstract class ParameterCallable<T,I> implements Callable<T> {
		protected final I mParameter;
		public ParameterCallable(I parameter) {
			mParameter = parameter;
		}
	}
	
	// TODO: compute probability
	public Double probability(Long user, Double timestamp) {
		if (mTimeSeriesData.containsKey(user)) {
			double prob = 0.0;
			List<Double> timeSeries = mTimeSeriesData.get(user);
			for (double mu: timeSeries) {
				double delta = (timestamp - mu) / SCALE;
				prob += Math.exp(-delta*delta/2/mSigmaSquare) /  Math.sqrt(2*Math.PI*mSigmaSquare);
			}
			prob = prob / timeSeries.size();
			if (prob < Double.MIN_NORMAL) {
				prob = Double.MIN_NORMAL;
			}
			return prob;
		} else {
			// only for comparison between one user's favorite items
			return 1.0;
		}
	}
	

}
