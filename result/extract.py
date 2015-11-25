#!/bin/python

import os
import sys
from subprocess import check_output

path = sys.argv[1]

AlgoTypesNeighbor = set(("UserCF", "UserCFIDF", "UserCFIDF2", "UserCFIDF3"))
AlgoTypesFactor = set(("LFM", "SM", "SDM", "SDPM", "SDRPM"))
RPrecision = {}
NDCG20 = {}

FileNameDictRPrecision = {
	"UserCF": "neighborhood_rprec.tsv",
	"UserCFIDF": "neighborhood_idf_rprec.tsv",
	"UserCFIDF2": "neighborhood_idf2_rprec.tsv",
	"UserCFIDF3": "neighborhood_idf3_rprec.tsv",
	"LFM": "lfm_rprec.tsv",
	"SM": "sm_rprec.tsv",
	"SDM": "sdm_rprec.tsv",
	"SDPM": "sdpm_rprec.tsv",
	"SDRPM": "sdrpm_rprec.tsv",
	}
FileNameDictNDCG20 = {
	"UserCF": "neighborhood_ndcg20.tsv",
	"UserCFIDF": "neighborhood_idf_ndcg20.tsv",
	"UserCFIDF2": "neighborhood_idf2_ndcg20.tsv",
	"UserCFIDF3": "neighborhood_idf3_ndcg20.tsv",
	"LFM": "lfm_ndcg20.tsv",
	"SM": "sm_ndcg20.tsv",
	"SDM": "sdm_ndcg20.tsv",
	"SDPM": "sdpm_ndcg20.tsv",
	"SDRPM": "sdrpm_ndcg20.tsv",
	}

popularity_rprec = 0.0
popularity_ndcg20 = 0.0

def get_rprec(filename):
	rprec = check_output('''grep -e "^RPrecision:" '''+filename+''' | sed "s/RPrecision://" | sed "s/\s//"''', shell=True)
	rprec = float(rprec)
	return rprec

def get_ndcg(filename):
	ndcg = check_output('''grep -e "^nDCG:" '''+filename+''' | sed "s/nDCG://" | sed "s/\s//"''', shell=True)
	ndcg = float(ndcg)
	return ndcg

def write_tsv(List, filename):
	with open(filename, "w") as F:
		F.writelines(["\t".join([str(item) for item in t])+"\n" for t in List])
	return None

for filename in os.listdir(path):
	filepath = path+filename
	if os.path.isfile(filepath):
		if filename.startswith("CFPTest") and filename.endswith(".log"):
			string = filename.replace(".log", "").split("-")
			algotype = string[1]
			N = string[2]
			if algotype == "Popularity":
				popularity_rprec = get_rprec(filepath)
				popularity_ndcg20 = get_ndcg(filepath)
			elif algotype == "Name":
				name_rprec = get_rprec(filepath)
				name_ndcg20 = get_ndcg(filepath)
			else:
				parameter = None
				if algotype in AlgoTypesNeighbor:
					neighbor = int(string[3].replace("b",""))
					parameter = neighbor
				elif algotype in AlgoTypesFactor:
					factor = int(string[3].replace("f",""))
					parameter = factor
				else:
					continue
				if RPrecision.has_key(algotype):
					RPrecision[algotype].append((parameter,get_rprec(filepath)))
				else:
					RPrecision[algotype] = [(parameter,get_rprec(filepath)),]
				if NDCG20.has_key(algotype):
					NDCG20[algotype].append((parameter,get_ndcg(filepath)))
				else:
					NDCG20[algotype] = [(parameter,get_ndcg(filepath)),]
						
			
for algotype in RPrecision.keys():
	L = RPrecision[algotype]
	L.sort()
	if algotype in AlgoTypesNeighbor:
		L.insert(0,("neighbor","RPrecision"))
	elif algotype in AlgoTypesFactor:
		L.insert(0,("factor","RPrecision"))
for algotype in NDCG20.keys():
	L = NDCG20[algotype]
	L.sort()
	if algotype in AlgoTypesNeighbor:
		L.insert(0,("neighbor","nDCG@20"))
	elif algotype in AlgoTypesFactor:
		L.insert(0,("factor","nDCG@20"))


write_tsv([("ndcg", popularity_ndcg20), ("rprec", popularity_rprec)], "popularity.tsv")
write_tsv([("ndcg", name_ndcg20), ("rprec", name_rprec)], "name.tsv")
for algotype in FileNameDictRPrecision:
	write_tsv(RPrecision.get(algotype,[]), FileNameDictRPrecision[algotype])
for algotype in FileNameDictNDCG20:
	write_tsv(NDCG20.get(algotype,[]), FileNameDictNDCG20[algotype])

