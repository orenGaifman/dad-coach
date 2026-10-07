"""Dad Coach's spoken intro for the hero (ElevenLabs TTS, Hebrew, male voice, masculine address).

Each caption line is synthesized on its own and joined with a fixed pause, so the caption timings in
site/src/intro.js are exact (this script prints them). Output: site/public/dad-coach-intro.mp3.

Run from the repo root:  python3 site/scripts/intro_voice.py [workdir]
Needs ffmpeg and ELEVENLABS_API_KEY in ~/repos/.env (read here, never printed).
Optional check: python3 site/scripts/intro_voice.py --stt  (transcribes the result back, Hebrew).
"""
import json, os, subprocess, sys, urllib.request, uuid

ENV = os.path.expanduser('~/repos/.env')
KEY = [l.split('=', 1)[1].strip().strip('"') for l in open(ENV) if l.startswith('ELEVENLABS_API_KEY=')][0]
API = 'https://api.elevenlabs.io'
VOICE = 'uwHajH4FhtzVp6X17pr7'  # "amit" (male, auditioned for the Big Boss ad)
MODEL = 'eleven_v3'
GAP = 0.32  # seconds of silence between lines
OUT = os.path.join(os.path.dirname(__file__), '..', 'public', 'dad-coach-intro.mp3')

# (caption shown on the site, text spoken). "Dad Coach" is spelled for the Hebrew voice.
LINES = [
    ('היי, אני Dad Coach.', 'היי, אני דֶּד-קוֹאוּטְשׁ.'),
    ('אתה מחליט כמה זמן אתה רוצה עם הילדים השבוע,', 'אתה מחליט כמה זמן אתה רוצה עם הילדים השבוע,'),
    ('ואני דואג שזה באמת יקרה.', 'ואני דואג שזה באמת יקרה.'),
    ('מזכיר לך בבוקר ושעה לפני, ושואל אחרי איך היה.', 'מזכיר לך בבוקר, ושעה לפני... ושואל אחרי, איך היה.'),
    ('התבטל משהו? בלי רגשות אשם. מוצאים זמן אחר.', 'התבטל משהו? בלי רגשות אשם. מוצאים זמן אחר.'),
    ('וכשהשבוע מכוסה, אני שקט.', 'וכשהשבוע מכוסה... אני שקט.'),
]


def call(path, body):
    req = urllib.request.Request(API + path, data=json.dumps(body).encode(), method='POST')
    req.add_header('xi-api-key', KEY)
    req.add_header('Content-Type', 'application/json')
    with urllib.request.urlopen(req, timeout=180) as r:
        return r.read()


def duration(path):
    return float(subprocess.run(['ffprobe', '-v', 'error', '-show_entries', 'format=duration', '-of', 'csv=p=0', path],
                                capture_output=True, text=True, check=True).stdout.strip())


def stt(path):
    b = uuid.uuid4().hex
    data = open(path, 'rb').read()
    body = (f'--{b}\r\nContent-Disposition: form-data; name="model_id"\r\n\r\nscribe_v1\r\n--{b}\r\n'
            f'Content-Disposition: form-data; name="language_code"\r\n\r\nheb\r\n--{b}\r\n'
            f'Content-Disposition: form-data; name="file"; filename="a.mp3"\r\nContent-Type: audio/mpeg\r\n\r\n').encode() \
        + data + f'\r\n--{b}--\r\n'.encode()
    req = urllib.request.Request(API + '/v1/speech-to-text', data=body, method='POST')
    req.add_header('xi-api-key', KEY)
    req.add_header('Content-Type', f'multipart/form-data; boundary={b}')
    with urllib.request.urlopen(req, timeout=120) as r:
        return json.loads(r.read())['text']


def main(work):
    os.makedirs(work, exist_ok=True)
    wavs, starts, t = [], [], 0.0
    for i, (caption, spoken) in enumerate(LINES):
        mp3 = os.path.join(work, f'l{i}.mp3')
        wav = os.path.join(work, f'l{i}.wav')
        body = {'text': spoken, 'model_id': MODEL, 'seed': 11,
                'voice_settings': {'stability': 0.5, 'similarity_boost': 0.8, 'style': 0.25, 'use_speaker_boost': True}}
        open(mp3, 'wb').write(call(f'/v1/text-to-speech/{VOICE}?output_format=mp3_44100_128', body))
        # trim leading/trailing silence so the fixed gap is the only pause
        subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', mp3, '-af',
                        'silenceremove=start_periods=1:start_threshold=-50dB,areverse,silenceremove=start_periods=1:start_threshold=-50dB,areverse',
                        '-ar', '44100', '-ac', '1', wav], check=True)
        starts.append(round(t, 2))
        t += duration(wav) + GAP
        wavs.append(wav)
    gap = os.path.join(work, 'gap.wav')
    subprocess.run(['ffmpeg', '-v', 'error', '-y', '-f', 'lavfi', '-i', f'anullsrc=r=44100:cl=mono', '-t', str(GAP), gap], check=True)
    lst = os.path.join(work, 'list.txt')
    with open(lst, 'w') as f:
        for i, w in enumerate(wavs):
            f.write(f"file '{w}'\n")
            if i < len(wavs) - 1:
                f.write(f"file '{gap}'\n")
    subprocess.run(['ffmpeg', '-v', 'error', '-y', '-f', 'concat', '-safe', '0', '-i', lst,
                    '-af', 'loudnorm=I=-16:TP=-1.5:LRA=11', '-ar', '44100', '-ac', '1', '-b:a', '80k', OUT], check=True)
    print(f'wrote {os.path.relpath(OUT)} ({duration(OUT):.1f}s)')
    print('LINES for site/src/intro.js:')
    for (caption, _), s in zip(LINES, starts):
        print(f"  [{s}, '{caption}'],")


if __name__ == '__main__':
    if len(sys.argv) > 1 and sys.argv[1] == '--stt':
        print(stt(OUT))
    else:
        main(sys.argv[1] if len(sys.argv) > 1 else '/tmp/dad-coach-intro')
