# Environments: `local` vs `prod`

Exactly two environments exist (FR-001). This table lists every setting that differs between them and where its
value comes from. Shared configuration (`jordylab-be/src/main/resources/application.yaml`, the base Kustomize
layer) holds none of these — they live only in the environment-specific files listed below (FR-002). Starting with
neither `local` nor `prod` active fails fast (`EnvironmentProfileGuard`).

| Setting | `local` | `prod` | Source |
|---|---|---|---|
| Active Spring profile | `local` | `prod` | `SPRING_PROFILES_ACTIVE` env var; enforced by `EnvironmentProfileGuard` |
| Backend config file | `application-local.yaml` | `application-prod.yaml` | `jordylab-be/src/main/resources/` |
| Database URL | `jdbc:postgresql://localhost:5432/jordylab` (literal, Podman) | `${POSTGRES_URL}` (CNPG's `cnpg-cluster-rw` service) | `application-local.yaml` / `backend-config` ConfigMap |
| Keycloak URL | `http://localhost:8180` (literal, Podman) | `${KEYCLOAK_URL}` = `https://<domain>/auth` | `application-local.yaml` / `backend-config` ConfigMap |
| CORS allowed origins | `http://localhost:4200,4300,4400` (per-app dev servers) | `https://<domain>,https://localhost` (web + Capacitor mobile app origin) | `application-local.yaml` / `backend-config` ConfigMap |
| Secrets (API keys, DB/Keycloak credentials) | gitignored `jordylab-be/.env` (Podman compose reads it) | SOPS+age ciphertext in `deploy/k8s/overlays/prod/secrets.sops.yaml`, decrypted by CI at deploy time | `.env` / `secrets.sops.yaml` |
| Keycloak startup mode | `start-dev --import-realm` (compose.yaml) | `start --optimized` with `KC_HOSTNAME`/`KC_HTTP_RELATIVE_PATH`/`KC_PROXY_HEADERS` | `compose.yaml` / `keycloak-config` ConfigMap |
| Keycloak realm file | `jordylab-be/compose/keycloak-realm-export.json` (has a dev user, localhost redirects) | `deploy/keycloak/realm-prod.json` (no dev user, real-domain redirects) | repo files |
| Frontend Angular build | `environment.ts` (`nx serve`, `keycloakUrl: http://localhost:8180`) | `environment.prod.ts` (`nx build --configuration=production`, real domain) | `apps/jordylab/src/environments/` |
| Frontend/backend/Keycloak runtime | Podman Compose on your machine (`compose.yaml`) | k3s Deployments on the OVH VPS (`deploy/k8s/`) | — |
| Frontend serving | `nx serve` dev server | `nginx-unprivileged` container, built by CI | `deploy/containers/frontend/Containerfile` |
| Image tags | N/A — runs from source | Commit SHA only, never `latest` (FR-005) | `deploy/k8s/overlays/prod/kustomization.yaml` |
| Artwork/APK/ntfy storage | Local filesystem paths (`/var/jordylab/...` on your machine) | `local-path` PVCs on the VPS's NVMe | `deploy/k8s/base/*.yaml` |
| TLS | None (plain HTTP) | Let's Encrypt via cert-manager, auto-renewed | `deploy/k8s/cluster/cert-manager-clusterissuer.yaml` |
