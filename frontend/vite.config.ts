import { loadEnv } from 'vite';
import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

/**
 * The dev server proxies the API rather than letting the browser call it
 * cross-origin.
 *
 * With a proxy, `fetch('/api/...')` is same-origin, so a mismatch between the
 * port Vite happens to be on and CORS_ALLOWED_ORIGINS on the server can no
 * longer present itself as "cannot reach MBank". The production bundle
 * still talks to an absolute origin, baked in at build time from
 * VITE_API_BASE_URL (see the Dockerfile build arg).
 */
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');
  const target = env.VITE_DEV_API_TARGET || 'http://localhost:8080';

  return {
    plugins: [react()],
    server: {
      port: 5173,
      strictPort: true,
      proxy: {
        '/api': { target, changeOrigin: true },
        // Probed by the connection banner to tell "server is down" apart from
        // "this request failed".
        '/actuator': { target, changeOrigin: true },
      },
    },
    build: {
      outDir: 'dist',
      sourcemap: false,
    },
    test: {
      environment: 'jsdom',
      include: ['src/**/*.test.{ts,tsx}'],
      restoreMocks: true,
    },
  };
});
