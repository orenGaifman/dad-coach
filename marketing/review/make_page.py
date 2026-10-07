"""python3 marketing/review/make_page.py -> review/index.html + review/media/ (hard links to the release files, ignored
by git): the five father videos and the ad on one page for the owner's review. Titles, descriptions and the primary
flag come from backend/src/main/resources/training/catalog.json; lengths are measured on the released files."""
import html, json, os, subprocess

HERE = os.path.dirname(os.path.abspath(__file__))
MK = os.path.dirname(HERE)
CAT = json.load(open(f'{os.path.dirname(MK)}/backend/src/main/resources/training/catalog.json'))
REL = f'{MK}/training/release/training/v2'
SHOWS = {
    'welcome': 'The site card (name and mobile, then its done state), the first WhatsApp message, the welcome, the name, '
               'one child and age, the confirmation, the first weekly goal and the slots the coach offers right after it.',
    'book-a-session': 'The free slots the coach offers, a booking typed in one message, the confirmation with the week so far, '
                      'and "איך אני עומד השבוע?" answered with minutes and what is left.',
    'reminders': 'The morning reminder as the coach promises it, the reminder an hour before with ideas for a 7-year-old, '
                 '"נו, איך היה?", the father\'s answer recorded, and that note next to the session on the dashboard.',
    'when-cancelled': 'A cancel and the replacement slots in the same week, the rebooking, "לא הספקנו" answered without guilt, '
                      'and the belt card (it counts only sessions that happened).',
    'my-dashboard': 'The login page (phone number, link in WhatsApp), home (goal, done, booked), the next session and the belt, '
                    'a session waiting for "היה / לא יצא", sessions, progress with the belts, settings (calendar optional).',
}
os.makedirs(f'{HERE}/media', exist_ok=True)


def dur(p):
    return float(subprocess.run(['ffprobe', '-v', 'error', '-show_entries', 'format=duration', '-of', 'csv=p=0', p], capture_output=True, text=True).stdout)


def link(src, name):
    dst = f'{HERE}/media/{name}'
    if os.path.exists(dst):
        os.remove(dst)
    os.link(src, dst)
    return f'media/{name}'


def mmss(d):
    return f'{int(d // 60)}:{int(round(d % 60)):02d}'


def card(name, mp4, jpg, title, desc, shows, tag, primary=False):
    d = dur(mp4)
    return d, f'''
    <article class="card{' primary' if primary else ''}" id="{name}">
      <video controls playsinline preload="none" poster="{link(jpg, name + '.jpg')}" src="{link(mp4, name + '.mp4')}"></video>
      <div class="meta">
        <div class="row"><span class="tag{'' if primary else ' quiet'}">{tag}</span><span class="len">{mmss(d)} · {os.path.getsize(mp4) / 1e6:.1f} MB</span></div>
        <h3 lang="he" dir="rtl">{html.escape(title)}</h3>
        {f'<p class="desc" lang="he" dir="rtl">{html.escape(desc)}</p>' if desc else ''}
        <p class="shows">{html.escape(shows)}</p>
        <p class="file">{html.escape(name)}.mp4</p>
      </div>
    </article>'''


cards, total = [], 0.0
for e in sorted(CAT, key=lambda e: e['order']):
    if not e['active'] or not e['video']:
        continue
    name = f'father-{e["slug"]}'
    d, c = card(name, f'{REL}/{name}.mp4', f'{REL}/{name}.jpg', e['title'], e['description'], SHOWS[e['slug']],
                'Primary · after signup' if e['primary'] else f'Guide {e["order"]}', e['primary'])
    total += d
    cards.append(c)
    print(name, round(d, 1))
ad_d, ad = card('dad-coach-ad-v2', f'{MK}/ad/release/dad-coach-ad-v2.mp4', f'{MK}/ad/release/dad-coach-ad-v2.jpg',
                'הזמן שתכננת. הפעם הוא קורה.', '',
                'Pain (the week pushes the planned time away), the brand, booking in one message, the reminder an hour '
                'before, "נו, איך היה?", the dashboard, a missed session without guilt, the half-minute setup, a covered '
                'week with no nagging, the motto and the call to action. Voice 7% faster than the library.', 'Ad · 9:16')
print('ad', round(ad_d, 1))

NOTES = '''
<div class="note"><span class="state ok">Checked</span><h3>Only real lines and real screens</h3>
<ul><li>Every WhatsApp bubble is copied from the qa-lab runs v4-s1 and v4-s2 on the real workflow. Three are shortened by
dropping whole sentences; each cut is noted in <code>training/film/msgs.js</code>.</li>
<li>Every dashboard is a capture of the real dashboard on the lab's demo data and carries its "הדגמה" badge. The site
card is the real site with a sample name.</li>
<li>Every clip of the voice ("amit", male, the site intro's voice) was transcribed back and compared with its script.</li>
<li>The "השבוע שלך" week in the ad is our drawing and is labelled "איור".</li></ul></div>
<div class="note"><span class="state warn">Shown as a promise</span><h3>What the lab did not produce</h3>
<ul><li>No morning reminder and no Sunday check-in fired in the lab. The reminders video shows the coach's own sentence
"אזכיר לך בבוקר ושעה לפני" and does not draw a morning message.</li>
<li>Logging time that happened without a booking has no tool yet, so that video is not in the library.</li></ul></div>
<div class="note"><span class="state ok">v2</span><h3>Hebrew on every screen</h3>
<ul><li>The brand is written "דאד קואץ׳" everywhere: titles, captions, the chat header, notifications, the end card.
The voice says the name as before (the clips are unchanged).</li>
<li>Every coach bubble starts with "❤️ דאד קואץ׳:", the identity line live since 2026-10-07; the rest of each bubble is
verbatim. In the welcome bubble the coach's own "אני Dad Coach" is "אני דאד קואץ׳", as the workflow now writes it.</li>
<li>The dashboard screens were captured again from the current dashboard (local run, same demo data). The settings
screen still says "יומן Google", as the dashboard does; the caption says "יומן גוגל".</li>
<li>The site card is the real site with its brand name shown in Hebrew. The live site still writes "Dad Coach" and
needs the same change.</li>
<li>The ad's end card shows dad-coach-site.onrender.com, the site today.</li></ul></div>
<div class="note"><span class="state ok">Ready</span><h3>Upload</h3>
<ul><li>The files are <code>marketing/training/release/training/v2/father-&lt;slug&gt;.mp4|.jpg</code>; on the Bunny
zone they go to <code>dad-coach/training/v2/</code>, the paths the catalog already names.</li>
<li>Loudness: the videos at −16 LUFS, the ad at −14 LUFS, music under the voice well below −24 LUFS.</li></ul></div>'''

page = open(f'{HERE}/template.html').read()
page = (page.replace('{{TOTAL}}', f'{int(total // 60)} min {int(round(total % 60))} s').replace('{{COUNT}}', str(len(cards)))
        .replace('{{CARDS}}', ''.join(cards)).replace('{{AD}}', ad).replace('{{NOTES}}', NOTES))
open(f'{HERE}/index.html', 'w').write(page)
print('review/index.html', len(cards), 'videos +', 'ad', mmss(total))
