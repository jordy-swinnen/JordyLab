# Quickstart: Validate Eufy Presence End-to-End

This guide proves the spike (Phase 0) and validates the full feature once built.

## Prerequisites

- A Eufy HomeBase 2 (T8010) with at least one camera paired.
- The JordyLab Android app from spec 007 installed on a OnePlus 12.
- Access to the production k3s cluster (spec 008) with `kubectl` and SOPS age key.
- Podman or Docker locally for the first gateway dry-run.

## 1. Try the free mitigation first

Before creating any accounts or running the gateway, apply the OnePlus battery settings to the Eufy app:

1. Battery optimisation → "Not optimised".
2. Lock the app in Recents.
3. Disable Advanced/Deep optimisation and Sleep standby optimisation.
4. Allow location "All the time" and background activity.

Use Eufy's own geofencing for a day or two. If it becomes reliable, this feature may not be needed.

## 2. Create the Eufy guest account

**Stop and report before this step if you want a final go/no-go check.**

1. Create a new Eufy account (e.g. `jordylab-eufy@yourdomain.com`).
2. From your primary Eufy app, share the home with this guest account.
3. Do **not** sign the guest account into your everyday phone — simultaneous sessions interfere.

## 3. Run the gateway locally (simulated)

```bash
cd eufy-mega-security/eufy_event_gateway
npm ci
npm run build
EUFY_GATEWAY_PROVIDER=simulated \
  EUFY_GATEWAY_API_TOKEN='use-a-random-secret-of-at-least-32-characters' \
  EUFY_GATEWAY_HOST='0.0.0.0' \
  npm start
```

Verify `GET http://localhost:PORT/health` returns process healthy.

## 4. Run the gateway locally with the real guest account

```bash
EUFY_USERNAME='jordylab-eufy@yourdomain.com' \
EUFY_PASSWORD='<redacted>' \
EUFY_COUNTRY='BE' \
EUFY_GATEWAY_API_TOKEN='use-a-random-secret-of-at-least-32-characters' \
EUFY_GATEWAY_HOST='0.0.0.0' \
npm start
```

- Open the gateway's Web UI when prompted for CAPTCHA/email code.
- Find the guard-mode read/write HTTP endpoint by inspecting `src/server.ts` and
  `custom_components/eufy_event_gateway/client.py`.
- Confirm the HomeBase 2 appears in the inventory and that guard mode can be read.
- Script a few `Away → Home → Away` switches and verify each shows in the Eufy app within 30 seconds.

## 5. Deploy the gateway to the k3s cluster

1. Build the gateway Containerfile (`deploy/containers/eufy-gateway/Containerfile`).
2. Add `EUFY_USERNAME`, `EUFY_PASSWORD`, `EUFY_COUNTRY`, and `EUFY_GATEWAY_API_TOKEN` to `secrets.sops.yaml` (spec 008
   SOPS flow).
3. `kubectl apply -f deploy/k8s/base/eufy-gateway.yaml` (Deployment + ClusterIP Service + PVC + NetworkPolicy).
4. Port-forward the pod once to complete the initial CAPTCHA/email challenge via the Web UI.
5. Confirm the session PVC survives a pod restart without re-challenge.

## 6. Verify cluster-internal isolation

From a non-backend pod in the same namespace:

```bash
kubectl run probe --rm -it --image=alpine/curl -- /bin/sh
# Should fail/timeout:
curl -H 'Authorization: Bearer <token>' http://eufy-gateway/api/...
```

From the backend pod, the same call should succeed.

## 7. Run the 7-day trial

Use a small script to switch modes at least twice a day through the JordyLab backend → gateway path.

- Record each switch, firmware version, gateway version, and cluster state.
- Pass criterion: ≥ 95% successful switches.
- If the first run is below 95%, one retry round with an adjusted setup is allowed.
- If the retry also fails: stop and ship only the phone-settings checklist.

## 8. Validate the full feature (post-implementation)

1. Register the OnePlus 12 from the JordyLab app.
2. Leave home: verify "Eufy armed (Away)" notification after the debounce.
3. Return home: verify "Disarm Eufy?" notification; disarm with fingerprint.
4. Lose mobile data on leave: verify the event queues and a late-arm notification arrives.
5. Reboot the phone: verify geofences re-register and the next leave/return still works.
6. Revoke the device from web Settings: verify the phone's next event is rejected with 403.
7. Run the two-week field test against SC-001 and SC-002 before disabling Eufy's own geofencing.
