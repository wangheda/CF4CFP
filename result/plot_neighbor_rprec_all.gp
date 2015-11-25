
set terminal push
set terminal eps size 5,3
set output "neighborhood_rprec_all.eps"
set key right top
set logscale x
set xlabel "neighborhood size"
set ylabel "RPrecision"
set style data linesp
set yrange [0:0.05]
set xrange [1:110]
plot "neighborhood_rprec.tsv" u 1:2 t "neighborhood", \
	 "neighborhood_idf_rprec.tsv" u 1:2 t "neighborhood-idf", \
	 "neighborhood_idf2_rprec.tsv" u 1:2 t "neighborhood-idf2", \
	 "neighborhood_idf3_rprec.tsv" u 1:2 t "neighborhood-idf3"
set terminal pop


