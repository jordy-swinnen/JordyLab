# Presence API Contract

Base path: `/api/presence`

All endpoints require:

- A valid Keycloak JWT with the `admin` role.
- For event/disarm endpoints, the request must also be signed by the registered device's ARM or DISARM key.

---

## Register a device

`POST /api/presence/devices`

Registers a new phone. Requires an admin JWT and a fingerprint confirmation on the phone.

### Request

```json
{
  "name": "OnePlus 12",
  "armKeyPublic": "base64(ECDSA P-256 SPKI)",
  "disarmKeyPublic": "base64(ECDSA P-256 SPKI)"
}
```

### Response 201

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "name": "OnePlus 12",
  "registeredAt": "2026-09-29T12:00:00Z"
}
```

### Errors

- `403` — caller is not an admin.

---

## Revoke a device

`DELETE /api/presence/devices/{id}`

Revokes a registered device. Available from the web Settings as well as the app.

### Response 204

No body.

### Errors

- `403` — caller is not an admin.
- `404` — device not found.

---

## Submit a presence event

`POST /api/presence/events`

Sent by the phone when a geofence or Wi-Fi event occurs. Signed with the device's ARM key.

### Request

```json
{
  "deviceId": "550e8400-e29b-41d4-a716-446655440000",
  "type": "GEOFENCE_EXIT",
  "occurredAt": "2026-09-29T12:05:00Z",
  "nonce": "b64…",
  "signature": "b64(ECDSA-P256(SHA-256(deviceId|type|occurredAt|nonce)))"
}
```

### Response 202

```json
{
  "accepted": true,
  "presenceState": "LEAVING"
}
```

### Errors

- `400` — malformed request.
- `401` — invalid signature or expired nonce/timestamp.
- `403` — unknown or revoked device, or caller lacks admin role.
- `409` — replayed nonce.

---

## Request disarm

`POST /api/presence/disarm`

Sent after the user taps the "Disarm Eufy?" notification and passes fingerprint. Signed with the device's DISARM key.

### Request

```json
{
  "deviceId": "550e8400-e29b-41d4-a716-446655440000",
  "requestedMode": "HOME",
  "timestamp": "2026-09-29T12:06:00Z",
  "nonce": "b64…",
  "signature": "b64(ECDSA-P256(SHA-256(deviceId|requestedMode|timestamp|nonce)))"
}
```

### Response 200

```json
{
  "success": true,
  "mode": "HOME"
}
```

### Errors

- `400` — malformed request.
- `401` — invalid signature, stale timestamp (> 60 s), or expired nonce.
- `403` — unknown/revoked device, non-admin caller, or DISARM key verification failed.
- `409` — replayed nonce.
- `422` — disarm request rejected because the real Eufy mode or presence state does not allow it.

---

## Get status

`GET /api/presence/status`

Returns the real current Eufy mode, JordyLab's presence state, and recent events/mode changes.

### Response 200

```json
{
  "eufyMode": "AWAY",
  "presenceState": "AWAY",
  "lastEvents": [
    {
      "type": "GEOFENCE_EXIT",
      "occurredAt": "2026-09-29T12:05:00Z",
      "receivedAt": "2026-09-29T12:05:03Z"
    }
  ],
  "lastModeChanges": [
    {
      "requestedMode": "AWAY",
      "trigger": "AUTO_LEAVE",
      "result": "SUCCESS",
      "requestedAt": "2026-09-29T12:08:00Z"
    }
  ]
}
```

### Errors

- `403` — caller is not an admin.
