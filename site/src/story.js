// The hero's signature moment, in two acts. Act 1: the plan ("פארק עם נועה") hops from day to day, always to
// "tomorrow", until it lands on "שבוע הבא". Act 2: the same week with Dad Coach: booked in one message, the
// morning reminder, "נו, איך היה?", one word, done. The coach's lines are the demo's (scenarios.js).
// Deterministic, pauses offscreen and in a hidden tab. Reduced motion (or ?demo=static) → the final state only,
// which is what the HTML already shows.
const ABORT = Symbol('abort');
const DAD = 'אורי';
const COACH = 'דאד קואץ׳';

// [today index, note index (7 = "שבוע הבא"), what he tells himself]
const ACT1 = [
  [0, 0, '״פארק עם נועה. השבוע, בטוח.״'],
  [1, 2, '״מחר.״'],
  [2, 3, '״מחר. באמת.״'],
  [3, 5, '״אחרי הישיבה… בסופ״ש.״'],
  [5, 6, '״שבת. יותר רגוע.״'],
  [6, 7, '״שבוע הבא. בטוח.״'],
];

export function initStory(root, { reducedMotion }) {
  const isStatic = reducedMotion || new URLSearchParams(location.search).get('demo') === 'static';
  const track = root.querySelector('[data-story-track]');
  const days = [...root.querySelectorAll('[data-day]')];
  const note = root.querySelector('[data-story-note]');
  const noteTitle = root.querySelector('[data-story-note-title]');
  const noteSub = root.querySelector('[data-story-note-sub]');
  const act = root.querySelector('[data-story-act]');
  const msg = root.querySelector('[data-story-msg]');
  const from = root.querySelector('[data-story-from]');
  const text = root.querySelector('[data-story-text]');
  const bar = root.querySelector('[data-story-bar]');
  const line = root.querySelector('[data-story-line]');
  const replay = root.querySelector('[data-story-replay]');

  const state = { token: 0, offscreen: false, at: 0 };
  const paused = () => state.offscreen || document.hidden;

  function wait(ms, token) {
    return new Promise((resolve, reject) => {
      let left = ms;
      let last = performance.now();
      const iv = setInterval(() => {
        const now = performance.now();
        if (token !== state.token) { clearInterval(iv); reject(ABORT); return; }
        if (!paused()) left -= now - last;
        last = now;
        if (left <= 0) { clearInterval(iv); resolve(); }
      }, 50);
    });
  }

  // The note sits centered over its day (clamped inside the track). Physical px, so RTL needs no special case.
  function place(index) {
    state.at = index;
    const t = track.getBoundingClientRect();
    const d = days[index].getBoundingClientRect();
    const w = note.offsetWidth;
    const x = Math.max(0, Math.min(t.width - w, d.left - t.left + d.width / 2 - w / 2));
    note.style.transform = `translateX(${x}px)`;
  }
  function hop(index) {
    place(index);
    note.classList.remove('is-hopping');
    void note.offsetWidth; // restart the hop
    note.classList.add('is-hopping');
  }
  function today(index) { days.forEach((d, i) => d.classList.toggle('is-today', i === index)); }
  function say(who, words, out) {
    from.textContent = who;
    text.textContent = words;
    msg.classList.toggle('is-out', !!out);
    msg.classList.toggle('is-self', who.startsWith(DAD) && !out);
    msg.classList.remove('is-new');
    void msg.offsetWidth;
    msg.classList.add('is-new');
  }
  function week(done, planned, words) {
    bar.style.setProperty('--done', `${(done / 3) * 100}%`);
    bar.style.setProperty('--planned', `${(planned / 3) * 100}%`);
    line.textContent = words;
  }
  function setNote(kind, title, sub) {
    note.classList.remove('is-booked', 'is-done', 'is-pushed');
    if (kind) note.classList.add(kind);
    noteTitle.textContent = title;
    noteSub.textContent = sub;
  }

  async function play() {
    state.token += 1;
    const token = state.token;
    replay.hidden = true;
    root.classList.add('is-playing');
    act.textContent = 'בלי תוכנית';
    act.classList.remove('is-coach');
    setNote(null, 'פארק עם נועה', 'מתישהו השבוע');
    today(0);
    place(0);
    week(0, 0, 'יעד: 3 שעות. עוד לא נקבע כלום.');
    say(`${DAD}, לעצמו`, ACT1[0][2]);
    try {
      await wait(1500, token);
      for (const [day, to, words] of ACT1.slice(1)) {
        today(day);
        hop(to);
        say(`${DAD}, לעצמו`, words);
        if (to === 7) { setNote('is-pushed', 'פארק עם נועה', 'נדחה'); week(0, 0, '0 מתוך 3. קורה לכולם.'); }
        await wait(to === 7 ? 2200 : 1150, token);
      }
      // Act 2: the same week, with Dad Coach.
      act.textContent = 'עם דאד קואץ׳';
      act.classList.add('is-coach');
      today(0);
      say(`${DAD} · ראשון 21:20`, 'נועה, שלישי 17:00, שעה', true);
      await wait(900, token);
      hop(2);
      setNote('is-booked', 'נועה · 17:00', 'שעה, נקבעה');
      week(0, 1, 'שעה אחת מתוכננת. עוד 2 לתכנן.');
      say(`${COACH} · 21:20`, 'יופי, קבעתי! אזכיר לך ביום עצמו ושעה לפני.');
      await wait(1900, token);
      today(2);
      say(`${COACH} · שלישי 08:00`, 'היום ב-17:00 זה הזמן שלך ושל נועה 🙂');
      await wait(1900, token);
      say(`${COACH} · 18:30`, 'נו, איך היה לכם עם נועה?');
      await wait(1500, token);
      say(`${DAD} · 18:41`, 'היה מעולה. בנינו מבצר מכריות 😄', true);
      setNote('is-done', 'נועה · 17:00', 'היה ✔');
      week(1, 0, 'שעה אחת מתוך 3 כבר קרתה.');
      await wait(600, token);
      root.classList.remove('is-playing');
      replay.hidden = false;
    } catch (err) {
      if (err !== ABORT) throw err;
    }
  }

  window.addEventListener('resize', () => place(state.at));
  place(2);
  document.fonts?.ready.then(() => place(state.at));
  if (isStatic) return; // the markup is the final state; only the note needs placing
  replay.addEventListener('click', play);
  if ('IntersectionObserver' in window) {
    new IntersectionObserver((entries) => { for (const e of entries) state.offscreen = !e.isIntersecting; }, { threshold: 0 }).observe(root);
  }
  play();
}
