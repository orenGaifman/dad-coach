"""ElevenLabs music beds (instrumental), kept as mp3: audio/music/bed_training.mp3 (one bed for every training video,
trimmed and faded per video by the mix) and ../ad/audio/bed_ad.mp3 (the ad).  python3 audio/music.py [training|ad]"""
import os, sys
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import el

BEDS = {
    'training': (f'{HERE}/music/bed_training.mp3', 82000,
                 'Instrumental only, no vocals. Warm, light, gentle background for a short how-to video for fathers: '
                 'soft fingerpicked acoustic guitar and felt piano, a light shaker, very soft kick at 92 bpm, warm pads, '
                 'calm and friendly, major key, steady and unobtrusive under a voice-over, no big drops, no build-ups, '
                 'ends on a soft resolving chord. Not cheesy, not corporate.'),
    'ad': (f'{HERE}/../../ad/audio/bed_ad.mp3', 64000,
           'Instrumental only, no vocals. Warm, heartfelt, modern background for a 60 second ad about fathers making real '
           'time with their kids: starts sparse and a little restless (muted felt piano, ticking hi-hat) for 6 seconds, '
           'then opens up warm and hopeful with fingerpicked acoustic guitar, soft claps and a gentle kick at 96 bpm, '
           'light pads, a subtle lift around 45 seconds, ends with a clean resolving final chord at 62 seconds. '
           'Tender but not sentimental, not cheesy, uncluttered.'),
}

if __name__ == '__main__':
    for name in (sys.argv[1:] or BEDS):
        path, ms, prompt = BEDS[name]
        el.music(prompt, ms, path)
        print(name, path, round(el.duration(path), 1), 's')
