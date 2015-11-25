/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.msiiplab.recsys.rwr;

import java.io.Serializable;

import org.apache.mahout.cf.taste.eval.IRStatistics;

import com.google.common.base.Preconditions;

public final class GLIRStatisticsImpl implements IRStatistics, Serializable {

	private static final long serialVersionUID = 7635838088058371698L;
	private final double precision;
	private final double rprecision;
	private final double recall;
	private final double fallOut;
	private final double ndcg;
	private final double rndcg;
	private final double reach;
	private final double macroDOA;
	private final double microDOA;
	private final double macroInnerDOA;
	private final double microInnerDOA;
	
	private void checkStat(double stats, String statsName) {
		Preconditions.checkArgument(Double.isNaN(stats)
				|| (stats >= 0.0 && stats <= 1.0),
				"Illegal "+statsName+": " + stats);
	}

	GLIRStatisticsImpl(double precision, double rprecision, double recall, double fallOut,
			double ndcg, double rndcg, double reach, double macroDOA, double microDOA, double macroInnerDOA, double microInnerDOA) {
		checkStat(precision, "precision");
		checkStat(rprecision, "rprecision");
		checkStat(recall, "recall");
		checkStat(fallOut, "fallOut");
		checkStat(ndcg, "ndcg");
		checkStat(rndcg, "rndcg");
		checkStat(reach, "reach");
		checkStat(macroDOA, "macroDOA");
		checkStat(microDOA, "microDOA");
		checkStat(macroInnerDOA, "macroInnerDOA");
		checkStat(microInnerDOA, "microInnerDOA");
		checkStat(precision, "precision");
		checkStat(precision, "precision");

		this.precision = precision;
		this.rprecision = rprecision;
		this.rndcg = rndcg;
		this.recall = recall;
		this.fallOut = fallOut;
		this.ndcg = ndcg;
		this.reach = reach;
		this.macroDOA = macroDOA;
		this.microDOA = microDOA;
		this.macroInnerDOA = macroInnerDOA;
		this.microInnerDOA = microInnerDOA;
	}

	public double getMacroInnerDOA() {
		return macroInnerDOA;
	}

	public double getMicroInnerDOA() {
		return microInnerDOA;
	}

	@Override
	public double getPrecision() {
		return precision;
	}

	public double getRprecision() {
		return rprecision;
	}

	public double getRndcg() {
		return rndcg;
	}

	@Override
	public double getRecall() {
		return recall;
	}

	@Override
	public double getFallOut() {
		return fallOut;
	}

	@Override
	public double getF1Measure() {
		return getFNMeasure(1.0);
	}

	@Override
	public double getFNMeasure(double b) {
		double b2 = b * b;
		double sum = b2 * precision + recall;
		return sum == 0.0 ? Double.NaN : (1.0 + b2) * precision * recall / sum;
	}

	@Override
	public double getNormalizedDiscountedCumulativeGain() {
		return ndcg;
	}

	@Override
	public double getReach() {
		return reach;
	}

	public double getMacroDegreeOfAgreement() {
		return macroDOA;
	}

	public double getMicroDegreeOfAgreement() {
		return microDOA;
	}

	@Override
	public String toString() {
		return "IRStatisticsImpl[precision:" + precision + ",recall:" + recall
				+ ",fallOut:" + fallOut + ",nDCG:" + ndcg + ",reach:" + reach
				+ ']';
	}

}
