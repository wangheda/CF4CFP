
set terminal push
set terminal eps size 5,3
set output "lfm_rprec.eps"
set key right bottom
set logscale x
set xlabel "number of latent classes"
set ylabel "RPrecision"
set style data linesp
set yrange [0.01:0.04]
set xrange [3:3000]
plot "lfm_rprec.tsv" u 1:2 t "latent class model", \
	"sdm_rprec.tsv" u 1:2 t "series-deadline model", \
#"sdpm_rprec.tsv" u 1:2 t "series-deadline-pop model", \
#"sm_rprec.tsv" u 1:2 t "series model", \
		
set terminal pop


