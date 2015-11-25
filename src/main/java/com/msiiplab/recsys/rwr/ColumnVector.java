package com.msiiplab.recsys.rwr;

import java.util.Iterator;

import org.apache.mahout.cf.taste.impl.common.FastByIDMap;


public class ColumnVector {
	private static final int sInitialSize = 10;
	private static final float sLoadFactor = 2f;
	protected FastByIDMap<Double> mVector;
	
	public ColumnVector clone() {
		ColumnVector newVector = new ColumnVector(this);
		return newVector;
	}
	
	public ColumnVector() {
		mVector = new FastByIDMap<Double>(sInitialSize, sLoadFactor);
	}
	
	private ColumnVector(ColumnVector vector) {
		mVector = vector.mVector.clone();
	}
	
	public ColumnVector(FastByIDMap<Double> vector) {
		mVector = vector;
	}
	
	public FastByIDMap<Double> toFastByIDMap() {
		return mVector;
	}
	
	public Double get(Long key) {
		return mVector.get(key);
	}
	
	public ColumnVector add(final ColumnVector A, final ColumnVector B) {
		ColumnVector C = new ColumnVector();
		C.dotAdd(A);
		C.dotAdd(B);
		return C;
	}
	
	// in place add 
	public ColumnVector dotAdd(final ColumnVector addtion) {
		Iterator<Long> ids = addtion.mVector.keySetIterator();
		while (ids.hasNext()) {
			long id = ids.next();
			if (mVector.containsKey(id)) {
				mVector.put(id, addtion.mVector.get(id) + mVector.get(id) );
			} else {
				mVector.put(id, addtion.mVector.get(id));
			}
		}
		return this;
	}
	
	// in place multiply a number
	public ColumnVector dotMul(double beta) {
		Iterator<Long> ids = mVector.keySetIterator();
		while (ids.hasNext()) {
			long id = ids.next();
			mVector.put(id, (double) (beta * mVector.get(id)));
		}
		return this;
	}
}
