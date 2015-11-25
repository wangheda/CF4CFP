package com.msiiplab.recsys.implicit;

import java.util.Collection;
import java.util.List;

import org.apache.mahout.cf.taste.common.Refreshable;
import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.eval.RecommenderBuilder;
import org.apache.mahout.cf.taste.impl.common.FastByIDMap;
import org.apache.mahout.cf.taste.impl.common.FastIDSet;
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
import com.msiiplab.recsys.callforpaper.MetaData;
import com.msiiplab.recsys.lfm.SeriesDeadlineRecommender;
import com.msiiplab.recsys.lfm.SeriesDeadlineRecommender.Estimator;

public class NameRecommender extends AbstractRecommender {

	private FastByIDMap<Long> mSeriesMap;

	public static final Logger log = LoggerFactory.getLogger(NameRecommender.class);

	protected NameRecommender(DataModel dataModel, MetaData metaData) {
		super(dataModel);
		// TODO Auto-generated constructor stub
		mSeriesMap = metaData.getSeriesMap();
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
		float num = 0;
		for (Preference pref: getDataModel().getPreferencesFromUser(userID)) {
			long itemID_pref = pref.getItemID();
			long seriesID_pref = mSeriesMap.get(itemID_pref);
			long seriesID = mSeriesMap.get(itemID);
			if (seriesID == seriesID_pref) {
				num ++;
			}
		}
		return num;
	}

	@Override
	public void refresh(Collection<Refreshable> alreadyRefreshed) {
	}
	
	public static RecommenderBuilder getRecommenderBuilder(final MetaData metaData) {
		return new RecommenderBuilder() {
			@Override
			public Recommender buildRecommender(DataModel dataModel)
					throws TasteException {
				return new NameRecommender(dataModel, metaData);
			}
		};
	}

}
