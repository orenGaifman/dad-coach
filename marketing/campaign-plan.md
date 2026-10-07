# Dad Coach — campaign plan v1 (Meta, Israel) · 2026-10-07

Status: plan only. Nothing runs before the owner approves the budget tier, the creatives (rendered by
WS-E from the real lab chat and dashboard) and the open decisions in `README.md`.

## Goal and the funnel we optimize

The product succeeds when quality time **happens** (NORTH_STAR), so the campaign is judged by the
same thing, not by clicks:

| Stage | Definition | Where it is measured |
|---|---|---|
| 1. Conversation started | A father sends the first WhatsApp message to +972 55-296-1164 from an ad | Meta Ads Manager ("messaging conversations started"), Click-to-WhatsApp |
| 2. Onboarded | His profile has a display name and one child (ONBOARDING → ACTIVE_COACHING) | Product DB (father + child rows, created_at) |
| 3. Goal set | A weekly goal exists for his first week | Product DB (weekly goal) |
| 4. **First session completed** | His first quality-time session is confirmed "it happened" | Product DB (quality_time COMPLETED) |
| 5. Week 2 | He has a goal again the following Sunday-Saturday week | Product DB |

**North-star KPI of the campaign: cost per first completed session** (spend ÷ fathers reaching stage 4
within 14 days of their first message). Secondary: cost per conversation, stage-to-stage conversion,
week-2 return.

Attribution: Click-to-WhatsApp messages carry a `referral` object (ad id, source URL, `ctwa_clid`) in
the inbound webhook. **Task for WS-A:** store the referral on the father's first inbound message
(`first_referral_ad_id`, `first_referral_at`), so stages 2–5 can be counted per ad without guessing.
Until then, count per day/campaign by the first-message timestamp. No pixel, no cookies needed for
this funnel; the site's analytics stays off unless the owner enables a cookieless tool.

## Audience

- Israel, Hebrew. Men 28–45 (TARGET_AUDIENCE), parents. Meta's "Parents" demographics (parents of
  0–12) where available, else broad men 28–45 and let the creative self-select ("אבא").
- Lookalike later: from fathers who reached stage 4 (hashed phone list, with the privacy page updated
  to say so) — not before ~100 such fathers.
- Exclusions: existing fathers (custom audience of phones, once the privacy page covers it), and
  anyone who messaged in the last 30 days.
- Placements: Instagram Reels + Stories, Facebook Reels + Feed. 9:16 first; 4:5 cut for feed.
- Respect Meta's personal-attributes policy: the copy talks about the week ("השבוע נגמר לפני
  שהגעת לזמן עם הילדים?"), never asserts something about the viewer ("אתה אבא לא נוכח").

## Structure

| Campaign | Objective | Creative | Destination |
|---|---|---|---|
| A — Cold | Engagement → **Messaging conversations** (Click-to-WhatsApp) | Concept 1 (60 s) + its 15 s cut | WhatsApp, prefilled "היי, אני רוצה להתחיל" |
| B — Retargeting | Messaging conversations | Concept 2 (silence punchline) 30 s + 15 s | WhatsApp |
| C — Test (optional) | Traffic → site | Concept 3 15 s | The site (`/?utm_source=meta&utm_campaign=c3`) → wa.me; for fathers who want to read first |

Retargeting pool: 50%+ video viewers of A, Instagram/Facebook engagers, site visitors (only if the
site analytics is enabled).

Ad copy (primary text, draft, masculine singular):

> אתה כבר יודע שאתה רוצה עוד זמן עם הילדים. השבוע פשוט נגמר לפניו.
> Dad Coach הוא מאמן בוואטסאפ: אתה קובע כמה זמן השבוע, הוא מכניס את זה ללוח, מזכיר בבוקר ושעה לפני,
> ושואל אחרי איך היה. התבטל משהו? הוא מוצא זמן אחר, בלי רגשות אשם.
> הרשמה בהודעה אחת. בתקופת ההשקה ללא עלות.

Headline: "הזמן שתכננת. הפעם הוא קורה." · CTA button: "שליחת הודעה" (Send WhatsApp message).

## Budget tiers (₪, per month, excl. VAT)

| Tier | Spend | Split | When |
|---|---|---|---|
| **Pilot** | ₪3,000 (~₪100/day) | A 80% (two creatives, A/B), B 20% from week 2 | The first 4 weeks; learn the real funnel |
| **Grow** | ₪9,000 (~₪300/day) | A 65%, B 25%, C 10% | Only if Pilot shows stage-4 fathers and the coach holds up (lab + real conversations reviewed) |
| **Scale** | ₪20,000+ | A 60%, B 25%, lookalikes 15% | After ~100 stage-4 fathers and a stable week-2 rate |

Rules: one change at a time per ad set; let each ad set exit learning before judging (Meta needs a
few dozen events per week); pause any creative whose stage-2 rate is clearly below the others after
~50 conversations. All targets below are **hypotheses to validate in the Pilot, not market data**:

| Metric | Pilot hypothesis to test |
|---|---|
| Cost per conversation started | set after week 1 (no reliable benchmark for this category) |
| Conversation → onboarded | high, because onboarding is two questions; investigate if most stop at the name or the child question |
| Onboarded → first session completed (14 days) | the real test of the product; review the conversations of those who stop |
| Week-2 return | qualitative read in Pilot (sample too small) |

## Operations and readiness (before spending the first shekel)

- [ ] WhatsApp number capacity: the Dad Coach WABA tier and quality rating can carry the new
      conversations; the business profile (name, logo, description, website) is filled in.
- [ ] Every coach line in the ad matches the real workflow (lab transcripts).
- [ ] The site is live on the real domain with the legal pages reviewed (the ad's profile links to it).
- [ ] Someone reads new conversations daily in the Pilot (admin) and owns fixes.
- [ ] Referral capture (WS-A task above) — or accept day-level attribution for the Pilot.
- [ ] Cost: service conversations started by the father are within the 24 h window; proactive
      reminders outside the window need approved templates — check the template set covers the
      morning reminder, the 1-hour reminder, the follow-up and the Sunday check-in before scaling.

## Reporting (weekly, one table)

Spend · conversations started · onboarded · goal set · first session completed (14 d) · cost per
first completed session · week-2 return · notes from reading 10 conversations. Per creative and in total.
