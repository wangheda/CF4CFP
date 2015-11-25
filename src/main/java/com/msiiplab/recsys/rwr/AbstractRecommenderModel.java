package com.msiiplab.recsys.rwr;

import org.apache.mahout.cf.taste.common.NoSuchItemException;
import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.impl.common.FastByIDMap;

public abstract class AbstractRecommenderModel implements RecommenderModel {
	protected UserPreferenceCache mUserPreferenceCache;
	protected static final float sLoadFactor = 3f;
	protected static final int sInitialSize = 10;

	
	public AbstractRecommenderModel() {
		mUserPreferenceCache = new UserPreferenceCache(sInitialSize, sLoadFactor);
	}
	
	@Override
	public double getCachedPreferencesForUserAndItem(long userID, long itemID)
			throws TasteException {
		FastByIDMap<Double> userPreference = getCachedPreferencesForUser(userID);
		if (userPreference.containsKey(itemID)) {
			return userPreference.get(itemID);
		} else {
			throw new NoSuchItemException();
		}
	}

	@Override
	public FastByIDMap<Double> getCachedPreferencesForUser(long userID)
			throws TasteException {
		FastByIDMap<Double> map = mUserPreferenceCache.getCache(userID);
		if (map != null) {
			return map;
		} else {
			FastByIDMap<Double> preferences = getPreferencesForUser(userID);
			mUserPreferenceCache.setCache(userID, preferences);
			return preferences;
		}
	}

	abstract protected FastByIDMap<Double> getPreferencesForUser(long userID)
			throws TasteException;
}
