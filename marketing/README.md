# marketing/

Dad Coach's launch kit documents (playbook §58.1). Reference: `~/repos/tair/marketing`.

| File | What it is |
|---|---|
| `whatsapp-demo-flow.md` | The scripted demo conversations (5 scenarios, the father's phone). **Single source of truth** for the site's demo (`site/src/demo/scenarios.js` mirrors it). Coach lines are drafts from the workflow rules until replaced by qa-lab transcripts. |
| `ad-concepts.md` | Three concepts for the ~60 s vertical (9:16) ad, three mottos, the recommendation. |
| `brand-sheet.md` | Colors, type, logo and image usage for the site, the ad and the videos (from dad-coach-web `docs/design`). |
| `campaign-plan.md` | Meta campaign for Israeli fathers 28–45: structure, budget tiers, KPIs (WhatsApp conversation → onboarded → first session completed), measurement. |
| `training-plan.md` | The father onboarding video library (6 videos ≤ 60 s), what each shows, voice-over drafts. |
| `ad/`, `training/` | Production (later session, WS-E): HTML film + Playwright render + ElevenLabs VO, like `tair/marketing/ad`. |

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
| DC-D09 | Ad + training videos from the lab and the real dashboard | OPEN (WS-E) |
