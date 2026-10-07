"""ElevenLabs helper (copied from tair/marketing/ad/audio/el.py): tts -> mp3 + trimmed 48k mono wav, music, speech-to-text.
Reads ELEVENLABS_API_KEY from ~/repos/.env and never prints it."""
import json, os, subprocess, urllib.request, uuid
KEY = [l.split('=', 1)[1].strip().strip('"') for l in open(os.path.expanduser('~/repos/.env')) if l.startswith('ELEVENLABS_API_KEY=')][0]
API = 'https://api.elevenlabs.io'
TRIM = 'silenceremove=start_periods=1:start_threshold=-50dB,areverse,silenceremove=start_periods=1:start_threshold=-50dB,areverse'


def call(path, body=None, raw=False, timeout=240):
    req = urllib.request.Request(API + path, data=json.dumps(body).encode() if body is not None else None, method='POST' if body is not None else 'GET')
    req.add_header('xi-api-key', KEY); req.add_header('Content-Type', 'application/json')
    with urllib.request.urlopen(req, timeout=timeout) as r:
        data = r.read()
        return data if raw else json.loads(data)


def to_wav(mp3, wav):
    subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', mp3, '-af', TRIM, '-ar', '48000', '-ac', '1', wav], check=True)


def tts(voice_id, text, out, model='eleven_v3', stability=0.5, similarity=0.8, style=0.25, seed=11):
    body = {'text': text, 'model_id': model, 'seed': seed,
            'voice_settings': {'stability': stability, 'similarity_boost': similarity, 'style': style, 'use_speaker_boost': True}}
    open(out + '.mp3', 'wb').write(call(f'/v1/text-to-speech/{voice_id}?output_format=mp3_44100_128', body, raw=True))
    to_wav(out + '.mp3', out + '.wav')
    return out + '.wav'


def music(prompt, ms, out_mp3):
    open(out_mp3, 'wb').write(call('/v1/music?output_format=mp3_44100_192', {'prompt': prompt, 'music_length_ms': ms}, raw=True, timeout=400))


def stt(path):
    b = uuid.uuid4().hex
    body = (f'--{b}\r\nContent-Disposition: form-data; name="model_id"\r\n\r\nscribe_v1\r\n--{b}\r\n'
            f'Content-Disposition: form-data; name="language_code"\r\n\r\nheb\r\n--{b}\r\n'
            f'Content-Disposition: form-data; name="file"; filename="a.mp3"\r\nContent-Type: audio/mpeg\r\n\r\n').encode() \
        + open(path, 'rb').read() + f'\r\n--{b}--\r\n'.encode()
    req = urllib.request.Request(API + '/v1/speech-to-text', data=body, method='POST')
    req.add_header('xi-api-key', KEY); req.add_header('Content-Type', f'multipart/form-data; boundary={b}')
    with urllib.request.urlopen(req, timeout=120) as r:
        return json.loads(r.read())['text']


def duration(path):
    return float(subprocess.run(['ffprobe', '-v', 'error', '-show_entries', 'format=duration', '-of', 'csv=p=0', path],
                                capture_output=True, text=True, check=True).stdout.strip())
