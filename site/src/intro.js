// Dad Coach introduces itself in the hero: a 20-second Hebrew voice (site/scripts/intro_voice.py),
// live captions in the bubble, a glow that follows the voice. Nothing plays until the visitor taps.
// A try-out, measured: ?coach=off hides it (compare the hero with and without it); when analytics is
// enabled, play/finish are sent as "coach-intro" events.
const LINES = [
  [0.0, 'היי, אני דאד קואץ׳.'],
  [2.08, 'אתה מחליט כמה זמן אתה רוצה עם הילדים השבוע,'],
  [5.19, 'ואני דואג שזה באמת יקרה.'],
  [7.58, 'מזכיר לך בבוקר ושעה לפני, ושואל אחרי איך היה.'],
  [12.3, 'התבטל משהו? בלי רגשות אשם. מוצאים זמן אחר.'],
  [17.1, 'וכשהשבוע מכוסה, אני שקט.'],
];

function track(action) {
  if (typeof window.plausible === 'function') window.plausible('coach-intro', { props: { action } });
}

export function initIntro() {
  const root = document.querySelector('[data-intro]');
  if (!root) return;
  if (new URLSearchParams(location.search).get('coach') === 'off') { root.hidden = true; return; }
  const text = root.querySelector('[data-intro-text]');
  const label = root.querySelector('[data-intro-label]');
  const glow = root.querySelector('[data-intro-glow]');
  const audio = new Audio('/dad-coach-intro.mp3');
  audio.preload = 'none';
  let analyser = null;
  let samples = null;
  let raf = 0;
  let level = 0;

  function connect() {
    if (analyser) return;
    try {
      const ctx = new (window.AudioContext || window.webkitAudioContext)();
      const src = ctx.createMediaElementSource(audio);
      analyser = ctx.createAnalyser();
      analyser.fftSize = 512;
      samples = new Uint8Array(analyser.fftSize);
      src.connect(analyser);
      analyser.connect(ctx.destination);
      if (ctx.state === 'suspended') ctx.resume();
    } catch { analyser = null; }
  }

  function loudness() {
    if (!analyser) return 0.5;
    analyser.getByteTimeDomainData(samples);
    let sum = 0;
    for (const v of samples) { const x = (v - 128) / 128; sum += x * x; }
    return Math.min(1, Math.sqrt(sum / samples.length) * 5);
  }

  function frame() {
    level += (loudness() - level) * 0.35;
    glow.style.setProperty('--level', level.toFixed(3));
    const t = audio.currentTime;
    let line = LINES[0][1];
    for (const [at, words] of LINES) if (t >= at) line = words;
    if (text.textContent !== line) text.textContent = line;
    raf = requestAnimationFrame(frame);
  }

  function stop(ended) {
    cancelAnimationFrame(raf);
    glow.style.setProperty('--level', '0');
    root.classList.remove('is-speaking');
    label.textContent = ended ? 'להקשיב שוב' : 'להמשיך להקשיב';
    if (ended) { text.textContent = LINES[0][1]; track('ended'); }
  }

  function toggle() {
    if (audio.paused) {
      connect();
      audio.play().then(() => {
        root.classList.add('is-speaking');
        label.textContent = 'השהיה';
        if (audio.currentTime < 0.2) track('play');
        raf = requestAnimationFrame(frame);
      }).catch(() => stop(false));
    } else {
      audio.pause();
      stop(false);
    }
  }

  root.querySelectorAll('[data-intro-play]').forEach((b) => b.addEventListener('click', toggle));
  audio.addEventListener('ended', () => stop(true));
}
