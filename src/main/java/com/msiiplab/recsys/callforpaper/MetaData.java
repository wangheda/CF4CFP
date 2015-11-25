package com.msiiplab.recsys.callforpaper;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import org.apache.mahout.cf.taste.impl.common.FastByIDMap;

public class MetaData {
	
	private FastByIDMap<Long> mSeriesMap;
	private FastByIDMap<Long> mTimeMap;
	private FastByIDMap<String> mNameMap;
	
	public FastByIDMap<Long> getSeriesMap() {
		return mSeriesMap;
	}
	
	public FastByIDMap<Long> getTimeMap() {
		return mTimeMap;
	}
	
	public FastByIDMap<String> getNameMap() {
		return mNameMap;
	}
	
	public MetaData(File file) {
		mSeriesMap = new FastByIDMap<Long>();
		mTimeMap = new FastByIDMap<Long>();
		mNameMap = new FastByIDMap<String>();
		try {
			readFromFile(file);
		} catch (FormatException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public MetaData(String string) {
		this(new File(string));
	}

	public void readFromFile(File file) throws IOException, FormatException {
		FileReader fReader = null;
		BufferedReader reader = null;
		try { 
			fReader = new FileReader(file);
			reader = new BufferedReader(fReader);
			String line;
			while ((line = reader.readLine()) != null) {
				String[] tuple = line.trim().split("\t");
				if (tuple.length != 4) {
					throw new FormatException();
				}
				long cfpID = Long.parseLong(tuple[0]);
				long seriesID = Long.parseLong(tuple[1]);
				long timestamp = Long.parseLong(tuple[2]);
				String cfpName = tuple[3];
				mSeriesMap.put(cfpID, seriesID);
				mTimeMap.put(cfpID, timestamp);
				mNameMap.put(cfpID, cfpName);
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (reader != null) {
				reader.close();
			}
		}
	}
	
	public class FormatException extends IOException {
		private static final long serialVersionUID = -4962953617338176096L;
	}


}
