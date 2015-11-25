package com.msiiplab.recsys.rwr;


import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.impl.common.FastByIDMap;


public interface RecommenderModel {
	
	public double getCachedPreferencesForUserAndItem(long userID, long itemID) throws TasteException;
	
	public FastByIDMap<Double> getCachedPreferencesForUser(long userID) throws TasteException;
	
}
