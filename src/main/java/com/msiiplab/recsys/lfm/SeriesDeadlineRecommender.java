package com.msiiplab.recsys.lfm;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.apache.mahout.cf.taste.common.Refreshable;
import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.eval.RecommenderBuilder;
import org.apache.mahout.cf.taste.impl.common.FastByIDMap;
import org.apache.mahout.cf.taste.impl.common.FastIDSet;
import org.apache.mahout.cf.taste.impl.common.LongPrimitiveIterator;
import org.apache.mahout.cf.taste.impl.model.GenericDataModel;
import org.apache.mahout.cf.taste.impl.model.GenericPreference;
import org.apache.mahout.cf.taste.impl.model.GenericUserPreferenceArray;
import org.apache.mahout.cf.taste.impl.recommender.AbstractRecommender;
import org.apache.mahout.cf.taste.impl.recommender.TopItems;
import org.apache.mahout.cf.taste.model.DataModel;
import org.apache.mahout.cf.taste.model.Preference;
import org.apache.mahout.cf.taste.model.PreferenceArray;
import org.apache.mahout.cf.taste.recommender.IDRescorer;
import org.apache.mahout.cf.taste.recommender.RecommendedItem;
import org.apache.mahout.cf.taste.recommender.Recommender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.msiiplab.recsys.callforpaper.DeadlineModel;
import com.msiiplab.recsys.callforpaper.MetaData;

public class SeriesDeadlineRecommender extends AbstractRecommender {
	
	private AspectModelRecommender mSeriesModelRecommender;
	private DeadlineModel mDeadlineModel;
	private boolean mTimeEnabled;
	private FastByIDMap<Long> mTimeMap;
	private FastByIDMap<Long> mSeriesMap;

	public static final Logger log = LoggerFactory.getLogger(SeriesDeadlineRecommender.class);

	public SeriesDeadlineRecommender(DataModel dataModel, int factor, MetaData metaData, boolean timeEnabled) {
		super(dataModel);
		
		mTimeEnabled = timeEnabled;
		
		if (mTimeEnabled) {
			mTimeMap = metaData.getTimeMap();
			FastByIDMap<List<Double>> timeSeriesData = getTimeSeriesData(dataModel, mTimeMap);
			mDeadlineModel = new DeadlineModel(timeSeriesData);
		} else {
			mDeadlineModel = null;
			mTimeMap = null;
		}

		mSeriesMap = metaData.getSeriesMap();
		FastByIDMap<PreferenceArray> userData = getSeriesDataModel(dataModel, mSeriesMap);
		GenericDataModel seriesDataModel = new GenericDataModel(userData);
		mSeriesModelRecommender = new AspectModelRecommender(seriesDataModel, factor);
	}
	
	public SeriesDeadlineRecommender(DataModel dataModel, int factor, MetaData metaData) {
		this(dataModel, factor, metaData, true);
	}
	
	public static FastByIDMap<List<Double>> getTimeSeriesData(DataModel dataModel,
			FastByIDMap<Long> timeMap) {
		try {
			FastByIDMap<List<Double>> timeSeriesData = new FastByIDMap<List<Double>>();
			LongPrimitiveIterator it_user = dataModel.getUserIDs();
			while (it_user.hasNext()) {
				long userID = it_user.nextLong();
				List<Double> timeSeries = new ArrayList<Double>();
				Iterator<Preference> it_pref = dataModel.getPreferencesFromUser(userID).iterator();
				while (it_pref.hasNext()) {
					Preference pref = it_pref.next();
					long itemID = pref.getItemID();
					timeSeries.add((double) timeMap.get(itemID));
				}
				timeSeriesData.put(userID, timeSeries);
			}
			return timeSeriesData;
		} catch (TasteException e) {
			e.printStackTrace();
		}
		return null;
	}

	public static FastByIDMap<PreferenceArray> getSeriesDataModel(
			DataModel dataModel, FastByIDMap<Long> seriesMap) {
		try {
			FastByIDMap<PreferenceArray> seriesDataModel = new FastByIDMap<PreferenceArray>();
			LongPrimitiveIterator it_user = dataModel.getUserIDs();
			while (it_user.hasNext()) {
				long userID = it_user.nextLong();
				List<Preference> list = new ArrayList<Preference>();
				Iterator<Preference> it_pref = dataModel.getPreferencesFromUser(userID).iterator();
				while (it_pref.hasNext()) {
					Preference pref = it_pref.next();
					long itemID = pref.getItemID();
					if (seriesMap.containsKey(itemID)) {
						long seriesID = seriesMap.get(itemID);
						list.add(new GenericPreference(userID, seriesID, 1));
					} else {
						long seriesID = 0L;
						list.add(new GenericPreference(userID, seriesID, 1));
					}
				}
				seriesDataModel.put(userID, new GenericUserPreferenceArray(list));
			}
			return seriesDataModel;
		} catch (TasteException e) {
			e.printStackTrace();
		}
		return null;
	}

	public static RecommenderBuilder getRecommenderBuilder(final int factor, final MetaData metaData, final boolean timeEnabled) {
		return new RecommenderBuilder() {
			@Override
			public Recommender buildRecommender(DataModel dataModel)
					throws TasteException {
				return new SeriesDeadlineRecommender(dataModel, factor, metaData, timeEnabled);
			}
		};
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

	@Override
	public float estimatePreference(long userID, long itemID)
			throws TasteException {
		long seriesID = 0;
		if (mSeriesMap.containsKey(itemID)) {
			seriesID = mSeriesMap.get(itemID);
		}
		double seriesProb = mSeriesModelRecommender.estimatePreference(userID, seriesID);
		
		if (mTimeEnabled) {
			double timestamp = 1261440000.0;
			if (mTimeMap.containsKey(itemID)) {
				timestamp = mTimeMap.get(itemID);
			}
			double timeProb = mDeadlineModel.probability(userID, timestamp);
			return (float)(timeProb * seriesProb);
		} else {
			return (float) seriesProb;
		}
	}

	@Override
	public void refresh(Collection<Refreshable> alreadyRefreshed) {
	}
}
