import path from 'node:path'
import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vitest/config'
import { VitePWA } from 'vite-plugin-pwa'
import { pwaOptions } from './src/app/pwa/config'

export default defineConfig({
  plugins: [
    react(),
    tailwindcss(),
    VitePWA(pwaOptions),
    {
      name: 'meloo-pwa-icons',
      transformIndexHtml: () => [
        { tag: 'meta', attrs: { name: 'theme-color', content: '#0d0d0e' } },
        { tag: 'link', attrs: { rel: 'icon', type: 'image/svg+xml', href: '/pwa/icon.svg' } },
        { tag: 'link', attrs: { rel: 'apple-touch-icon', href: '/pwa/apple-touch-icon.png' } },
      ],
    },
  ],
  resolve: { alias: { '@': path.resolve(import.meta.dirname, 'src') } },
  server: {
    port: 5173,
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
      '/ws': { target: 'ws://localhost:8080', ws: true },
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    include: ['src/**/*.test.{ts,tsx}'],
    css: false,
  },
})
