/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.msiiplab.recsys.rwr;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.mahout.cf.taste.common.NoSuchUserException;
import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.eval.RecommenderBuilder;
import org.apache.mahout.cf.taste.eval.RelevantItemsDataSplitter;
import org.apache.mahout.cf.taste.impl.common.FastByIDMap;
import org.apache.mahout.cf.taste.impl.common.FastIDSet;
import org.apache.mahout.cf.taste.impl.common.LongPrimitiveIterator;
import org.apache.mahout.cf.taste.impl.common.RunningAverage;
import org.apache.mahout.cf.taste.model.DataModel;
import org.apache.mahout.cf.taste.model.Preference;
import org.apache.mahout.cf.taste.model.PreferenceArray;
import org.apache.mahout.cf.taste.recommender.IDRescorer;
import org.apache.mahout.cf.taste.recommender.RecommendedItem;
import org.apache.mahout.cf.taste.recommender.Recommender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

/**
 * <p>
 * For each user, these implementation determine the top {@code n} preferences,
 * then evaluate the IR statistics based on a {@link DataModel} that does not
 * have these values. This number {@code n} is the "at" value, as in
 * "precision at 5". For example, this would mean precision evaluated by
 * removing the top 5 preferences for a user and then finding the percentage of
 * those 5 items included in the top 5 recommendations for that user.
 * </p>
 */
public class ParallelGLRecommenderIRStatsEvaluator extends GLRecommenderIRStatsEvaluator {

	private static final Logger log = LoggerFactory
			.getLogger(ParallelGLRecommenderIRStatsEvaluator.class);
	
	public ParallelGLRecommenderIRStatsEvaluator() {
		super();
	}

	public ParallelGLRecommenderIRStatsEvaluator(RelevantItemsDataSplitter dataSplitter) {
		super(dataSplitter);
	}

	@Override
	public GLIRStatisticsImpl evaluate(RecommenderBuilder recommenderBuilder,
			List<DataModel> trainingDataModels,
			List<DataModel> testingDataModels, 
			IDRescorer rescorer, int at,
			double relevanceThreshold, double evaluationPercentage)
			throws TasteException {

		Preconditions.checkArgument(recommenderBuilder != null,
				"recommenderBuilder is null");
		Preconditions.checkArgument(trainingDataModels != null,
				"trainingDataModels is null");
		Preconditions.checkArgument(testingDataModels != null,
				"testingDataModels is null");
		Preconditions.checkArgument(
				testingDataModels.size() == trainingDataModels.size(),
				"trainingDataModels.size must equals testingDataModels.size");
		Preconditions.checkArgument(at >= 1, "at must be at least 1");
		Preconditions.checkArgument(evaluationPercentage > 0.0
				&& evaluationPercentage <= 1.0,
				"Invalid evaluationPercentage: %s", evaluationPercentage);

		// num of train/test pair: num of cross validation folds
		int numFolds = trainingDataModels.size();

		RunningAverage CrossValidationPrecision = new GLRunningAverage();
		RunningAverage CrossValidationRPrecision = new GLRunningAverage();
		RunningAverage CrossValidationRecall = new GLRunningAverage();
		RunningAverage CrossValidationFallOut = new GLRunningAverage();
		RunningAverage CrossValidationNDCG = new GLRunningAverage();
		RunningAverage CrossValidationRNDCG = new GLRunningAverage();//rating-nDCG
		RunningAverage CrossValidationReach = new GLRunningAverage();
		RunningAverage CrossValidationMacroDOA = new GLRunningAverage();
		RunningAverage CrossValidationMicroDOA = new GLRunningAverage();
		RunningAverage CrossValidationMacroInnerDOA = new GLRunningAverage();
		RunningAverage CrossValidationMicroInnerDOA = new GLRunningAverage();

		for (int i_folds = 0; i_folds < numFolds; i_folds++) {
			
			log.info("fold {}", i_folds);
			DataModel trainDataModel = trainingDataModels.get(i_folds);
			DataModel testDataModel = testingDataModels.get(i_folds);

			FastIDSet MovieIDs = new FastIDSet();
			LongPrimitiveIterator it_train_temp = trainDataModel.getItemIDs();
			LongPrimitiveIterator it_test_temp = testDataModel.getItemIDs();
			while (it_train_temp.hasNext()) {
				MovieIDs.add(it_train_temp.nextLong());
			}
			while (it_test_temp.hasNext()) {
				MovieIDs.add(it_test_temp.nextLong());
			}

			int numTrainItems = trainDataModel.getNumItems();
			int numTestItems = testDataModel.getNumItems();
			int numItems = numTestItems + numTrainItems;

			RunningAverage precision = new GLRunningAverage();
			RunningAverage rPrecision = new GLRunningAverage();
			RunningAverage recall = new GLRunningAverage();
			RunningAverage fallOut = new GLRunningAverage();
			RunningAverage nDCG = new GLRunningAverage();
			RunningAverage rNDCG = new GLRunningAverage();
			RunningAverage macroDOA = new GLRunningAverage();
			RunningAverage microDOA1 = new GLRunningAverage();
			RunningAverage microDOA2 = new GLRunningAverage();
			RunningAverage macroInnerDOA = new GLRunningAverage();
			RunningAverage microInnerDOA1 = new GLRunningAverage();
			RunningAverage microInnerDOA2 = new GLRunningAverage();
			
			int numUsersRecommendedFor = 0;
			int numUsersWithRecommendations = 0;

			long start = System.currentTimeMillis();

			// Build recommender
			Recommender recommender = recommenderBuilder
					.buildRecommender(trainDataModel);
			
			// Futures
			ArrayList<Future<Integer>> futureList = new ArrayList<Future<Integer>>();

			int N_CPUS = Runtime.getRuntime().availableProcessors();
			ExecutorService pool = Executors.newFixedThreadPool(N_CPUS-1);
			// ExecutorService pool = Executors.newFixedThreadPool(1);
			
			LongPrimitiveIterator it_user = testDataModel.getUserIDs();
			while (it_user.hasNext()) {
				long userID = it_user.nextLong();
				
				Future<Integer> future = pool.submit(new Eval(precision, rPrecision, recall, fallOut, nDCG, rNDCG, macroDOA, microDOA1, microDOA2, macroInnerDOA, microInnerDOA1, microInnerDOA2, trainDataModel, testDataModel, userID, recommender, at, rescorer, numItems));
				futureList.add(future);
			}
			
			for (Future<Integer> future: futureList) {
				numUsersRecommendedFor ++;
				try {
					if (future.get() == 1) {
						numUsersWithRecommendations ++;
					}
				} catch (InterruptedException e) {
					e.printStackTrace();
				} catch (ExecutionException e) {
					e.printStackTrace();
					e.getCause().printStackTrace();
					System.exit(1);
				}
			}
			
			pool.shutdown();

			long end = System.currentTimeMillis();

			CrossValidationPrecision.addDatum(precision.getAverage());
			CrossValidationRPrecision.addDatum(rPrecision.getAverage());
			CrossValidationRecall.addDatum(recall.getAverage());
			CrossValidationFallOut.addDatum(fallOut.getAverage());
			CrossValidationNDCG.addDatum(nDCG.getAverage());
			CrossValidationRNDCG.addDatum(rNDCG.getAverage());
			CrossValidationReach.addDatum((double) numUsersWithRecommendations
					/ (double) numUsersRecommendedFor);
			CrossValidationMacroDOA.addDatum(macroDOA.getAverage());
			CrossValidationMicroDOA.addDatum(microDOA1.getAverage()
					/ microDOA2.getAverage());
			CrossValidationMacroInnerDOA.addDatum(macroInnerDOA.getAverage());
			CrossValidationMicroInnerDOA.addDatum(microInnerDOA1.getAverage()
					/ microInnerDOA2.getAverage());

			log.info( "Evaluated with training/testing set # {} in {}ms", i_folds, end - start);
			System.out.printf("Evaluated with training/testing set # %d in %d ms \n", i_folds, end - start);

			log.info("Precision/R-Precision/recall/fall-out/nDCG/rNDCG/reach/macroDOA/microDOA/macroInnerDOA/microInnerDOA: {} / {} / {} / {} / {} / {} / {} / {} / {} / {} / {}",
					precision.getAverage(), rPrecision.getAverage(), recall.getAverage(),
					fallOut.getAverage(), nDCG.getAverage(), rNDCG.getAverage(),
					(double) numUsersWithRecommendations / (double) numUsersRecommendedFor,
					macroDOA.getAverage(), microDOA1.getAverage() / microDOA2.getAverage(),
					macroInnerDOA.getAverage(), microInnerDOA1.getAverage() / microInnerDOA2.getAverage());
			System.out.printf("Precision/R-Precision/recall/fall-out/nDCG/rNDCG/reach/macroDOA/microDOA/macroInnerDOA/microInnerDOA: %f / %f / %f / %f / %f / %f / %f / %f / %f / %f / %f \n",
							precision.getAverage(), rPrecision.getAverage(), recall.getAverage(),
							fallOut.getAverage(), nDCG.getAverage(), rNDCG.getAverage(),
							(double) numUsersWithRecommendations / (double) numUsersRecommendedFor,
							macroDOA.getAverage(), microDOA1.getAverage() / microDOA2.getAverage(),
							macroInnerDOA.getAverage(), microInnerDOA1.getAverage() / microInnerDOA2.getAverage());

		}

		log.info(
				"Cross Validation Precision/R-Precision/recall/fall-out/nDCG/rNDCG/reach/macroDOA/microDOA: {} / {} / {} / {} / {} / {} / {} / {} / {} / {} / {}",
				CrossValidationPrecision.getAverage(),
				CrossValidationRPrecision.getAverage(),
				CrossValidationRecall.getAverage(),
				CrossValidationFallOut.getAverage(),
				CrossValidationNDCG.getAverage(),
				CrossValidationRNDCG.getAverage(),
				CrossValidationReach.getAverage(),
				CrossValidationMacroDOA.getAverage(),
				CrossValidationMicroDOA.getAverage(),
				CrossValidationMacroInnerDOA.getAverage(),
				CrossValidationMicroInnerDOA.getAverage());
		System.out.printf("Cross Validation: \nPrecision/R-Precision/recall/fall-out/nDCG/rNDCG/reach/macroDOA/microDOA: %f / %f / %f / %f / %f / %f / %f / %f / %f / %f / %f\n",
						CrossValidationPrecision.getAverage(),
						CrossValidationRPrecision.getAverage(),
						CrossValidationRecall.getAverage(),
						CrossValidationFallOut.getAverage(),
						CrossValidationNDCG.getAverage(),
						CrossValidationRNDCG.getAverage(),
						CrossValidationReach.getAverage(),
						CrossValidationMacroDOA.getAverage(),
						CrossValidationMicroDOA.getAverage(),
						CrossValidationMacroInnerDOA.getAverage(),
						CrossValidationMicroInnerDOA.getAverage());

		return new GLIRStatisticsImpl(CrossValidationPrecision.getAverage(),
				CrossValidationRPrecision.getAverage(),
				CrossValidationRecall.getAverage(),
				CrossValidationFallOut.getAverage(),
				CrossValidationNDCG.getAverage(),
				CrossValidationRNDCG.getAverage(),
				CrossValidationReach.getAverage(),
				CrossValidationMacroDOA.getAverage(),
				CrossValidationMicroDOA.getAverage(),
				CrossValidationMacroInnerDOA.getAverage(),
				CrossValidationMicroInnerDOA.getAverage());
	}
	
	public class Eval implements Callable<Integer> {
		private RunningAverage precision;
		private RunningAverage rPrecision;
		private RunningAverage recall;
		private RunningAverage fallOut;
		private RunningAverage nDCG;
		private RunningAverage rNDCG;
		private RunningAverage macroDOA;
		private RunningAverage microDOA1;
		private RunningAverage microDOA2;
		private RunningAverage macroInnerDOA;
		private RunningAverage microInnerDOA1;
		private RunningAverage microInnerDOA2; 
		
		private final DataModel trainDataModel;
		private final DataModel testDataModel;
		private final long userID;
		
		private final Recommender recommender; 
		private final int at; 
		private final IDRescorer rescorer; 
		private final int numItems;

		public Eval(RunningAverage precision, RunningAverage rPrecision, RunningAverage recall,
				RunningAverage fallOut, RunningAverage nDCG,
				RunningAverage rNDCG, RunningAverage macroDOA,
				RunningAverage microDOA1, RunningAverage microDOA2,
				RunningAverage macroInnerDOA, RunningAverage microInnerDOA1,
				RunningAverage microInnerDOA2, DataModel trainDataModel,
				DataModel testDataModel, long userID, Recommender recommender,
				int at, IDRescorer rescorer, int numItems) {
			super();
			this.precision = precision;
			this.rPrecision = rPrecision;
			this.recall = recall;
			this.fallOut = fallOut;
			this.nDCG = nDCG;
			this.rNDCG = rNDCG;
			this.macroDOA = macroDOA;
			this.microDOA1 = microDOA1;
			this.microDOA2 = microDOA2;
			this.macroInnerDOA = macroInnerDOA;
			this.microInnerDOA1 = microInnerDOA1;
			this.microInnerDOA2 = microInnerDOA2;
			this.trainDataModel = trainDataModel;
			this.testDataModel = testDataModel;
			this.userID = userID;
			this.recommender = recommender;
			this.at = at;
			this.rescorer = rescorer;
			this.numItems = numItems;
		}

		@Override
		public Integer call() throws Exception {
			log.info("user {}", userID);
			// Use all in testDataModel as relevant
			FastIDSet learnedItemIDs;
			FastIDSet relevantItemIDs;

			try {
				learnedItemIDs = trainDataModel
						.getItemIDsFromUser(userID);
				relevantItemIDs = testDataModel
						.getItemIDsFromUser(userID);
			} catch (NoSuchUserException e1) {
				return 0;
			}

			// We excluded zero relevant items situation
			int numRelevantItems = relevantItemIDs.size();
			if (numRelevantItems <= 0) {
				return 0;
			}

			// We excluded all prefs for the user that has no pref record in
			// training set
			try {
				trainDataModel.getPreferencesFromUser(userID);
			} catch (NoSuchUserException nsee) {
				return 0; // Oops we excluded all prefs for the user -- just
							// move on
			}

			// Recommend items
			List<RecommendedItem> recommendedItems = recommender.recommend(
					userID, at, rescorer);
			List<RecommendedItem> recommendedItemsAtRelNum = recommender.recommend(
					userID, numRelevantItems, rescorer);
			
			PreferenceArray userPreferences = testDataModel.getPreferencesFromUser(userID);
			FastByIDMap<Preference> userPreferenceMap = getPrefereceMap(userPreferences);
			userPreferences.sortByValueReversed();

			// relevantItemIDsAtN only consider top N items as relevant items
			FastIDSet relevantItemIDsAtN = new FastIDSet();
			Iterator<Preference> it_pref = userPreferences.iterator();
			int num_pref = 0;
			while (it_pref.hasNext()) {
				relevantItemIDsAtN.add(it_pref.next().getItemID());
				num_pref ++;
				if (num_pref >= at) {
					break;
				}
			}

			// Compute intersection between recommended items and relevant
			// items
			int intersectionSize = 0;
			int numRecommendedItems = recommendedItems.size();
			for (RecommendedItem recommendedItem : recommendedItems) {
				if (relevantItemIDs.contains(recommendedItem.getItemID())) {
					intersectionSize++;
				} 
			}

			// Precision
			double prec = 0;
			if (numRecommendedItems > 0) {
				prec = (double) intersectionSize
						/ (double) numRecommendedItems;
			}
			precision.addDatum(prec);
			log.info("Precision for user {} is {}", userID, prec);

			// Recall
			double rec = (double) intersectionSize
					/ (double) numRelevantItems;
			recall.addDatum(rec);
			log.info("Recall for user {} is {}", userID, rec);
			
			// R-precision
			double rprec = 0;
			int intersectionSizeAtRelNum = 0;
			for (RecommendedItem recommendedItem : recommendedItemsAtRelNum) {
				if (relevantItemIDs.contains(recommendedItem.getItemID())) {
					intersectionSizeAtRelNum++;
				} 
			}
			if (numRelevantItems > 0) {
				rprec = (double) intersectionSizeAtRelNum
						/ (double) numRelevantItems;
				rPrecision.addDatum(rprec);
				log.info("RPrecision for user {} is {}", userID, rprec);
			}


			// Fall-out
			double fall = 0;
			int size = numRelevantItems
					+ trainDataModel.getItemIDsFromUser(userID).size();
			if (numRelevantItems < size) {
				fall = (double) (numRecommendedItems - intersectionSize)
						/ (double) (numItems - numRelevantItems);
			}
			fallOut.addDatum(fall);
			log.info("Fallout for user {} is {}", userID, fall);


			// nDCG
			// In computing, assume relevant IDs have relevance ${rating} and others
			// 0
			PreferenceArray userPredictions = getPreferenceArray(recommendedItems, userID);
			double userNDCG = computeNDCG(userPreferences, userPredictions, relevantItemIDs, userPreferenceMap, at);
			double userRNDCG = computeRNDCG(userPreferences, userPredictions, relevantItemIDs, userPreferenceMap, at);
			nDCG.addDatum(userNDCG);
			rNDCG.addDatum(userRNDCG);
			log.info("NDCG for user {} is {}", userID, userNDCG);
			log.info("RNDCG for user {} is {}", userID, userRNDCG);
			
			return 1;
		}
		
	}
	
}


