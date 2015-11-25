package com.msiiplab.recsys.lfm;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.mahout.cf.taste.common.Refreshable;
import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.eval.RecommenderBuilder;
import org.apache.mahout.cf.taste.impl.common.FastByIDMap;
import org.apache.mahout.cf.taste.impl.common.FastIDSet;
import org.apache.mahout.cf.taste.impl.common.LongPrimitiveIterator;
import org.apache.mahout.cf.taste.impl.recommender.AbstractRecommender;
import org.apache.mahout.cf.taste.impl.recommender.TopItems;
import org.apache.mahout.cf.taste.model.DataModel;
import org.apache.mahout.cf.taste.model.Preference;
import org.apache.mahout.cf.taste.model.PreferenceArray;
import org.apache.mahout.cf.taste.recommender.CandidateItemsStrategy;
import org.apache.mahout.cf.taste.recommender.IDRescorer;
import org.apache.mahout.cf.taste.recommender.RecommendedItem;
import org.apache.mahout.cf.taste.recommender.Recommender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

public class AspectModelRecommender extends AbstractRecommender {
	
	private static final int sMaxLoop = 200;
	private static final double sThreshold = 0.00001;
	
	public static final Logger log = LoggerFactory.getLogger(AspectModelRecommender.class);
	
	// model parameters
	private ArrayList<Double> mLatentPrior;
	private ArrayList<FastByIDMap<Double>> mUserConditional;
	private ArrayList<FastByIDMap<Double>> mItemConditional;
	private FastByIDMap<FastByIDMap<ArrayList<Double>>> mLatentExpectation;
	private Long mTotalCount;
	private Integer mNumOfLatentFactor;

	public AspectModelRecommender(DataModel dataModel, int factor) {
		super(dataModel);
		try {
			modelFitting(factor);
		} catch (TasteException e) {
			e.printStackTrace();
		}
	}
	
	public AspectModelRecommender(DataModel dataModel, CandidateItemsStrategy candidateItemsStrategy, int factor) {
		super(dataModel, candidateItemsStrategy);
		try {
			modelFitting(factor);
		} catch (TasteException e) {
			e.printStackTrace();
		}
	}
	
	private void modelFitting(int factor) throws TasteException {
		initializeModel(factor);
		double oldLogLikelihood = Double.MIN_VALUE;
		double logLikelihood = getLogLikelihood();
		for (int loop=0; loop<sMaxLoop; loop++) {
			eStep();
			mStep();
			oldLogLikelihood = logLikelihood;
			logLikelihood = getLogLikelihood();
			log.info("OldLogLikelihood = " + oldLogLikelihood + "; logLikelihood = " + logLikelihood);
			if (loop > 10) {
				if (Math.abs((logLikelihood - oldLogLikelihood) / logLikelihood) < sThreshold) {
					log.info("Finished model fitting, final logLikelihood = " + logLikelihood);
					break;
				}
			}
		}
		System.out.println("logLikelihood: "+logLikelihood);
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
	
	private void eStep() throws TasteException {
		ArrayList<Callable<Integer>> callableList = new ArrayList<Callable<Integer>>();
		LongPrimitiveIterator it_user = getDataModel().getUserIDs();
		while (it_user.hasNext()) {
			long userID = it_user.nextLong();
			callableList.add(new EStepCallable(userID));
		}
		runCallableList(callableList);
	}
	
	public class EStepCallable implements Callable<Integer> {
		
		private final long mUserID;
		
		public EStepCallable(long userID) {
			mUserID = userID;
		}

		@Override
		public Integer call() throws Exception {
			PreferenceArray userPrefs = getDataModel().getPreferencesFromUser(mUserID);
			for (Preference pref : userPrefs) {
				long itemID = pref.getItemID();
				ArrayList<Double> expectation = mLatentExpectation.get(mUserID).get(itemID);
				double probSum = 0.0;
				for (int i=0; i<mNumOfLatentFactor; i++) {
					double prob = mLatentPrior.get(i) 
							* mUserConditional.get(i).get(mUserID) 
							* mItemConditional.get(i).get(itemID);
					probSum += prob;
					expectation.set(i, prob);
				}
				for (int i=0; i<mNumOfLatentFactor; i++) {
					expectation.set(i, expectation.get(i)/probSum);
				}
			}
			return 1;
		}
		
	}
	
	private void mStep() throws TasteException {
		ArrayList<Callable<Integer>> callableList = new ArrayList<Callable<Integer>>();
		for (int i=0; i<mNumOfLatentFactor; i++) {
			callableList.add(new ParameterCallable<Integer,Integer>(i){
				@Override
				public Integer call() throws Exception {
					int i = mParameter;
					
					FastByIDMap<Double> iUserConditional = mUserConditional.get(i);
					FastByIDMap<Double> iItemConditional = mItemConditional.get(i);
					// Initialization
					{
						synchronized (mLatentPrior) {
							mLatentPrior.set(i, 0.0);
						}
						LongPrimitiveIterator it_user = iUserConditional.keySetIterator();
						while (it_user.hasNext()) {
							long userID = it_user.nextLong();
							iUserConditional.put(userID, 0.0);
						}
						LongPrimitiveIterator it_item = iItemConditional.keySetIterator();
						while (it_item.hasNext()) {
							long itemID = it_item.nextLong();
							iItemConditional.put(itemID, 0.0);
						}
					}
					{
						// Item and User Conditional
						double totalProb = 0.0;
						{
							LongPrimitiveIterator it_user = getDataModel().getUserIDs();
							while (it_user.hasNext()) {
								long userID = it_user.nextLong();
								PreferenceArray userPrefs = getDataModel().getPreferencesFromUser(userID);
								for (Preference pref : userPrefs) {
									long itemID = pref.getItemID();
									double prob = mLatentExpectation.get(userID).get(itemID).get(i);
									iUserConditional.put(userID, iUserConditional.get(userID) + prob);
									iItemConditional.put(itemID, iItemConditional.get(itemID) + prob);
									totalProb += prob;
								}
							}
						}
						{
							LongPrimitiveIterator it_user = iUserConditional.keySetIterator();
							while (it_user.hasNext()) {
								long userID = it_user.nextLong();
								iUserConditional.put(userID, iUserConditional.get(userID) / totalProb);
							}
							LongPrimitiveIterator it_item = iItemConditional.keySetIterator();
							while (it_item.hasNext()) {
								long itemID = it_item.nextLong();
								iItemConditional.put(itemID, iItemConditional.get(itemID) / totalProb);
							}
						}
						// Latent Prior
						synchronized (mLatentPrior) {
							mLatentPrior.set(i, totalProb / mTotalCount);
						}
					}
					return 1;
				}});
		}
		runCallableList(callableList);
	}
	
	private void initializeModel(int numOfLatentFactor) throws TasteException {
		Preconditions.checkArgument(numOfLatentFactor > 0 && numOfLatentFactor <= 2000, "num of latent factors must be between 0 and 100");
		
		mNumOfLatentFactor = numOfLatentFactor;
		mLatentPrior = new ArrayList<Double>(mNumOfLatentFactor+1);
		{
			double sumPrior = 0.0;
			for (int i=0; i<mNumOfLatentFactor; i++) {
				double value = Math.random();
				mLatentPrior.add(value);
				sumPrior += value;
			}
			for (int i=0; i<mNumOfLatentFactor; i++) {
				mLatentPrior.set(i, mLatentPrior.get(i)/sumPrior);
			}
		}
		
		mUserConditional = new ArrayList<FastByIDMap<Double>>(mNumOfLatentFactor+1);
		ArrayList<Callable<FastByIDMap<Double>>> userCallableList = new ArrayList<Callable<FastByIDMap<Double>>>();
		for (int i=0; i<mNumOfLatentFactor; i++) {
			userCallableList.add(new Callable<FastByIDMap<Double>>() {
				@Override
				public FastByIDMap<Double> call() throws Exception {
					double sumUserConditional = 0.0;
					FastByIDMap<Double> userConditional = new FastByIDMap<Double>();
					
					LongPrimitiveIterator it_user = getDataModel().getUserIDs();
					while (it_user.hasNext()) {
						long userID = it_user.nextLong();
						double value = Math.random();
						userConditional.put(userID, value);
						sumUserConditional += value;
					}
					it_user = getDataModel().getUserIDs();
					while (it_user.hasNext()) {
						long userID = it_user.nextLong();
						userConditional.put(userID, userConditional.get(userID)/sumUserConditional);
					}
					
					return userConditional;
				}
			});
		}
		mUserConditional.addAll(runCallableList(userCallableList));
		
		mItemConditional = new ArrayList<FastByIDMap<Double>>(mNumOfLatentFactor+1);
		ArrayList<Callable<FastByIDMap<Double>>> itemCallableList = new ArrayList<Callable<FastByIDMap<Double>>>();
		for (int i=0; i<mNumOfLatentFactor; i++) {
			itemCallableList.add(new Callable<FastByIDMap<Double>>() {
				@Override
				public FastByIDMap<Double> call() throws Exception {
					double sumItemConditional = 0.0;
					FastByIDMap<Double> itemConditional = new FastByIDMap<Double>();
					
					LongPrimitiveIterator it_item = getDataModel().getItemIDs();
					while (it_item.hasNext()) {
						long itemID = it_item.nextLong();
						double value = Math.random();
						itemConditional.put(itemID, value);
						sumItemConditional += value;
					}
					it_item = getDataModel().getItemIDs();
					while (it_item.hasNext()) {
						long itemID = it_item.nextLong();
						itemConditional.put(itemID, itemConditional.get(itemID)/sumItemConditional);
					}
					
					return itemConditional;
				}
			});
		}
		mItemConditional.addAll(runCallableList(itemCallableList));
		
		mLatentExpectation = new FastByIDMap<FastByIDMap<ArrayList<Double>>>();
		ArrayList<Callable<Integer>> latentCallableList = new ArrayList<Callable<Integer>>();
		LongPrimitiveIterator it_user = getDataModel().getUserIDs();
		while (it_user.hasNext()) {
			long userID = it_user.nextLong();
			latentCallableList.add(new ParameterCallable<Integer,Long>(userID) {
				@Override
				public Integer call() throws Exception {
					long userID = mParameter;
					
					FastByIDMap<ArrayList<Double>> expectation = new FastByIDMap<ArrayList<Double>>();
					
					PreferenceArray userPrefs = getDataModel().getPreferencesFromUser(userID);
					for (Preference pref : userPrefs) {
						long itemID = pref.getItemID();
						ArrayList<Double> array = new ArrayList<Double>(mNumOfLatentFactor+1);
						
						double sumPrior = 0.0;
						
						for (int i=0; i<mNumOfLatentFactor; i++) {
							double value = Math.random();
							array.add(value);
							sumPrior += value;
						}
						for (int i=0; i<mNumOfLatentFactor; i++) {
							array.set(i, array.get(i)/sumPrior);
						}
						
						expectation.put(itemID, array);
					}
					synchronized (mLatentExpectation) {
						mLatentExpectation.put(userID, expectation);
					}
					return 1;
				}
			});
		}
		runCallableList(latentCallableList);
		
		mTotalCount = 0L;
		LongPrimitiveIterator it_item = getDataModel().getItemIDs();
		while (it_item.hasNext()) {
			long itemID = it_item.nextLong();
			mTotalCount += getDataModel().getNumUsersWithPreferenceFor(itemID);
		}

	}
	
	private double getLogLikelihood() throws TasteException {
		double totalLogLikelihood = 0.0;
		ArrayList<Callable<Double>> callableList = new ArrayList<Callable<Double>>();
		LongPrimitiveIterator it_user = getDataModel().getUserIDs();
		while (it_user.hasNext()) {
			long userID = it_user.nextLong();
			callableList.add(new ParameterCallable<Double, Long>(userID) {
				@Override
				public Double call() throws Exception {
					long userID = mParameter;
					double logLikelihood = 0.0;
					PreferenceArray userPrefs = getDataModel().getPreferencesFromUser(userID);
					for (Preference pref : userPrefs) {
						long itemID = pref.getItemID();
						double prob = 0.0;
						for (int i=0; i<mNumOfLatentFactor; i++) {
							prob += mLatentPrior.get(i) * mItemConditional.get(i).get(itemID) * mUserConditional.get(i).get(userID);
						}
						logLikelihood += Math.log(prob);
					}
					return logLikelihood;
				}
			});
		}
		for (double logLikelihood : runCallableList(callableList)) {
			totalLogLikelihood += logLikelihood;
		}
		return totalLogLikelihood;
	}

	@Override
	public List<RecommendedItem> recommend(long userID, int howMany,
			IDRescorer rescorer) throws TasteException {
		Preconditions.checkArgument(howMany>=1, "howMany must be >= 1");
		log.debug("Recommend items for user '{}'", userID);
		
		PreferenceArray preferencesFromUser = getDataModel().getPreferencesFromUser(userID);
		FastIDSet possibleItemIDs = getAllOtherItems(userID, preferencesFromUser);
		
		List<RecommendedItem> topItems = TopItems.getTopItems(howMany, 
				possibleItemIDs.iterator(),
				rescorer, 
				new Estimator(userID));
		log.debug("Recommendations are: {}", topItems);		
		
		return topItems;
	}

	@Override
	public float estimatePreference(long userID, long itemID)
			throws TasteException {
		double prob = 0.0;
		for (int i=0; i<mNumOfLatentFactor; i++) {
			prob += mLatentPrior.get(i) * mItemConditional.get(i).get(itemID) * mUserConditional.get(i).get(userID);
		}
		// multiply a constant won't affect the relative ranking of items
		prob *= getDataModel().getNumItems();
		return (float) prob;
	}

	@Override
	public void refresh(Collection<Refreshable> alreadyRefreshed) {
	}
	
	public class Estimator implements TopItems.Estimator<Long> {
		private final long theUserID;
		public Estimator(long userID) {
			theUserID = userID;
		}
		@Override
		public double estimate(Long itemID) throws TasteException {
			return estimatePreference(theUserID, itemID);
		}
	}
	
	public ArrayList<Double> getLatentPrior() {
		return mLatentPrior;
	}

	public ArrayList<FastByIDMap<Double>> getUserConditional() {
		return mUserConditional;
	}

	public ArrayList<FastByIDMap<Double>> getItemConditional() {
		return mItemConditional;
	}

	public Integer getNumOfLatentFactor() {
		return mNumOfLatentFactor;
	}

	public static RecommenderBuilder getRecommenderBuilder(final int factor) {
		return new RecommenderBuilder() {
			@Override
			public Recommender buildRecommender(DataModel dataModel)
					throws TasteException {
				return new AspectModelRecommender(dataModel, factor);
			}
		};
	}
}
