import { defineConfig } from 'vitest/config'

// Unit tests of the pure helpers (formatting, labels, links). Screens are covered end to end by Playwright (e2e/).
export default defineConfig({
  test: { include: ['src/**/*.test.ts'], environment: 'node' },
})
