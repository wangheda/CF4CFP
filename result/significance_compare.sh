#!/bin/bash

options="CFPTest-Popularity-n20.log.error CFPTest-Name-n20.log.error CFPTest-UserCF-n20-b16.log.error CFPTest-UserCFIDF-n20-b16.log.error CFPTest-LFM-n20-f320.log.error CFPTest-SDM-n20-f80.log.error CFPTest-SDPM-n20-f80.log.error CFPTest-SM-n20-f80.log.error"

count=$(echo $options | wc -w)

function ttest() {
	metricName=$1
	t=$2
	printf "%-10s\t" $metricName
	for i in $( seq 1 $count ); do
		printf "%-10s\t" $(echo $options | cut -f $i -d' ' | cut -d'-' -f2)
	done
	echo ""

	for i in $( seq 1 $count ); do
		len=$(($i-1))
		printf "%-10s\t" $(echo $options | cut -f $i -d' ' | cut -d'-' -f2)
		for j in $( seq 1 $len ); do
			printf "%-10s\t" $(python significance_compare.py "$(echo $options | cut -f $i -d' ')" "$(echo $options | cut -f $j -d' ')" $metricName $t)
		done
		echo ""
	done
}

metric=$1
t=$2

ttest $metric $t
