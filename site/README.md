# Dad Coach marketing site

Static, Hebrew RTL landing page + four legal pages. Vite 6 multi-page build, plain HTML/CSS/JS, no
framework. Reference implementation: `~/repos/tair/site` (same structure, same quality bar). Brand and
design rules come from `dad-coach-web/docs/brand` and `docs/design`; tokens live in
`src/styles/tokens.css`. Light page with navy bands, address the father in the masculine singular.

**Everything visible is Hebrew.** The brand is written **דאד קואץ׳** (Hebrew geresh ׳, U+05F3), never
"Dad Coach", in headings, buttons, the demo phone, alt texts, meta tags, the OG image, the footer and the
legal pages. Latin stays only where it is a name or a command: vendor names in the privacy table (Meta,
Render, Supabase, Google Fonts), standards and screen readers in the accessibility statement, and the
deletion command `DELETE MY DATA` (the backend only knows the English phrase; the FAQ says so).

**Voice: impressive and humorous** (owner, 2026-10-07). The jokes are fathers recognizing themselves,
never mocking the father or the kids, never guilt. Humor lives in the headlines and around the demo; the
coach's own bubbles stay the real product lines. Page story (UX pass 2026-10-08: say what it is in one
line, examples a father gets at once, no "מבצר"/"פארק" riddles): the hero ("יותר זמן עם הילדים. כל שבוע, באמת.",
a one-line gym-coach analogy, the three steps as a numbered list) with the coach in his black belt
(`coach-black-belt.webp`, from the dashboard's belt art), the belt ladder under him and his voice intro in his
speech bubble; then the excuse graveyard right after the hero (`src/excuses.js`: a tombstone per excuse, פ״נ/ז״ל,
tapping one shows "סיבת המוות:" = what the product really does), how it works, the belts as a dojo with a pace
switch (`src/belts.js`, plain arithmetic on the real thresholds, labeled as such), the demo, capabilities, the
covered week ("זה לא באג, זה פיצ׳ר."), the notifications he never gets, the personal page, FAQ (two "שאלה כמעט
רצינית"), and the final call ("החגורה הלבנה מחכה."). Example activities are concrete (מגדל לגו, כדורגל בסלון).
Every motion respects `prefers-reduced-motion` and `?demo=static` (the HTML is the final state).

## Build and preview

```sh
cd site
npm ci
npm run build        # → site/dist/ (static, deploy as-is)
npm run preview      # serves dist on http://localhost:4175
npm run dev          # live reload while editing
npm run images       # re-render og-image.png, apple-touch-icon.png, favicon.png (headless Chrome) + the WhatsApp QR
                     # (the OG headline is the hero's: "יותר זמן עם הילדים. כל שבוע, באמת.", with the black-belt coach)
npm run screenshots  # Playwright: screenshots/home-390.png, demo-1440.png, full-{390,1440}.jpg (needs `npm run preview` running)
python3 scripts/intro_voice.py   # (from the repo root) re-record the hero voice intro, prints the caption timings
```

`dist/` contains `index.html`, `privacy/`, `terms/`, `data-deletion/`, `accessibility/`, `robots.txt`,
`sitemap.xml`, the images, the intro mp3 and hashed `assets/`. Directory URLs (`/privacy/`), so any
static host works without rewrites. `/data-deletion/` keeps the path Meta's app settings may point to.

## Configuration (build-time env vars, see `src/config.js`)

| Env var | Default | Effect |
|---|---|---|
| `VITE_SIGNUP_ENDPOINT` | empty | Where the desktop signup form POSTs. **Empty → nothing is sent**; the visitor still sees "תודה, נחזור אליך בוואטסאפ בהקדם". Target: `https://<dad-coach host>/api/public/site-signups`, with `SITE_ORIGINS` on the backend set to this site's origin. |
| `VITE_WHATSAPP_NUMBER` | `972552961164` | The Dad Coach number every "מתחילים בוואטסאפ" link opens (prefilled text "היי, אני רוצה להתחיל"; he still presses send). The HTML carries the default too, so links work without JS. The QR (`public/img/wa-qr.svg`) is rendered by `npm run images` — re-render it if the number changes. |
| `VITE_ANALYTICS_SRC` + `VITE_ANALYTICS_DOMAIN` | empty | Cookieless analytics hook (Plausible-style). Loaded only when both are set. The coach intro then reports `coach-intro` play/ended events. Update the privacy page before enabling. |

**Signup POST contract** (JSON): `{ name, phone /* E.164 +9725… */, source: "site", page, website /* honeypot, empty */ }`.
The backend answers 202 for every readable body (stored, honeypot, rate-limited or invalid look the same).
The form shows a retry message only on a network error/timeout (10 s) or a non-2xx answer. Phone
validation accepts Israeli mobiles only (05X), local or +972 format. Hidden only on phones/touch
devices (there the wa.me link is the whole flow).

## The demo

`src/demo/scenarios.js` mirrors `../marketing/whatsapp-demo-flow.md` line for line (edit the doc
first). Every coach bubble and lock-screen notification opens with the product's identity line
`❤️ דאד קואץ׳:` (`COACH_LINE`), and the phone's contact name is דאד קואץ׳. The coach lines are **drafts from the workflow rules**; replace them with real qa-lab lines
before launch (checklist at the end of the doc). `src/demo/player.js` is deterministic: typing
indicator, proactive messages shown first as a lock-screen notification, a "השבוע שלך" card that
follows the week, pause/skip/replay, pauses when scrolled away or the tab is hidden.
`prefers-reduced-motion` or `?demo=static` → every scenario renders complete and static.

## The coach intro (try-out)

`src/intro.js`: the black-belt coach card in the hero + a 20-second Hebrew male
voice (ElevenLabs, voice "amit", script in `scripts/intro_voice.py`) with live captions in his speech bubble. Plays only on
tap. `?coach=off` hides it, to compare the hero with and without it. The captions say דאד קואץ׳; the
recording says the name out loud, which sounds the same in Hebrew, so the mp3 is unchanged. Note: the brand's
ILLUSTRATION_STYLE says "no character mascots"; the dashboard already uses this character, so it is
used here as a measured, removable experiment.

## Placeholders the owner must fill before launch

- **Domain**: every `https://www.dad-coach.example` (canonical, OG, robots, sitemap):
  `grep -rn "dad-coach.example" site --include=*.html --include=*.txt --include=*.xml`.
  Never `dadcoach.app` — that is a third-party site.
- **Legal/company details** (highlighted `class="ph"`): company name, ח.פ., address, contact email,
  dates, retention periods, sub-processor regions, the LLM provider's data terms, liability cap,
  jurisdiction city, accessibility coordinator. `grep -rn 'class="ph"' site/*/index.html site/index.html`.
- **Legal pages are drafts** with a visible "טיוטה לבדיקת עו״ד" banner: Hebrew rewrites of the
  dad-coach-web English pages (`app/{privacy,terms,data-deletion}`), extended to what the product
  really does (children's first names and ages, AI processing, the platform, Google Calendar). Review
  by an Israeli lawyer, then remove the banner.

## Honesty rules baked in

No testimonials, logos, ratings, user counts or invented statistics. The demo says "שיחה מתוסרטת
להמחשה … לדוגמה", the week card and the weekly summary carry a "דוגמה" badge, the footer repeats it.
Belt thresholds are the real ones (`workflow/Belt.java`), timer times are the real policy
(`SessionTimerPlanner`: 08:00 on the day, 1 hour before, 30 minutes after the end). The dojo's "שבוע N" is labeled as arithmetic,
not a promise; the struck-through notifications are invented on purpose (they are what he never gets).
No testimonials, no stats, no price beyond the launch wording.

## Slots for real product screenshots

`[data-screenshot-slot="dashboard-week"]` — the "שבוע של אבא" summary mockup. Replace with a real
screenshot of the new dashboard's week view once it ships (keep the "דוגמה" label until it shows
real data with permission).

## Structure

```
site/
  index.html                       landing page
  privacy/ terms/ data-deletion/ accessibility/   legal pages (index.html each)
  src/main.js                      reveal, demo, signup form, intro, excuses, belt pace
  src/excuses.js, belts.js         the excuse tombstones, the dojo's pace switch
  src/config.js                    the configuration above
  src/signup-form.js               validation, phone normalization, submit (honeypot, 10 s timeout)
  src/intro.js                     the coach's voice intro + captions
  src/demo/scenarios.js            demo script (mirror of marketing/whatsapp-demo-flow.md)
  src/demo/player.js               deterministic player
  src/styles/tokens.css, site.css  design tokens and all styles
  public/                          robots.txt, sitemap.xml, og-image.png, apple-touch-icon.png, favicon.png,
                                   dad-coach-intro.mp3, img/ (logo, belts, hero, coach, QR)
  og/                              HTML sources of the rendered PNGs
  scripts/                         render-images.sh, intro_voice.py, screenshots.mjs
  screenshots/                     home-390, demo-1440, full-page 390/1440 (for the hub page)
  render.yaml                      the Render static service (Blueprint path site/render.yaml)
```

Images: coach-black-belt.webp (hero + OG), hero.webp (the final call to action and the demo lock screen), belts/, and two dashboard
illustrations copied from `frontend/public/img` (coach-thinking: the excuses; mission-quality-time: how it
works). All lazy except the first screen, which has no image: the LCP is the headline.

Quality notes: one layout breakpoint (760px) plus a 1080px grid step, logical CSS properties,
no horizontal scroll at 390px (checked by `npm run screenshots`), visible focus ring, skip link,
semantic headings, text contrast ≥ 4.5:1 (muted ink #6B6358 on white 5.9:1, gold-700 on cream 5.8:1).
Fonts: Assistant from Google Fonts (disclosed in the privacy page); self-hosting is a small follow-up.

## Deploy (main session)

Render static site `dad-coach-site` from `site/render.yaml` (build `cd site && npm ci && npm run build`,
publish `site/dist`). Then: domain + SSL → fill the placeholders → set `VITE_SIGNUP_ENDPOINT` here and
`SITE_ORIGINS` on the `dad-coach` service → lawyer review of the legal pages → lab lines in the demo.
