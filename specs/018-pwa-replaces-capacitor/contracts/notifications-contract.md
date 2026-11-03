# Contract: Admin Push Notifications (ntfy, relocated)

Feature: `018-pwa-replaces-capacitor` · Date: 2026-11-02 · Spec: FR-009, FR-010, US3

## Component

`shared/rest/client/NtfyClient` (moved from `mobile/rest/client`; delivery logic unchanged), configured by `shared/config/NotificationProperties` (`jordylab.notifications.*`).

## API

```java
public void publish(String title, String body, String clickPath)
```

- `clickPath` is an in-app path (`/settings/users`, `/fna/briefing`); the client resolves `appBaseUrl + clickPath` into the `X-Click` header. A blank `app-base-url` behaves like an unconfigured client (log-and-skip).
- HTTP: `POST {base-url}/{topic}` with `Content-Type: text/plain`, `X-Title`, `X-Click`, optional `Authorization: Bearer <token>`.
- Semantics (unchanged from 007/011): no-op with a startup warning while `base-url`/`topic` blank (BUG-013 rule: never silent); every send logged by title; `RestClientException` logged as a warning and swallowed — **never** fails the sign-up or briefing flow that triggered it.
- Titles stay fixed strings; names/emails only ever in the body (logging rule).

## Call sites (direct, no events)

| Caller | Trigger | Title | Click path |
|---|---|---|---|
| `settings.PendingSignupWatcherService` | newly pending sign-up in its 5-min poll | `New JordyLab sign-up` | `/settings/users` |
| `fna.BriefingGeneratorService` | briefing persisted (scheduled or manual) | `Your monthly briefing is ready` | `/fna/briefing` |

Bodies keep today's wording (name + email for sign-ups; generation timestamp for briefings).

## Deleted with this contract

- `mobile.MobileNotificationListener`, `UserSignUpPending`, `BriefingReady`, `MobileProperties` (its `ntfy` block becomes `NotificationProperties`), and the `/mobile/open?screen=…` URL builder (click-throughs now go straight to web routes).
- `settings`' `revokeMobileOfflineConsent` + `KeycloakAdminClient.revokeConsent` (the only consumer was the native app's offline token).

## Boundaries

- `settings` and `fna` depend on `shared` only (allowed for every module); nothing depends on a `mobile` module because none exists. `ModularityTests` must pass unchanged in this shape.
- WireMock tests move with the client; each caller gets an `@ApplicationModuleTest` asserting the publish call and the swallow-on-failure behaviour.

## Config / deploy

| Env var | Where | Change |
|---|---|---|
| `NTFY_BASE_URL` / `NTFY_TOPIC` / `NTFY_TOKEN` | prod `backend-config` ConfigMap + `jordylab-secrets` | unchanged values, read by the new prefix |
| `JORDYLAB_APP_BASE_URL` | prod `backend-config` | new literal `https://jordylab.be` (replaces `MOBILE_PRODUCTION_DOMAIN`) |
| `MOBILE_*` literals + secret keys | prod overlay + SOPS | deleted (research D10) |
