# Dad Coach marketing site

Static, Hebrew RTL landing page + four legal pages. Vite 6 multi-page build, plain HTML/CSS/JS, no
framework. Reference implementation: `~/repos/tair/site` (same structure, same quality bar). Brand and
design rules come from `dad-coach-web/docs/brand` and `docs/design`; tokens live in
`src/styles/tokens.css`. Light page with navy bands, address the father in the masculine singular.

## Build and preview

```sh
cd site
npm ci
npm run build        # → site/dist/ (static, deploy as-is)
npm run preview      # serves dist on http://localhost:4175
npm run dev          # live reload while editing
npm run images       # re-render og-image.png, apple-touch-icon.png, favicon.png (headless Chrome) + the WhatsApp QR
npm run screenshots  # Playwright screenshots into screenshots/ (needs `npm run preview` running)
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
first). The coach lines are **drafts from the workflow rules**; replace them with real qa-lab lines
before launch (checklist at the end of the doc). `src/demo/player.js` is deterministic: typing
indicator, proactive messages shown first as a lock-screen notification, a "השבוע שלך" card that
follows the week, pause/skip/replay, pauses when scrolled away or the tab is hidden.
`prefers-reduced-motion` or `?demo=static` → every scenario renders complete and static.

## The coach intro (try-out)

`src/intro.js`: the coach's avatar (the dashboard's `coach-avatar` artwork) + a 20-second Hebrew male
voice (ElevenLabs, voice "amit", script in `scripts/intro_voice.py`) with live captions. Plays only on
tap. `?coach=off` hides it, to compare the hero with and without it. Note: the brand's
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
(`SessionTimerPlanner`: 08:00 on the day, 1 hour before, 30 minutes after the end).

## Slots for real product screenshots

`[data-screenshot-slot="dashboard-week"]` — the "שבוע של אבא" summary mockup. Replace with a real
screenshot of the new dashboard's week view once it ships (keep the "דוגמה" label until it shows
real data with permission).

## Structure

```
site/
  index.html                       landing page
  privacy/ terms/ data-deletion/ accessibility/   legal pages (index.html each)
  src/main.js                      reveal, demo, signup form, intro
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
  screenshots/                     home/demo at 390 and 1440 px (for the hub page)
  render.yaml                      the Render static service (Blueprint path site/render.yaml)
```

Quality notes: one layout breakpoint (760px) plus a 1080px grid step, logical CSS properties,
no horizontal scroll at 390px (checked by `npm run screenshots`), visible focus ring, skip link,
semantic headings, text contrast ≥ 4.5:1 (muted ink #6B6358 on white 5.9:1, gold-700 on cream 5.8:1).
Fonts: Assistant from Google Fonts (disclosed in the privacy page); self-hosting is a small follow-up.

## Deploy (main session)

Render static site `dad-coach-site` from `site/render.yaml` (build `cd site && npm ci && npm run build`,
publish `site/dist`). Then: domain + SSL → fill the placeholders → set `VITE_SIGNUP_ENDPOINT` here and
`SITE_ORIGINS` on the `dad-coach` service → lawyer review of the legal pages → lab lines in the demo.
