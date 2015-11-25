package com.msiiplab.recsys.lfm;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.mahout.cf.taste.common.Refreshable;
import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.eval.RecommenderBuilder;
import org.apache.mahout.cf.taste.impl.common.FastByIDMap;
import org.apache.mahout.cf.taste.impl.common.FastIDSet;
import org.apache.mahout.cf.taste.impl.common.LongPrimitiveIterator;
import org.apache.mahout.cf.taste.impl.recommender.AbstractRecommender;
import org.apache.mahout.cf.taste.impl.recommender.TopItems;
import org.apache.mahout.cf.taste.model.DataModel;
import org.apache.mahout.cf.taste.model.PreferenceArray;
import org.apache.mahout.cf.taste.recommender.IDRescorer;
import org.apache.mahout.cf.taste.recommender.RecommendedItem;
import org.apache.mahout.cf.taste.recommender.Recommender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.msiiplab.recsys.callforpaper.MetaData;
import com.msiiplab.recsys.implicit.ItemPopularityRecommender;

public class SeriesDeadlinePopularityRecommender extends AbstractRecommender {

	public static final Logger log = LoggerFactory.getLogger(SeriesDeadlinePopularityRecommender.class);
	private ItemPopularityRecommender mPopularityDelegate;
	private SeriesDeadlineRecommender mSeriesDeadlineDelegate;
	private FastByIDMap<Long> mSeriesMap;
	private FastByIDMap<List<Long>> mSeriesItems;
	private final boolean mRelative;

	protected SeriesDeadlinePopularityRecommender(DataModel dataModel, int factor, MetaData metaData, boolean relative) throws TasteException {
		super(dataModel);
		mPopularityDelegate = new ItemPopularityRecommender(dataModel);
		mSeriesDeadlineDelegate = new SeriesDeadlineRecommender(dataModel, factor, metaData);
		mSeriesMap = metaData.getSeriesMap();
		mSeriesItems = new FastByIDMap<List<Long>>();
		for (LongPrimitiveIterator it_item = mSeriesMap.keySetIterator(); 
				it_item.hasNext(); ) {
			long itemID = it_item.nextLong();
			long seriesID = mSeriesMap.get(itemID);
			if (mSeriesItems.containsKey(seriesID)) {
				mSeriesItems.get(seriesID).add(itemID);
			} else {
				mSeriesItems.put(seriesID, new ArrayList<Long>());
			}
		}
		mRelative = relative;
	}
	
	protected SeriesDeadlinePopularityRecommender(DataModel dataModel, int factor, MetaData metaData) throws TasteException {
		this(dataModel, factor, metaData, false);
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
		if (mRelative) {
			float popularity = mPopularityDelegate.estimatePreference(userID, itemID);
			long seriesID = mSeriesMap.get(itemID);
			float popSum = 0f;
			for (int i=0; i<mSeriesItems.get(seriesID).size(); i++) {
				popSum += mSeriesItems.get(seriesID).get(i);
			}
			float averagePopularity = popSum / mSeriesItems.get(seriesID).size();
			return  ( popularity / averagePopularity )
					* mSeriesDeadlineDelegate.estimatePreference(userID, itemID);
		} else {
			return mPopularityDelegate.estimatePreference(userID, itemID) 
					* mSeriesDeadlineDelegate.estimatePreference(userID, itemID);
		}
	}

	@Override
	public void refresh(Collection<Refreshable> alreadyRefreshed) {
		mPopularityDelegate.refresh(alreadyRefreshed);
		mSeriesDeadlineDelegate.refresh(alreadyRefreshed);
	}

	public static RecommenderBuilder getRecommenderBuilder(final int factor,
			final MetaData metaData, final boolean relative) {
		return new RecommenderBuilder() {
			@Override
			public Recommender buildRecommender(DataModel dataModel)
					throws TasteException {
				return new SeriesDeadlinePopularityRecommender(dataModel, factor, metaData, relative);
			}
		};
	}


}
