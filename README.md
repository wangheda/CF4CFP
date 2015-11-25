# CF4CFP
Collaborative Filtering of Call for Papers

## compile

    mvn package

## test

    ./CFPTest-simple.sh

## data set

The WikiCFP data set we crawled contains 5 files:

    item.txt  itemIDs, seriesIDs, deadlines, and conference names of items
	meta.txt  meta-data of items in json format
	posted.txt  posted-by relations
	series.txt  seriesIDs and series names of series
	tracked.txt  tracked-by relations

The files train1 to train5 and test1 to test5 are 5-fold cross evaluation version of tracked.txt.

## reference

	He-Da Wang, Ji Wu. Collaborative Filtering of Call for Papers. IEEE SSCI 2015.
