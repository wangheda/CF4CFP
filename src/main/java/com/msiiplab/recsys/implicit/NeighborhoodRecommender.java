package com.msiiplab.recsys.implicit;

import java.util.Collection;
import java.util.List;

import org.apache.mahout.cf.taste.common.Refreshable;
import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.eval.RecommenderBuilder;
import org.apache.mahout.cf.taste.impl.neighborhood.NearestNUserNeighborhood;
import org.apache.mahout.cf.taste.impl.recommender.AbstractRecommender;
import org.apache.mahout.cf.taste.impl.recommender.GenericUserBasedRecommender;
import org.apache.mahout.cf.taste.impl.similarity.TanimotoCoefficientSimilarity;
import org.apache.mahout.cf.taste.model.DataModel;
import org.apache.mahout.cf.taste.neighborhood.UserNeighborhood;
import org.apache.mahout.cf.taste.recommender.IDRescorer;
import org.apache.mahout.cf.taste.recommender.RecommendedItem;
import org.apache.mahout.cf.taste.recommender.Recommender;
import org.apache.mahout.cf.taste.similarity.UserSimilarity;

public class NeighborhoodRecommender extends AbstractRecommender {
	
	private GenericUserBasedRecommender delegate; 
	
	public NeighborhoodRecommender(DataModel dataModel, int neighbor) throws TasteException {
		super(dataModel);
		UserSimilarity similarity = new TanimotoCoefficientSimilarity(dataModel);
		UserNeighborhood neighborhood = new NearestNUserNeighborhood(neighbor, similarity, dataModel);
		delegate = new GenericUserBasedRecommender(dataModel, neighborhood, similarity);
	}

	@Override
	public List<RecommendedItem> recommend(long userID, int howMany,
			IDRescorer rescorer) throws TasteException {
		List<RecommendedItem> items = delegate.recommend(userID, howMany, rescorer);
		return items;
	}

	@Override
	public float estimatePreference(long userID, long itemID)
			throws TasteException {
		return delegate.estimatePreference(userID, itemID);
	}

	@Override
	public void refresh(Collection<Refreshable> alreadyRefreshed) {
		delegate.refresh(alreadyRefreshed);
	}
	
	public static RecommenderBuilder getRecommenderBuilder(final int neighbor) {
		return new RecommenderBuilder() {
			@Override
			public Recommender buildRecommender(DataModel dataModel)
					throws TasteException {
				return new NeighborhoodRecommender(dataModel, neighbor);
			}
		};
	}

}
