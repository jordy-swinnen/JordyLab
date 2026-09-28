# Learning Kubernetes and Podman on JordyLab

A guide for someone who knows Kubernetes basics and is new to Podman (spec.md US6), explaining
every concept this deployment uses by pointing at the real JordyLab file that uses it — not a
generic tutorial.

## Chapters

1. [Containers & Images](01-containers-and-images.md) — Containerfiles, multi-stage builds, `linux/amd64`
2. [Podman](02-podman.md) — local dev engine, `podman kube play` as a learning bridge, rootless containers, macOS vs native
3. [k3s and What It Bundles](03-k3s-and-bundled-components.md) — containerd, Traefik, CoreDNS, ServiceLB, local-path, what MKS would have hidden from you
4. [Pods, Deployments, Services](04-workloads.md)
5. [Gateway API and TLS](05-gateway-api-and-tls.md) — Gateway/HTTPRoute, cert-manager, Let's Encrypt
6. [ConfigMap, Secret, SOPS + age](06-config-and-secrets.md)
7. [PVC, StorageClass, local-path](07-storage.md)
8. [Operators, CRDs, and Helm](08-operators-and-crds.md) — CloudNativePG, the Barman Cloud Plugin, `HelmChartConfig`
9. [Probes, Resources, RBAC](09-probes-resources-rbac.md)
10. [CI/CD](10-cicd.md) — the build and deploy workflows

## Hands-on exercises (do these once the cluster exists)

- [ ] Build an image locally: Chapter 1's `docker build` command against `deploy/containers/backend/Containerfile`.
- [ ] Sandbox a manifest: Chapter 2's `podman kube play deploy/k8s/base/backend.yaml`.
- [ ] Port-forward to the Keycloak admin console (never publicly routed — Chapter 5):
      `kubectl -n jordylab port-forward deploy/keycloak 8080:8080`, then open `http://localhost:8080/auth/admin`.
- [ ] Read pod logs: Chapter 4/9's `kubectl -n jordylab logs deploy/backend -f`.
- [ ] Rotate a SOPS secret: Chapter 6's `sops` edit → commit → deploy → restart sequence (`docs/runbook.md` §13).
- [ ] Perform a rollback on prod: Chapter 10's `kubectl rollout undo` (`docs/runbook.md` §11).

Once every box above is checked, you should be able to do everything in `docs/runbook.md` without
looking anything up — that's SC-006.
