#!/bin/bash


if [ "$#" -ge 2 ]; then
	file=$1
	n=$2
	sort -R $file > .tmp~
	lineNum=$(wc -l $file | cut -d ' ' -f 1)
	declare -i i
	i=1
	while [ "$i" -le "$n" ]; do
		head -n $(($i*$lineNum/$n)) .tmp~ | tail -n $(($lineNum/$n)) > .tmp~$i
		i=$(($i+1))
	done
	declare -i j
	i=1
	while [ "$i" -le "$n" ]; do
		rm "train$i"
		rm "test$i"
		cat .tmp~$i > "test$i"
		j=1
		while [ "$j" -le "$n" ]; do
			if [ "$i" -ne "$j" ]; then
				cat .tmp~$j >> "train$i"
			fi
			j=$(($j+1))
		done
		i=$(($i+1))
	done
	i=1
	while [ "$i" -le "$n" ]; do
		rm .tmp~$i
		i=$(($i+1))
	done
	rm .tmp~
else
	echo "Divide a file into N random train-testing pairs"
	echo "Usage: $0 [file] [N]"
fi

