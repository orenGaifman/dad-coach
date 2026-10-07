"""python3 training/audio/mix.py <video|ad>: the sound of one film, then the picture muxed in.

The voice (audio/vo/<id>.wav, played TIMELINE.tempo faster for the ad) on its beats, a few quiet UI sounds, the music bed
under it (ducked further while the voice speaks), the whole loudness-matched in two passes:
  training: -16 LUFS, bed at -30 LUFS before ducking (the plan's rule: music under the voice never above -24 LUFS)
  ad:       -14 LUFS (social feeds), bed at -27 LUFS before ducking
-> <out>/<v>_master.mp4 (the archive: picture copied, AAC 256k) and <out>/<v>_web.mp4 (H.264 crf 24, faststart, AAC 128k),
where <out> is training/out or ad/out. Reads <out>/<v>_timeline.json and <out>/<v>_video.mp4 (film/timeline.mjs, render.mjs).
Without the video it takes the picture from an existing master (a remix of the sound), else it only writes <out>/<v>_mix.wav (a sound check). Intermediates are left to build.sh to remove."""
import json, os, re, subprocess, sys

AUDIO = os.path.dirname(os.path.abspath(__file__))
MARKETING = os.path.dirname(os.path.dirname(AUDIO))
v = sys.argv[1]
AD = v.startswith('ad')
OUT = f'{MARKETING}/{"ad" if AD else "training"}/out'
BED = f'{MARKETING}/ad/audio/bed_ad.mp3' if AD else f'{AUDIO}/music/bed_training.mp3'
TARGET, BED_LUFS, VOICE_LUFS = (-14.0, -27.0, -17.0) if AD else (-16.0, -30.0, -17.0)
SFX_GAIN = {'ping': -16, 'tick': -14, 'confirm': -12, 'whoosh': -18, 'reminder_ping': -14}
SR = 48000

tl = json.load(open(f'{OUT}/{v}_timeline.json'))
total, tempo = tl['total'], tl.get('tempo', 1)


def ff(*args):
    subprocess.run(['ffmpeg', '-v', 'error', '-y', *args], check=True)


def lufs(path):
    """Integrated loudness and true peak of a file (ebur128)."""
    err = subprocess.run(['ffmpeg', '-nostats', '-i', path, '-af', 'ebur128=peak=true', '-f', 'null', '-'], capture_output=True, text=True).stderr
    summary = err[err.rfind('Summary:'):]
    i = float(re.search(r'I:\s+(-?[\d.]+) LUFS', summary).group(1))
    tp = float(re.search(r'Peak:\s+(-?[\d.]+|-inf) dBFS', summary).group(1))
    return i, tp


def place(items, path):
    """items: [(file, seconds, gain dB, extra filter)] laid on one mono track of the film's length."""
    inputs, chains, labels = [], [], []
    for k, (f, at, gain, extra) in enumerate(items):
        inputs += ['-i', f]
        chains.append(f'[{k}:a]aresample={SR},aformat=channel_layouts=mono{extra},volume={gain}dB,adelay={int(round(at * 1000))}:all=1,apad=whole_dur={total}[s{k}]')
        labels.append(f'[s{k}]')
    if not items:
        ff('-f', 'lavfi', '-i', f'anullsrc=r={SR}:cl=mono', '-t', f'{total}', path)
        return
    chains.append(f'{"".join(labels)}amix=inputs={len(labels)}:normalize=0[o]')
    ff(*inputs, '-filter_complex', ';'.join(chains), '-map', '[o]', '-t', f'{total}', '-c:a', 'pcm_s24le', path)


stem = lambda name: f'{OUT}/{v}_{name}.wav'
speed = f',atempo={tempo}' if tempo != 1 else ''
place([(f'{AUDIO}/vo/{b["id"]}.wav', b['voAt'], 0, speed) for b in tl['beats']], stem('voice'))
place([(f'{AUDIO}/sfx/{s["name"]}.wav', s['at'], SFX_GAIN.get(s['name'], -14), '') for b in tl['beats'] for s in b['sfx']], stem('fx'))
ff('-i', BED, '-af', f'aresample={SR},aformat=channel_layouts=mono,atrim=0:{total},afade=t=in:d=1.2,afade=t=out:st={max(0, total - 3)}:d=3,apad=whole_dur={total}',
   '-t', f'{total}', '-c:a', 'pcm_s24le', stem('bed'))

gv = VOICE_LUFS - lufs(stem('voice'))[0]
gb = BED_LUFS - lufs(stem('bed'))[0]
ff('-i', stem('voice'), '-i', stem('fx'), '-i', stem('bed'), '-filter_complex',
   f'[0:a]volume={gv:.2f}dB,asplit=2[voice][key];[2:a]volume={gb:.2f}dB[bed];'
   '[bed][key]sidechaincompress=threshold=0.03:ratio=3:attack=30:release=500[ducked];'
   '[voice][1:a][ducked]amix=inputs=3:normalize=0[o]', '-map', '[o]', '-c:a', 'pcm_s24le', stem('pre'))
gm = TARGET - lufs(stem('pre'))[0] - 3.01   # the mono mix goes out as dual mono: +3 dB in a stereo measurement
wav = stem('mix')
for _ in range(3):   # the limiter takes a little off the loudest films: measure again and make it up
    ff('-i', stem('pre'), '-af', f'volume={gm:.2f}dB,alimiter=limit=0.71:level=disabled:attack=3:release=60,aresample={SR},pan=stereo|c0=c0|c1=c0',
       '-c:a', 'pcm_s16le', wav)
    miss = TARGET - lufs(wav)[0]
    if abs(miss) < 0.3:
        break
    gm += miss
for s in ('voice', 'fx', 'bed', 'pre'):
    os.remove(stem(s))
i, tp = lufs(wav)
print(f'{v} mix {i:.1f} LUFS, true peak {tp:.1f} dBFS (voice {gv:+.1f} dB, bed {gb:+.1f} dB, master {gm:+.1f} dB)')

video = f'{OUT}/{v}_video.mp4'
if not os.path.exists(video) and os.path.exists(f'{OUT}/{v}_master.mp4'):   # a remix: the picture from the master
    ff('-i', f'{OUT}/{v}_master.mp4', '-map', '0:v', '-c:v', 'copy', video)
if os.path.exists(video):
    ff('-i', video, '-i', wav, '-map', '0:v', '-map', '1:a', '-c:v', 'copy', '-c:a', 'aac', '-b:a', '256k', '-shortest', f'{OUT}/{v}_master.mp4')
    ff('-i', video, '-i', wav, '-map', '0:v', '-map', '1:a', '-c:v', 'libx264', '-preset', 'slow', '-crf', '24', '-maxrate', '2500k', '-bufsize', '5000k',
       '-pix_fmt', 'yuv420p', '-profile:v', 'high', '-color_primaries', 'bt709', '-color_trc', 'bt709', '-colorspace', 'bt709',
       '-movflags', '+faststart', '-c:a', 'aac', '-b:a', '128k', '-shortest', f'{OUT}/{v}_web.mp4')
    for f in (f'{v}_master.mp4', f'{v}_web.mp4'):
        p = f'{OUT}/{f}'
        d = subprocess.run(['ffprobe', '-v', 'error', '-show_entries', 'format=duration', '-of', 'csv=p=0', p], capture_output=True, text=True).stdout.strip()
        print(' ', f, f'{float(d):.1f}s', f'{os.path.getsize(p) / 1e6:.1f}MB')
