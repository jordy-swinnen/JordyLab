# Contract: Production Smoke Suite (Sections A + B core)

Run after every deploy and at close-out. Each check → PASS/FAIL with evidence. Nothing here mutates data.

| # | Check | Expected |
|---|-------|----------|
| A1 | DNS `jordylab.be` A record | resolves to the VPS public IPv4 |
| A2 | TLS certificate | valid chain, CN/SAN `jordylab.be`, expiry > 14 days (record the date) |
| A3 | `http://jordylab.be/` | 301/308 → `https://jordylab.be/` |
| A4 | Security headers on `/` | HSTS present; `X-Content-Type-Options: nosniff`; frame protection; no `Server` version leak (missing → S3 bug) |
| A5 | `/` and a nested deep link (e.g. `/gamecatalog/...`) reloaded | 200, Angular app shell (not 404) |
| A6 | Static assets | gzip/br encoding; hashed assets long-cached, `index.html` not cached |
| A7 | favicon / manifest | 200 |
| A8 | `/auth/realms/jordylab/.well-known/openid-configuration` | 200; `issuer` = `https://jordylab.be/auth/realms/jordylab` |
| A9 | `/api/gamecatalog/games` unauthenticated | 401, no stack trace in body |
| A10 | CORS preflight from `https://evil.example` | no `Access-Control-Allow-Origin`; from `https://jordylab.be` and `https://localhost` → allowed |
| A11 | Backend readiness/liveness (in-cluster) | UP; pods Ready; 0 recent restarts |
| A12 | Browser pass over every top-level route (as admin, then guest) | 0 console errors, 0 failed requests, 0 mixed content |
| A13 | Running image tags | backend/frontend/keycloak all `sha-<expected>` |
| B1 | Login → app → logout → login | no redirect loop; tokens refresh silently |
| B2 | Guest | sees Game Catalog only; `/api/fna/**`, `/api/settings/**`, admin `/api/gamecatalog/**` writes → 403 |
| B3 | Unauthenticated SPA route | redirected to Keycloak login |
