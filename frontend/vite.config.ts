import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// Dev server on 5390 (Big Boss uses 3000, Tair 3001/5293 on this machine; localhost cookies ignore the port, so
// Dad Coach's cookies have their own names too). VITE_API_TARGET points /api at a backend (local profile = :8081).
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5390,
    strictPort: true,
    proxy: {
      '/api': process.env.VITE_API_TARGET ?? 'http://localhost:8081',
    },
  },
  preview: { port: 5390 },
})
