#!/bin/zsh
# ./build.sh <video|ad>... (videos: welcome book-a-session reminders when-cancelled my-dashboard; ad = the 9:16 ad)
# timeline -> picture (render.mjs, piped to ffmpeg) -> sound (audio/mix.py) -> <out>/<v>_master.mp4 + <out>/<v>_web.mp4,
# then the web file copied to the release: release/training/v3/father-<v>.mp4 (served from the CDN as
# dad-coach/training/v3/father-<v>.mp4) or ../ad/release/dad-coach-ad-v2.mp4. The intermediates are removed after each
# video, and nothing starts with less than 700 MB free. ./build.sh --sound <v>... remixes the sound only (the picture
# from out/<v>_master.mp4). Needs marketing/ served on 127.0.0.1:8788 (started if it is not).
set -e
cd "$(dirname "$0")"
TRAINING=$PWD
if ! curl -sf -o /dev/null http://127.0.0.1:8788/training/film/index.html; then
  (cd .. && nohup python3 -m http.server 8788 --bind 127.0.0.1 > /dev/null 2>&1 &)
  sleep 1
fi
mkdir -p out release/training/v3 ../ad/out ../ad/release
SOUND=0; [[ $1 == --sound ]] && { SOUND=1; shift; }
for v in "$@"; do
  free=$(df -m / | awk 'NR==2 {print $4}')
  if (( free < 700 )); then echo "stop: only ${free} MB free"; exit 1; fi
  if [[ $v == ad* ]]; then dir=../ad; rel=../ad/release/dad-coach-${v}-v2.mp4; else dir=.; rel=release/training/v3/father-$v.mp4; fi
  if (( ! SOUND )); then
    (cd $dir && node $TRAINING/film/timeline.mjs $v && node $TRAINING/film/render.mjs $v 30 > out/render_$v.log 2>&1)
    grep -q ERRORS $dir/out/render_$v.log && { cat $dir/out/render_$v.log; exit 1; }
  fi
  python3 audio/mix.py $v
  rm -f $dir/out/${v}_video.mp4 $dir/out/${v}_mix.wav
  cp $dir/out/${v}_web.mp4 $rel
  echo "$v -> $rel ($(du -h $rel | cut -f1)), ${free} MB were free"
done
