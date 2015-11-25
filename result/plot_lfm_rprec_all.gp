
set terminal push
set terminal eps size 5,3
set output "lfm_rprec_all.eps"
set key right top
set logscale x
set xlabel "number of latent classes"
set ylabel "RPrecision"
set style data linesp
set yrange [0.0074:0.05]
set xrange [3:3000]
plot "lfm_rprec.tsv" u 1:2 t "latent class model", \
	"sdm_rprec.tsv" u 1:2 t "series-deadline model", \
	"sdpm_rprec.tsv" u 1:2 t "series-deadline-popularity model", \
	"sdrpm_rprec.tsv" u 1:2 t "series-deadline-relative-popularity model", \
#"sm_rprec.tsv" u 1:2 t "series model", \
		
set terminal pop


