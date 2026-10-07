#!/bin/zsh
# ./qa.sh <video|ad>... : the released web file checked - a frame every 2.5 s in one sheet (<out>/qa/<v>_scan.jpg),
# duration, size, picture format and loudness (training -16 LUFS, the ad -14 LUFS, true peak under -1 dBFS).
cd "$(dirname "$0")"
for v in "$@"; do
  if [[ $v == ad* ]]; then f=../ad/release/dad-coach-${v}-v1.mp4; q=../ad/out/qa; else f=release/training/v1/father-$v.mp4; q=out/qa; fi
  mkdir -p $q
  ffmpeg -v error -y -i $f -vf "fps=0.4,scale=180:320,tile=10x3" -frames:v 1 -q:v 4 $q/${v}_scan.jpg
  d=$(ffprobe -v error -show_entries format=duration -of csv=p=0 $f)
  l=$(ffmpeg -nostats -i $f -af ebur128=peak=true -f null - 2>&1 | sed -n '/Summary/,$p' | grep -E "^\s+(I|Peak):" | tr -s ' ' | tr '\n' ' ')
  r=$(ffprobe -v error -select_streams v -show_entries stream=width,height,r_frame_rate -of csv=p=0 $f)
  echo "$v $(printf %.1f $d)s $(du -h $f | cut -f1) video=$r audio:$l"
done
