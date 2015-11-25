package com.msiiplab.recsys.implicit;

import java.util.Collection;
import java.util.List;

import org.apache.mahout.cf.taste.common.NoSuchItemException;
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

public class ItemPopularityRecommender extends AbstractRecommender {
	
	private final FastByIDMap<Long> mItemPopularity;

	public ItemPopularityRecommender(DataModel dataModel) throws TasteException {
		super(dataModel);
		mItemPopularity = new FastByIDMap<Long>();
		LongPrimitiveIterator it_item = getDataModel().getItemIDs();
		while (it_item.hasNext()) {
			long itemID = it_item.nextLong();
			PreferenceArray itemPrefArray;
			try {
				itemPrefArray = getDataModel().getPreferencesForItem(itemID);
			} catch (NoSuchItemException e) {
				itemPrefArray = null;
			}
			if (itemPrefArray == null) {
				mItemPopularity.put(itemID, 0L);
			} else {
				mItemPopularity.put(itemID, (long) itemPrefArray.length());
			}
		}
	}

	@Override
	public List<RecommendedItem> recommend(long userID, int howMany,
			IDRescorer rescorer) throws TasteException {
		FastIDSet candidates = getAllOtherItems(userID, getDataModel().getPreferencesFromUser(userID));
		List<RecommendedItem> topItems = TopItems.getTopItems(howMany, candidates.iterator(), null, new Estimator());
		return topItems;
	}
	
	@Override
	public float estimatePreference(long userID, long itemID)
			throws TasteException {
		if (mItemPopularity.containsKey(itemID)) {
			return (float) mItemPopularity.get(itemID);
		} else {
			return 0;
		}
	}

	@Override
	public void refresh(Collection<Refreshable> alreadyRefreshed) {
	}
	
	private final class Estimator implements TopItems.Estimator<Long> {
	    
	    @Override
	    public double estimate(Long itemID) {
	      return doEstimatePreference(itemID);
	    }
	}

	public double doEstimatePreference(Long itemID) {
		try {
			return (double) estimatePreference(0, itemID);			
		} catch (NoSuchItemException e) {
			return 0;
		} catch (TasteException e) {
			e.printStackTrace();
			return 0;
		}
	}
	
	public static RecommenderBuilder getRecommenderBuilder() {
		return new RecommenderBuilder() {
			@Override
			public Recommender buildRecommender(DataModel dataModel)
					throws TasteException {
				return new ItemPopularityRecommender(dataModel);
			}
		};
	}

}
