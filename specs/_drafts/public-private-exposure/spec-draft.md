# Feature Draft: Public and private exposure per app

**Status**: Draft, proposed (not yet agreed; Jordy has only confirmed the per-app split)
**Target path**: `specs/_drafts/public-private-exposure/spec.md`
**Relates to**: spec 008 T074 (public port-scan security review), `compose-deployment`, `auth-without-keycloak`

> Written without repo access. Which modules non-admin users actually use is not in the notes I have, so the audience table is a placeholder to fill in.

## Problem

Everything reachable on `https://jordylab.be` is protected only by login and role checks, including modules that only the owner uses and that can have real-world effects (finance data, health data, home security, trading). One auth or authorization bug exposes all of it to the internet. The infrastructure already has a private network (Tailscale, with the k3s API on `:6443` reachable only there), so sensitive surfaces can be taken off the public internet entirely.

## Requirements

**Phase 1: public allow-list (cheap, no new hostnames)**

- **FR-001**: The public route forwards only an explicit allow-list of path prefixes (frontend assets, auth endpoints, and the endpoints non-admin users and the scanner need). Everything else returns 404 from the proxy, never reaching the backend.
- **FR-002**: An audience table lists every module and endpoint group as public, authenticated-public or private, and the allow-list is derived from it.
- **FR-003**: Keycloak's admin console is not reachable on the public hostname. Check this today regardless of the rest of this draft, since `/auth` is public.
- **FR-004**: The ntfy server's public paths are reviewed (what is exposed and with what access control).

**Phase 2: private hostname over the tailnet**

- **FR-005**: Private modules (candidates: `fna`, `garmin`, `trading`, `settings` admin features) are served on a second hostname that resolves and listens only on the tailnet.
- **FR-006**: The application still authenticates users on the private hostname (defense in depth), and role checks stay as they are.
- **FR-007**: Clients used outside the home (phone, the Mac) have Tailscale installed. The usability cost of this is accepted explicitly in the audience table.
- **FR-008**: The scanner's ingest endpoint is classified: public with token auth (today's design, push-based) or tailnet-only (requires Tailscale on JordyBox and the Mac).

## Success criteria

- **SC-001**: An external port and path scan of `jordylab.be` finds only allow-listed paths, and a request to a private path returns 404 from the proxy.
- **SC-002**: With the phone off the tailnet, private features are unreachable and public features are unaffected.
- **SC-003**: The new check is part of CI or the post-deploy smoke test, so the allow-list cannot drift silently.

## Out of scope

Replacing application-level authorization, a WAF, rate limiting products, moving modules to separate deployments.

## Open questions

1. Which modules do non-admin users (friends, guests) actually use? If only `gamecatalog`, the split is clean; if they also use `fna`, Phase 2 is smaller than it looks.
2. How is the private listener implemented? On k3s: Traefik entrypoint bound to the tailnet address, or the Tailscale Kubernetes operator. On Compose: `tailscale serve` or a second Caddy site bound to the tailnet interface. This depends on whether `compose-deployment` happens first.
3. Should the private hostname trust Tailscale identity headers (no login screen) or keep passkey login? Passkey login is one auth path and the safer default.
4. Is Tailscale on the phone acceptable battery-wise, and does it interact with the presence work in `presence-ha-prototype`?

## Facts to verify in the repo

- Current HTTPRoute definitions in `deploy/k8s/` and what they expose.
- Whether `/auth/admin` and ntfy admin paths are blocked publicly today.
- Which endpoints the scanner calls and from which networks.
