import './styles/site.css';
import { initCommon } from './common.js';
import { initDemo } from './demo/player.js';
import { initSignupForm } from './signup-form.js';
import { initIntro } from './intro.js';

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

const demo = document.querySelector('[data-demo]');
if (demo) initDemo(demo, { reducedMotion });

const form = document.querySelector('[data-signup-form]');
if (form) initSignupForm(form);
