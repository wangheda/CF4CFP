package com.msiiplab.recsys.rwr;

import org.apache.mahout.cf.taste.common.NoSuchItemException;
import org.apache.mahout.cf.taste.impl.common.FastByIDMap;

public class UserPreferenceCache {
	private final FastByIDMap<FastByIDMap<Double>> mCache;
	
	public UserPreferenceCache(int initialSize, float loadFactor) {
		mCache = new FastByIDMap<FastByIDMap<Double>>(initialSize, loadFactor);
	}
	
	public synchronized FastByIDMap<Double> getCache(long userID) throws NoSuchItemException {
		if (mCache.containsKey(userID)) {
			return mCache.get(userID);
		} else {
			return null;
		}
	}
	
	public synchronized void setCache(long userID, FastByIDMap<Double> preferenceArray) {
		mCache.put(userID, preferenceArray);
	}
}