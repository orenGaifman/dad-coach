// Shared pieces of the Dad Coach films (the ad and the training videos): the WhatsApp phone, the real dashboard
// captures, top lines, captions and the timeline. Plain HTML strings, rebuilt for every frame by window.renderAt(t).
// Adapted from big-boss-onboarding/film (main.js, lib/ui.js).

export const clamp = (x, a = 0, b = 1) => Math.min(b, Math.max(a, x));
export const lerp = (a, b, p) => a + (b - a) * p;
export const ease = (x) => { x = clamp(x); return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2; };
export const easeOut = (x) => 1 - Math.pow(1 - clamp(x), 3);
export const backOut = (x) => { x = clamp(x); const c1 = 1.2, c3 = c1 + 1; return 1 + c3 * Math.pow(x - 1, 3) + c1 * Math.pow(x - 1, 2); };
export const fadeIn = (lt, at = 0, d = 0.35) => easeOut(clamp((lt - at) / d));
export const esc = (s) => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
// WhatsApp *bold*; times, ranges and numbers isolated so they never flip inside the Hebrew line
export const rich = (s) => esc(s).replace(/\*([^*\n]+)\*/g, '<b>$1</b>');
export const num = (s) => `<span class="num">${esc(s)}</span>`;

const A = (p) => `${window.ASSET_BASE || 'assets/'}${p}`;
export const asset = A;

// a message pops in: scale from 0.86 + fade over 0.2s
function popStyle(lt, at) {
  if (at === undefined) return '';
  const p = clamp((lt - at) / 0.2);
  if (p <= 0) return 'display:none';
  return `opacity:${Math.min(1, p * 1.6)};transform:scale(${(0.86 + 0.14 * backOut(p)).toFixed(3)});transform-origin:center bottom`;
}

/** phone({clock, lt, messages:[{dir:'in'|'out'|'day', text, time, ticks, at, cls}], compose:{text, caret}, sub}) */
export function phone(o) {
  const lt = o.lt ?? 99;
  const msgs = (o.messages || []).map((m) => {
    const st = popStyle(lt, m.at);
    if (st === 'display:none') return '';
    if (m.dir === 'day') return `<div class="day" style="${st}">${esc(m.text)}</div>`;
    const ticks = m.ticks ? `<span class="ticks ${m.ticks === 'grey' ? 'grey' : ''}">✓✓</span>` : '';
    return `<div class="msg ${m.dir}${m.cls ? ' ' + m.cls : ''}" style="${st}">${m.html || rich(m.text)}<div class="meta">${m.time || ''}${ticks}</div></div>`;
  }).join('');
  let compose;
  if (o.compose && o.compose.text) {
    compose = `<div class="compose"><div class="send">➤</div><div class="field" style="${o.compose.text.length > 26 ? 'justify-content:flex-end' : ''}">${esc(o.compose.text)}${o.compose.caret ? '<span class="caret"></span>' : ''}</div></div>`;
  } else {
    compose = `<div class="compose"><div class="send">🎤</div><div class="field"><span class="ph">הודעה</span></div></div>`;
  }
  const sub = o.typing ? '<div class="sub typing">מקליד…</div>' : '<div class="sub">חשבון עסקי</div>';
  return `<div class="phone">
    <div class="status"><span>${o.clock || '16:00'}</span><span class="icons">●●● <span class="bat"></span></span></div>
    <div class="head"><span class="back">‹</span><div class="av"><img src="${A('logo-mark.webp')}"></div><div><div class="nm">דאד קואץ׳</div>${sub}</div></div>
    <div class="chat">${msgs}</div>
    ${compose}
  </div>`;
}

// the device frame around a screen (the phone or a dashboard capture)
export const device = (inner, opts = {}) => `<div class="device" style="opacity:${opts.o ?? 1};${opts.style || ''}"><div class="screen">${inner}</div></div>`;

// a camera move inside the screen (x, y = the point that comes to the centre, z = zoom)
export const cam = (inner, c) => {
  if (!c) return inner;
  const z = c.z;
  const tx = clamp(540 - c.x * z, 1080 - 1080 * z, 0), ty = clamp(960 - c.y * z, 1920 - 1920 * z, 0);
  return `<div class="cam" style="transform:translate(${tx.toFixed(1)}px,${ty.toFixed(1)}px) scale(${z.toFixed(4)})">${inner}</div>`;
};
export const camTo = (a, b, p) => ({ x: lerp(a.x, b.x, p), y: lerp(a.y, b.y, p), z: lerp(a.z, b.z, p) });

// a real dashboard capture (780 px wide, the SPA at 390 pt x2) under a status bar. scroll/boxes in capture pixels.
export const K = 1080 / 780;
const SB = 110;
export const statusBar = (clock) => `<div class="shot-status"><span>${clock || '20:41'}</span><span class="demo">הדגמה</span><span><span class="bat"></span></span></div>`;
export function dash(img, o = {}) {
  const scroll = o.scroll || 0;
  const top = SB - scroll * K;
  let hl = '';
  if (o.hl) {
    const p = fadeIn(o.lt ?? 9, o.hl.at || 0, 0.35);
    if (p > 0) hl = `<div class="hl${o.hl.soft ? ' soft' : ''}" style="opacity:${p};left:${o.hl.x * K}px;top:${SB + (o.hl.y - scroll) * K}px;width:${o.hl.w * K}px;height:${o.hl.h * K}px"></div>`;
  }
  return `<div class="dash"><img src="${A('screens/' + img)}" style="top:${top}px">${hl}${o.noStatus ? '' : statusBar(o.clock)}</div>`;
}
export const dashCam = (sx, sy, scroll) => ({ x: sx * K, y: SB + (sy - scroll) * K });
// a dashboard screen with a camera: the status bar stays on top, outside the zoom
export const dashShot = (img, o, c) => cam(dash(img, { ...o, noStatus: true }), c) + statusBar(o.clock);

export function notif(lt, at, title, body, hold = 2.4) {
  const p = clamp((lt - at) / 0.35), q = clamp((lt - at - hold) / 0.35);
  if (p <= 0 || q >= 1) return '';
  const y = lerp(-280, 0, easeOut(p)) + lerp(0, -320, ease(q));
  return `<div class="notif" style="transform:translateY(${y}px)"><div class="ic"><img src="${A('logo-mark.webp')}"></div><div style="min-width:0"><div class="t" dir="ltr">${esc(title)}</div><div class="b">${esc(body)}</div></div></div>`;
}

export const ost = (html, lt, opts = {}) => {
  const p = fadeIn(lt, opts.at || 0, 0.3);
  if (p <= 0) return '';
  return `<div class="ost${opts.gold ? ' gold' : ''}" style="opacity:${p};transform:translateX(-50%) scale(${(0.94 + 0.06 * backOut(p)).toFixed(3)})">${html}</div>`;
};

// ---------------------------------------------------------------- timeline + captions + renderAt
// plan: [[voice id, scene name, minimum seconds, sound cues [[at, name]]], ...]; beat length = voice + lead + tail.
export function buildTimeline(plan, VO, LINES, { lead = 0.35, tail = 0.65 } = {}) {
  const beats = [];
  let t = 0;
  for (const [id, scene, min = 0, sfx = []] of plan) {
    const vo = VO[id];
    if (vo === undefined) throw new Error('no voice clip ' + id);
    const dur = Math.max(min, lead + vo + tail);
    beats.push({ id, scene, start: t, dur, vo, voAt: t + lead, text: LINES[id], sfx: sfx.map(([at, name]) => ({ at: t + at, name })) });
    t += dur;
  }
  return { total: t, beats };
}

// the caption: the line split at its pauses, each piece on screen for its share of the clip (<= 2 lines)
export function captionAt(beat, t) {
  const lt = t - beat.voAt;
  if (lt < -0.1 || lt > beat.vo + 0.3) return '';
  const parts = beat.text.split(/(?<=[.?!:,])\s+/).reduce((acc, s) => {
    const last = acc[acc.length - 1];
    if (last && (last + ' ' + s).length <= 44) acc[acc.length - 1] = last + ' ' + s; else acc.push(s);
    return acc;
  }, []);
  const total = parts.reduce((n, s) => n + s.length, 0);
  let acc = 0;
  for (const s of parts) {
    acc += s.length;
    if (lt <= (acc / total) * beat.vo + 0.05) return `<div class="caption"><span>${capText(s)}</span></div>`;
  }
  return `<div class="caption"><span>${capText(parts[parts.length - 1])}</span></div>`;
}
const capText = (s) => esc(s).replace(/דאד קואץ׳/g, '<span class="wm">דאד קואץ׳</span>').replace(/(\d[\d:–-]*\d|\d)/g, '<span class="num">$1</span>');

export function install({ S, beats, total, assets = [], fadeColor = 'var(--bg)' }) {
  const ui = document.getElementById('stage');
  window.TIMELINE = { total, beats };
  window.renderAt = (time) => {
    const beat = beats.find((b) => time >= b.start && time < b.start + b.dur) || beats[beats.length - 1];
    const lt = time - beat.start;
    const fade = 1 - clamp(lt / 0.25);
    ui.innerHTML = S[beat.scene](lt, beat) + captionAt(beat, time) + (beat.start > 0 && fade > 0 && !beat.noFade ? `<div class="fade" style="opacity:${fade};background:${fadeColor}"></div>` : '');
    return Promise.all([...ui.querySelectorAll('img')].map((img) => (img.complete ? null : new Promise((r) => { img.onload = img.onerror = r; }))));
  };
  window.ready = (async () => {
    await document.fonts.ready;
    const imgs = ['logo-mark.webp', 'logo-full.webp', 'coach-black-belt.webp', ...assets];
    await Promise.all(imgs.map((src) => new Promise((r) => { const i = new Image(); i.onload = i.onerror = r; i.src = A(src); })));
    await window.renderAt(0);
    return true;
  })();
}
