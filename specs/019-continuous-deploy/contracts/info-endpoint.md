# Contract: Backend info endpoint (commit)

**Endpoint**: `GET /actuator/info` on the backend container (port 8080, inside the cluster)

## Response

```json
{
  "commit": "9f3c2a1e0b7d4c6a8e5f1b2c3d4e5f6071829304"
}
```

- `commit` is the full 40-hex sha of the commit the image was built from (FR-017).
- It is `"unknown"` for images built before this feature, or when the build argument was not passed.
- No other `info.*` key is defined. The `build` and `git` contributors stay at their defaults and add nothing here.

## Source of the value

| Layer | Value |
|-------|-------|
| `Containerfile` (backend, runtime stage) | `ARG JORDYLAB_GIT_COMMIT=unknown`, `ENV JORDYLAB_GIT_COMMIT=${JORDYLAB_GIT_COMMIT}` |
| `build.yml`, backend matrix entry | `build-args: JORDYLAB_GIT_COMMIT=${{ github.sha }}` |
| `application.yaml` | `info.commit: ${JORDYLAB_GIT_COMMIT:unknown}`; `management.info.env.enabled: true` |

## Exposure

- `management.endpoints.web.exposure.include` already contains `info` (base and prod).
- `SecurityConfig` permits `/actuator/info` without authentication.
- The gateway does **not** route `/actuator` to the backend. The endpoint is reachable from inside the cluster only, which
  is how the deploy reads it (`kubectl port-forward deploy/backend 18080:8080`, then `GET localhost:18080/actuator/info`).

## Verification

- Spring test (see research D15): with `JORDYLAB_GIT_COMMIT` set, `/actuator/info` returns that value under `commit`.
- Deploy check (quickstart Q3): after a deploy, `commit` equals the deploy run's target sha (SC-006).
