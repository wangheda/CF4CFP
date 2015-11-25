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

package com.msiiplab.recsys.implicit;

import java.util.ArrayList;
import java.util.Collection;
import org.apache.mahout.cf.taste.common.Refreshable;
import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.impl.common.FastByIDMap;
import org.apache.mahout.cf.taste.impl.common.FastIDSet;
import org.apache.mahout.cf.taste.impl.common.LongPrimitiveIterator;
import org.apache.mahout.cf.taste.impl.common.RefreshHelper;
import org.apache.mahout.cf.taste.impl.similarity.AbstractItemSimilarity;
import org.apache.mahout.cf.taste.model.DataModel;
import org.apache.mahout.cf.taste.model.Preference;
import org.apache.mahout.cf.taste.model.PreferenceArray;
import org.apache.mahout.cf.taste.similarity.PreferenceInferrer;
import org.apache.mahout.cf.taste.similarity.UserSimilarity;

import com.msiiplab.recsys.lfm.AspectModelRecommender;

/**
 * <p>
 * An implementation of a "similarity" based on the <a href=
 * "http://en.wikipedia.org/wiki/Jaccard_index#Tanimoto_coefficient_.28extended_Jaccard_coefficient.29"
 * > Tanimoto coefficient</a>, or extended <a
 * href="http://en.wikipedia.org/wiki/Jaccard_index">Jaccard coefficient</a>.
 * </p>
 * 
 * <p>
 * This is intended for "binary" data sets where a user either expresses a
 * generic "yes" preference for an item or has no preference. The actual
 * preference values do not matter here, only their presence or absence.
 * </p>
 * 
 * <p>
 * The value returned is in [0,1].
 * </p>
 * 
 * <p>
 * Modified by Heda Wang, by adding LFM weight to items.
 * </p>
 */
public final class TanimotoLFMCoefficientSimilarity extends
		AbstractItemSimilarity implements UserSimilarity {
	
	private FastByIDMap<Double> mUserPrefEntropy;
	private FastByIDMap<Double> mItemPrefEntropy;
	
	private AspectModelRecommender mAspectModelRecommender;
	private final int mFactor;
	
	private void refreshAspectModel() throws TasteException {
		mAspectModelRecommender = new AspectModelRecommender(getDataModel(), mFactor);
		mUserPrefEntropy = new FastByIDMap<Double>();
		mItemPrefEntropy = new FastByIDMap<Double>();
		int numOfLatentFactor = mAspectModelRecommender.getNumOfLatentFactor();
		ArrayList<FastByIDMap<Double>> userConditional = mAspectModelRecommender.getUserConditional();
		ArrayList<FastByIDMap<Double>> itemConditional = mAspectModelRecommender.getItemConditional();
		ArrayList<Double> latentPrior = mAspectModelRecommender.getLatentPrior();
		double[] distribution = new double[numOfLatentFactor];
		for (LongPrimitiveIterator it_user = getDataModel().getUserIDs(); it_user.hasNext();) {
			long userID = it_user.nextLong();
			for (int i = 0; i < distribution.length; i++) {
				distribution[i] = userConditional.get(i).get(userID) * latentPrior.get(i);
			}
			double entropy = getEntropy(distribution);
			mUserPrefEntropy.put(userID, entropy);
		}
		for (LongPrimitiveIterator it_item = getDataModel().getItemIDs(); it_item.hasNext();) {
			long itemID = it_item.nextLong();
			for (int i = 0; i < distribution.length; i++) {
				distribution[i] = itemConditional.get(i).get(itemID) * latentPrior.get(i);
			}
			double entropy = getEntropy(distribution);
			mItemPrefEntropy.put(itemID, entropy);
		}
	}
	
	private double getEntropy(double[] distribution) {
		double sum = 0;
		double entropy = 0;
		synchronized (distribution) {
			for (int i = 0; i < distribution.length; i++) {
				sum += distribution[i];
			}
			for (int i = 0; i < distribution.length; i++) {
				double p = distribution[i] / sum;
				if (p != 0) {
					entropy += - p * Math.log(p);
				} else {
					p = Double.MIN_NORMAL;
					entropy += - p * Math.log(p);
				}
			}
		}
		return entropy;
	}

	public TanimotoLFMCoefficientSimilarity(DataModel dataModel, int factor) {
		super(dataModel);
		mFactor = factor;
		try {
			refreshAspectModel();
		} catch (TasteException e) {
			e.printStackTrace();
		}
	}

	/**
	 * @throws UnsupportedOperationException
	 */
	@Override
	public void setPreferenceInferrer(PreferenceInferrer inferrer) {
		throw new UnsupportedOperationException();
	}

	@Override
	public double userSimilarity(long userID1, long userID2)
			throws TasteException {

		DataModel dataModel = getDataModel();
		
		FastIDSet xPrefs = dataModel.getItemIDsFromUser(userID1);
		FastIDSet yPrefs = dataModel.getItemIDsFromUser(userID2);

		int xPrefsSize = xPrefs.size();
		int yPrefsSize = yPrefs.size();
		if (xPrefsSize == 0 && yPrefsSize == 0) {
			return Double.NaN;
		}
		if (xPrefsSize == 0 || yPrefsSize == 0) {
			return 0.0;
		}
		
		double intersection = 0.0;
		double union = 0.0;
		
		for (LongPrimitiveIterator it_item = xPrefs.iterator(); it_item.hasNext();) {
			long itemID = (long) it_item.nextLong();
			double weight = mItemPrefEntropy.get(itemID);
			if (yPrefs.contains(itemID)) {
				intersection += weight;
				union -= weight;
			}
			union += weight;
		}
		for (LongPrimitiveIterator it_item = yPrefs.iterator(); it_item.hasNext();) {
			long itemID = (long) it_item.nextLong();
			double weight = mItemPrefEntropy.get(itemID);
			union += weight;
		}

		return intersection / union;
	}

	@Override
	public double itemSimilarity(long itemID1, long itemID2)
			throws TasteException {
		FastIDSet preferring1 = toUserFastIDSet(getDataModel().getPreferencesForItem(itemID1));
		return doItemSimilarity(itemID1, itemID2, preferring1);
	}
	
	private FastIDSet toUserFastIDSet(PreferenceArray array) {
		FastIDSet fastIDSet = new FastIDSet();
		for (Preference preference : array) {
			fastIDSet.add(preference.getUserID());
		}
		return fastIDSet;
	}

	@Override
	public double[] itemSimilarities(long itemID1, long[] itemID2s)
			throws TasteException {
		FastIDSet preferring1 = toUserFastIDSet(getDataModel().getPreferencesForItem(itemID1));
		int length = itemID2s.length;
		double[] result = new double[length];
		for (int i = 0; i < length; i++) {
			result[i] = doItemSimilarity(itemID1, itemID2s[i], preferring1);
		}
		return result;
	}

	private double doItemSimilarity(long itemID1, long itemID2, FastIDSet preferring1)
			throws TasteException {
		double intersection = 0.0;
		double union = 0.0;
		
		for (Preference pref : getDataModel().getPreferencesForItem(itemID2)) {
			long userID = pref.getUserID();
			double weight = mUserPrefEntropy.get(userID);
			if (preferring1.contains(userID)) {
				intersection += weight;
				union -= weight;
			}
			union += weight;
		}
		for (LongPrimitiveIterator it_user = preferring1.iterator(); it_user.hasNext();) {
			long userID = (long) it_user.nextLong();
			double weight = mUserPrefEntropy.get(userID);
			union += weight;
		}
		
		if (intersection == 0) {
			return Double.NaN;
		}
		
		return intersection / union;
	}

	@Override
	public void refresh(Collection<Refreshable> alreadyRefreshed) {
		alreadyRefreshed = RefreshHelper.buildRefreshed(alreadyRefreshed);
		RefreshHelper.maybeRefresh(alreadyRefreshed, getDataModel());
		try {
			refreshAspectModel();
		} catch (TasteException e) {
			e.printStackTrace();
		}
	}

	@Override
	public String toString() {
		return "TanimotoCoefficientSimilarity[dataModel:" + getDataModel()
				+ ']';
	}

}
