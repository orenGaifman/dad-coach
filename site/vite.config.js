import { defineConfig } from 'vite';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { siteUrl } from './site-url.js';

const here = fileURLToPath(new URL('.', import.meta.url));

// Multi-page static build: the landing page + four legal pages. Directory URLs (/privacy/), so any
// static host serves them without rewrites.
export default defineConfig({
  root: here,
  base: '/',
  plugins: [siteUrl('https://dad-coach-site.onrender.com')],
  build: {
    outDir: 'dist',
    emptyOutDir: true,
    rollupOptions: {
      input: {
        main: resolve(here, 'index.html'),
        privacy: resolve(here, 'privacy/index.html'),
        terms: resolve(here, 'terms/index.html'),
        dataDeletion: resolve(here, 'data-deletion/index.html'),
        accessibility: resolve(here, 'accessibility/index.html'),
      },
    },
  },
});
