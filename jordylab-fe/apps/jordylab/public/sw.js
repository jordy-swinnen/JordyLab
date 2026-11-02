/*
 * Minimal service worker: it exists so Chrome offers the automatic install prompt (that algorithm still requires a
 * registered fetch handler), and for nothing else. The handler never calls respondWith, so every request — navigation,
 * /api/**, LibBot's server-sent events, /auth/** — falls through to the network and nothing is ever cached (spec 018
 * FR-002). Offline support is explicitly out of scope.
 */
self.addEventListener('install', () => {
  self.skipWaiting();
});

self.addEventListener('activate', (event) => {
  event.waitUntil(self.clients.claim());
});

self.addEventListener('fetch', () => {
  // Intentionally empty: a pass-through handler that satisfies the installability criterion without intercepting.
});
