import type { VitePWAOptions } from 'vite-plugin-pwa'

export const pwaOptions: Partial<VitePWAOptions> = {
  injectRegister: 'script-defer',
  registerType: 'prompt',
  includeAssets: ['offline.html', 'pwa/*.png', 'pwa/icon.svg'],
  manifest: {
    id: '/',
    name: 'Meloo',
    short_name: 'Meloo',
    description: 'Nghe nhạc, đoán bài hát cùng Meloo.',
    lang: 'vi',
    start_url: '/',
    scope: '/',
    display: 'standalone',
    background_color: '#0d0d0e',
    theme_color: '#0d0d0e',
    icons: [
      { src: '/pwa/icon-192.png', sizes: '192x192', type: 'image/png', purpose: 'any' },
      { src: '/pwa/icon-512.png', sizes: '512x512', type: 'image/png', purpose: 'any' },
      { src: '/pwa/maskable-512.png', sizes: '512x512', type: 'image/png', purpose: 'maskable' },
    ],
  },
  workbox: {
    // Cache only the offline document and branding, never the app or game data.
    globPatterns: [],
    navigateFallback: null,
    cleanupOutdatedCaches: true,
    clientsClaim: true,
    runtimeCaching: [
      {
        urlPattern: ({ request, url }) =>
          request.mode === 'navigate' &&
          !/^\/(api|audio|ws)(\/|$)/i.test(url.pathname) &&
          !/\.(mp3|m4a|aac|ogg|oga|opus|wav|flac|webm|mp4)$/i.test(url.pathname),
        handler: 'NetworkOnly',
        options: { precacheFallback: { fallbackURL: '/offline.html' } },
      },
    ],
  },
}
