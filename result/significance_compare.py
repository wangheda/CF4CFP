#!/bin/python
# Usage: python significance_compare.py [stderr_file1] [stderr_file1] [metric] [ind/1samp]

import sys
import os
import re
import numpy
from scipy import stats

metric_options = set(["Precision", "Recall", "RPrecision", "NDCG", "RNDCG"])

def parse_parameters():
	if len(sys.argv) == 5:
		stderr_file1 = sys.argv[1]
		stderr_file2 = sys.argv[2]
		metric = sys.argv[3]
		type = sys.argv[4]
		if type not in ["ind", "1samp"]:
			sys.exit("test type is limited to one of ind/1samp")
	else:
		sys.exit("Usage: python %s [stderr_file1] [stderr_file1] [metric]"%sys.argv[0])
	return stderr_file1, stderr_file2, metric, type

def get_regex(metric):
	if metric in metric_options:
		regex = re.compile("INFO:\s*%s\s*for\s*user\s*(?P<user>\d+)\s*is\s*(?P<metric>\S+)"%metric)
	else:
		sys.exit("metric name is limited to one of %s"%str(tuple(metric_options)))
	return regex

def get_performance_map(filename, regex):
	performanceMap = {}
	if os.path.isfile(filename):
		with open(filename) as F:
			for line in F.readlines():
				match = regex.search(line)
				if match:
					user = int(match.group("user"))
					metric = float(match.group("metric"))
					performanceMap[user] = metric
	else:
		sys.exit("file %s not found"%filename)
	return performanceMap

def significance_test(map1, map2, type):
	if type == "ind":
		# 2 sample ttest
		t, pvalue = stats.ttest_ind(map1.values(), map2.values())
		#print "2 independent sample t-test, t = %f, reject by %f"%(t,pvalue)
		print "%f"%(pvalue),
	if type == "1samp":
		# 1 sample ttest
		map = dict([(k,map1[k]-map2[k]) for k in map1.keys()]) 
		t, pvalue = stats.ttest_1samp(map.values(), 0)
		#print "1 sample t-test, t = %f, reject by %f"%(t,pvalue)
		print "%f"%(pvalue),
	return None

if __name__ == "__main__":
	stderr_file1, stderr_file2, metric, type = parse_parameters()
	regex = get_regex(metric)
	map1 = get_performance_map(stderr_file1, regex)
	map2 = get_performance_map(stderr_file2, regex)
	if set(map1.keys()) != set(map2.keys()):
		print sys.stderr, "warning: keys not consistent"
	keyList = set()
	for key in map1.keys():
		if numpy.isnan(map1[key]) or numpy.isinf(map1[key]):
			keyList.add(key)
	for key in map2.keys():
		if numpy.isnan(map2[key]) or numpy.isinf(map2[key]):
			keyList.add([key])
	if keyList:
		print sys.stderr, "warning: nan found"
		for key in keyList:
			if map1.has_key(key):
				del map1[key]
			if map2.has_key(key):
				del map2[key]
	significance_test(map1, map2, type)




