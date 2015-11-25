package com.msiiplab.recsys.rwr;

import java.util.Collection;
import java.util.List;

import org.apache.mahout.cf.taste.common.NoSuchItemException;
import org.apache.mahout.cf.taste.common.Refreshable;
import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.eval.RecommenderBuilder;
import org.apache.mahout.cf.taste.impl.common.FastIDSet;
import org.apache.mahout.cf.taste.impl.recommender.AbstractRecommender;
import org.apache.mahout.cf.taste.impl.recommender.TopItems;
import org.apache.mahout.cf.taste.model.DataModel;
import org.apache.mahout.cf.taste.model.PreferenceArray;
import org.apache.mahout.cf.taste.recommender.IDRescorer;
import org.apache.mahout.cf.taste.recommender.RecommendedItem;
import org.apache.mahout.cf.taste.recommender.Recommender;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import com.google.common.base.Preconditions;

public class PersonalizedPageRankRecommender extends AbstractRecommender{
	
	private final RecommenderModel mModel;
	
	public static final Logger log = LoggerFactory.getLogger(PersonalizedPageRankRecommender.class);

	public PersonalizedPageRankRecommender(DataModel dataModel, RecommenderModel model) {
		super(dataModel);
		// TODO Auto-generated constructor stub
		mModel = model;
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

	@Override
	public float estimatePreference(long userID, long itemID)
			throws TasteException {
		try {
			return (float) mModel.getCachedPreferencesForUserAndItem(userID, itemID);
		} catch (NoSuchItemException e) {
			return 0;
		}
	}

	@Override
	public void refresh(Collection<Refreshable> alreadyRefreshed) {
		// TODO Auto-generated method stub
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
	
	public static RecommenderBuilder getRecommenderBuilder() {
		return new RecommenderBuilder() {
			@Override
			public Recommender buildRecommender(DataModel dataModel)
					throws TasteException {
				return new PersonalizedPageRankRecommender( dataModel, 
						PersonalizedPageRankModel.getInstance(dataModel));
			}
		};
	}
}
