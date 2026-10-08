// "תירוצים שמתו בשקט": each tombstone flips to its cause of death (what the product does about that excuse).
// Without JS every answer is visible (the CSS flips only under .js-graves).
export function initExcuses(section) {
  const list = section.querySelector('[data-excuses]');
  const notes = [...list.querySelectorAll('.grave')];
  const count = section.querySelector('[data-excuses-count]');
  const all = section.querySelector('[data-excuses-all]');
  const done = section.querySelector('[data-excuses-done]');
  list.classList.add('js-graves');

  function update() {
    const n = notes.filter((b) => b.getAttribute('aria-expanded') === 'true').length;
    count.textContent = n === notes.length ? `נחשפו ${n} מתוך ${n}. אין יותר תירוצים.` : `נחשפו ${n} מתוך ${notes.length}`;
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
