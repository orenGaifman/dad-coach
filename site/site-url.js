import { existsSync, readFileSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';

// The site's public address, used by canonical, Open Graph and Twitter tags, robots.txt and
// sitemap.xml. Until the product has its own domain it is the Render address; set VITE_SITE_URL at
// build time once a domain is connected. Every "__SITE_URL__" is replaced at build time, so a link
// shared in WhatsApp always gets a preview image that exists.
export function siteUrl(defaultUrl) {
  const url = (process.env.VITE_SITE_URL || defaultUrl).replace(/\/+$/, '');
  let outDir;
  return {
    name: 'site-url',
    configResolved(config) {
      outDir = resolve(config.root, config.build.outDir);
    },
    transformIndexHtml(html) {
      return html.replaceAll('__SITE_URL__', url);
    },
    closeBundle() {
      for (const file of ['robots.txt', 'sitemap.xml']) {
        const path = resolve(outDir, file);
        if (existsSync(path)) writeFileSync(path, readFileSync(path, 'utf8').replaceAll('__SITE_URL__', url));
      }
    },
  };
}
