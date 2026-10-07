"""python3 training/release.py -> training/release/media.json: what the upload needs, measured on the released files.
Each entry maps a local file (release/training/v1/X) to its path on the CDN zone (dad-coach/training/v1/X, the path
backend/src/main/resources/training/catalog.json names), with the real length. Fails if the catalog and the files
disagree (a missing file, another path, or a duration more than a second off)."""
import hashlib, json, os, subprocess, sys

HERE = os.path.dirname(os.path.abspath(__file__))
REL = f'{HERE}/release'
CAT = json.load(open(f'{os.path.dirname(os.path.dirname(HERE))}/backend/src/main/resources/training/catalog.json'))


def dur(p):
    return float(subprocess.run(['ffprobe', '-v', 'error', '-show_entries', 'format=duration', '-of', 'csv=p=0', p], capture_output=True, text=True).stdout)


media, bad = {}, []
for e in sorted(CAT, key=lambda e: e['order']):
    if not e['active']:
        continue
    entry = {}
    for kind in ('video', 'poster'):
        cdn = e[kind]
        local = cdn.removeprefix('dad-coach/')
        if not cdn.startswith('dad-coach/training/v1/') or not os.path.exists(f'{REL}/{local}'):
            bad.append(f'{e["slug"]}: {kind} {cdn} has no release/{local}')
            continue
        data = open(f'{REL}/{local}', 'rb').read()
        entry[kind] = {'local': f'release/{local}', 'cdn': cdn, 'bytes': len(data), 'sha256': hashlib.sha256(data).hexdigest()}
    if 'video' in entry:
        d = dur(f'{REL}/{e["video"].removeprefix("dad-coach/")}')
        entry['durationSeconds'] = round(d, 2)
        if abs(d - e['durationSeconds']) > 1:
            bad.append(f'{e["slug"]}: catalog says {e["durationSeconds"]}s, the file is {d:.1f}s')
    media[e['slug']] = entry
json.dump(media, open(f'{REL}/media.json', 'w'), ensure_ascii=False, indent=1)
for slug, m in media.items():
    print(slug, m.get('durationSeconds'), 's', round(m.get('video', {}).get('bytes', 0) / 1e6, 1), 'MB')
if bad:
    print('\n'.join(bad))
    sys.exit(1)
