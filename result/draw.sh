#!/bin/bash

python extract.py $1
gnuplot *.gp
./result.sh
