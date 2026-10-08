// Deterministic scenario player for the demo: one phone (the father's), a "השבוע שלך" card next to it.
// No network, no randomness. Pauses while scrolled away or while the tab is hidden.
// Reduced motion → every scenario renders complete and static (no lock screen, no typing).
import { scenarios, COACH_LINE } from './scenarios.js';

const ABORT = Symbol('abort');
const TICK = 50;
const STATUS_IDLE = 'מחובר';
const STATUS_TYPING = 'מקליד…';

export const BELTS = {
  white: { name: 'לבנה', img: '/img/belts/white.webp' },
  yellow: { name: 'צהובה', img: '/img/belts/yellow.webp' },
  orange: { name: 'כתומה', img: '/img/belts/orange.webp' },
  green: { name: 'ירוקה', img: '/img/belts/green.webp' },
  blue: { name: 'כחולה', img: '/img/belts/blue.webp' },
  brown: { name: 'חומה', img: '/img/belts/brown.webp' },
  black: { name: 'שחורה', img: '/img/belts/black.webp' },
};

function el(tag, className, text) {
  const node = document.createElement(tag);
  if (className) node.className = className;
  if (text != null) node.textContent = text;
  return node;
}

// WhatsApp formatting the coach uses: *bold* (the session's day and time). Built as nodes, never innerHTML.
function appendRich(parent, text) {
  text.split(/(\*[^*\n]+\*)/).forEach((part) => {
    if (/^\*[^*\n]+\*$/.test(part)) parent.append(el('strong', '', part.slice(1, -1)));
    else if (part) parent.append(document.createTextNode(part));
  });
}
const plain = (text) => text.replace(/\*([^*\n]+)\*/g, '$1');

const typingDuration = (text) => Math.min(2200, Math.max(800, 450 + text.length * 14));
const hours = (n) => (n === 1 ? 'שעה' : `${n} שעות`);
const happened = (n) => (n === 1 ? 'שעה אחת כבר קרתה' : `${n} שעות כבר קרו`);
const inPlan = (n) => (n === 1 ? 'שעה אחת מתוכננת' : `${n} שעות מתוכננות`);

export function initDemo(root, options) {
  // ?demo=static renders every scenario complete (screenshots, and visitors who prefer reading).
  const reducedMotion = options.reducedMotion || new URLSearchParams(location.search).get('demo') === 'static';
  const tabs = [...root.querySelectorAll('[data-scenario]')];
  const sceneEl = root.querySelector('[data-demo-scene]');
  const outroEl = root.querySelector('[data-demo-outro]');
  const pauseBtn = root.querySelector('[data-demo-pause]');
  const skipBtn = root.querySelector('[data-demo-skip]');
  const replayBtn = root.querySelector('[data-demo-replay]');
  const phone = root.querySelector('[data-phone]');
  const chat = phone.querySelector('[data-chat]');
  const status = phone.querySelector('[data-status]');
  const lock = phone.querySelector('[data-lock]');
  const lockTime = lock.querySelector('[data-lock-time]');
  const lockDay = lock.querySelector('[data-lock-day]');
  const lockList = lock.querySelector('[data-lock-list]');
  const statusTime = phone.querySelector('[data-status-time]');
  const week = root.querySelector('[data-week]');

  const state = { token: 0, userPaused: false, offscreen: false, current: scenarios[0], started: false, day: 'היום' };
  const isPaused = () => state.userPaused || state.offscreen || document.hidden;

  function wait(ms, token) {
    return new Promise((resolve, reject) => {
      let left = ms;
      let last = performance.now();
      const iv = setInterval(() => {
        const now = performance.now();
        if (token !== state.token) { clearInterval(iv); reject(ABORT); return; }
        if (!isPaused()) left -= now - last;
        last = now;
        if (left <= 0) { clearInterval(iv); resolve(); }
      }, TICK);
    });
  }

  function scrollChat(instant) {
    if (instant || reducedMotion) { chat.scrollTop = chat.scrollHeight; return; }
    chat.scrollTo({ top: chat.scrollHeight, behavior: 'smooth' });
  }

  function reset() {
    chat.textContent = '';
    status.textContent = STATUS_IDLE;
    lock.hidden = true;
    state.day = 'היום';
    statusTime.textContent = '';
  }

  function dayPill(text) {
    state.day = text;
    chat.append(el('div', 'chat-day', text));
  }

  function bubble(step, animate) {
    if (!chat.querySelector('.chat-day')) dayPill('היום');
    const coach = step.from === 'coach';
    const b = el('div', `bub ${coach ? 'bub-in' : 'bub-out'}${animate ? ' bub-enter' : ''}`);
    if (!coach) b.append(el('span', 'visually-hidden', 'אורי: '));
    if (step.notify) b.append(el('span', 'bub-proactive', 'הודעה יזומה'));
    if (coach) b.append(el('span', 'bub-id', COACH_LINE));
    appendRich(b, step.text);
    b.append(el('span', 'bub-time', step.time));
    chat.append(b);
    if (coach && step.buttons) {
      // reply buttons under the coach's message, like WhatsApp (plain text, no emoji)
      const row = el('div', `bub-btns${animate ? ' bub-enter' : ''}`);
      step.buttons.forEach((title) => row.append(el('span', 'bub-btn', title)));
      chat.append(row);
    }
    statusTime.textContent = step.time;
    scrollChat(!animate);
  }

  function note(step, animate) {
    if (step.day) dayPill(step.day);
    const n = el('div', `chat-note${animate ? ' bub-enter' : ''}`);
    n.append(el('span', 'chat-note-time', step.time));
    n.append(document.createTextNode(step.text));
    chat.append(n);
    scrollChat(!animate);
  }

  function progress(step) {
    if (!week) return;
    const goal = step.goal;
    const done = Math.min(step.done, goal || step.done);
    const planned = Math.max(0, Math.min(step.planned, (goal || 0) - done));
    week.querySelector('[data-week-goal]').textContent = goal ? hours(goal) : 'עוד לא נקבע';
    const bar = week.querySelector('[data-week-bar]');
    bar.style.setProperty('--done', goal ? `${(done / goal) * 100}%` : '0%');
    bar.style.setProperty('--planned', goal ? `${(planned / goal) * 100}%` : '0%');
    bar.setAttribute('aria-valuenow', String(done));
    bar.setAttribute('aria-valuemax', String(goal || 0));
    let line;
    if (!goal) line = 'שבוע חדש. מתחילים ביעד.';
    else if (done >= goal) line = `היעד הושלם: ${hours(goal)} מתוך ${goal}.`;
    else if (done + planned >= goal) line = done ? `${happened(done)}, והשאר מתוכנן. השבוע מכוסה.` : 'השבוע מכוסה: הכול מתוכנן.';
    else line = `${done ? happened(done) : 'עוד לא היה מפגש'}${planned ? `, ${inPlan(planned)}` : ''}. עוד ${hours(goal - done - planned)} לתכנן.`;
    week.querySelector('[data-week-line]').textContent = line;
    const belt = BELTS[step.belt] || BELTS.white;
    const img = week.querySelector('[data-week-belt-img]');
    if (img.getAttribute('src') !== belt.img) img.setAttribute('src', belt.img);
    week.querySelector('[data-week-belt]').textContent = `חגורה ${belt.name}`;
    week.querySelector('[data-week-note]').textContent = step.note || '';
  }

  function showLock(step, withCard) {
    lockTime.textContent = step.time;
    lockDay.textContent = step.day || state.day;
    lockList.textContent = '';
    if (withCard) {
      const card = el('div', 'lock-card');
      const head = el('div', 'lock-card-head');
      const icon = el('img', 'lock-card-icon');
      icon.src = '/img/logo-mark.webp';
      icon.alt = '';
      head.append(icon, el('b', '', 'דאד קואץ׳'), el('span', '', 'עכשיו'));
      card.append(head, el('p', 'lock-card-text', `${COACH_LINE}\n${plain(step.text)}`));
      lockList.append(card);
    } else {
      lockList.append(el('p', 'lock-empty', 'אין התראות חדשות'));
    }
    lock.hidden = false;
    lock.classList.remove('is-open');
  }

  function renderStatic(step) {
    switch (step.kind) {
      case 'scene': if (!sceneEl.textContent) sceneEl.textContent = step.text; break; // static: the opening scene
      case 'day': dayPill(step.text); break;
      case 'msg': bubble(step, false); break;
      case 'event': note(step, false); break;
      case 'progress': progress(step); break;
      case 'outro': outroEl.textContent = step.text; outroEl.hidden = false; break;
      default: break;
    }
  }

  async function renderAnimated(step, token, index) {
    switch (step.kind) {
      case 'scene': sceneEl.textContent = step.text; await wait(700, token); break;
      case 'day': await wait(500, token); dayPill(step.text); break;
      case 'progress': progress(step); await wait(300, token); break;
      case 'event':
        await wait(600, token);
        showLock(step, false);
        await wait(1800, token);
        lock.hidden = true;
        note(step, true);
        await wait(900, token);
        break;
      case 'msg':
        if (step.from === 'coach' && step.notify) {
          await wait(700, token);
          showLock(step, true);
          await wait(2600, token);
          lock.classList.add('is-open');
          await wait(350, token);
          lock.hidden = true;
          bubble(step, true);
          await wait(600, token);
        } else if (step.from === 'coach') {
          await wait(index <= 2 ? 300 : 450, token);
          const typing = el('div', 'typing');
          typing.setAttribute('aria-hidden', 'true');
          typing.append(el('i'), el('i'), el('i'));
          chat.append(typing);
          status.textContent = STATUS_TYPING;
          scrollChat();
          try { await wait(typingDuration(step.text), token); } finally { typing.remove(); status.textContent = STATUS_IDLE; }
          bubble(step, true);
        } else {
          await wait(index <= 2 ? 600 : 1400, token);
          bubble(step, true);
        }
        break;
      case 'outro': await wait(700, token); outroEl.textContent = step.text; outroEl.hidden = false; break;
      default: break;
    }
  }

  function setControls(running) {
    pauseBtn.hidden = !running;
    skipBtn.hidden = !running;
    replayBtn.hidden = running;
    state.userPaused = false;
    pauseBtn.textContent = 'השהיה';
    pauseBtn.setAttribute('aria-pressed', 'false');
  }

  function prepare(scenario) {
    state.token += 1;
    state.started = true;
    state.current = scenario;
    tabs.forEach((t) => t.setAttribute('aria-pressed', String(t.dataset.scenario === scenario.id)));
    sceneEl.textContent = '';
    outroEl.hidden = true;
    reset();
    return state.token;
  }

  function showComplete(scenario) {
    prepare(scenario);
    scenario.steps.forEach(renderStatic);
    scrollChat(true);
    setControls(false);
  }

  async function play(scenario) {
    if (reducedMotion) { showComplete(scenario); return; }
    const token = prepare(scenario);
    setControls(true);
    try {
      for (let i = 0; i < scenario.steps.length; i += 1) await renderAnimated(scenario.steps[i], token, i);
      if (token === state.token) setControls(false);
    } catch (err) {
      if (err !== ABORT) throw err;
    }
  }

  tabs.forEach((tab) => tab.addEventListener('click', () => {
    const scenario = scenarios.find((s) => s.id === tab.dataset.scenario);
    if (scenario) play(scenario);
  }));
  pauseBtn.addEventListener('click', () => {
    state.userPaused = !state.userPaused;
    pauseBtn.textContent = state.userPaused ? 'המשך' : 'השהיה';
    pauseBtn.setAttribute('aria-pressed', String(state.userPaused));
  });
  skipBtn.addEventListener('click', () => showComplete(state.current));
  replayBtn.addEventListener('click', () => play(state.current));

  if (reducedMotion) { showComplete(scenarios[0]); return; }
  prepare(scenarios[0]);
  renderStatic(scenarios[0].steps[0]);
  renderStatic(scenarios[0].steps[1]);
  setControls(false);
  state.started = false; // waiting for the visitor to scroll the demo into view

  if ('IntersectionObserver' in window) {
    new IntersectionObserver((entries) => {
      for (const entry of entries) state.offscreen = !entry.isIntersecting;
    }, { threshold: 0 }).observe(root);
    const starter = new IntersectionObserver((entries) => {
      if (!state.started && entries.some((e) => e.isIntersecting)) { starter.disconnect(); play(scenarios[0]); }
    }, { threshold: 0.45 });
    starter.observe(phone);
  } else {
    play(scenarios[0]);
  }
}
