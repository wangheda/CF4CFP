
set terminal push
set terminal eps size 5,3
set output "lfm_ndcg_all.eps"
set key right top
set logscale x
set xlabel "number of latent classes"
set ylabel "nDCG@20"
set style data linesp
set yrange [0.04:0.2]
set xrange [3:3000]
plot "lfm_ndcg20.tsv" u 1:2 t "latent class model", \
	"sdm_ndcg20.tsv" u 1:2 t "series-deadline model", \
	"sdpm_ndcg20.tsv" u 1:2 t "series-deadline-popularity model", \
	"sdrpm_ndcg20.tsv" u 1:2 t "series-deadline-relative-popularity model", \
	# "sm_ndcg20.tsv" u 1:2 t "series model", \

set terminal pop


