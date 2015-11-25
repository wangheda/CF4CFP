package com.msiiplab.recsys.rwr;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.eval.RecommenderBuilder;
import org.apache.mahout.cf.taste.impl.eval.GenericRecommenderIRStatsEvaluator;
import org.apache.mahout.cf.taste.impl.model.file.FileDataModel;
import org.apache.mahout.cf.taste.model.DataModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NFoldTest {
	
	private List<String> mTrainingPathList;
	private List<String> mTestingPathList;
	private boolean mFlagOutput;
	Logger log = LoggerFactory.getLogger(NFoldTest.class);
	
	public NFoldTest() {
		this(null, false);
	}
	
	public NFoldTest(List<String> pathList) {
		this(pathList, false);
	}
	
	public NFoldTest(List<String> pathList, boolean flagOutput) {
		mFlagOutput = flagOutput;
		if (pathList != null) {
			mTrainingPathList = new ArrayList<String>();
			mTestingPathList = new ArrayList<String>();
			for (int i=0; i<pathList.size(); i++) {
				if (i%2 == 0) {
					mTrainingPathList.add(pathList.get(i));
				} else {
					mTestingPathList.add(pathList.get(i));
				}
			}
		}
	}
	
	public void test(RecommenderBuilder builder, String recommenderType, int at) throws IOException, TasteException {
		GLRecommenderIRStatsEvaluator evaluator = new GLRecommenderIRStatsEvaluator();
		test(builder, recommenderType, at, evaluator);
	}
	
	public void testInParallel(RecommenderBuilder builder, String recommenderType, int at) throws IOException, TasteException {
		GLRecommenderIRStatsEvaluator evaluator = new ParallelGLRecommenderIRStatsEvaluator();
		test(builder, recommenderType, at, evaluator);
	}
	
	public void test(RecommenderBuilder builder, String recommenderType, int at, GLRecommenderIRStatsEvaluator evaluator) throws IOException, TasteException {
		
		
		List<DataModel> trainingDataModels = new ArrayList<DataModel>();
		List<DataModel> testingDataModels = new ArrayList<DataModel>();
		List<File> outputFileList = null;
		if (mFlagOutput) {
			outputFileList = new ArrayList<File>();
		}

		if (mTrainingPathList == null || mTestingPathList == null) {
			// Load DEFAULT files
			log.warn("path not found, load default files instead!");
			for (int i=1; i<=5; i++) {
				String filename = "u" + i;
				trainingDataModels.add(new FileDataModel(new File("ml-100k" + File.separator + filename + "base")));
				testingDataModels.add(new FileDataModel(new File("ml-100k" + File.separator + filename + "test")));
				if (outputFileList != null) {
					outputFileList.add(new File("predict" + File.separator + filename + "test." + recommenderType));
				}
			}
		} else {
			for (int i=0; i<mTrainingPathList.size() && i<mTestingPathList.size(); i++) {
				trainingDataModels.add(new FileDataModel(new File(mTrainingPathList.get(i))));
				testingDataModels.add(new FileDataModel(new File(mTestingPathList.get(i))));
				if (outputFileList != null) {
					String[] pathList = mTestingPathList.get(i).split("/");
					String filename = pathList[pathList.length-1];
					outputFileList.add(new File("predict" + File.separator + filename + "." + recommenderType));
				}
			}
		}
		
		if (outputFileList == null) {
			GLIRStatisticsImpl stats = evaluator.evaluate(
					builder, trainingDataModels, testingDataModels, null, at, 
					GenericRecommenderIRStatsEvaluator.CHOOSE_THRESHOLD, 
					1.0);
			
			System.out.println("Precision:\t" + stats.getPrecision());
			System.out.println("RPrecision:\t" + stats.getRprecision());
			System.out.println("Reca:\t" + stats.getRecall());
			System.out.println("F1:\t" + stats.getF1Measure());
			System.out.println("fallOut:\t" + stats.getFallOut());
			System.out.println("Reach:\t" + stats.getReach());
			System.out.println("macroDOA:\t" + stats.getMacroDegreeOfAgreement());
			System.out.println("microDOA:\t" + stats.getMicroDegreeOfAgreement());
			System.out.println("macroInnerDOA:\t" + stats.getMacroInnerDOA());
			System.out.println("microInnerDOA:\t" + stats.getMicroInnerDOA());
			System.out.println("nDCG:\t" + stats.getNormalizedDiscountedCumulativeGain());
			System.out.println("rNDCG:\t" + stats.getRndcg());
		} else {
			evaluator.predict(builder, trainingDataModels, testingDataModels, outputFileList);
		}

	}
}
