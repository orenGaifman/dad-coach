// דאד קואץ׳ (Dad Coach) father training videos: index.html?v=<slug>. window.renderAt(t) draws the frame at t seconds; film/render.mjs
// screenshots it frame by frame. Every chat bubble is real lab output (msgs.js); every dashboard is a capture of the
// real SPA on the lab's demo data (frontend/e2e/screenshots, labelled "הדגמה"); the site card is the real site (capture/site.mjs).
import { clamp, lerp, ease, fadeIn, esc, phone, device, cam, dash, dashShot, dashCam, statusBar, notif, ost, buildTimeline, install, K, asset } from './lib/ui.js';
import { M } from './msgs.js';

const VO = await (await fetch('vo.json')).json();
const LINES = await (await fetch('lines.json')).json();
const V = new URLSearchParams(location.search).get('v') || 'welcome';

const out = (text, time, at, extra = {}) => ({ dir: 'out', text, time, ticks: 'blue', at, ...extra });
const inn = (text, time, at, extra = {}) => ({ dir: 'in', text, time, at, ...extra });
const day = (text) => ({ dir: 'day', text });
const chat = (lt, clock, messages, o = {}) => phone({ lt, clock, messages, ...o });
// typing a message into the compose field, then sending it
const typed = (text, lt, a, b) => text.slice(0, Math.round(text.length * clamp((lt - a) / (b - a))));
const caret = (lt) => Math.floor(lt * 2.4) % 2 === 0;

// a dashboard capture whose bottom navigation bar stays at the bottom of the screen while the page scrolls
const NAV = 130; // capture px of the SPA's bottom bar (last rows of every *-full capture)
const H = { '03-home-full.png': 3064, '05-sessions-past-full.png': 2142, '07-progress-full.png': 3824, '08-settings-full.png': 2438, '10-home-awaiting-full.png': 3604, '01-login.png': 1688 };
const VIEW = (1920 - 110) / K; // capture px visible under the status bar
function page(img, lt, o = {}) {
  const h = H[img];
  const scroll = clamp(o.scroll || 0, 0, Math.max(0, h - VIEW));
  const nav = o.nav === false ? '' : `<div style="position:absolute;left:0;right:0;bottom:0;height:${NAV * K}px;background:url(${asset('screens/' + img)}) bottom/1080px auto no-repeat;z-index:2"></div>`;
  return dash(img, { scroll, lt, hl: o.hl, noStatus: true }) + nav + statusBar(o.clock);
}
const scrollAt = (lt, a, b, from, to) => lerp(from, to, ease(clamp((lt - a) / (b - a))));

const titleCard = (lt, title, lede, o = {}) => {
  const p = fadeIn(lt, 0.1, 0.6);
  return `<div class="center" style="top:${o.top ?? 520}px;opacity:${p};transform:translateY(${lerp(30, 0, p)}px)">
    <div class="coach"><img src="${asset('coach-black-belt.webp')}"></div>
    <div class="title" style="margin-top:80px">${title}</div>${lede ? `<div class="lede">${lede}</div>` : ''}</div>`;
};
const endCard = (lt, beat, title, lede) => {
  const e = fadeIn(lt, 0.2, 0.6);
  return `<div class="center" style="top:470px;opacity:${e}">
    <div class="coach" style="width:440px"><img src="${asset('coach-black-belt.webp')}"></div>
    <div class="title" style="margin-top:80px;font-size:88px">${title}</div>${lede ? `<div class="lede">${lede}</div>` : ''}</div>`;
};
const steps = (lt, items, at, gap, top = 640) => items.map((t, i) => `<div class="step" style="top:${top + i * 200}px;opacity:${fadeIn(lt, at + i * gap, 0.4)}"><span class="n">${i + 1}</span>${t}</div>`).join('');

// ---------------------------------------------------------------- the chat histories (s1: יואב and נועה)
const S1 = {
  hi: out(M.hi, '15:40'), welcome: inn(M.welcome, '15:40'), name: out(M.name, '15:41'), askChild: inn(M.askChild, '15:41'),
  child: out(M.child, '15:41'), confirmQ: inn(M.confirmQ, '15:41'), yes: out(M.yes, '15:42'), registered: inn(M.registered, '15:42'),
  goal: out(M.goal, '15:42'), goalSet: inn(M.goalSet, '15:42'), book: out(M.book, '15:43'), booked: inn(M.booked, '15:43'),
};
const at = (m, t, extra = {}) => ({ ...m, at: t, ...extra });

const S = {};
// ---------------------------------------------------------------- 1. ברוך הבא
S.wTitle = (lt) => titleCard(lt, 'ברוך הבא לדאד קואץ׳', 'זמן עם הילדים, שבאמת קורה');
S.wSite = (lt) => {
  const p = fadeIn(lt, 0.1, 0.5), swap = ease(clamp((lt - 3.4) / 0.5));
  const card = (img, o) => `<img src="${asset('screens/' + img)}" style="position:absolute;inset:0;width:100%;opacity:${o}">`;
  return `${ost('מתחילים באתר', lt, { at: 0.1 })}
    <div class="card" style="left:90px;right:90px;top:${lerp(560, 520, p)}px;height:${(lerp(636, 758, swap) / 716) * 900}px;opacity:${p};background:#fff">
      ${card('site-signup-filled.png', 1 - swap)}${card('site-signup-done.png', swap)}</div>
    <div class="center" style="top:1520px;opacity:${p};font-size:40px;color:var(--navy-200);font-weight:600">האתר של דאד קואץ׳ · הפרטים לדוגמה</div>`;
};
S.wHi = (lt) => {
  const msgs = [day('היום'), at(S1.hi, 0.6), at(S1.welcome, 1.8)];
  return `${ost('ואז הודעה בוואטסאפ', lt, { at: 0.1 })}${device(chat(lt, '15:40', msgs, { typing: lt > 0.9 && lt < 1.8 }))}`;
};
S.wName = (lt, beat) => {
  const g = (beat.dur - 1.2) / 6;
  const msgs = [S1.welcome, at(S1.name, 0.3), at(S1.askChild, 0.3 + g), at(S1.child, 0.3 + 2 * g), at(S1.confirmQ, 0.3 + 3 * g), at(S1.yes, 0.3 + 4 * g), at(S1.registered, 0.3 + 5 * g)];
  return `${ost('שם · ילד וגיל · אישור', lt, { at: 0.1 })}${device(chat(lt, '15:42', msgs))}`;
};
S.wGoal = (lt) => {
  const msgs = [S1.confirmQ, S1.yes, S1.registered, at(S1.goal, 0.6), at(S1.goalSet, 2.0, { cls: lt > 2.8 ? 'glow' : '' })];
  return `${ost('יעד לשבוע, בשעות', lt, { at: 0.1, gold: true })}${device(chat(lt, '15:42', msgs, { typing: lt > 0.9 && lt < 2.0 }))}`;
};
S.wDone = (lt, beat) => {
  const e = fadeIn(lt, beat.dur - 2.6, 0.5);
  const msgs = [S1.registered, S1.goal, { ...S1.goalSet, cls: 'glow' }];
  return `${ost('זהו, אתה בפנים', lt, { at: 0.1 })}${device(chat(lt, '15:42', msgs), { o: 1 - e })}${e > 0 ? endCard(lt - (beat.dur - 2.6), beat, 'הזמן שתכננת.<br><span class="g">הפעם הוא קורה.</span>', 'הבא בתור: קובעים זמן') : ''}`;
};

// ---------------------------------------------------------------- 2. קובעים זמן
S.bTitle = (lt) => titleCard(lt, 'קובעים זמן', 'הודעה אחת בוואטסאפ');
S.bSlots = (lt) => {
  const msgs = [S1.registered, at(S1.goal, 0.4), at(S1.goalSet, 1.4, { cls: lt > 2.2 ? 'glow' : '' })];
  return `${ost('זמנים פנויים בשבוע', lt, { at: 0.1 })}${device(chat(lt, '15:42', msgs))}`;
};
S.bType = (lt, beat) => {
  const sendAt = beat.dur - 1.0;
  const msgs = [S1.registered, S1.goal, S1.goalSet];
  if (lt >= sendAt) msgs.push(at(S1.book, sendAt));
  const compose = lt < sendAt ? { text: typed(M.book, lt, 0.5, sendAt - 0.4), caret: caret(lt) } : null;
  return `${ost('עם מי · מתי · כמה זמן', lt, { at: 0.1, gold: true })}${device(chat(lt, '15:43', msgs, { compose }))}`;
};
S.bBooked = (lt) => {
  const msgs = [S1.goal, S1.goalSet, S1.book, at(S1.booked, 0.3, { cls: lt > 1.3 ? 'glow' : '' })];
  return `${ost('נקבע, ובלוח', lt, { at: 0.1 })}${device(chat(lt, '15:43', msgs, { typing: lt < 0.3 }))}`;
};
S.bAsk = (lt) => {
  const msgs = [S1.book, S1.booked, day('מחר'), at(out(M.howAmI, '08:12'), 0.4), at(inn(M.standing, '08:12'), 1.6, { cls: lt > 2.4 ? 'glow' : '' })];
  return `${ost('"איך אני עומד השבוע?"', lt, { at: 0.1 })}${device(chat(lt, '08:12', msgs, { typing: lt > 0.7 && lt < 1.6 }))}`;
};
S.bEnd = (lt, beat) => `${steps(lt, ['עם מי', 'מתי', 'כמה זמן'], 0.3, 0.7, 520)}
  <div class="center" style="top:1250px;opacity:${fadeIn(lt, 2.4, 0.5)}"><div class="coach" style="width:260px"><img src="${asset('coach-black-belt.webp')}"></div></div>`;

// ---------------------------------------------------------------- 3. התזכורות
S.rMorning = (lt) => {
  const msgs = [day('היום'), at(out(M.bookItai, '19:20'), 0.4), at(inn(M.bookedItaiShort, '19:20'), 1.6, { cls: lt > 2.4 ? 'glow' : '' })];
  return `${ost('קבעת ליום אחר? תזכורת בבוקר', lt, { at: 0.1 })}${device(chat(lt, '19:20', msgs, { typing: lt > 0.7 && lt < 1.6 }))}`;
};
const DAY_S1 = [day('היום'), S1.book, S1.booked];
S.r1h = (lt) => {
  const msgs = [...DAY_S1, at(inn(M.reminder1h, '15:55'), 0.9, { cls: lt > 2.6 ? 'glow' : '' })];
  return `${ost('שעה לפני · עם רעיונות', lt, { at: 0.1, gold: true })}${device(chat(lt, '15:55', msgs) + notif(lt, 0.2, 'דאד קואץ׳', 'עוד שעה הזמן שלך עם נועה 😊'))}`;
};
const R1H = inn(M.reminder1h, '15:55');
S.rFollow = (lt) => {
  const msgs = [...DAY_S1, R1H, at(inn(M.followUp, '18:25'), 0.8, { cls: lt > 1.6 ? 'glow' : '' })];
  return `${ost('אחרי: "נו, איך היה?"', lt, { at: 0.1 })}${device(chat(lt, '18:25', msgs) + notif(lt, 0.15, 'דאד קואץ׳', 'נו, איך היה לכם עם נועה? 😊', 1.8))}`;
};
S.rAnswer = (lt) => {
  const msgs = [R1H, inn(M.followUp, '18:25'), at(out(M.happened, '18:31'), 0.3), at(inn(M.recorded, '18:31'), 2.0, { cls: lt > 2.8 ? 'glow' : '' })];
  return `${ost('נרשם ✔', lt, { at: 0.1, gold: true })}${device(chat(lt, '18:31', msgs, { typing: lt > 0.7 && lt < 2.0 }))}`;
};
S.rDash = (lt) => {
  const hl = { x: 34, y: 690, w: 712, h: 205, at: 0.6 };
  return `${ost('בלוח: מה שסיפרת', lt, { at: 0.1 })}${device(page('05-sessions-past-full.png', lt, { scroll: 0, hl }))}`;
};
S.rEnd = (lt) => `${steps(lt, ['בבוקר של היום', 'שעה לפני, עם רעיונות', 'אחרי: "נו, איך היה?"'], 0.3, 1.0, 520)}
  <div class="center" style="top:1250px;opacity:${fadeIn(lt, 3.4, 0.5)}"><div class="coach" style="width:260px"><img src="${asset('coach-black-belt.webp')}"></div></div>`;

// ---------------------------------------------------------------- 4. כשמשהו מתבטל (s2: one father's thread, as it happened)
const S2 = {
  book: out(M.bookItai, '19:20'), booked: inn(M.bookedItai, '19:20'), cancel: out(M.cancel, '19:31'), cancelled: inn(M.cancelled, '19:31'),
  rebook: out(M.rebook, '19:33'), rebooked: inn(M.rebooked, '19:33'),
};
S.cIntro = (lt) => `${ost('משהו התבטל?', lt, { at: 0.1 })}${device(chat(lt, '19:31', [day('היום'), S2.book, S2.booked]))}`;
S.cCancel = (lt) => {
  const msgs = [day('היום'), S2.book, S2.booked, at(S2.cancel, 0.3), at(S2.cancelled, 1.6, { cls: lt > 2.4 ? 'glow' : '' })];
  return `${ost('מבטל, ומציע זמן אחר', lt, { at: 0.1, gold: true })}${device(chat(lt, '19:31', msgs, { typing: lt > 0.6 && lt < 1.6 }))}`;
};
S.cRebook = (lt) => {
  const msgs = [S2.booked, S2.cancel, S2.cancelled, at(S2.rebook, 0.3), at(S2.rebooked, 1.5, { cls: lt > 2.3 ? 'glow' : '' })];
  return `${ost('נקבע מחדש ✔', lt, { at: 0.1 })}${device(chat(lt, '19:33', msgs, { typing: lt > 0.6 && lt < 1.5 }))}`;
};
S.cMissed = (lt) => {
  const msgs = [S2.cancelled, S2.rebook, S2.rebooked, day('יום שישי'), at(out(M.missed, '18:40'), 0.4), at(inn(M.missedReplyShort, '18:40'), 1.7, { cls: lt > 2.5 ? 'glow' : '' })];
  return `${ost('לא יצא? קורה.', lt, { at: 0.1 })}${device(chat(lt, '18:40', msgs, { typing: lt > 0.7 && lt < 1.7 }))}`;
};
S.cBelt = (lt) => {
  const hl = { x: 34, y: 318, w: 712, h: 820, at: 0.5 };
  return `${ost('החגורה סופרת מה שקרה', lt, { at: 0.1, gold: true })}${device(page('07-progress-full.png', lt, { scroll: 110, hl }))}`;
};
S.cEnd = (lt, beat) => endCard(lt, beat, 'העיקר שממשיכים.', null);

// ---------------------------------------------------------------- 5. הלוח שלי
S.dIntro = (lt) => {
  const p = ease(clamp((lt - 1.9) / 0.6));
  const wa = chat(lt, '08:12', [S1.book, S1.booked, day('היום'), out(M.howAmI, '08:12'), inn(M.standing, '08:12')]);
  const screen = `<div style="position:absolute;inset:0;opacity:${1 - p}">${wa}</div><div style="position:absolute;inset:0;opacity:${p}">${page('03-home-full.png', lt, {})}</div>`;
  return `${ost(p < 0.5 ? 'וואטסאפ' : 'הלוח האישי', lt, { at: p < 0.5 ? 0.1 : 1.9, gold: p >= 0.5 })}${device(screen)}`;
};
S.dLogin = (lt) => `${ost('בלי סיסמה', lt, { at: 0.1 })}${device(page('01-login.png', lt, { nav: false, scroll: 120, hl: { x: 60, y: 860, w: 660, h: 290, at: 0.8 } }))}`;
S.dHome = (lt) => `${ost('היעד של השבוע', lt, { at: 0.1, gold: true })}${device(page('03-home-full.png', lt, { scroll: 0, hl: { x: 30, y: 316, w: 720, h: 490, at: 0.6 } }))}`;
S.dNext = (lt) => {
  const scroll = scrollAt(lt, 0.1, 0.9, 0, 640);
  return `${ost('המפגש הבא · החגורה', lt, { at: 0.1 })}${device(page('03-home-full.png', lt, { scroll, hl: { x: 30, y: 842, w: 720, h: 670, at: 1.0 } }))}`;
};
S.dAwaiting = (lt) => {
  const scroll = scrollAt(lt, 0.1, 0.9, 400, 820);
  return `${ost('"היה!" או "לא יצא"', lt, { at: 0.1, gold: true })}${device(page('10-home-awaiting-full.png', lt, { scroll, hl: { x: 30, y: 1122, w: 720, h: 415, at: 1.0 } }))}`;
};
S.dSessions = (lt) => `${ost('מפגשים', lt, { at: 0.1 })}${device(page('05-sessions-past-full.png', lt, { scroll: scrollAt(lt, 1.6, 3.4, 0, 420), hl: { x: 34, y: 688, w: 712, h: 880, at: 0.6, soft: true } }))}`;
S.dProgress = (lt) => {
  const scroll = scrollAt(lt, 2.2, 4.2, 110, 1150);
  const hl = lt < 2.2 ? { x: 34, y: 318, w: 712, h: 820, at: 0.5 } : null;
  return `${ost('התקדמות', lt, { at: 0.1, gold: true })}${device(page('07-progress-full.png', lt, { scroll, hl }))}`;
};
S.dSettings = (lt) => {
  const scroll = scrollAt(lt, 2.0, 3.0, 0, 420);
  const hl = lt < 2.0 ? { x: 34, y: 255, w: 712, h: 660, at: 0.4 } : { x: 34, y: 955, w: 712, h: 515, at: 3.0 };
  return `${ost('הגדרות', lt, { at: 0.1 })}${device(page('08-settings-full.png', lt, { scroll, hl }))}`;
};
S.dEnd = (lt, beat) => {
  const e = fadeIn(lt, beat.dur - 2.4, 0.5);
  const scroll = scrollAt(lt, 0.1, 0.9, 900, 1800);
  return `${ost('קובעים בוואטסאפ', lt, { at: 0.1, gold: true })}${device(page('03-home-full.png', lt, { scroll, hl: { x: 30, y: 2140, w: 720, h: 712, at: 1.0 } }), { o: 1 - e })}${e > 0 ? endCard(lt - (beat.dur - 2.4), beat, 'הזמן שתכננת.<br><span class="g">הפעם הוא קורה.</span>', null) : ''}`;
};

// ---------------------------------------------------------------- the videos: [voice line, scene, minimum seconds, sound cues]
const PLAN = {
  welcome: [['w01', 'wTitle', 7.6], ['w02', 'wSite', 6.8], ['w03', 'wHi', 6.4, [[0.6, 'tick'], [1.8, 'ping']]],
    ['w04', 'wName', 10.4, [[0.3, 'tick'], [2.0, 'ping'], [5.4, 'ping'], [8.6, 'ping']]], ['w05', 'wGoal', 8.6, [[0.6, 'tick'], [2.0, 'ping']]], ['w06', 'wDone', 9.0]],
  'book-a-session': [['b01', 'bTitle', 4.4], ['b02', 'bSlots', 5.2, [[0.4, 'tick'], [1.4, 'ping']]], ['b03', 'bType', 6.6, [[5.6, 'tick']]],
    ['b04', 'bBooked', 5.6, [[0.3, 'ping']]], ['b05', 'bAsk', 5.6, [[0.4, 'tick'], [1.6, 'ping']]], ['b06', 'bEnd', 6.0]],
  reminders: [['r01', 'rMorning', 6.0, [[0.4, 'tick'], [1.6, 'ping']]], ['r02', 'r1h', 8.8, [[0.2, 'reminder_ping']]], ['r03', 'rFollow', 5.2, [[0.15, 'reminder_ping']]],
    ['r04', 'rAnswer', 7.6, [[0.3, 'tick'], [2.0, 'ping']]], ['r05', 'rDash', 5.6], ['r06', 'rEnd', 7.2]],
  'when-cancelled': [['c01', 'cIntro', 5.0], ['c02', 'cCancel', 7.0, [[0.3, 'tick'], [1.6, 'ping']]], ['c03', 'cRebook', 5.6, [[0.3, 'tick'], [1.5, 'ping']]],
    ['c04', 'cMissed', 6.4, [[0.4, 'tick'], [1.7, 'ping']]], ['c05', 'cBelt', 5.6], ['c06', 'cEnd', 4.0]],
  'my-dashboard': [['d01', 'dIntro', 4.6], ['d02', 'dLogin', 6.4], ['d03', 'dHome', 5.6], ['d04', 'dNext', 5.0], ['d05', 'dAwaiting', 7.6],
    ['d06', 'dSessions', 5.8], ['d07', 'dProgress', 6.6], ['d08', 'dSettings', 8.2], ['d09', 'dEnd', 6.6]],
};

const tl = buildTimeline(PLAN[V], VO, LINES);
install({ S, ...tl, assets: Object.keys(H).map((f) => 'screens/' + f).concat(['screens/site-signup-filled.png', 'screens/site-signup-done.png']) });
window.TIMELINE.video = V;
