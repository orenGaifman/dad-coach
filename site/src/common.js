// Shared by every page: footer year, WhatsApp links, the (disabled by default) analytics hook.
import { ANALYTICS_SRC, ANALYTICS_DOMAIN, WHATSAPP_URL } from './config.js';

export function initCommon() {
  document.querySelectorAll('[data-year]').forEach((node) => {
    node.textContent = String(new Date().getFullYear());
  });

  // Every "start on WhatsApp" link gets the configured number + prefilled text (the HTML already
  // carries the default, so links work without JS too).
  document.querySelectorAll('[data-wa]').forEach((a) => { a.href = WHATSAPP_URL; });

  if (ANALYTICS_SRC && ANALYTICS_DOMAIN) {
    const s = document.createElement('script');
    s.defer = true;
    s.src = ANALYTICS_SRC;
    s.dataset.domain = ANALYTICS_DOMAIN;
    document.head.append(s);
  }
}
