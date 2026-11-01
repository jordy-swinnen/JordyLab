# Feature Draft: Presence automation prototype with Home Assistant (spike)

**Status**: Draft, time-boxed spike, direction agreed (prototype first, JordyLab fix second)
**Target path**: `specs/_drafts/presence-ha-prototype/spec.md`
**Decides the fate of**: spec 010 (Eufy presence, geofence auto-arm, 0/68 tasks). Its outcome also feeds `pwa-replaces-capacitor`.

> Written without repo access. Phone model, home hardware and the current Eufy setup are taken from earlier notes and need confirming where marked.

## Problem

Spec 010 plans to arm and disarm the Eufy HomeBase 2 from JordyLab based on the phone's location, using the Capacitor app. Geofencing from a Capacitor app is the one thing a PWA cannot replace, so it is the main reason Capacitor stays. Before building 68 tasks, check whether the unreliable part is the app at all. Aggressive OEM background killing affects any app, including Home Assistant's companion app and a Capacitor geofence. DontKillMyApp lists OnePlus among the worst offenders and reports exemptions being reverted at random.

## Steps (in order, stop when a step works)

**Step 0: no code, one week.** On the phone (OnePlus, confirm): exempt the relevant apps from battery optimization, allow auto-launch, lock them in the recent apps list, then re-check the settings after a few days because they may revert. Observe whether existing location-based behavior (for example the Eufy app's own geofence) becomes reliable.

**Step 1: Home Assistant prototype, two weeks.**
- Run Home Assistant on an always-on machine at home (see Open question 1).
- Add the community Eufy Security integration (`fuatakgun/eufy_security`) and the HA companion app on the phone with a home zone.
- Two automations: leaving the zone arms the HomeBase (away), arriving disarms it.
- Log every real departure and arrival with timestamp and outcome.

**Step 2: JordyLab's own implementation (spec 010)**, only if steps 0 and 1 both fail.

## Requirements for step 1

- **FR-001**: Arm and disarm from HA reach the HomeBase 2 on current firmware. The integration's 2022 issue thread says arm away, arm home and disarm worked through HA alarm services, so re-test rather than trust it.
- **FR-002**: "Disarm" from HA puts the HomeBase in the mode you expect. The same thread discusses a difference between a disarmed state and an "off" mode, so check which one you get and whether that is what you want.
- **FR-003**: Disarming on arrival requires two signals (inside the zone and connected to the home Wi-Fi, or similar), arming on departure only one. Disarming is the security-relevant direction.
- **FR-004**: The phone can reach HA when away (Tailscale on the phone, or a hosted HA remote option), and the choice is recorded with its battery cost.
- **FR-005**: A break-glass path exists to arm or disarm manually when the automation misbehaves (the Eufy app still works independently).

## Success criteria (proposed, adjust before starting)

- **SC-001**: Over the two weeks, at least 19 of 20 logged transitions are correct within 3 minutes.
- **SC-002**: No false disarm happens while nobody is home.
- **SC-003**: Zero manual interventions needed in the final week.

## Decision gate

| Outcome | Result |
|---|---|
| Step 0 or step 1 meets the criteria | Spec 010 is cancelled or reduced to an optional status display. Go ahead with `pwa-replaces-capacitor`. |
| Both fail | Spec 010 proceeds, and Capacitor stays at least until it ships. If the native fix also fails, go to the PWA anyway and keep HA as a fallback. |

## Out of scope

Building anything in JordyLab during the spike, other smart-home integrations, voice assistants.

## Open questions

1. Where does HA run? The home machines (main desktop, JordyBox, laptop) are not always on, and the home mini-PC idea was dropped. Needs a small always-on device, which is a purchase decision.
2. Does the integration need a second Eufy account to avoid session conflicts with the phone's Eufy app? Check the integration's README.
3. Is the Eufy cloud login in the integration still working with current Eufy authentication (captcha or 2FA changes)?
4. Phone model and Android version: confirm before step 0.
