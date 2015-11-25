
set terminal push
set terminal eps size 5,3
set output "neighborhood_ndcg_all.eps"
set key right top
set logscale x
set xlabel "neighborhood size"
set ylabel "nDCG@20"
set style data linesp
set yrange [0:0.16]
set xrange [1:110]
plot "neighborhood_ndcg20.tsv" u 1:2 t "neighborhood", \
	 "neighborhood_idf_ndcg20.tsv" u 1:2 t "neighborhood-idf", \
	 "neighborhood_idf2_ndcg20.tsv" u 1:2 t "neighborhood-idf2", \
	 "neighborhood_idf3_ndcg20.tsv" u 1:2 t "neighborhood-idf3"
set terminal pop


