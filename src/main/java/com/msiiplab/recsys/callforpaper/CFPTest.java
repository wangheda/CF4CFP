package com.msiiplab.recsys.callforpaper;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.eval.RecommenderBuilder;
import com.msiiplab.recsys.implicit.ItemPopularityRecommender;
import com.msiiplab.recsys.implicit.NameRecommender;
import com.msiiplab.recsys.implicit.NeighborhoodIDFRecommender;
import com.msiiplab.recsys.implicit.NeighborhoodLFMRecommender;
import com.msiiplab.recsys.implicit.NeighborhoodRecommender;
import com.msiiplab.recsys.lfm.AspectModelRecommender;
import com.msiiplab.recsys.lfm.SeriesDeadlinePopularityRecommender;
import com.msiiplab.recsys.lfm.SeriesDeadlineRecommender;
import com.msiiplab.recsys.rwr.NFoldTest;
import com.msiiplab.recsys.rwr.PersonalizedPageRankRecommender;


import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;

public class CFPTest {
	
	
	private static String sRecommenderType;
	private static int sNumEvaluated;
	private static int sFactor;
	private static int sNeighbor;
	private static List<String> sFiles;
	private static String sMetaData;
	private static boolean sFlagOutput = false;

	/**
	 * @param args
	 * @throws IOException 
	 * @throws TasteException 
	 */
	public static void main(String[] args) throws IOException, TasteException {
		// TODO Auto-generated method stub
		ArgumentParser parser = ArgumentParsers.newArgumentParser("CFPTest")
				.defaultHelp(true)
				.description("Test all recommender method on CFP data.");
		parser.addArgument("-t", "--type").required(true)
				.choices("UserCF", "Name", "Popularity", "RWR", "LFM", 
						"UserCFIDF", "UserCFIDF2", "UserCFIDF3", "UserCFLFM", 
						"SDM", "SDPM", "SDRPM", "SM")
				.help("Type of recommender to use");
		parser.addArgument("-f", "--factor").type(Integer.class)
				.help("Number of latent factors, only applicable when type is LFM (Latent Factor Model)");
		parser.addArgument("-b", "--neighbor").type(Integer.class)
				.help("Number of nearest neighbors, only applicable when type is UserCF");
		parser.addArgument("-m", "--metadata").type(String.class)
				.help("tsv file that contains cfp meta data including cfpID seriesID timestamp cfpName");
		parser.addArgument("-n", "--numEvaluated").required(true).type(Integer.class)
				.help("N as in NDCG@N");
		parser.addArgument("file").nargs("*")
				.help("File of training and testing (5-fold)");
		
		Namespace ns = null;
		try	{
			ns = parser.parseArgs(args);
			sNumEvaluated = ns.getInt("numEvaluated");
			sRecommenderType = ns.getString("type");
			if (ns.getInt("factor") == null) {
				sFactor = 20;
			} else {
				sFactor = ns.getInt("factor");
			}
			if (ns.getInt("neighbor") == null) {
				sNeighbor = 10;
			} else {
				sNeighbor = ns.getInt("neighbor");
			}
			if (ns.getString("metadata") != null) {
				sMetaData = ns.getString("metadata");
			} else {
				sMetaData = "item.txt";
			}
			sFiles = ns.<String>getList("file");
			
			RecommenderBuilder builder = getRecommenderBuilder(sRecommenderType);
			NFoldTest test = new NFoldTest(sFiles, sFlagOutput);
			test.testInParallel(builder, sRecommenderType, sNumEvaluated);
		} catch (ArgumentParserException e) {
			parser.handleError(e);
			System.exit(1);
		}
	}

	private static RecommenderBuilder getRecommenderBuilder(String recommenderType) {
		if (recommenderType.equals("UserCF")) { // User CF
			return NeighborhoodRecommender.getRecommenderBuilder(sNeighbor);
		} else if (recommenderType.equals("UserCFIDF")) { // User CF with IDF as Similarity
			return NeighborhoodIDFRecommender.getRecommenderBuilder(sNeighbor, 1);
		} else if (recommenderType.equals("UserCFIDF2")) { // User CF with IDF as Similarity
			return NeighborhoodIDFRecommender.getRecommenderBuilder(sNeighbor, 2);
		} else if (recommenderType.equals("UserCFIDF3")) { // User CF with IDF as Similarity
			return NeighborhoodIDFRecommender.getRecommenderBuilder(sNeighbor, 3);
		} else if (recommenderType.equals("UserCFLFM")) { // User CF with LFM as Similarity
			return NeighborhoodLFMRecommender.getRecommenderBuilder(sFactor, sNeighbor);
		} else if (recommenderType.equals("Popularity")) { // Popularity
			return ItemPopularityRecommender.getRecommenderBuilder();
		} else if (recommenderType.equals("RWR")) { // Random Walk with Restart
			return PersonalizedPageRankRecommender.getRecommenderBuilder();
		} else if (recommenderType.equals("LFM")) { // Latent Factor Model
			return AspectModelRecommender.getRecommenderBuilder(sFactor);
		} else if (recommenderType.equals("SDM")) { // Series-Deadline Model
			return SeriesDeadlineRecommender.getRecommenderBuilder(sFactor, new MetaData(new File(sMetaData)), true);
		} else if (recommenderType.equals("SDPM")) { // Series-Deadline-Popularity Model
			return SeriesDeadlinePopularityRecommender.getRecommenderBuilder(sFactor, new MetaData(new File(sMetaData)), false);
		} else if (recommenderType.equals("SDRPM")) { // Series-Deadline-RelativePopularity Model
			return SeriesDeadlinePopularityRecommender.getRecommenderBuilder(sFactor, new MetaData(new File(sMetaData)), true);
		} else if (recommenderType.equals("SM")) { // Series Model
			return SeriesDeadlineRecommender.getRecommenderBuilder(sFactor, new MetaData(new File(sMetaData)), false);
		} else if (recommenderType.equals("Name")) { // Name Model
			return NameRecommender.getRecommenderBuilder(new MetaData(new File(sMetaData)));
		} else {
			return null;
		}
	}

}
