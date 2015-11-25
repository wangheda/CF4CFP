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
import java.util.Random;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import org.apache.mahout.cf.taste.common.NoSuchItemException;
import org.apache.mahout.cf.taste.common.NoSuchUserException;
import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.eval.DataModelBuilder;
import org.apache.mahout.cf.taste.eval.RecommenderBuilder;
import org.apache.mahout.cf.taste.eval.RelevantItemsDataSplitter;
import org.apache.mahout.cf.taste.impl.common.FastByIDMap;
import org.apache.mahout.cf.taste.impl.common.FastIDSet;
import org.apache.mahout.cf.taste.impl.common.LongPrimitiveIterator;
import org.apache.mahout.cf.taste.impl.common.RunningAverage;
import org.apache.mahout.cf.taste.impl.eval.GenericRelevantItemsDataSplitter;
import org.apache.mahout.cf.taste.impl.model.GenericPreference;
import org.apache.mahout.cf.taste.impl.model.GenericUserPreferenceArray;
import org.apache.mahout.cf.taste.model.DataModel;
import org.apache.mahout.cf.taste.model.Preference;
import org.apache.mahout.cf.taste.model.PreferenceArray;
import org.apache.mahout.cf.taste.recommender.IDRescorer;
import org.apache.mahout.cf.taste.recommender.RecommendedItem;
import org.apache.mahout.cf.taste.recommender.Recommender;
import org.apache.mahout.common.RandomUtils;
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
public class GLRecommenderIRStatsEvaluator {

	private static final Logger log = LoggerFactory
			.getLogger(GLRecommenderIRStatsEvaluator.class);

	protected static final double LOG2 = Math.log(2.0);

	/**
	 * Pass as "relevanceThreshold" argument to
	 * {@link #evaluate(RecommenderBuilder, DataModelBuilder, DataModel, IDRescorer, int, double, double)}
	 * to have it attempt to compute a reasonable threshold. Note that this will
	 * impact performance.
	 */
	public static final double CHOOSE_THRESHOLD = Double.NaN;

	protected final Random random;
	protected final RelevantItemsDataSplitter dataSplitter;

	public GLRecommenderIRStatsEvaluator() {
		this(new GenericRelevantItemsDataSplitter());
	}

	public GLRecommenderIRStatsEvaluator(RelevantItemsDataSplitter dataSplitter) {
		Preconditions.checkNotNull(dataSplitter);
		random = RandomUtils.getRandom();
		this.dataSplitter = dataSplitter;
	}
	
	public void predict(RecommenderBuilder recommenderBuilder,
			List<DataModel> trainingDataModels,
			List<DataModel> testingDataModels, 
			List<File> outputFileList) throws TasteException {

		// num of train/test pair: num of cross validation folds
		int numFolds = trainingDataModels.size();

		for (int i_folds = 0; i_folds < numFolds; i_folds++) {
			DataModel trainDataModel = trainingDataModels.get(i_folds);
			DataModel testDataModel = testingDataModels.get(i_folds);

			// Build recommender
			Recommender recommender = recommenderBuilder
					.buildRecommender(trainDataModel);
			
			LongPrimitiveIterator it_item;
			FastIDSet allItems = new FastIDSet();
			it_item = trainDataModel.getItemIDs();
			while (it_item.hasNext()) {
				allItems.add(it_item.nextLong());
			}
			it_item = testDataModel.getItemIDs();
			while (it_item.hasNext()) {
				allItems.add(it_item.nextLong());
			}
			
			if (outputFileList != null) {
				File file = outputFileList.get(i_folds);
				log.info("Writing recommender output to file: " + file.getPath());
				try {
					FileOutputStream out = new FileOutputStream(file);
					LongPrimitiveIterator it_user = testDataModel.getUserIDs();
					while (it_user.hasNext()) {
						long userID = it_user.nextLong();
						FastIDSet ratedItems = trainDataModel.getItemIDsFromUser(userID);
						StringBuilder sb = new StringBuilder();
						Iterator<Long> it = allItems.iterator();
						while (it.hasNext()) {
							long itemID = it.next();
							if (!ratedItems.contains(itemID)) {
								float pref = 0;
								try {
									pref = recommender.estimatePreference(userID, itemID);
								} catch (NoSuchItemException e) {
									pref = Float.NaN;
								}
								sb.append(userID+","+itemID+","+pref+"\n");
							}
						}
						out.write(sb.toString().getBytes());
						out.flush();
					}
					out.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
	}

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

			LongPrimitiveIterator it_user = testDataModel.getUserIDs();
			while (it_user.hasNext()) {
				long userID = it_user.nextLong();
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
					continue;
				}

				// We excluded zero relevant items situation
				int numRelevantItems = relevantItemIDs.size();
				if (numRelevantItems <= 0) {
					continue;
				}

				// We excluded all prefs for the user that has no pref record in
				// training set
				try {
					trainDataModel.getPreferencesFromUser(userID);
				} catch (NoSuchUserException nsee) {
					continue; // Oops we excluded all prefs for the user -- just
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
				int numRecommendedItemsAtRelNum = recommendedItemsAtRelNum.size();
				for (RecommendedItem recommendedItem : recommendedItemsAtRelNum) {
					if (relevantItemIDs.contains(recommendedItem.getItemID())) {
						intersectionSizeAtRelNum++;
					} 
				}
				if (numRecommendedItemsAtRelNum > 0) {
					rprec = (double) intersectionSizeAtRelNum
							/ (double) numRelevantItems;
				}
				rPrecision.addDatum(rprec);
				log.info("RPrecision for user {} is {}", userID, rprec);
				
				double F1 = 0;
				if (prec + rec > 0) {
					F1 = 2 * prec * rec / (prec + rec);
				}
				log.info("F1 for user {} is {}", userID, F1);


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

				// Reach
				numUsersRecommendedFor++;
				if (numRecommendedItems > 0) {
					numUsersWithRecommendations++;
				}

				// DOA
				// [Siegel and Castellan, 1988] and [Gori and Pucci, 2007]
				// LongPrimitiveIterator it_movies = MovieIDs.iterator();
				LongPrimitiveIterator it_movies = trainDataModel.getItemIDs();
				long numNW = 0;
				long sumCheckOrder = 0;
				while (it_movies.hasNext()) {
					long itemID = it_movies.nextLong();
					if (!learnedItemIDs.contains(itemID)
							&& !relevantItemIDs.contains(itemID)) {
						// itemID is in NW_{u_i}
						numNW++;

						LongPrimitiveIterator it_test = relevantItemIDs
								.iterator();
						while (it_test.hasNext()) {
							long testItemID = it_test.nextLong();
							float itemPref = 0;
							float testItemPref = 0;
							try {
								itemPref = recommender.estimatePreference(
										userID, itemID);
							} catch (NoSuchItemException e) {
							}
							try {
								testItemPref = recommender.estimatePreference(
										userID, testItemID);
							} catch (NoSuchItemException e) {
							}
							if (itemPref <= testItemPref) {
								sumCheckOrder++;
							}
						}
					}
				}
				if (numNW > 0 && relevantItemIDs.size() > 0) {
					macroDOA.addDatum((double) sumCheckOrder / (double) (relevantItemIDs.size() * numNW));
					microDOA1.addDatum((double) sumCheckOrder);
					microDOA2.addDatum((double) (relevantItemIDs.size() * numNW));
				}
//				log.info(
//						"sumCheckOrder / (numNW * numRelevant) = {} / ({} * {})",
//						sumCheckOrder, numNW, relevantItemIDs.size());

				
				// InnerDOA: only check the agreement of order in test set
				LongPrimitiveIterator it_test1 = relevantItemIDs
						.iterator();
				long sumCheckInnerOrder = 0;
				long sumAll = 0;
				while (it_test1.hasNext()) {
					long itemID1 = it_test1.nextLong();
					LongPrimitiveIterator it_test2 = relevantItemIDs
							.iterator();
					while (it_test2.hasNext()) {
						long itemID2 = it_test2.nextLong();
						if (itemID1 != itemID2) {
							try {
								float pref_v1 = testDataModel.getPreferenceValue(userID, itemID1);
								float pref_v2 = testDataModel.getPreferenceValue(userID, itemID2);
								float predict_v1 = recommender.estimatePreference(userID, itemID1);
								float predict_v2 = recommender.estimatePreference(userID, itemID2);
								if ( (pref_v1 >= pref_v2 && predict_v1 >= predict_v2)
										|| (pref_v1 <= pref_v2 && predict_v1 <= predict_v2) ) {
									sumCheckInnerOrder ++;
								}
								sumAll ++;
							} catch (NoSuchItemException e) {
								// do nothing, just ignore
							}
						}
					}
				}
				if (relevantItemIDs.size() > 1) {
					macroInnerDOA.addDatum((double) sumCheckInnerOrder / (double) sumAll);
					microInnerDOA1.addDatum((double) sumCheckInnerOrder);
					microInnerDOA2.addDatum((double) sumAll);
				}
//				log.info(
//						"sumCheckInnerOrder / (|T| * (|T|-1) ) = {} / ({} * {}) = ",
//						sumCheckInnerOrder, relevantItemIDs.size(), relevantItemIDs.size()-1);
			}

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

	protected FastByIDMap<Preference> getPrefereceMap(
			PreferenceArray userPreferences) {
		FastByIDMap<Preference> map = new FastByIDMap<Preference>();
		for (Preference pref: userPreferences) {
			map.put(pref.getItemID(), pref);
		}
		return map;
	}

	protected static double log2(double value) {
		return Math.log(value) / LOG2;
	}
	
	protected PreferenceArray getPreferenceArray(List<RecommendedItem> recommendedItems, long userID) {
		ArrayList<Preference> userPredictionArray = new ArrayList<Preference>();
		for (int i=0; i<recommendedItems.size(); i++) {
			RecommendedItem item = recommendedItems.get(i);
			userPredictionArray.add(new GenericPreference(userID, item.getItemID(), item.getValue()));
		}
		PreferenceArray userPredictions = new GenericUserPreferenceArray(userPredictionArray);
		userPredictions.sortByValueReversed();
		return userPredictions;
	}
	
	protected double computeNDCG(PreferenceArray userPreferences, PreferenceArray userPredictions, FastIDSet relevantItemIDs, FastByIDMap<Preference> userPreferenceMap, int at) {
		double cumulativeGain = 0.0;
		double idealizedGain = 0.0;
		// compute IDCG
		Iterator<Preference> it_pref = userPreferences.iterator();
		int i = 0;
		while (it_pref.hasNext()) {
			it_pref.next();
			if (i < at) {
				idealizedGain += 1.0 / log2(i + 2.0);;
			} else {
				break;
			}
			i ++;
		}
		// compute DCG
		it_pref = userPredictions.iterator();
		i = 0;
		while (it_pref.hasNext()) {
			Preference pref = it_pref.next();
			long itemID = pref.getItemID();
			if (relevantItemIDs.contains(itemID) && i<at) {
				cumulativeGain += 1.0 / log2(i + 2.0);;
			}
			i ++;
		}
		if (idealizedGain > 0) {
			return cumulativeGain/idealizedGain;
		} else {
			return 0;
		}
	}
		
	protected double computeRNDCG(PreferenceArray userPreferences, PreferenceArray userPredictions, FastIDSet relevantItemIDs, FastByIDMap<Preference> userPreferenceMap, int at) {
		double rCumulativeGain = 0.0;
		double rIdealizedGain = 0.0;
		// compute IDCG
		Iterator<Preference> it_pref = userPreferences.iterator();
		int i = 0;
		while (it_pref.hasNext()) {
			Preference pref = it_pref.next();
			if (i < at) {
				double discount = 1.0 / log2(i + 2.0);
				float rel = pref.getValue();
				rIdealizedGain += rel * discount;
			} else {
				break;
			}
			i ++;
		}
		// compute DCG
		it_pref = userPredictions.iterator();
		i = 0;
		while (it_pref.hasNext()) {
			Preference pref = it_pref.next();
			long itemID = pref.getItemID();
			if (relevantItemIDs.contains(itemID) && i<at) {
				double discount = 1.0 / log2(i + 2.0); 
				float rel = userPreferenceMap.get(itemID).getValue();
				rCumulativeGain += rel * discount;
			}
			i ++;
		}
		if (rIdealizedGain > 0) {
			return rCumulativeGain/rIdealizedGain;
		} else {
			return 0;
		}
	}

}
