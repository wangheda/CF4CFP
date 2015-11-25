package com.msiiplab.recsys.rwr;

import org.apache.mahout.cf.taste.common.NoSuchUserException;
import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.impl.common.FastByIDMap;
import org.apache.mahout.cf.taste.model.DataModel;
import org.apache.mahout.cf.taste.model.Preference;
import org.apache.mahout.cf.taste.model.PreferenceArray;

import java.util.Iterator;

public class PersonalizedPageRankModel extends AbstractRecommenderModel {
	
	private final StochasticMatrix mStochasticMatrix;
	private final DataModel mDataModel;
	private final ColumnVector UnificationRank;
	private final UserPreferenceCache mPersonalizedRankCache;
	private final FastByIDMap<Double> mPrefSumCache;
	private ColumnVector mReWeight;
	private static final double sAlpha = 0.9;
	private static final int sWalkingSteps = 6;
	
	private PersonalizedPageRankModel(DataModel dataModel) throws TasteException {
		super();
		mReWeight = null;
		new FastByIDMap<Double>();
		mPersonalizedRankCache = new UserPreferenceCache(sInitialSize, sLoadFactor);
		mPrefSumCache = new FastByIDMap<Double>();
		mStochasticMatrix = new StochasticMatrix(10, sLoadFactor);
		FastByIDMap<Double> UnifiedRank = new FastByIDMap<Double>();
		mDataModel = dataModel;
		Iterator<Long> items = dataModel.getItemIDs();
		double numItems = dataModel.getNumItems();
		while ( items.hasNext() ) {
			long itemID = items.next();
			Iterator<Long> items2 = dataModel.getItemIDs();
			while ( items2.hasNext() ) {
				long itemID2 = items2.next();
				int userNum = dataModel.getNumUsersWithPreferenceFor(itemID, itemID2);
				if (userNum > 0) {
					mStochasticMatrix.put(itemID, itemID2, userNum);
				}
			}
			UnifiedRank.put(itemID, (double) 1.0/ (double) numItems);
		}
		UnificationRank = new ColumnVector(UnifiedRank);
	}
	
	private PersonalizedPageRankModel(DataModel dataModel, FastByIDMap<Double> reWeight) throws TasteException {
		this(dataModel);
		mReWeight = new ColumnVector(reWeight);
	}

	@Override
	protected FastByIDMap<Double> getPreferencesForUser(long userID) throws TasteException {
		try {
			FastByIDMap<Double> PersonalizedRank = getPersonalizedRank(userID);
			FastByIDMap<Double> PersonalizedPageRank = RandomWalk(
						UnificationRank, 
						new ColumnVector(PersonalizedRank)
					).toFastByIDMap();
			return PersonalizedPageRank;
		} catch (NoSuchUserException e) {
			FastByIDMap<Double> PersonalizedPageRank = new FastByIDMap<Double>();
			return PersonalizedPageRank;
		}
	}
	
	public double getPrefSum(long userID) throws TasteException {
		try {
			if (!mPrefSumCache.containsKey(userID)) {
				// In this function we compute prefSum and store it to cache
				getPersonalizedRank(userID);
			}
			return mPrefSumCache.get(userID);
		} catch (NoSuchUserException e) {
			return 0.0;
		}
	}
	
	private FastByIDMap<Double> getPersonalizedRank(long userID) throws TasteException {
		FastByIDMap<Double> PersonalizedRank = mPersonalizedRankCache.getCache(userID);
		if (PersonalizedRank == null) {
			PersonalizedRank = new FastByIDMap<Double>();
			PreferenceArray array = mDataModel.getPreferencesFromUser(userID);
			Iterator<Preference> it = array.iterator();
			double prefSum = 0;
			while (it.hasNext()) {
				// multiply mReWeight to personalized rank ( in other words, bias )
				Preference pref = it.next();
				double prefValue;
				if (mReWeight != null) {
					prefValue = (double) pref.getValue() * mReWeight.get(pref.getItemID());
				} else {
					prefValue = (double) pref.getValue();
				}
				PersonalizedRank.put(pref.getItemID(), prefValue);
				prefSum += prefValue;
			}
			if (prefSum > 0) {
				mPrefSumCache.put(userID, prefSum);
				PersonalizedRank = new ColumnVector(PersonalizedRank).dotMul(1.0/prefSum).toFastByIDMap();
			} else {
				// No Preference Data! Do something here!
				throw new NoSuchUserException(userID);
			}
			mPersonalizedRankCache.setCache(userID, PersonalizedRank);
		}
		return PersonalizedRank;
	}
	
	public ColumnVector RandomWalk ( final ColumnVector InitialRank, 
									final ColumnVector PersonalizedRank ) throws TasteException {
		ColumnVector PR = Walk(InitialRank, PersonalizedRank, sAlpha);
		for (int i=1; i<sWalkingSteps; i++) {
			PR = Walk(PR, PersonalizedRank, sAlpha);
		}
	 	return PR;
	}
	
	private ColumnVector Walk ( final ColumnVector InitialRank, 
			final ColumnVector PersonalizedRank,
			double alpha) throws TasteException {
		// NR = a*SM*IR + (1-a)*P 
		//    = (1-a)( a/(1-a)*SM*IR + P )
		ColumnVector NextRank = mStochasticMatrix
				.mul(InitialRank)
				.dotMul(alpha / (1.0-alpha))
				.dotAdd(PersonalizedRank)
				.dotMul(1.0-alpha);
		return NextRank;
	}
	
	public class StochasticMatrix {
		private final FastByIDMap<FastByIDMap<Long>> mMatrix;
		private final FastByIDMap<Long> mColSum;
		
		public StochasticMatrix(int size, double loadFactor) {
			mMatrix = new FastByIDMap<FastByIDMap<Long>>(sInitialSize, sLoadFactor);
			mColSum = new FastByIDMap<Long>(sInitialSize, sLoadFactor);
		}
		
		public int getSize() {
			return mMatrix.size();
		}
		
		public void put(long i, long j, long value) {
			if (value > 0) {
				if (mMatrix.containsKey(i)) {
					if (mMatrix.get(i).containsKey(j)) {
						long oldValue = mMatrix.get(i).get(j);
						addToColSum(i, value-oldValue);
					} else {
						addToColSum(i, value);
					}
					mMatrix.get(i).put(j, value);
				} else {
					FastByIDMap<Long> temp = new FastByIDMap<Long>();
					temp.put(j, value);
					mMatrix.put(i, temp);
					addToColSum(i, value);
				}
			}
		}
		
		private Iterator<Long> getRows(long i) {
			if (mMatrix.containsKey(i)) {
				return mMatrix.get(i).keySetIterator();
			} else {
				return null;
			}
		}
		
		public long get(long i, long j) {
			if (mMatrix.containsKey(i)) {
				if (mMatrix.get(i).containsKey(j)) {
					return mMatrix.get(i).get(j);
				} else {
					return 0;
				}
			} else {
				return 0;
			}
		}
		
		// get Normalized value (by column summation)
		public double getNormalized(long i, long j) throws TasteException {
			long value = get(i, j);
			long sum = getColSum(i);
			if (sum > 0) {
				return (double) value / sum;
			} else {
				if (value == 0) {
					return 0;
				} else {
					throw new TasteException("column sum <= 0, while value != 0");
				}
			}
		}
		
		public long getColSum(long i) {
			if (mColSum.containsKey(i)) {
				return mColSum.get(i);
			} else {
				return 0;
			}
		}
		
		private long addToColSum(long i, long addition) {
			if (mColSum.containsKey(i)) {
				long origin = mColSum.get(i);
				mColSum.put(i, origin+addition);
				return origin+addition;
			} else {
				mColSum.put(i, addition);
				return addition;
			}
		}
		
		public ColumnVector mul(ColumnVector V) throws TasteException {
			Iterator<Long> i_set = V.mVector.keySetIterator();
			FastByIDMap<Double> vector = new FastByIDMap<Double>(sInitialSize, sLoadFactor);
			while (i_set.hasNext()) {
				long i = i_set.next();
				double V_i = V.mVector.get(i);
				Iterator<Long> j_set = getRows(i);
				if (j_set != null) {
					while (j_set.hasNext()) {
						long j = j_set.next();
						double value = getNormalized(i, j)*V_i;
						// add value to v(j)
						if (vector.containsKey(j)) {
							vector.put(j, value + vector.get(j));
						} else {
							vector.put(j, value);
						}
					}
				}
			}
			return new ColumnVector(vector);
		}
		
		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder();
			Iterator<Long> i_set = mMatrix.keySetIterator();
			while (i_set.hasNext()) {
				long i = i_set.next();
				Iterator<Long> j_set = mMatrix.get(i).keySetIterator();
				while (j_set.hasNext()) {
					long j = j_set.next();
					long v = mMatrix.get(i).get(j);
					sb.append(i);
					sb.append(",");
					sb.append(j);
					sb.append(",");
					sb.append(v);
					sb.append("\n");
				}
			}
			return sb.toString();
		}
	}
	
	public static PersonalizedPageRankModel getInstance(DataModel dataModel) {
		try {
			return new PersonalizedPageRankModel(dataModel);
		} catch (TasteException e) {
			e.printStackTrace();
			return null;
		}
	}

	/**
	 * @param dataModel data model
	 * @param reWeight weight factor of each item, dot multiplied to personalized rank
	 * @return a PersonalizedPageRankModel
	 */
	public static PersonalizedPageRankModel getInstance(DataModel dataModel, FastByIDMap<Double> reWeight) {
		try {
			return new PersonalizedPageRankModel(dataModel, reWeight);
		} catch (TasteException e) {
			e.printStackTrace();
			return null;
		}
	}

	/**
	 * @return the stochasticMatrix
	 */
	public StochasticMatrix getStochasticMatrix() {
		return mStochasticMatrix;
	}

	/**
	 * @return the dataModel
	 */
	public DataModel getDataModel() {
		return mDataModel;
	}
}
