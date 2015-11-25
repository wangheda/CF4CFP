
set terminal push
set terminal eps size 5,3
set output "neighborhood_ndcg.eps"
set key right bottom
set logscale x
set xlabel "neighborhood size"
set ylabel "nDCG@20"
set style data linesp
set yrange [0:0.14]
set xrange [1:110]
plot "neighborhood_ndcg20.tsv" u 1:2 t "neighborhood", \
	 "neighborhood_idf_ndcg20.tsv" u 1:2 t "neighborhood-idf"
set terminal pop


