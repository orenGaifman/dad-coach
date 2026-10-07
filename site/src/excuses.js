// "תירוצים שמתו": each sticky note flips to the true answer (what the product does about that excuse).
// Without JS every answer is visible (the CSS flips only under .js-notes).
export function initExcuses(section) {
  const list = section.querySelector('[data-excuses]');
  const notes = [...list.querySelectorAll('.note')];
  const count = section.querySelector('[data-excuses-count]');
  const all = section.querySelector('[data-excuses-all]');
  const done = section.querySelector('[data-excuses-done]');
  list.classList.add('js-notes');

  function update() {
    const n = notes.filter((b) => b.getAttribute('aria-expanded') === 'true').length;
    count.textContent = n === notes.length ? `נקברו ${n} מתוך ${n}. אין יותר.` : `נקברו ${n} מתוך ${notes.length}`;
    done.hidden = n !== notes.length;
    all.hidden = n === notes.length;
  }
  notes.forEach((b) => b.addEventListener('click', () => {
    b.setAttribute('aria-expanded', String(b.getAttribute('aria-expanded') !== 'true'));
    update();
  }));
  all.addEventListener('click', () => {
    notes.forEach((b, i) => setTimeout(() => { b.setAttribute('aria-expanded', 'true'); update(); }, i * 90));
  });
  update();
}
