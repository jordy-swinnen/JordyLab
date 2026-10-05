#!/usr/bin/env bun
// Boots the *built* app in headless Chrome and checks it reaches the login page without a start-up error.
// Unit tests construct services inside a TestBed; this is the only check that runs the real bootstrap path
// (main.ts -> pre-bootstrap auth -> bootstrapApplication -> router). It would have caught the rc12 blank page
// (NG0201: AuthService -> Router, spec 011 BUG-056). No dependencies: Chrome DevTools Protocol over a WebSocket.
//
//   bun tools/app-boot-check.mjs [distDir]      (default dist/apps/jordylab/browser; CHROME_BIN overrides the browser)
import { spawn } from 'node:child_process';
import { existsSync, mkdtempSync, readFileSync, rmSync, statSync } from 'node:fs';
import { createServer } from 'node:http';
import { tmpdir } from 'node:os';
import { extname, join, normalize } from 'node:path';

const distDir = process.argv[2] ?? 'dist/apps/jordylab/browser';
const EXPECTED_TEXT = 'Sign in with Keycloak';
const BOOT_TIMEOUT_MS = 45_000;
const CHROME_CANDIDATES = [
  process.env.CHROME_BIN,
  'google-chrome',
  'google-chrome-stable',
  'chromium',
  'chromium-browser',
  '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',
].filter(Boolean);
const MIME = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.json': 'application/json', '.svg': 'image/svg+xml', '.png': 'image/png', '.webmanifest': 'application/manifest+json', '.ico': 'image/x-icon', '.woff2': 'font/woff2' };

function serve(root) {
  const server = createServer((request, response) => {
    const pathname = normalize(decodeURIComponent(new URL(request.url, 'http://x').pathname)).replace(/^(\.\.[/\\])+/, '');
    let file = join(root, pathname);
    if (!existsSync(file) || statSync(file).isDirectory()) file = join(root, 'index.html'); // SPA fallback
    response.writeHead(200, { 'content-type': MIME[extname(file)] ?? 'application/octet-stream' });
    response.end(readFileSync(file));
  });
  return new Promise((resolve) => server.listen(0, '127.0.0.1', () => resolve({ server, port: server.address().port })));
}

async function launchChrome(profileDir, debugPort) {
  for (const binary of CHROME_CANDIDATES) {
    const child = spawn(binary, ['--headless=new', '--no-sandbox', '--disable-gpu', '--disable-dev-shm-usage', '--no-first-run',
      `--remote-debugging-port=${debugPort}`, `--user-data-dir=${profileDir}`, 'about:blank'], { stdio: 'ignore' });
    const failed = await new Promise((resolve) => { child.once('error', () => resolve(true)); setTimeout(() => resolve(false), 400); });
    if (!failed) return child;
  }
  throw new Error(`No Chrome found (tried ${CHROME_CANDIDATES.join(', ')}); set CHROME_BIN.`);
}

async function pageSocketUrl(debugPort) {
  for (let attempt = 0; attempt < 60; attempt++) {
    try {
      const targets = await (await fetch(`http://127.0.0.1:${debugPort}/json/list`)).json();
      const page = targets.find((target) => target.type === 'page');
      if (page) return page.webSocketDebuggerUrl;
    } catch { /* Chrome is still starting */ }
    await new Promise((resolve) => setTimeout(resolve, 250));
  }
  throw new Error('Chrome did not expose a page target');
}

const profileDir = mkdtempSync(join(tmpdir(), 'app-boot-'));
const { server, port } = await serve(distDir);
const debugPort = 9300 + Math.floor(Math.random() * 500);
const chrome = await launchChrome(profileDir, debugPort);
const problems = [];
const consoleLog = [];
let exitCode = 1;
try {
  const socket = new WebSocket(await pageSocketUrl(debugPort));
  await new Promise((resolve, reject) => { socket.onopen = resolve; socket.onerror = reject; });
  let nextId = 0;
  const pending = new Map();
  const send = (method, params = {}) => new Promise((resolve) => { const id = ++nextId; pending.set(id, resolve); socket.send(JSON.stringify({ id, method, params })); });
  socket.onmessage = (event) => {
    const message = JSON.parse(event.data);
    if (message.id && pending.has(message.id)) { pending.get(message.id)(message.result); pending.delete(message.id); return; }
    if (message.method === 'Runtime.exceptionThrown') {
      const details = message.params.exceptionDetails;
      problems.push(details.exception?.description ?? details.text);
    } else if (message.method === 'Runtime.consoleAPICalled') {
      const text = message.params.args.map((arg) => arg.value ?? arg.description ?? '').join(' ');
      consoleLog.push(`${message.params.type}: ${text}`);
      if (message.params.type === 'error' && /NG0\d+|Uncaught/.test(text)) problems.push(text);
    } else if (message.method === 'Log.entryAdded') {
      consoleLog.push(`${message.params.entry.level}: ${message.params.entry.text} ${message.params.entry.url ?? ''}`);
    }
  };
  await send('Runtime.enable');
  await send('Log.enable');
  await send('Page.enable');
  await send('Page.navigate', { url: `http://127.0.0.1:${port}/` });

  const deadline = Date.now() + BOOT_TIMEOUT_MS;
  let text = '';
  while (Date.now() < deadline && problems.length === 0) {
    const result = await send('Runtime.evaluate', { expression: "document.querySelector('app-root')?.innerText ?? ''", returnByValue: true });
    text = result?.result?.value ?? '';
    if (text.includes(EXPECTED_TEXT)) break;
    await new Promise((resolve) => setTimeout(resolve, 500));
  }
  if (problems.length > 0) {
    console.error('The app threw while starting:\n' + problems.map((problem) => `  - ${String(problem).split('\n')[0]}`).join('\n'));
  } else if (!text.includes(EXPECTED_TEXT)) {
    console.error(`The app did not reach the login page within ${BOOT_TIMEOUT_MS / 1000}s (page text: ${JSON.stringify(text.slice(0, 120))}).`);
    console.error('Browser log:\n' + consoleLog.slice(0, 15).map((line) => `  ${line.slice(0, 220)}`).join('\n'));
  } else {
    console.log(`App boot check passed: the login page rendered (${JSON.stringify(EXPECTED_TEXT)}).`);
    exitCode = 0;
  }
  socket.close();
} catch (error) {
  console.error(`App boot check could not run: ${error.message}`);
} finally {
  chrome.kill('SIGKILL');
  server.close();
  rmSync(profileDir, { recursive: true, force: true });
}
process.exit(exitCode);
