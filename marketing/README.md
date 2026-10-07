# marketing/

Dad Coach's launch kit documents (playbook §58.1). Reference: `~/repos/tair/marketing`.

| File | What it is |
|---|---|
| `whatsapp-demo-flow.md` | The scripted demo conversations (5 scenarios, the father's phone). **Single source of truth** for the site's demo (`site/src/demo/scenarios.js` mirrors it). Coach lines are drafts from the workflow rules until replaced by qa-lab transcripts. |
| `ad-concepts.md` | Three concepts for the ~60 s vertical (9:16) ad, three mottos, the recommendation. |
| `brand-sheet.md` | Colors, type, logo and image usage for the site, the ad and the videos (from dad-coach-web `docs/design`). |
| `campaign-plan.md` | Meta campaign for Israeli fathers 28–45: structure, budget tiers, KPIs (WhatsApp conversation → onboarded → first session completed), measurement. |
| `training-plan.md` | The father onboarding video library (6 videos ≤ 60 s), what each shows, voice-over drafts. |
| `training/`, `ad/` | The father video library (5 videos) and the 9:16 ad, built from the lab and the real dashboard. See **Videos** below. |
| `review/` | `make_page.py` builds the review page of all six films (`review/index.html`). |

The site lives in `../site` (see its README).

## Open owner decisions

1. **Price wording (site FAQ, hero point, terms §6).** Draft: "בתקופת ההשקה ההצטרפות ללא עלות; לפני כל
   שינוי נודיע מראש". Confirm, or give the price and the date it starts.
2. **Who follows up a desktop signup, and how.** The form stores name + mobile (admin page to be built
   by WS-B on `SiteSignupService.recentSignups(days)`). Nothing messages him automatically. Writing
   first to someone who never wrote to the number needs an approved WhatsApp template (owner submits
   templates) — or the team calls/sends from a personal phone. The thank-you text promises "נחזור
   אליך בוואטסאפ בהקדם"; change it if the answer is "no follow-up".
3. **The coach character + voice in the hero** (`?coach=off` to compare). The brand's
   ILLUSTRATION_STYLE says "no character mascots", but the dashboard already uses this character. Keep,
   or drop it. Voice: ElevenLabs "amit" (male), 20 s, script in `site/scripts/intro_voice.py`.
4. **Company / legal details** for the legal pages and footer (name, ח.פ., address, contact email,
   accessibility coordinator) and a lawyer review (the pages carry a "טיוטה לבדיקת עו״ד" banner).
   The old Vercel pages show the owner's personal email; the new ones use a placeholder on purpose.
5. **Domain** for the site (placeholder `www.dad-coach.example`; never `dadcoach.app`, a third-party
   baseball site).
6. **Motto** for the ad and the site footer: recommended "הזמן שתכננת. הפעם הוא קורה." (see
   `ad-concepts.md`).
7. **Analytics**: off. To measure the coach intro and the campaign (site visit → wa.me tap), enable a
   cookieless tool (Plausible-style) and update the privacy page.

## Dependencies on other workstreams (copy that is only true once they land)

- **D-007 (WS-A)** — "יומן Google לא חובה": booking must work without a connected calendar. Today the
  workflow still says booking needs the calendar (`dad-coach-3.workflow.yaml` section C).
- **WS-B dashboard** — "הקישור מגיע בוואטסאפ; לא צריך סיסמה" (login link), the week view (the site's
  `dashboard-week` screenshot slot), and "ילדים נוספים מוסיפים בלוח האישי" (ACTIVE_COACHING has no
  add_child tool, so more children are added in the dashboard — or WS-C adds the tool).
- **WS-C qa-lab** — replace every `draft` coach bubble in `whatsapp-demo-flow.md` with the real line.
- **Data deletion**: the phrase is the English `DELETE MY DATA` (`WhatsAppDeletionRequests`); a Hebrew
  phrase (e.g. "מחק את המידע שלי") and a Hebrew confirmation would fit a Hebrew product (WS-A), and a
  father's `site_signup` row should be removed with `SiteSignupService.deleteByPhone` on that path.

## Tasks (DC-D…, for docs/implementation/TASKS.md)

| Id | Task | Status |
|---|---|---|
| DC-D01 | `marketing/whatsapp-demo-flow.md`, 5 scenarios | DONE (draft lines) |
| DC-D02 | `site/` landing + demo + legal drafts + OG/icons + robots/sitemap | DONE |
| DC-D03 | `com.dadcoach.publicsite` signup endpoint + V40 + integration test | DONE |
| DC-D04 | `site/render.yaml` static service | DONE (main session creates the service) |
| DC-D05 | Marketing docs: ad concepts, brand sheet, campaign plan, training plan | DONE |
| DC-D06 | Admin page "הרשמות מהאתר" on `recentSignups(days)` | OPEN (WS-B) |
| DC-D07 | Replace draft demo lines with qa-lab transcripts | OPEN (WS-C → WS-D) |
| DC-D08 | Domain, placeholders, lawyer review, `VITE_SIGNUP_ENDPOINT` + `SITE_ORIGINS` | OPEN (owner + main) |
| DC-D09 | Ad + training videos from the lab and the real dashboard | DONE v2, Hebrew (upload to Bunny open) |

## Videos (v2, Hebrew)

Five father videos (catalog `backend/src/main/resources/training/catalog.json`) and one ad, all 1080×1920, 30 fps,
captions burned in, voice "amit" (male, the site intro's voice). Every word on screen is Hebrew: the brand is written
"דאד קואץ׳" (titles, captions, the chat header, notifications, the end card) and every coach bubble starts with
"❤️ דאד קואץ׳:", the WhatsApp identity line since 2026-10-07; the voice says the name as before. The only Latin is the
ad's site address and "יומן Google" on the real settings screen. v1 (Latin brand) stays in `release/training/v1` and on
the CDN; the CDN caches by URL, so a new cut is always a new folder.

| Slug | Title | What it shows |
|---|---|---|
| `welcome` (primary) | ברוך הבא ל-Dad Coach | site card → first message → name, child, confirm → weekly goal → offered slots |
| `book-a-session` | קובעים זמן עם הילדים | offered slots → a booking in one message → confirmation → "איך אני עומד השבוע?" |
| `reminders` | התזכורות | the morning promise → 1 h reminder with ideas → "נו, איך היה?" → answer recorded → the note on the dashboard |
| `when-cancelled` | כשמשהו מתבטל | cancel → replacement slots → rebooked → "לא הספקנו" without guilt → the belt card |
| `my-dashboard` | הלוח האישי שלך | login, home, next session and belt, awaiting card, sessions, progress, settings |
| ad | הזמן שתכננת. הפעם הוא קורה. | pain → brand → book → reminder → follow-up → dashboard → missed → setup → covered week → CTA |

Sources: every WhatsApp bubble is copied from qa-lab transcripts v4-s1 and v4-s2 (`training/film/msgs.js`; three
bubbles shortened by whole sentences, each marked `cut:`); every dashboard is a capture of the real SPA on the lab's demo
data (`training/film/assets/screens`, from `frontend/e2e/e2e-04-screenshots.spec.ts` phone, labelled "הדגמה"); the site
card is the real site with its brand name shown in Hebrew (`training/capture/site.mjs`; the live site still writes
"Dad Coach").
Not in the library yet: logging time that happened without a booking (no tool yet) and the calendar video (after D-007). No morning
reminder or Sunday check-in fired in the lab, so the reminders video shows the coach's promise, not a morning message.

### Build

Needs ffmpeg, Python 3, and Playwright: `marketing/node_modules` is a symlink to an existing install
(`ln -s ~/repos/big-boss-ad/node_modules marketing/node_modules`). The films are served from `marketing/` on
127.0.0.1:8788 (`build.sh` starts the server if it is not up).

```sh
cd marketing/training
node film/sheet.mjs <slug>          # contact sheet out/qa/<slug>_sheet_<n>.jpg - check a film before rendering it
./build.sh welcome book-a-session reminders when-cancelled my-dashboard ad
node film/posters.mjs               # release/training/v2/father-<slug>.jpg + ../ad/release/dad-coach-ad-v2.jpg
python3 release.py                  # release/media.json, checked against the catalog
./qa.sh welcome ad                  # length, size, loudness, a frame scan in out/qa
python3 ../review/make_page.py      # the review page
```

Screens: `node training/capture/site.mjs` (from `marketing/`) for the site card; for the dashboard, the phone screenshot test
(`npx playwright test e2e-04 -g phone` in `frontend/`, local stack as in `docs/implementation/DEPLOYMENT.md`), then copy
the files the films use from `frontend/e2e/screenshots/phone` to `training/film/assets/screens` (same sizes, 780 px wide).

`build.sh` renders each frame straight into ffmpeg (no frames on disk), mixes the sound (`audio/mix.py`: voice,
UI sounds, ducked music bed; −16 LUFS for the videos, −14 LUFS for the ad), writes `out/<v>_master.mp4` (archive,
not in git) and the web file, removes the intermediates, and stops when less than 700 MB is free. The voice is
regenerated with `audio/vo.py` (ElevenLabs; `--wav` rebuilds the ignored wavs from the committed mp3s), the music with
`audio/music.py`. The ad's end card shows dad-coach-site.onrender.com; another address goes in as `?site=`
(`ad/film/index.html?site=www.example`).

### Release and upload

`training/release/training/v2/father-<slug>.mp4|.jpg` go to the Bunny zone (shared with Big Boss) as
`dad-coach/training/v2/father-<slug>.mp4|.jpg` (`provisioning/scripts/upload_training_media.py`), the paths the catalog names; `release/media.json` lists each file with
its size, sha256 and length. The dashboard shows them once `TRAINING_MEDIA_BASE_URL` and the token key are set. The ad
is `ad/release/dad-coach-ad-v2.mp4` (+ `.jpg`), for the campaign, not the CDN.
