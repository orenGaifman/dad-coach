// The start: name + Israeli mobile, then WhatsApp. The shared WhatsApp number is invite-only - the gateway drops
// a sender no product claims - so the signup is what lets his first message reach Dad Coach (the backend claims a
// phone that signed up in the last 30 days). Unconfigured (SIGNUP_ENDPOINT empty) → nothing leaves the browser.
import { SIGNUP_ENDPOINT } from './config.js';

const TIMEOUT_MS = 10000;

/**
 * Normalizes an Israeli phone number to E.164 (+972…), or returns null.
 * Accepts 050-1234567, 0501234567, +972 50 123 4567, 972501234567, 00972501234567.
 * Mobile only (05X): Dad Coach talks to the father on WhatsApp.
 */
export function normalizeIsraeliMobile(raw) {
  let s = String(raw || '').replace(/[\s\-().]/g, '');
  if (s.startsWith('+')) s = s.slice(1);
  if (s.startsWith('00')) s = s.slice(2);
  let national;
  if (s.startsWith('972')) national = s.slice(3).replace(/^0/, '');
  else if (s.startsWith('0')) national = s.slice(1);
  else return null;
  return /^5\d{8}$/.test(national) ? `+972${national}` : null;
}

function setError(input, message) {
  const err = document.getElementById(`${input.id}-error`);
  if (message) input.setAttribute('aria-invalid', 'true');
  else input.removeAttribute('aria-invalid');
  if (err) err.textContent = message || '';
}

const validateName = (input) => (input.value.trim().length < 2 ? 'איך קוראים לך? (שתי אותיות לפחות. גם ״אבא״ עובד.)' : '');
function validatePhone(input) {
  const v = input.value.trim();
  if (!v) return 'נא למלא מספר נייד.';
  if (!normalizeIsraeliMobile(v)) return 'זה לא נראה כמו נייד ישראלי. לדוגמה: 050-1234567';
  return '';
}

export function initSignupForm(form) {
  const name = form.querySelector('#su-name');
  const phone = form.querySelector('#su-phone');
  const trap = form.querySelector('[name="website"]');
  const submit = form.querySelector('[type="submit"]');
  const status = form.querySelector('[data-form-status]');
  const done = form.parentElement.querySelector('[data-form-done]');
  const doneTitle = done.querySelector('[data-form-done-title]');

  form.noValidate = true;
  for (const [input, fn] of [[name, validateName], [phone, validatePhone]]) {
    input.addEventListener('blur', () => { if (input.value) setError(input, fn(input)); });
    input.addEventListener('input', () => { if (input.getAttribute('aria-invalid')) setError(input, fn(input)); });
  }

  function showDone(firstName) {
    form.hidden = true;
    doneTitle.textContent = firstName ? `מעולה, ${firstName}. עכשיו שלח לנו הודעה בוואטסאפ.` : 'מעולה. עכשיו שלח לנו הודעה בוואטסאפ.';
    done.hidden = false;
    done.focus();
  }

  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    status.textContent = '';
    status.classList.remove('is-error');
    const nameErr = validateName(name);
    const phoneErr = validatePhone(phone);
    setError(name, nameErr);
    setError(phone, phoneErr);
    if (nameErr || phoneErr) { (nameErr ? name : phone).focus(); return; }

    const firstName = name.value.trim().split(/\s+/)[0];
    if (trap && trap.value) { showDone(firstName); return; } // a bot filled the hidden field
    if (!SIGNUP_ENDPOINT) { showDone(firstName); return; }   // not configured: nothing is sent

    const payload = {
      name: name.value.trim(),
      phone: normalizeIsraeliMobile(phone.value),
      source: 'site',
      page: location.pathname,
      website: '',
    };
    submit.disabled = true;
    status.textContent = 'שולח…';
    const ctrl = new AbortController();
    const timer = setTimeout(() => ctrl.abort(), TIMEOUT_MS);
    try {
      const res = await fetch(SIGNUP_ENDPOINT, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
        signal: ctrl.signal,
      });
      if (!res.ok) throw new Error(`status ${res.status}`);
      status.textContent = '';
      showDone(firstName);
    } catch {
      status.textContent = 'משהו השתבש בשליחה. הפרטים נשארו בטופס, אפשר לנסות שוב בעוד רגע.';
      status.classList.add('is-error');
    } finally {
      clearTimeout(timer);
      submit.disabled = false;
    }
  });
}
