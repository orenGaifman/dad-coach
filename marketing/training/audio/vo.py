"""The narrator (ElevenLabs voice "amit", male, the site intro's voice) reads every line of lines.py into vo/<id>.mp3
(+ a silence-trimmed vo/<id>.wav for the mix), then writes ../film/vo.json (measured seconds per clip - the films'
timelines are built from these) and ../film/lines.json (the captions).

  python3 audio/vo.py [ids...]     (from marketing/training; no ids: every clip whose mp3 is missing)
  python3 audio/vo.py --wav        (rebuild the wavs from the committed mp3s only - no API calls)
  python3 audio/vo.py --stt [ids]  (transcribe clips back to Hebrew text -> vo/stt.json, to check pronunciation)
Key: ELEVENLABS_API_KEY in ~/repos/.env (read by el.py, never printed)."""
import json, os, sys
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import el
from lines import ALL, spoken

VOICE = 'uwHajH4FhtzVp6X17pr7'   # "amit" - same voice, model and settings as site/scripts/intro_voice.py
VO = os.path.join(HERE, 'vo')
FILM = os.path.join(HERE, '..', 'film')
SEEDS = {'a06': 21, 'd05': 21, 'd06': 21, 'b03': 21, 'r02': 21}  # re-takes after the STT check (vo/stt.json)


def write_meta():
    durs, caps = {}, {}
    for lid, caption, _ in ALL:
        caps[lid] = caption
        wav = f'{VO}/{lid}.wav'
        if os.path.exists(wav):
            durs[lid] = round(el.duration(wav), 3)
    json.dump(durs, open(f'{FILM}/vo.json', 'w'), ensure_ascii=False, indent=1)
    json.dump(caps, open(f'{FILM}/lines.json', 'w'), ensure_ascii=False, indent=1)
    print('vo.json', len(durs), 'clips,', round(sum(durs.values()), 1), 's')


if __name__ == '__main__':
    os.makedirs(VO, exist_ok=True)
    args = [a for a in sys.argv[1:] if not a.startswith('--')]
    if '--stt' in sys.argv:
        path = f'{VO}/stt.json'
        report = json.load(open(path)) if os.path.exists(path) else {}
        for line in ALL:
            if args and line[0] not in args:
                continue
            report[line[0]] = {'said': el.stt(f'{VO}/{line[0]}.mp3'), 'script': line[1]}
            print(line[0], '|', report[line[0]]['said'])
        json.dump(report, open(path, 'w'), ensure_ascii=False, indent=1)
        sys.exit(0)
    for line in ALL:
        lid = line[0]
        mp3 = f'{VO}/{lid}.mp3'
        if '--wav' in sys.argv:
            if os.path.exists(mp3) and not os.path.exists(f'{VO}/{lid}.wav'):
                el.to_wav(mp3, f'{VO}/{lid}.wav')
            continue
        if args and lid not in args:
            continue
        if not args and os.path.exists(mp3):
            continue
        el.tts(VOICE, spoken(line), f'{VO}/{lid}', seed=SEEDS.get(lid, 11))
        print(lid, f"{el.duration(f'{VO}/{lid}.wav'):5.2f}s", flush=True)
    write_meta()
