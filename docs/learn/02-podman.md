# 2. Podman

**Podman's actual role in this project is narrower than "container tool" suggests — read this
chapter before assuming it does more than it does.**

## What Podman IS here

1. **Your local dev container engine**, replacing Docker. `jordylab-be/compose.yaml` runs
   Postgres+pgvector and Keycloak via `podman compose up -d`. Testcontainers (used by the backend's
   test suite) points `DOCKER_HOST` at the Podman socket instead of Docker's. You also use it to
   build the three images locally from their Containerfiles (Chapter 1) before ever pushing to CI.
2. **A learning bridge to Kubernetes**, via two specific commands:
   - `podman generate kube` turns an already-running container or pod into Kubernetes YAML — a
     reverse-engineering tool for "what would this look like as a Pod spec?"
   - `podman kube play <file>.yaml` runs a Pod/Deployment-shaped YAML file *locally*, using Podman
     instead of a real cluster. This lets you sandbox a manifest — check it's syntactically sound,
     watch it start, poke at it — before ever running `kubectl apply` against the real k3s cluster.
     Try this against `deploy/k8s/base/backend.yaml`.

## What Podman is NOT

**It is not a cluster manager.** Once something is running in the k3s cluster, Podman plays no
part — the cluster is managed with `kubectl` (and Helm for add-ons, Chapter 8), and k3s runs
containers with **containerd** (Chapter 3), a completely different, purpose-built runtime for
orchestrated environments. `podman kube play`'s single-host simulation doesn't have real
Services, a real Gateway, or real networking between pods — it's a sandbox, not a preview of
production behavior.

## Platform differences

- **On CachyOS** (native Linux): Podman runs containers directly, rootless by default — no VM
  layer, no daemon running as root.
- **On macOS**: there's no native Linux container support, so `podman machine` runs a small Linux
  VM under the hood, and your Podman CLI talks to the engine inside that VM. This is also why a
  macOS-built image is `linux/arm64` (Apple Silicon) even though Podman "feels" native — the VM's
  architecture matches your Mac's, not the VPS's x86_64 (Chapter 1's `linux/amd64` note).

## Rootless containers

Both platforms run Podman **rootless** by default: the container's root user maps to your own
unprivileged user on the host, not real root. This is a meaningful security property Docker's
classic root-daemon model didn't have by default. It's also why `deploy/containers/frontend`'s
runtime stage uses `nginx-unprivileged` rather than plain `nginx` — the latter expects to bind
port 80 as root, which conflicts with rootless/non-root execution.

## Why prod images are built in CI, not on your laptop

Two independent reasons: (1) architecture — your Mac builds arm64, the VPS runs amd64 (Chapter 1);
(2) reproducibility — CI's environment is identical on every run, so "works on my machine" can't
silently diverge from what actually gets deployed.

## Hands-on

1. `podman machine start` (macOS only) or confirm `podman info` runs natively (CachyOS).
2. `podman compose up -d` in `jordylab-be/` — this is Postgres + Keycloak for local dev.
3. `podman kube play deploy/k8s/base/backend.yaml` — sandbox the backend's Deployment locally, then
   `podman kube down deploy/k8s/base/backend.yaml` to tear it down. (It won't fully start without
   real Secrets/ConfigMaps — that's expected; the point is exercising the workflow.)
