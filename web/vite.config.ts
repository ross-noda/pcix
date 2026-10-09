import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
export default defineConfig({
  plugins: [
    react(),
    {
      name: "pcix-offline-shell",
      apply: "build",
      generateBundle(_, bundle) {
        const files = [
          "/",
          ...Object.keys(bundle).map((name) => "/" + name),
          "/assets/inter.ttf",
          "/assets/manrope.ttf",
          "/assets/brand_wordmark_light.svg",
          "/assets/brand_wordmark_dark.svg",
          "/assets/brand_icon_light.svg",
          "/assets/brand_icon_dark.svg",
        ];
        this.emitFile({
          type: "asset",
          fileName: "sw.js",
          source: `const CACHE = ${JSON.stringify("pcix-shell-" + Object.keys(bundle).join("|"))};
const FILES = ${JSON.stringify(files)};
self.addEventListener('install', event => event.waitUntil(caches.open(CACHE).then(cache => cache.addAll(FILES))));
self.addEventListener('activate', event => event.waitUntil(caches.keys().then(keys => Promise.all(keys.filter(key => key.startsWith('pcix-shell-') && key !== CACHE).map(key => caches.delete(key)))).then(() => self.clients.claim())));
self.addEventListener('fetch', event => {
  const url = new URL(event.request.url);
  if (event.request.method !== 'GET' || url.origin !== self.location.origin) return;
  if (event.request.mode === 'navigate') {
    event.respondWith(fetch(event.request).catch(() => caches.open(CACHE).then(cache => cache.match('/'))));
  } else if (FILES.includes(url.pathname)) {
    event.respondWith(caches.open(CACHE).then(async cache => (await cache.match(event.request)) || fetch(event.request)));
  }
});`,
        });
      },
    },
  ],
  clearScreen: false,
  server: { port: 5173, strictPort: true },
  envPrefix: ["VITE_", "TAURI_ENV_"],
  build: { target: "es2022" },
});
