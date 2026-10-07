// The dojo's pace switch: at N completed sessions a week, in which week each belt arrives. Plain arithmetic on
// the real thresholds (workflow/Belt.java: 3/10/25/50/100/200), labeled on the page as a calculation, not a promise.
const QUIPS = {
  1: 'הילדים יהיו גבוהים בהרבה.',
  2: 'הילדים יהיו גבוהים יותר.',
  3: 'עדיין יספיקו לגדול קצת.',
};

export function initBeltRate(section) {
  const buttons = [...section.querySelectorAll('[data-rate]')];
  const result = section.querySelector('[data-rate-result]');
  const belts = [...section.querySelectorAll('.belt[data-at]')];

  function set(rate) {
    buttons.forEach((b) => b.setAttribute('aria-pressed', String(Number(b.dataset.rate) === rate)));
    for (const belt of belts) {
      const at = Number(belt.dataset.at);
      belt.querySelector('[data-belt-when]').textContent = at ? `שבוע ${Math.ceil(at / rate)}` : 'מהיום';
    }
    const weeks = Math.ceil(200 / rate);
    result.textContent = `שחורה בעוד ${weeks} שבועות. ${QUIPS[rate]}`;
  }
  buttons.forEach((b) => b.addEventListener('click', () => set(Number(b.dataset.rate))));
}
