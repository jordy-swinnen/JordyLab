# 9. Probes, Resources, RBAC

## Readiness vs. liveness probes

Two different questions Kubernetes asks a container, both present on
`deploy/k8s/base/backend.yaml`:

- **Readiness** (`/actuator/health/readiness`): "should traffic be sent to you *right now*?" A Pod
  that fails readiness is removed from its Service's endpoints — no traffic reaches it — but is
  **not** restarted. This is what lets a slow-starting backend (JVM warmup, DB connection pool
  filling) come up without receiving requests it can't yet handle.
- **Liveness** (`/actuator/health/liveness`): "are you fundamentally broken and need a restart?" A
  failed liveness probe kills and restarts the container. Spring Boot's actuator exposes both as
  separate **probe groups** (enabled in `application-prod.yaml`'s `management.endpoint.health.probes`
  block) — distinct from the single combined `/actuator/health` endpoint you'd hit for a manual
  check.

Keycloak (`deploy/k8s/base/keycloak.yaml`) exposes the equivalent on its separate **management
port** (`9000`, via `KC_HEALTH_ENABLED=true`) at `/health/ready` and `/health/live` — deliberately
never routed publicly (Chapter 5, FR-009).

## Resource requests and limits

Every container in this repo declares both:
- **requests** — what the scheduler reserves for this Pod; if the node doesn't have this much
  free, the Pod won't be scheduled at all.
- **limits** — the hard ceiling; exceeding the memory limit gets the container killed (OOMKilled),
  exceeding the CPU limit just throttles it.

On a single 8GB-RAM node with no second node to fail over to, these numbers are the difference
between "the JVM and Keycloak briefly overlap during a rolling deploy without incident" and "the
node runs out of memory and the kubelet starts evicting things unpredictably" — see the drafts'
research.md §3 for the sizing rationale behind choosing an 8GB VPS-2 over a 4GB VPS-1 in the first
place.

## RBAC: Role, RoleBinding, ServiceAccount

`deploy/k8s/cluster/ci-deploy-rbac.yaml` is the credential CI actually uses to deploy — three
pieces:
- **ServiceAccount** (`ci-deploy`) — an identity, not a person, that a token can be minted for.
- **Role** — a *namespaced* set of permissions (verbs × resource types), scoped to `jordylab` only.
  Compare to a **ClusterRole**, which would grant cluster-wide permissions — deliberately not used
  here (FR-007).
- **RoleBinding** — connects the two: "this ServiceAccount gets this Role's permissions."

The resulting token (minted with `kubectl create token`, `docs/runbook.md` §9) is what
`deploy-prod.yml` authenticates with — never `/etc/rancher/k3s/k3s.yaml`, the cluster-admin
kubeconfig that can do anything, anywhere in the cluster.

## Hands-on

```
kubectl -n jordylab get role,rolebinding,serviceaccount
kubectl auth can-i delete namespaces --as=system:serviceaccount:jordylab:ci-deploy
```
The second command should print `no` — the whole point of scoping the Role to one namespace.
