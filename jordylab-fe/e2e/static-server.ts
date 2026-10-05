// Serves the freshly built Angular app for one E2E run and proxies /api to the throwaway backend, like the production
// ingress does. Bun only, no dependencies. Config comes from the runner's environment.
import { existsSync } from 'node:fs';
import { join, normalize } from 'node:path';

const distDirectory = process.env['E2E_WEB_DIST'] ?? '';
const webPort = Number(process.env['E2E_WEB_PORT']);
const apiOrigin = process.env['E2E_API_ORIGIN'] ?? '';

if (!distDirectory || !existsSync(distDirectory) || !webPort || !apiOrigin) {
  console.error('static-server: E2E_WEB_DIST, E2E_WEB_PORT and E2E_API_ORIGIN must be set and the build must exist');
  process.exit(2);
}

Bun.serve({
  port: webPort,
  hostname: '127.0.0.1',
  async fetch(request) {
    const url = new URL(request.url);
    if (url.pathname.startsWith('/api/')) {
      return fetch(new Request(apiOrigin + url.pathname + url.search, request));
    }
    const requested = normalize(join(distDirectory, url.pathname));
    const insideDist = requested.startsWith(normalize(distDirectory));
    const file = insideDist && existsSync(requested) && !requested.endsWith('/') ? Bun.file(requested) : null;
    if (file && (await file.exists()) && url.pathname !== '/') {
      return new Response(file);
    }

    return new Response(Bun.file(join(distDirectory, 'index.html')));
  },
});
console.log(`static-server: http://127.0.0.1:${webPort} -> ${distDirectory}`);
