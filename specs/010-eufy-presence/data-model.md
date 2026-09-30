# Data Model: Eufy Presence

## Entities

### RegisteredDevice

A phone registered by the admin for presence/arm/disarm operations.

| Field           | Type            | Notes                                                 |
|-----------------|-----------------|-------------------------------------------------------|
| id              | UUID            | Primary key                                           |
| name            | String          | User-readable label (e.g. "OnePlus 12")               |
| armKeyPublic    | String / byte[] | ECDSA P-256 public key for arm/presence events        |
| disarmKeyPublic | String / byte[] | ECDSA P-256 public key for disarm requests            |
| registeredAt    | Instant         | Creation timestamp                                    |
| revokedAt       | Instant         | Null until revoked; revoked devices reject all events |

**Rules**:

- One admin may register one or more devices.
- Events from unknown or revoked devices are rejected with 403.
- Revocation is immediate and also exposed in web Settings.

### PresenceEvent

A discrete presence signal received from a registered device.

| Field      | Type    | Notes                                                                      |
|------------|---------|----------------------------------------------------------------------------|
| id         | UUID    | Primary key                                                                |
| deviceId   | UUID    | → RegisteredDevice                                                         |
| type       | Enum    | `GEOFENCE_EXIT`, `GEOFENCE_ENTER`, `WIFI_LOST`, `WIFI_CONNECTED`, `MANUAL` |
| occurredAt | Instant | Timestamp reported by the phone                                            |
| receivedAt | Instant | Timestamp the backend received the event                                   |
| nonce      | String  | One-time nonce, part of the signed payload                                 |
| signature  | String  | ECDSA P-256 signature over `deviceId                                       |type|occurredAt|nonce` |

**Rules**:

- Signature must be valid and from the device's ARM key.
- Nonce must not have been used before (replay protection).
- `occurredAt` must be within a short freshness window (e.g. ≤ 60 s for manual, ≤ a few minutes for geofence background
  delivery).
- Location is stored only as these discrete events — no continuous tracks.

### ModeChange

A request to change the Eufy mode and its outcome.

| Field          | Type    | Notes                                       |
|----------------|---------|---------------------------------------------|
| id             | UUID    | Primary key                                 |
| requestedMode  | Enum    | `AWAY`, `HOME`                              |
| trigger        | Enum    | `AUTO_LEAVE`, `CONFIRMED_ARRIVAL`, `MANUAL` |
| requestedAt    | Instant | When the request was made                   |
| result         | Enum    | `SUCCESS`, `FAILURE`                        |
| eufyModeBefore | String  | Real mode read from Eufy before acting      |
| eufyModeAfter  | String  | Real mode read from Eufy after acting       |
| error          | String  | Error message or null                       |

**Rules**:

- Every request is audit-logged.
- The backend reads the real Eufy mode before acting and refuses to overwrite a manual Disarmed state with Away unless
  presence confirms the user has left.
- A notification is sent for every attempt with outcome.

### PresenceSettings

Admin-editable settings for the presence logic.

| Field           | Type             | Notes                           |
|-----------------|------------------|---------------------------------|
| id              | UUID / singleton | One row per admin/home          |
| homeLatitude    | Double           | Home location latitude          |
| homeLongitude   | Double           | Home location longitude         |
| radiusMeters    | Integer          | Geofence radius; default 535 m  |
| debounceMinutes | Integer          | Arm debounce; default 3 minutes |
| homeWifiSsid    | String           | Home Wi-Fi network name         |
| homeEufyMode    | String           | Default `Home`; adjustable      |
| awayEufyMode    | String           | Default `Away`; adjustable      |

**Rules**:

- All fields editable in the app's settings UI without redeploy.
- Changes take effect immediately for new events.

## State Machine

```text
HOME  --geofence_exit & !homeWifi-->  LEAVING (start debounce T)
LEAVING --geofence_enter | wifiConnected before T--> HOME   (nothing armed)
LEAVING --T elapsed & read Eufy mode--> set AWAY → AWAY    (notify result)
AWAY  --geofence_enter | wifiConnected--> ARRIVING          (phone raises local "Disarm?" notification)
ARRIVING --valid disarm signature--> set HOME → HOME
ARRIVING --no confirmation--> stays ARRIVING (Eufy stays Away)
MANUAL Arm (Away) from app/tile: any state → AWAY
MANUAL Home from app/tile (biometric): any state → HOME
```

**Wi-Fi rule**: `WIFI_CONNECTED` blocks or cancels a pending `LEAVING` debounce. `WIFI_LOST` alone never arms.

## Signatures

### Signed presence event (ARM key)

```json
{
  "deviceId": "8f3c…",
  "type": "GEOFENCE_EXIT",
  "occurredAt": "2026-10-03T08:14:22Z",
  "nonce": "b64…",
  "signature": "b64(ECDSA-P256(SHA-256(deviceId|type|occurredAt|nonce)))"
}
```

### Signed disarm request (DISARM key)

```json
{
  "deviceId": "8f3c…",
  "requestedMode": "HOME",
  "timestamp": "2026-10-03T08:14:22Z",
  "nonce": "b64…",
  "signature": "b64(ECDSA-P256(SHA-256(deviceId|requestedMode|timestamp|nonce)))"
}
```

**Rules**:

- DISARM signature must be from a key that requires biometric authentication per use.
- Timestamp must be ≤ 60 s old at receipt.
- Nonce must be unique.
