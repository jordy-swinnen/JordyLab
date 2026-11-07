# Contract: Environment variables

Every variable the Compose stack needs, where its value comes from, and whether it changes from the k3s deploy. Values are never written here; only names and sources. The runner builds `prod.env` from these sources (`scripts/render-env.sh`, research R6).

Sources:
- **SOPS secret `jordylab-secrets`**: keys in `deploy/k8s/overlays/prod/secrets.sops.yaml`.
- **SOPS secret `jordylab-db-app`** and **`jordylab-db-keycloak`**: `username` and `password`.
- **Config**: the literal values in `deploy/k8s/overlays/prod/kustomization.yaml` (`configMapGenerator`).

## Backend

| Variable | Source | Changes? |
|----------|--------|----------|
| `SPRING_PROFILES_ACTIVE` | `prod` | no |
| `POSTGRES_URL` | `jdbc:postgresql://postgres:5432/jordylab` | **yes**: was `cnpg-cluster-rw` |
| `POSTGRES_USER` | `jordylab-db-app` → `username` | no |
| `POSTGRES_PASSWORD` | `jordylab-db-app` → `password` | no |
| `KEYCLOAK_URL` | `https://jordylab.be/auth` | no |
| `KEYCLOAK_INTERNAL_URL` | `http://keycloak:8080/auth` | no (the service name is the same) |
| `KEYCLOAK_BACKEND_SECRET` | `jordylab-secrets` → `KEYCLOAK_BACKEND_CLIENT_SECRET` | no |
| `JORDYLAB_CORS_ALLOWED_ORIGINS` | `https://jordylab.be,https://localhost` | no |
| `JORDYLAB_APP_BASE_URL` | `https://jordylab.be` | **new on main**: literal in `backend-config` |
| `NTFY_BASE_URL` | `https://jordylab.be/ntfy` | no |
| `NTFY_TOPIC` | `jordylab-secrets` → `NTFY_TOPIC` | no |
| `NTFY_TOKEN`, `LINEAR_API_KEY`, `LANGFUSE_BASE_URL` | `jordylab-secrets` (same key names) | no (optional; the k3s backend read them through its `envFrom`, so the first draft of this table missed them) |
| `LANGFUSE_ENVIRONMENT` | `production` | no |
| `LANGFUSE_CAPTURE_CONTENT` | `true` | no |
| `SPRINGDOC_API_DOCS_ENABLED` | `false` | no |
| `SPRINGDOC_SWAGGER_UI_ENABLED` | `false` | no |
| `ANTHROPIC_API_KEY`, `OPENROUTER_API_KEY`, `STEAM_WEB_API_KEY`, `STEAM_ID`, `IGDB_CLIENT_ID`, `IGDB_CLIENT_SECRET`, `THEGAMESDB_API_KEY`, Langfuse keys | `jordylab-secrets` (same key names) | no |

## Keycloak

| Variable | Source | Changes? |
|----------|--------|----------|
| `KC_DB_URL` | `jdbc:postgresql://postgres:5432/jordylab?currentSchema=keycloak` | **yes**: was `cnpg-cluster-rw` |
| `KC_DB_SCHEMA` | `keycloak` | no |
| `KC_DB_USERNAME` | `jordylab-db-keycloak` → `username` | no |
| `KC_DB_PASSWORD` | `jordylab-db-keycloak` → `password` | no |
| `KC_BOOTSTRAP_ADMIN_USERNAME`, `KC_BOOTSTRAP_ADMIN_PASSWORD` | `jordylab-secrets` (`KEYCLOAK_ADMIN_BOOTSTRAP_*`) | no (first boot only) |
| `KEYCLOAK_BACKEND_CLIENT_SECRET` | `jordylab-secrets` | no |
| `KC_HOSTNAME` | `https://jordylab.be/auth` | no |
| `KC_HTTP_RELATIVE_PATH` | `/auth` (must match the image) | no |
| `KC_PROXY_HEADERS` | `xforwarded` | no |
| `KC_HTTP_ENABLED` | `true` | no |
| `KC_PROXY_TRUSTED_ADDRESSES` | `172.28.0.0/16` (the fixed Compose subnet) | **yes**: was `10.42.0.0/16` |
| `PRODUCTION_DOMAIN` | `jordylab.be` | no |

Keycloak's build-time options (`KC_DB`, `KC_HEALTH_ENABLED`, `KC_METRICS_ENABLED`, `KC_HTTP_MANAGEMENT_RELATIVE_PATH`) are baked into the image and are not set here.

## Postgres

| Variable | Source | Changes? |
|----------|--------|----------|
| `POSTGRES_DB` | `jordylab` | no |
| `POSTGRES_PASSWORD` (the container) | `POSTGRES_SUPERUSER_PASSWORD` in `prod.env` (from the **new** secret key `POSTGRES_SUPERUSER_PASSWORD`) | **new**: `compose.yaml` maps it to the container's `POSTGRES_PASSWORD`. The name differs in `prod.env` because the backend's `POSTGRES_PASSWORD` is the application password, and one file holds both |
| `PGBACKREST_REPO1_S3_KEY` | `jordylab-secrets` → `S3_ACCESS_KEY` | renamed source only |
| `PGBACKREST_REPO1_S3_KEY_SECRET` | `jordylab-secrets` → `S3_SECRET_KEY` | renamed source only |

**New secret needing the owner**: `POSTGRES_SUPERUSER_PASSWORD` must be added to `secrets.sops.yaml` in IntelliJ before the first deploy (`SOPS_EDITOR="idea --wait" sops deploy/k8s/overlays/prod/secrets.sops.yaml`). The agent does not open or write the value.

## ntfy and Caddy

| Variable | Source | Changes? |
|----------|--------|----------|
| ntfy: none | — | — |
| Caddy ACME account e-mail | the e-mail already used by the cert-manager issuer (not a secret) | no |

## Rules

- Names only in git. `deploy/compose/env/prod.env.example` lists the names with empty values.
- Settings that are not secret and have one fixed value (for example `POSTGRES_URL`, `KC_PROXY_TRUSTED_ADDRESSES`) are written by `scripts/render-env.sh` from its own list, so a change is reviewed in git. The secret-backed names come from the SOPS file.
- `JORDYLAB_RELEASE` is not in this file. `scripts/deploy-release.sh` exports it for the GHCR image tags.
- The file on the host is mode 600 and is never echoed in a log or a job summary.
- A variable that is absent from the render fails `render-env.sh`; an empty value fails it too, except where the table above says a value is optional (for example the API keys, which the application already treats as unset). The Kaneo secrets (below) are the one exception: an absent key renders empty, so the application deploy keeps working until the owner adds them.

## Kaneo, the backlog (spec 022)

Kaneo is not part of the application release: `deploy-release.sh` starts only `backend` and `frontend`. Its names are read by `render-env.sh` and by the Compose file's `kaneo` service.

| Variable | Source | Notes |
|----------|--------|-------|
| `KANEO_AUTH_SECRET` | `jordylab-secrets` → `KANEO_AUTH_SECRET` | soft: absent renders empty; the host step does not start Kaneo while it is empty |
| `KANEO_DB_PASSWORD` | `jordylab-secrets` → `KANEO_DB_PASSWORD` | soft; the role's password, set by `ensure-kaneo-database.sh` |
| `KANEO_GITHUB_APP_ID`, `KANEO_GITHUB_WEBHOOK_SECRET`, `KANEO_GITHUB_PRIVATE_KEY_BASE64` | `jordylab-secrets` | soft; from the GitHub App (HANDOFF-03), after the first deploy |
| `KANEO_DB_NAME`, `KANEO_DB_USER` | literals in `render-env.sh` | `kaneo`, `kaneo` |
| `KANEO_DISABLE_REGISTRATION` | repository variable | default `true` |
| `KANEO_MEM_LIMIT` | repository variable | default `768m` |
| `BACKLOG_PROVIDER` | repository variable | default `linear` until the cutover; read by the backend once the Kaneo client exists |

An unset or empty repository variable takes the default. Soft secrets never make the render fail; every other secret keeps the strict rule above.
