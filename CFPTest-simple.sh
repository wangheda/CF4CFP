#!/bin/bash

mkdir result

N=20
filename="result/CFPTest-Popularity-n${N}.log"

if [ ! -f $filename ]; then 
	echo "File $filename not exists"; 
	java -jar target/CF4CFP-1.0.jar -t Popularity -n $N train1 test1 train2 test2 train3 test3 train4 test4 train5 test5 > $filename 2> $filename.error
fi

N=20
filename="result/CFPTest-Name-n${N}.log"

if [ ! -f $filename ]; then 
	echo "File $filename not exists"; 
	java -jar target/CF4CFP-1.0.jar -t Name -n $N train1 test1 train2 test2 train3 test3 train4 test4 train5 test5 > $filename 2> $filename.error
fi

N=20
for method in UserCF UserCFIDF; do 
	for neighbor in 16; do
		filename="result/CFPTest-${method}-n${N}-b${neighbor}.log"
		if [ ! -f $filename ]; then 
			echo "File $filename not exists"; 
			java -jar target/CF4CFP-1.0.jar -t $method -n $N -b $neighbor train1 test1 train2 test2 train3 test3 train4 test4 train5 test5 > $filename 2> $filename.error
		fi
	done
done

N=20
for method in LFM; do 
	for factor in 320; do
		filename="result/CFPTest-${method}-n${N}-f${factor}.log"
		if [ ! -f $filename ]; then 
			echo "File $filename not exists"; 
			java -jar target/CF4CFP-1.0.jar -t $method -n $N -f $factor train1 test1 train2 test2 train3 test3 train4 test4 train5 test5 > $filename 2> $filename.error
		fi
	done
done

N=20
for method in SDM; do 
	for factor in 80; do
		filename="result/CFPTest-${method}-n${N}-f${factor}.log"
		if [ ! -f $filename ]; then 
			echo "File $filename not exists"; 
			java -jar target/CF4CFP-1.0.jar -t $method -n $N -f $factor train1 test1 train2 test2 train3 test3 train4 test4 train5 test5 > $filename 2> $filename.error
		fi
	done
done
