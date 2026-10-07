// Site configuration. With the defaults the site sends no data anywhere and loads nothing
// third-party besides Google Fonts. Each value can be set at build time with the matching VITE_*
// environment variable (e.g. `VITE_SIGNUP_ENDPOINT=https://… npm run build`). See site/README.md.

const env = import.meta.env || {};

/** The Dad Coach WhatsApp number, digits only, international format. Onboarding happens there. */
export const WHATSAPP_NUMBER = (env.VITE_WHATSAPP_NUMBER || '972552961164').replace(/\D/g, '');

/** The text the father's WhatsApp opens with (he still presses send himself). */
export const WHATSAPP_TEXT = 'היי, אני רוצה להתחיל';

export const WHATSAPP_URL = `https://wa.me/${WHATSAPP_NUMBER}?text=${encodeURIComponent(WHATSAPP_TEXT)}`;

/**
 * Where the desktop signup form POSTs (JSON: { name, phone, source, page }).
 * Target: https://<dad-coach backend>/api/public/site-signups (the backend's SITE_ORIGINS must list
 * this site's origin). Empty → nothing is sent; the visitor still sees the thank-you state.
 */
export const SIGNUP_ENDPOINT = (env.VITE_SIGNUP_ENDPOINT || '').trim();

/** Privacy-respecting analytics hook (Plausible-style, cookieless). Off unless both are set. */
export const ANALYTICS_SRC = (env.VITE_ANALYTICS_SRC || '').trim();
export const ANALYTICS_DOMAIN = (env.VITE_ANALYTICS_DOMAIN || '').trim();
