package com.msiiplab.recsys.callforpaper;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.apache.mahout.cf.taste.impl.common.FastByIDMap;
import org.apache.mahout.cf.taste.impl.model.file.FileDataModel;
import org.apache.mahout.cf.taste.model.DataModel;
import org.junit.Test;

import com.msiiplab.recsys.lfm.SeriesDeadlineRecommender;

public class DeadlineModelTest {

	@Test
	public void test() throws IOException {
		DataModel dataModel = new FileDataModel(new File("train1"));
		FastByIDMap<Long> timeMap = new MetaData("item.txt").getTimeMap();
		FastByIDMap<List<Double>> timeSeriesData = SeriesDeadlineRecommender.getTimeSeriesData(dataModel, timeMap);
		DeadlineModel deadlineModel = new DeadlineModel(timeSeriesData);
		deadlineModel.getClass();
	}

}
