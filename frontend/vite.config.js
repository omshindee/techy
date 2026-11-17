import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { VitePWA } from 'vite-plugin-pwa'

// Theme colors match index.css: white app bars (--surface) over the warm paper background (--bg).
const THEME = '#ffffff'
const BACKGROUND = '#f4f3ef'

export default defineConfig(({ mode }) => {
  // `--mode h2` talks to a throwaway in-memory test backend on :8081, so it can run next to the
  // Docker backend on :8080.
  const api = mode === 'h2' ? 'http://localhost:8081' : 'http://localhost:8080'
  return {
    // Build stamp shown in Settings, handy for "which version are you on?" support questions.
    define: { __APP_BUILD__: JSON.stringify(new Date().toISOString().slice(0, 16).replace('T', ' ') + ' UTC') },
    plugins: [
      react(),
      VitePWA({
        // Ask before activating a new version (see src/components/PwaUpdater.jsx) instead of
        // swapping code under someone mid-post; registration happens from React.
        registerType: 'prompt',
        injectRegister: false,
        includeAssets: ['icons/icon.svg', 'icons/favicon-32.png', 'icons/apple-touch-icon.png'],
        manifest: {
          id: '/',
          name: 'Techy',
          short_name: 'Techy',
          description: 'The network for people who build things: share what you learn and ship, follow other developers.',
          lang: 'en',
          start_url: '/',
          scope: '/',
          display: 'standalone',
          theme_color: THEME,
          background_color: BACKGROUND,
          categories: ['social', 'developer', 'productivity'],
          icons: [
            { src: '/icons/icon-192.png', sizes: '192x192', type: 'image/png', purpose: 'any' },
            { src: '/icons/icon-512.png', sizes: '512x512', type: 'image/png', purpose: 'any' },
            { src: '/icons/maskable-192.png', sizes: '192x192', type: 'image/png', purpose: 'maskable' },
            { src: '/icons/maskable-512.png', sizes: '512x512', type: 'image/png', purpose: 'maskable' },
          ],
          shortcuts: [
            { name: 'New post', short_name: 'Post', url: '/new', icons: [{ src: '/icons/icon-192.png', sizes: '192x192' }] },
            { name: 'People', url: '/people', icons: [{ src: '/icons/icon-192.png', sizes: '192x192' }] },
          ],
        },
        workbox: {
          // Precache only the app shell: hashed JS/CSS, index.html, icons. Nothing user-specific.
          globPatterns: ['**/*.{js,css,html,svg,png,ico,woff2}'],
          // Deep links (e.g. /profile/priya) open the cached shell; API and health URLs never do.
          navigateFallback: '/index.html',
          navigateFallbackDenylist: [/^\/api\//, /^\/actuator\//],
          cleanupOutdatedCaches: true,
          // /api/** is intentionally NOT cached: feeds, profiles, tokens and signed private images
          // must always come from the server. Only Google's public font files are cached at runtime.
          runtimeCaching: [
            {
              urlPattern: ({ url }) => url.origin === 'https://fonts.googleapis.com',
              handler: 'StaleWhileRevalidate',
              options: { cacheName: 'google-fonts-css' },
            },
            {
              urlPattern: ({ url }) => url.origin === 'https://fonts.gstatic.com',
              handler: 'CacheFirst',
              options: {
                cacheName: 'google-fonts-files',
                expiration: { maxEntries: 20, maxAgeSeconds: 60 * 60 * 24 * 365 },
                cacheableResponse: { statuses: [0, 200] },
              },
            },
          ],
        },
      }),
    ],
    server: { port: 5173, proxy: { '/api': api } },
    preview: { port: 4173, proxy: { '/api': api } },
  }
})
