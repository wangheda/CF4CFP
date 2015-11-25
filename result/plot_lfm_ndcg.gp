
set terminal push
set terminal eps size 5,3
set output "lfm_ndcg.eps"
set key right bottom
set logscale x
set xlabel "number of latent classes"
set ylabel "nDCG@20"
set style data linesp
set yrange [0.05:0.15]
set xrange [3:3000]
plot "lfm_ndcg20.tsv" u 1:2 t "latent class model", \
	"sdm_ndcg20.tsv" u 1:2 t "series-deadline model", \
	# "sdpm_ndcg20.tsv" u 1:2 t "series-deadline-pop model", \
	# "sm_ndcg20.tsv" u 1:2 t "series model", \

set terminal pop


