
set terminal push
set terminal eps size 5,3
set output "neighborhood_rprec.eps"
set key right bottom
set logscale x
set xlabel "neighborhood size"
set ylabel "RPrecision"
set style data linesp
set yrange [0:0.04]
set xrange [1:110]
plot "neighborhood_rprec.tsv" u 1:2 t "neighborhood", \
	 "neighborhood_idf_rprec.tsv" u 1:2 t "neighborhood-idf"
set terminal pop


