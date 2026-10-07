// Dad Coach ad (9:16, ~60 s): pain -> brand -> the product in steps (the REAL chat from the qa-lab: one message books,
// the reminder an hour before with ideas, "נו, איך היה?" -> the week fills) -> the real dashboard (home + progress,
// labelled "הדגמה") -> no guilt when a session did not happen -> the one-time setup (the real site card, then WhatsApp)
// -> punchline: a covered week, no nagging -> motto + CTA. window.renderAt(t) is deterministic; training/film/render.mjs
// renders it (`ad`). The voice clips are the training library's (training/audio/vo/a*.wav), played 7% faster (TEMPO).
import { clamp, lerp, ease, easeOut, fadeIn, phone, device, dash, statusBar, notif, ost, buildTimeline, install, asset } from '../../training/film/lib/ui.js';
import { M } from '../../training/film/msgs.js';

const T = '../../training/film/';
const TEMPO = 1.07;
const VO = Object.fromEntries(Object.entries(await (await fetch(T + 'vo.json')).json()).map(([k, v]) => [k, v / TEMPO]));
const LINES = await (await fetch(T + 'lines.json')).json();
const SITE = new URLSearchParams(location.search).get('site') || '‹כתובת האתר›';

const out = (text, time, at, extra = {}) => ({ dir: 'out', text, time, ticks: 'blue', at, ...extra });
const inn = (text, time, at, extra = {}) => ({ dir: 'in', text, time, at, ...extra });
const day = (text) => ({ dir: 'day', text });
const chat = (lt, clock, messages, o = {}) => phone({ lt, clock, messages, ...o });
const typed = (text, lt, a, b) => text.slice(0, Math.round(text.length * clamp((lt - a) / (b - a))));

// ---------------------------------------------------------------- the week (our drawing, not the product's UI)
const DAYS = ['א', 'ב', 'ג', 'ד', 'ה', 'ו', 'ש'];
const COLW = (840 - 60) / 7;
const yH = (h) => ((h - 8) / 12) * 990;
const WORK = [[0, 9, 12, 'ישיבה'], [0, 14, 17, 'פרויקט'], [1, 8, 11, 'נסיעה'], [1, 12.5, 16.5, 'ישיבות'], [2, 9, 13, 'לקוח'], [2, 15, 19, 'עוד ישיבה'],
  [3, 9, 12, 'ישיבה'], [3, 13, 16, 'דדליין'], [4, 8, 12, 'נסיעה'], [4, 14, 18, 'ישיבה'], [5, 9, 11.5, 'סידורים']];
function week(lt, o) {
  const cols = DAYS.map((d, i) => `<div class="col" style="right:${30 + i * (COLW + 10)}px;width:${COLW}px"></div>`).join('');
  const blk = (i, a, b, label, cls, style = '') => `<div class="blk ${cls}" style="right:${30 + i * (COLW + 10) + 6}px;width:${COLW - 12}px;left:auto;top:${210 + yH(a)}px;height:${yH(b) - yH(a) - 6}px;${style}">${label}</div>`;
  const work = WORK.map(([i, a, b, l], k) => {
    const p = o.workAt === undefined ? 1 : fadeIn(lt, o.workAt + k * 0.2, 0.25);
    return p > 0 ? blk(i, a, b, l, 'work', `opacity:${p * (o.dimWork ? 0.55 : 1)};transform:scale(${0.9 + 0.1 * p})`) : '';
  }).join('');
  return `<div class="week" style="opacity:${o.o ?? 1}"><div class="hd"><span>השבוע שלך</span><span>איור</span></div>
    <div class="days">${DAYS.map((d) => `<div>${d}</div>`).join('')}</div>${cols}${work}${(o.kids || []).map((k) => blk(k.i, k.a, k.b, k.label, k.cls, k.style || '')).join('')}${o.stamp || ''}</div>`;
}

const S = {};
S.pain = (lt) => {
  const push = ease(clamp((lt - 3.7) / 0.9));
  const kid = { i: 3, a: 17, b: 18.5, label: 'נועה<br><span class="num">17:00</span>', cls: 'kid',
    style: `opacity:${fadeIn(lt, 0.2, 0.4) * (1 - push)};transform:translateX(${-lerp(0, 420, push)}px)` };
  const sp = fadeIn(lt, 4.4, 0.3);
  const stamp = sp > 0 ? `<div class="stamp next" style="top:560px;opacity:${sp}">נדחה לשבוע הבא</div>` : '';
  return week(lt, { workAt: 0.3, kids: [kid], stamp });
};
S.brand = (lt) => {
  const p = fadeIn(lt, 0.1, 0.6), q = fadeIn(lt, 1.2, 0.6);
  return `<div class="center" style="top:250px;opacity:${p};transform:translateY(${lerp(30, 0, p)}px)">
      <div class="logo" style="width:260px;height:260px"><img src="${asset('logo-mark.webp')}"></div>
      <div class="wordmark" style="margin-top:44px">Dad Coach</div><div class="lede" style="margin-top:8px">מאמן בוואטסאפ לאבות</div></div>
    <div class="hero-art" style="top:${lerp(1060, 1010, q)}px;opacity:${q}"><img src="${asset('hero.webp')}"></div>`;
};
S.book = (lt, beat) => {
  const sendAt = 2.6, replyAt = 3.7;
  const msgs = [inn(M.registered, '15:42'), out(M.goal, '15:42'), inn(M.goalSet, '15:42')];
  if (lt >= sendAt) msgs.push(out(M.book, '15:43', sendAt));
  if (lt >= replyAt) msgs.push(inn(M.bookedShort, '15:43', replyAt, { cls: lt > replyAt + 0.7 ? 'glow' : '' }));
  const compose = lt < sendAt ? { text: typed(M.book, lt, 0.3, sendAt - 0.3), caret: Math.floor(lt * 2.4) % 2 === 0 } : null;
  return `${ost('קובעים בהודעה אחת', lt, { at: 0.1, gold: true })}${device(chat(lt, '15:43', msgs, { compose, typing: lt > sendAt + 0.2 && lt < replyAt }))}`;
};
const BOOKED = [day('היום'), out(M.book, '15:43'), inn(M.bookedShort, '15:43')];
S.remind = (lt) => {
  const msgs = [...BOOKED, inn(M.reminder1h, '15:55', 0.8, { cls: lt > 2.0 ? 'glow' : '' })];
  return `${ost('שעה לפני · עם רעיונות', lt, { at: 0.1 })}${device(chat(lt, '15:55', msgs) + notif(lt, 0.15, 'Dad Coach', 'עוד שעה הזמן שלך עם נועה 😊', 1.8))}`;
};
S.after = (lt) => {
  const msgs = [...BOOKED, inn(M.reminder1h, '15:55'), inn(M.followUp, '18:25', 0.5), out(M.happened, '18:31', 2.4), inn(M.recorded, '18:31', 3.7, { cls: lt > 4.4 ? 'glow' : '' })];
  return `${ost('"נו, איך היה?"', lt, { at: 0.1, gold: true })}${device(chat(lt, lt < 2.4 ? '18:25' : '18:31', msgs, { typing: lt > 2.7 && lt < 3.7 }))}`;
};
S.dash = (lt) => {
  const swap = ease(clamp((lt - 2.9) / 0.5));
  const home = dash('03-home-full.png', { scroll: 0, lt, hl: { x: 30, y: 316, w: 720, h: 490, at: 0.5 }, noStatus: true });
  const prog = dash('07-progress-full.png', { scroll: 110, lt: lt - 3.0, hl: { x: 34, y: 318, w: 712, h: 820, at: 0.4 }, noStatus: true });
  const screen = `<div style="position:absolute;inset:0;opacity:${1 - swap}">${home}</div><div style="position:absolute;inset:0;opacity:${swap}">${prog}</div>${statusBar('20:41')}`;
  return `${ost(swap < 0.5 ? 'השבוע מתמלא' : 'והחגורה עולה', lt, { at: swap < 0.5 ? 0.1 : 2.9 })}${device(screen)}`;
};
S.missed = (lt) => {
  // the reply offers "today 17:00-18:00, or tomorrow (Thursday)": so the screen is a Wednesday afternoon, before 17:00
  const msgs = [day('יום רביעי'), out(M.missed, '15:10', 0.4), inn(M.missedReply, '15:10', 1.6, { cls: lt > 2.4 ? 'glow' : '' })];
  return `${ost('בלי רגשות אשם', lt, { at: 0.1, gold: true })}${device(chat(lt, '15:10', msgs, { typing: lt > 0.7 && lt < 1.6 }))}`;
};
S.setup = (lt) => {
  const p = fadeIn(lt, 0.05, 0.4), swap = ease(clamp((lt - 1.7) / 0.4)), toChat = ease(clamp((lt - 3.1) / 0.4));
  const img = (f, o) => `<img src="${asset('screens/' + f)}" style="position:absolute;inset:0;width:100%;opacity:${o}">`;
  const card = `<div class="card" style="left:90px;right:90px;top:520px;height:${(lerp(636, 758, swap) / 716) * 900}px;background:#fff;opacity:${p * (1 - toChat)}">${img('site-signup-filled.png', 1 - swap)}${img('site-signup-done.png', swap)}</div>`;
  const c = lt - 3.2;
  const msgs = [out(M.hi, '15:40'), inn(M.welcome, '15:40'), out(M.name, '15:41'), inn(M.askChild, '15:41', 0.1), out(M.child, '15:41', 0.7),
    inn(M.confirmQ, '15:41', 1.3), out(M.yes, '15:42', 1.9), inn(M.registered, '15:42', 2.5), out(M.goal, '15:42', 3.4), inn(M.goalSet, '15:42', 4.2)];
  const ph = toChat > 0 ? device(chat(c, '15:42', msgs), { o: toChat }) : '';
  return `${ost(lt < 3.1 ? 'באתר: שם ונייד' : 'בוואטסאפ: ילד, גיל ויעד', lt, { at: lt < 3.1 ? 0.1 : 3.1 })}${card}${ph}`;
};
S.covered = (lt) => {
  const kids = [[3, 17, 18.5, 'נועה ✔', 0.3], [1, 17, 18, 'איתי ✔', 0.8], [5, 15, 17, 'נועה ✔', 1.3]].map(([i, a, b, label, at]) => {
    const p = fadeIn(lt, at, 0.4);
    return { i, a, b, label, cls: 'kid done', style: `opacity:${p};transform:scale(${0.85 + 0.15 * easeOut(p)})` };
  });
  const sp = fadeIn(lt, 2.0, 0.35);
  const stamp = sp > 0 ? `<div class="stamp ok" style="top:470px;opacity:${sp};transform:translateX(-50%) rotate(-5deg) scale(${0.9 + 0.1 * sp})">השבוע מכוסה ✔</div>` : '';
  return week(lt, { kids, stamp, dimWork: true });
};
S.end = (lt) => {
  const p = fadeIn(lt, 0.1, 0.6), q = fadeIn(lt, 1.3, 0.5), r = fadeIn(lt, 2.4, 0.5);
  return `<div class="center" style="top:300px;opacity:${p}">
      <div class="logo" style="width:340px;height:340px"><img src="${asset('logo-full.webp')}"></div>
      <div class="wordmark" style="margin-top:40px;font-size:96px">Dad Coach</div></div>
    <div class="center motto" style="top:930px;opacity:${q}">הזמן שתכננת.<br><span class="g">הפעם הוא קורה.</span></div>
    <div class="center" style="top:1250px;opacity:${r}"><span class="cta">מתחילים באתר</span><div class="site">${SITE}</div></div>`;
};

const PLAN = [
  ['a01', 'pain', 6.6], ['a02', 'brand', 5.4], ['a03', 'book', 6.4, [[2.6, 'tick'], [3.7, 'ping']]], ['a04', 'remind', 5.0, [[0.15, 'reminder_ping']]],
  ['a05', 'after', 6.6, [[0.5, 'reminder_ping'], [2.4, 'tick'], [3.7, 'ping']]], ['a06', 'dash', 6.0, [[2.9, 'whoosh']]], ['a07', 'missed', 5.6, [[0.4, 'tick'], [1.6, 'ping']]],
  ['a08', 'setup', 9.0, [[3.1, 'whoosh']]], ['a09', 'covered', 5.6, [[2.0, 'confirm']]], ['a10', 'end', 6.6],
];
const tl = buildTimeline(PLAN, VO, LINES, { lead: 0.25, tail: 0.4 });
install({ S, ...tl, assets: ['hero.webp', 'screens/03-home-full.png', 'screens/07-progress-full.png', 'screens/site-signup-filled.png', 'screens/site-signup-done.png'] });
window.TIMELINE.video = 'ad';
window.TIMELINE.tempo = TEMPO;
