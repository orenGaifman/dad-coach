import './styles/site.css';
import { initCommon } from './common.js';
import { initDemo } from './demo/player.js';
import { initSignupForm } from './signup-form.js';
import { initIntro } from './intro.js';
import { initStory } from './story.js';
import { initExcuses } from './excuses.js';
import { initBeltRate } from './belts.js';

const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

initCommon();
initIntro();

// Scroll reveal: opt-in class, so everything is visible without JS or with reduced motion.
if (!reducedMotion && 'IntersectionObserver' in window) {
  document.documentElement.classList.add('motion');
  const io = new IntersectionObserver((entries) => {
    for (const entry of entries) {
      if (entry.isIntersecting) {
        entry.target.classList.add('is-visible');
        io.unobserve(entry.target);
      }
    }
  }, { threshold: 0.12, rootMargin: '0px 0px -40px 0px' });
  document.querySelectorAll('.reveal').forEach((n) => io.observe(n));
}

const story = document.querySelector('[data-story]');
if (story) initStory(story, { reducedMotion });

const excuses = document.getElementById('excuses');
if (excuses) initExcuses(excuses);

const belts = document.getElementById('belts');
if (belts) initBeltRate(belts);

const demo = document.querySelector('[data-demo]');
if (demo) initDemo(demo, { reducedMotion });

const form = document.querySelector('[data-signup-form]');
if (form) initSignupForm(form);

// "מתחילים" goes to the signup (the WhatsApp number is invite-only: he signs up, then writes).
document.querySelectorAll('[data-start]').forEach((a) => a.addEventListener('click', (e) => {
  const card = document.getElementById('signup');
  const name = document.getElementById('su-name');
  if (!card) return;
  e.preventDefault();
  card.scrollIntoView({ behavior: reducedMotion ? 'auto' : 'smooth', block: 'center' });
  if (name && !name.closest('[hidden]')) setTimeout(() => name.focus({ preventScroll: true }), reducedMotion ? 0 : 450);
}));
