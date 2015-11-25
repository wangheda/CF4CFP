package com.msiiplab.recsys.implicit;

import java.util.Collection;
import java.util.List;

import org.apache.mahout.cf.taste.common.Refreshable;
import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.eval.RecommenderBuilder;
import org.apache.mahout.cf.taste.impl.neighborhood.NearestNUserNeighborhood;
import org.apache.mahout.cf.taste.impl.recommender.AbstractRecommender;
import org.apache.mahout.cf.taste.impl.recommender.GenericUserBasedRecommender;
import org.apache.mahout.cf.taste.model.DataModel;
import org.apache.mahout.cf.taste.neighborhood.UserNeighborhood;
import org.apache.mahout.cf.taste.recommender.IDRescorer;
import org.apache.mahout.cf.taste.recommender.RecommendedItem;
import org.apache.mahout.cf.taste.recommender.Recommender;
import org.apache.mahout.cf.taste.similarity.UserSimilarity;

public class NeighborhoodIDFRecommender extends AbstractRecommender {
	
	private GenericUserBasedRecommender delegate; 
	
	public NeighborhoodIDFRecommender(DataModel dataModel, int neighbor, int similarityNo) throws TasteException {
		super(dataModel);
		UserSimilarity similarity = null;
		if (similarityNo == 1) {
			similarity = new TanimotoIDFCoefficientSimilarity(dataModel);
		} else if (similarityNo == 2) {
			similarity = new TanimotoIDF2CoefficientSimilarity(dataModel);
		} else if (similarityNo == 3) {
			similarity = new TanimotoIDF3CoefficientSimilarity(dataModel);
		} else {
			throw new TasteException("Wrong parameter");
		}
		UserNeighborhood neighborhood = new NearestNUserNeighborhood(neighbor, similarity, dataModel);
		delegate = new GenericUserBasedRecommender(dataModel, neighborhood, similarity);
	}
	
	public NeighborhoodIDFRecommender(DataModel dataModel, int neighbor) throws TasteException {
		this(dataModel, neighbor, 1); // default is No.1 similarity
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
	
	public static RecommenderBuilder getRecommenderBuilder(final int neighbor, final int similarityNo) {
		return new RecommenderBuilder() {
			@Override
			public Recommender buildRecommender(DataModel dataModel)
					throws TasteException {
				return new NeighborhoodIDFRecommender(dataModel, neighbor, similarityNo);
			}
		};
	}

}
