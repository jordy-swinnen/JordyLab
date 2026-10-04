# Plan Draft: How We'll Build It (k3s on an OVH VPS-2)

A reference for `/speckit-plan`. Versions and YAML fields must be checked against live docs during planning. The snippets are shape examples, not final files.

## 1. Repo layout (new and changed)

```
LICENSE                             ← all rights reserved, viewing only (replaces 4× MIT)
deploy/
  README.md                         ← start here: map of everything below
  containers/
    backend.Containerfile           ← multi-stage: Gradle build → JRE 25 runtime, non-root
    frontend.Containerfile          ← multi-stage: Bun + nx build → nginx-unprivileged
    frontend-nginx.conf             ← SPA fallback, caching, security headers
    keycloak.Containerfile          ← FROM keycloak:26.x + theme + kc.sh build
  host/
    k3s-config.yaml                 ← /etc/rancher/k3s/config.yaml (pinned version notes, tls-san, disable list if any)
    traefik-helmchartconfig.yaml    ← bundled Traefik customisation: Gateway API on, redirects, access logs
    firewall.md                     ← which ports open/closed and why
  k8s/
    base/                           ← what the app is (environment-free)
      kustomization.yaml
      namespace.yaml
      backend-deployment.yaml  backend-service.yaml  backend-pvc.yaml   ← storageClassName: local-path
      frontend-deployment.yaml frontend-service.yaml
      keycloak-deployment.yaml keycloak-service.yaml
      postgres-cluster.yaml         ← CloudNativePG Cluster (local-path) + backup config
      httproutes.yaml               ← /, /api, /auth/(realms|resources|.well-known)
    overlays/
      prod/                         ← the only overlay: domain, image tags, sizes
        kustomization.yaml
        config.env                  ← non-secret prod settings (→ ConfigMap)
        secrets.sops.yaml           ← SOPS+age encrypted K8s Secrets (committed)
    cluster/                        ← one-time add-ons, applied by hand from the runbook
      cert-manager-issuer.yaml      ← Let's Encrypt ClusterIssuer
      gateway.yaml                  ← Gateway (bundled Traefik) with HTTPS listener + cert
      cloudnative-pg-values.yaml
      ci-rbac.yaml                  ← ServiceAccount + Role for GitHub Actions (namespace-scoped)
  keycloak/
    realm-prod.json                 ← no dev user, prod redirect URIs, no secrets
.sops.yaml                          ← creation rules: which files, which age public key
docs/
  learn/  01-containers-and-podman.md  02-kubernetes-core.md  03-k3s-and-the-host.md
          04-networking-gateway-tls.md  05-config-and-secrets-sops.md
          06-storage-and-database.md  07-ci-cd.md  exercises.md
  runbook.md
  environments.md                   ← the local vs prod table
jordylab-be/src/main/resources/
  application.yaml                  ← shared, no hosts/credentials
  application-local.yaml            ← localhost DB/Keycloak/CORS
  application-prod.yaml             ← env-var driven, probes, forward headers
.github/workflows/
  build.yml                         ← test + gitleaks + build + push images (ghcr.io, sha tags) + MIT-licence check
  deploy-prod.yml                   ← environment: production (required reviewer) → sops -d → kubectl apply -k
```

Local keeps `jordylab-be/compose.yaml` + `.env`. Only what it needs changes (e.g. the profile).

## 2. Build order (phases)
0. **Licensing:** one root `LICENSE` (all rights reserved, viewing only) replacing the 4 MIT files; manifests, README and image labels aligned; CI check against MIT. A standalone commit that lands first, since the repo is already public.
1. **Environments split:** Spring profiles `local`/`prod` + fail-fast without a profile; Angular prod config on the real domain; `docs/environments.md`. *(No server needed.)*
2. **Containerfiles** for backend, frontend and Keycloak. Build and run locally with Podman (learning chapter 01 exercises, including `podman generate kube` / `podman kube play`).
3. **CI build:** `build.yml` pushes `ghcr.io/jordy-swinnen/jordylab-{backend,frontend,keycloak}:<sha>` and runs gitleaks.
4. **Host bring-up (manual, guided by the runbook):**
   1. Buy the domain and order the VPS-2 (confirm the term and renewal price).
   2. Harden the OS: SSH keys only, a non-root sudo user, unattended-upgrades, firewall (22 restricted; 80/443 open; 10250 and 8472/udp closed; 6443 per clarify).
   3. Install k3s with a pinned version (`curl -sfL https://get.k3s.io | INSTALL_K3S_VERSION=… sh -`) and copy the kubeconfig to your laptop (server address → VPS IP / DNS name).
   4. Verify the bundled pieces are running: `kubectl get pods -n kube-system` (Traefik, CoreDNS, ServiceLB `svc-*`, local-path-provisioner, metrics-server).
   5. Customise the bundled Traefik via `HelmChartConfig` (Gateway API on).
   6. Install cert-manager and the CloudNativePG operator + Barman Cloud plugin (Helm, pinned).
   7. Create the OVH Object Storage bucket + S3 user.
   8. Point a DNS A-record (and AAAA if used) at the VPS IP.
5. **SOPS + age setup:** `age-keygen` → public key in `.sops.yaml`, private key in your password manager + GitHub `production` environment secret → create `secrets.sops.yaml` with every prod secret.
6. **Manifests** (base + prod overlay), first manual `sops -d | kubectl apply` + `kubectl apply -k`, with you following the runbook.
7. **CI deploy:** `deploy-prod.yml` with approval gate, age-key decrypt step, namespace-scoped ServiceAccount, rollout wait, rollback path. The API-access method follows the clarify outcome.
8. **Backups + restore drill** (must pass before go-live), plus optional k3s datastore backup (`server/db` + `server/token`).
9. **Docs:** learning guide + runbook finalised (including k3s/OS upgrade procedure); AGENTS.md infra section rewritten.

## 3. Shape examples

**Bundled Traefik customisation (k3s `HelmChartConfig`)**
```yaml
apiVersion: helm.cattle.io/v1
kind: HelmChartConfig
metadata: { name: traefik, namespace: kube-system }
spec:
  valuesContent: |-
    providers:
      kubernetesGateway: { enabled: true }   # Gateway API (verify values for the bundled chart version)
    ports:
      web: { redirections: { entryPoint: { to: websecure, scheme: https } } }
```

**Backend Deployment (excerpt):** secrets come from a SOPS-decrypted Secret, non-secret settings from a ConfigMap.
```yaml
apiVersion: apps/v1
kind: Deployment
metadata: { name: backend, namespace: jordylab }
spec:
  replicas: 1
  strategy: { type: Recreate }          # RWO local-path artwork volume → no overlap
  selector: { matchLabels: { app: backend } }
  template:
    metadata: { labels: { app: backend } }
    spec:
      securityContext: { runAsNonRoot: true }
      containers:
        - name: backend
          image: ghcr.io/jordy-swinnen/jordylab-backend   # tag set by kustomize (sha)
          env:
            - { name: SPRING_PROFILES_ACTIVE, value: prod }
          envFrom:
            - configMapRef: { name: backend-config }       # from overlays/prod/config.env
            - secretRef:    { name: backend-secrets }      # from secrets.sops.yaml (decrypted in CI)
          ports: [{ containerPort: 8080 }]
          readinessProbe: { httpGet: { path: /actuator/health/readiness, port: 8080 } }
          livenessProbe:  { httpGet: { path: /actuator/health/liveness,  port: 8080 }, initialDelaySeconds: 60 }
          resources:
            requests: { cpu: 250m, memory: 1Gi }
            limits:   { memory: 1536Mi }
          volumeMounts: [{ name: artwork, mountPath: /var/jordylab/artwork }]
      volumes:
        - name: artwork
          persistentVolumeClaim: { claimName: backend-artwork }   # storageClassName: local-path
```

**SOPS-encrypted secret (as committed; values are ciphertext)**
```yaml
apiVersion: v1
kind: Secret
metadata: { name: backend-secrets, namespace: jordylab }
type: Opaque
stringData:
  ANTHROPIC_API_KEY: ENC[AES256_GCM,data:…,type:str]
  OPENROUTER_API_KEY: ENC[AES256_GCM,data:…,type:str]
sops:
  age: [{ recipient: age1…, enc: "…" }]
  encrypted_regex: ^(data|stringData)$
```

**HTTPRoute:** path routing on one domain; Keycloak admin is not routed.
```yaml
apiVersion: gateway.networking.k8s.io/v1
kind: HTTPRoute
metadata: { name: jordylab, namespace: jordylab }
spec:
  parentRefs: [{ name: jordylab-gateway, namespace: kube-system }]
  hostnames: ["<domain>"]
  rules:
    - matches: [{ path: { type: PathPrefix, value: /api } }]
      backendRefs: [{ name: backend, port: 8080 }]
    - matches:
        - { path: { type: PathPrefix, value: /auth/realms } }
        - { path: { type: PathPrefix, value: /auth/resources } }
      backendRefs: [{ name: keycloak, port: 8080 }]
    - matches: [{ path: { type: PathPrefix, value: / } }]
      backendRefs: [{ name: frontend, port: 8080 }]
```

**CloudNativePG cluster (excerpt)**
```yaml
apiVersion: postgresql.cnpg.io/v1
kind: Cluster
metadata: { name: jordylab-db, namespace: jordylab }
spec:
  instances: 1
  imageName: <cnpg postgres 16 image that includes pgvector — verify>
  storage: { size: 20Gi, storageClass: local-path }
  bootstrap:
    initdb:
      database: jordylab
      owner: jordylab
      postInitApplicationSQL:
        - CREATE EXTENSION IF NOT EXISTS vector;
        - CREATE SCHEMA IF NOT EXISTS keycloak AUTHORIZATION jordylab;
  plugins:
    - name: barman-cloud.cloudnative-pg.io     # backups → OVH Object Storage (S3)
      isWALArchiver: true
      parameters: { barmanObjectName: ovh-backups }
```

## 4. Things to verify during `/speckit-plan` (don't guess)
- The current stable k3s version, its bundled Traefik chart version, and the exact `HelmChartConfig` values for Gateway API + HTTP→HTTPS redirect.
- cert-manager with bundled Traefik (Gateway HTTP-01 solver) and the pinned versions of cert-manager and CloudNativePG + the Barman Cloud plugin.
- A CNPG PG16 image with pgvector.
- local-path provisioner behaviour: volume location, `WaitForFirstConsumer`, no volume expansion. Size the PVCs with that in mind.
- ServiceLB + host firewall interaction (hostPort/iptables vs `ufw`), and the OVH network firewall options for the VPS.
- SOPS in CI: plain `sops -d` pipe vs a kustomize plugin (ksops); choose the simplest that keeps plaintext off disk.
- Keycloak 26.x: env placeholders in realm import; `start --optimized` build options.
- Spring Boot 4: probe groups and `server.forward-headers-strategy` behind Traefik.
- The final VPS price for the chosen term (and renewal price) at checkout.

## 5. Cost check (ovhcloud.com, 2026-09-27, excl. VAT)
| Item | € / month |
|---|---|
| VPS-2: 4 vCores / 8 GB / 75 GB NVMe (anti-DDoS, IPv4, daily 24 h backup included) | from 7.21 (12-month upfront listing; confirm the no-commitment and renewal price) |
| Object Storage (backups, a few GB) | < 0.10 |
| Domain (.be, yearly spread) | ≈ 1 |
| **Total** | **≈ 8.50**, budget **≤ 13** in case the monthly or renewal rate is higher |

*For comparison, the rejected MKS setup came to ≈ €42–47/mo (see research.md §2).*
