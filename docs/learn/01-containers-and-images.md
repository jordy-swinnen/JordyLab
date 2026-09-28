# 1. Containers & Images

A **container image** is a filesystem snapshot plus metadata (entrypoint, exposed ports, env
defaults) that a container runtime can start as a running **container**. A **Containerfile**
(Docker calls it a `Dockerfile` — the name is interchangeable; Podman and Docker both read either
name) is the recipe that builds one, instruction by instruction, each producing a cached layer.

## JordyLab's three images

- `deploy/containers/backend/Containerfile` — a **multi-stage** build: an `eclipse-temurin:25-jdk`
  stage runs `./gradlew bootJar`, then only the resulting jar is copied into a slimmer
  `eclipse-temurin:25-jre` runtime stage. The JDK (compiler, build tools) never ships in the image
  that actually runs in prod — only the JRE (runtime) does.
- `deploy/containers/frontend/Containerfile` — builds the Angular app with Bun and Nx
  (`nx build jordylab --configuration=production`), then copies the static output into an
  `nginx-unprivileged` runtime stage. The Node/Bun toolchain never ships either.
- `deploy/containers/keycloak/Containerfile` — a different kind of multi-stage build: both stages
  use the *same* Keycloak base image, but the first stage runs `kc.sh build` (which bakes
  configuration decisions — which database, which features — into an optimized image), and the
  final stage just copies that pre-built `/opt/keycloak/` directory.

## Why multi-stage matters

Every `RUN`/`COPY` instruction adds a layer, and every layer that lands in your final image is
something an attacker (or just curiosity) can inspect in the pulled image, and something that adds
to the image's size and CI's build/push time. Multi-stage builds let the build stage be as messy
and heavyweight as it needs to be, while the runtime stage stays minimal.

## Build context

`docker build -f deploy/containers/backend/Containerfile .` — the trailing `.` is the **build
context**: everything the `COPY` instructions are allowed to reference. JordyLab's Containerfiles
live in `deploy/containers/*/` but build from the **repo root** as context, so they can `COPY
jordylab-be/...` — this is why `.dockerignore` (repo root) matters: it keeps `node_modules/`,
`.git/`, and other subprojects' build artifacts out of the context sent to the Docker daemon.

## `linux/amd64` only

Every image here is built by CI for `linux/amd64` — the VPS's architecture. If you build one
locally on Apple Silicon (arm64) without `--platform linux/amd64`, it will not run on the VPS.
CI is the only path to a real deploy (spec.md's edge case: "an image built on the MacBook is
pushed by mistake").

## Hands-on

Build the backend image locally and inspect its size vs. the build stage:
```
docker build -f deploy/containers/backend/Containerfile -t jordylab-backend:local .
docker images jordylab-backend
```
