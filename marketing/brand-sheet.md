# Dad Coach — brand sheet (site, ad, training videos)

Sources: `dad-coach-web/docs/brand/{MISSION,TONE_OF_VOICE,ANTI_GOALS,BRAND_PRINCIPLES}.md` and
`docs/design/{COLOR_SYSTEM,TYPOGRAPHY,ILLUSTRATION_STYLE,MOTION_PHILOSOPHY}.md`. The implemented tokens
are `site/src/styles/tokens.css`; the new dashboard (WS-B) should import the same values so the site,
the app and the videos stay one family.

## Essence

- **Mission:** help every father be the dad he wants to be, one intentional step at a time.
- **Product promise (one line):** הזמן שתכננת עם הילדים. הפעם הוא קורה.
- **Personality:** calm, warm, direct, encouraging, trustworthy. A knowledgeable friend, never a
  lecturer, never a nag.
- **Hard lines (ANTI_GOALS):** no guilt or shame, no over-notifying, no selling data, no maximizing
  screen time, no gamification that competes (no leaderboards, no comparison), no single "right way",
  not a substitute for professionals.

## Color

| Token | Hex | Use |
|---|---|---|
| navy-950 | `#0B1424` | header, footer, the darkest night |
| navy-900 | `#101C32` | hero and navy bands, phone frame (from the logo background) |
| navy-800 | `#1D2B4B` | primary dark buttons, WhatsApp chat header, "קרה" in progress bars (logo circle) |
| navy-700 | `#2A3D66` | links, focus ring, icon tiles on navy |
| navy-200 / 100 | `#C9D0DE` / `#E6E9F0` | text on navy / light icon tiles |
| gold-500 | `#DAAC5F` | the ONE primary action (מתחילים בוואטסאפ), accents on navy, earned moments (torii gold) |
| gold-300 / 100 | `#EBCB91` / `#F7EEDC` | hover, "דוגמה" badges, takeaway boxes |
| gold-700 | `#7F5B1C` | gold text on light backgrounds (5.8:1 on cream) |
| cream | `#FAF7F1` | page background (warm paper, never pure white) |
| sand-100 | `#F2ECE1` | tinted sections, captions |
| ink-900 / 700 / 500 | `#1F1B16` / `#4A443C` / `#6B6358` | headings / body / muted (all ≥ 4.5:1 on white) |
| success-600 | `#3F7D4E` | "קרה ✔" |
| danger-600 | `#B4473B` | form errors only |
| WhatsApp | `#EFE7DD` bg, `#FFFFFF` in, `#D9FDD3` out | only inside the phone |

Rules (COLOR_SYSTEM): never more than two colors competing on a view; gold is rare and means
"earned" or "the one action"; backgrounds warm and quiet; all text AA, critical text AAA where possible.
In videos: navy night backgrounds, cream cards, gold only for the completed session, the belt and the CTA.

## Type

- **Assistant** (Google Fonts) for everything: a humanist sans with good Hebrew, warm without being
  playful. Weights 400 body, 600 emphasis, 700–800 headings (headings are medium-heavy, never
  condensed, never uppercase Latin).
- Body 17px / line-height 1.65 on the site (TYPOGRAPHY: 16–18px, 1.6–1.75). Headings 20–32px, the
  hero 37–54px. Three or four sizes per view at most.
- Videos: captions 54–64px Assistant 700 on a navy-950/80% plate, max two lines, ≤ 12 words.
- Hebrew numbers and ranges inside RTL text: isolate them (`.ltr` / Unicode isolates) so "2–3" never flips.

## Logo and artwork

- **Logo mark:** the gold torii with the father inside, on a navy circle (`site/public/img/logo-mark.webp`,
  from `dad-coach-web/public/logos/dad-coach-logo-icon.webp`). Always circular, min 28px, on navy or
  cream; never recolored, never on a busy photo without a plate.
- **Full logo:** the torii with the father and two children (`logo-full.webp`) — for end cards and the
  site's final CTA.
- **Wordmark:** "Dad Coach" in Latin, set LTR inside Hebrew (`lang="en" dir="ltr"`). In Hebrew speech
  it is pronounced "דֶּד קוֹאוּץ'".
- **Artwork:** the brand's existing 3D illustrations — the hero scene (father and two kids facing a
  sunrise with a torii), the father in a gi for each belt (white → black; purple exists but is not a
  product belt), the coach avatar. Use as provided, cropped, never redrawn. ILLUSTRATION_STYLE prefers
  restraint: one artwork per section at most; the WhatsApp screens are the main visual.
- **Belts** in product order: לבנה (0), צהובה (3), כתומה (10), ירוקה (25), כחולה (50), חומה (100),
  שחורה (200) completed sessions (`workflow/Belt.java`).

## Voice and copy

- Address the father in the masculine singular ("אתה קובע", "תכתוב לו"); generic plurals masculine
  ("אבות"); children by first name; never "ליד" (use "פנייה" where needed).
- Short, concrete, warm. Specific over generic ("נועה, שלישי 17:00, שעה" beats "תבלה עם הילדים").
- Forward-looking after a miss: "קורה, העיקר שממשיכים" — never "פספסת", "שוב", "חבל".
- Every number on the site or in an ad is real (a product rule) or labelled "דוגמה".
- Emoji: at most one, and only where natural (🙂 💪 ☀️), as the coach itself writes.

## Motion

Quiet by default, expressive only for earned moments (MOTION_PHILOSOPHY): 150–250 ms transitions,
ease-out entrances, no bounce, no parallax, no confetti. The belt change may glow (500–800 ms).
`prefers-reduced-motion` → instant states, the demo shown complete.
