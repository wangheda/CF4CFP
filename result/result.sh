#!/bin/bash

function getNumber
{
	file=$1
	regex=$2
	echo $(grep "^$2" $1 | cut -f 2 | cut -b 1-10)
}



echo -e "Method                                   \tRPrecision   \tnDCG@20"
echo -e "Popularity                               \t$(getNumber popularity.tsv rprec)\t$(getNumber popularity.tsv ndcg)"
echo -e "Name                                     \t$(getNumber name.tsv rprec)\t$(getNumber name.tsv ndcg)"
echo -e "Neighborhood                             \t$(getNumber neighborhood_rprec.tsv 16)\t$(getNumber neighborhood_ndcg20.tsv 16)"
echo -e "Neighborhood-IDF                         \t$(getNumber neighborhood_idf_rprec.tsv 16)\t$(getNumber neighborhood_idf_ndcg20.tsv 16)"
echo -e "Neighborhood-IDF2                        \t$(getNumber neighborhood_idf2_rprec.tsv 16)\t$(getNumber neighborhood_idf2_ndcg20.tsv 16)"
echo -e "Neighborhood-IDF3                        \t$(getNumber neighborhood_idf3_rprec.tsv 16)\t$(getNumber neighborhood_idf3_ndcg20.tsv 16)"
echo -e "Latent Factor Model                      \t$(getNumber lfm_rprec.tsv 320)\t$(getNumber lfm_ndcg20.tsv 320)"
echo -e "Series Model                             \t$(getNumber sm_rprec.tsv 80)\t$(getNumber sm_ndcg20.tsv 80)"
echo -e "Series Deadline Model                    \t$(getNumber sdm_rprec.tsv 80)\t$(getNumber sdm_ndcg20.tsv 80)"
echo -e "Series Deadline Popularity Model         \t$(getNumber sdpm_rprec.tsv 80)\t$(getNumber sdpm_ndcg20.tsv 80)"
echo -e "Series Deadline Relative Popularity Model\t$(getNumber sdrpm_rprec.tsv 80)\t$(getNumber sdrpm_ndcg20.tsv 80)"


