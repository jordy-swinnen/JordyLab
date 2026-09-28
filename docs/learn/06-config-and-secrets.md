# 6. ConfigMap, Secret, SOPS + age

## ConfigMap

Non-sensitive configuration that varies by environment: `deploy/k8s/overlays/prod/kustomization.yaml`'s
`configMapGenerator` produces `backend-config` and `keycloak-config` from plain key-value
pairs (the real domain, CORS origins, Keycloak's hostname settings). `deploy/k8s/base/backend.yaml`
consumes it with `envFrom: - configMapRef: {name: backend-config}` — every key becomes an
environment variable inside the container, with no code change needed if a new key is added.

## Secret

Structurally identical to a ConfigMap — same shape, same `envFrom`/`valueFrom` consumption pattern
— but intended for sensitive values, and base64-encoded (not encrypted!) at rest inside
Kubernetes' own datastore. Base64 is an *encoding*, not encryption — a `kubectl get secret -o yaml`
against a real cluster would show you the value trivially decoded. This is exactly why the next
piece matters:

## SOPS + age

The Secret never exists in **git** as anything but ciphertext. `.sops.yaml` (repo root) names an
**age** public key and a rule: encrypt every file matching `*secrets*.yaml`, but only its `data`/
`stringData` *values*, leaving keys in the clear (`encrypted_regex: ^(data|stringData)$`). That's
why a `git diff` on `deploy/k8s/overlays/prod/secrets.sops.yaml` after a rotation is still
meaningful — you can see *which* key changed, just not the old or new value.

**age** is the encryption primitive: a small, modern alternative to GPG, with just two things to
manage — a public key (safe to commit, it can only encrypt) and a private key (never committed,
required to decrypt). `docs/runbook.md` §7 covers generating one; §14 covers what happens if you
lose it (nothing recoverable — this is why it lives in two places: your password manager and a
GitHub Actions secret, never a third).

**SOPS** is the tool that applies that age key to a Kubernetes-shaped YAML file: `sops
secrets.sops.yaml` decrypts to a temp file, opens your editor, and re-encrypts on save. In CI
(`deploy-prod.yml`), the same decrypt happens non-interactively, using the private key from a
GitHub Actions secret, entirely inside the ephemeral runner — the plaintext never touches disk in
your own checkout, and never gets committed.

## The realm file's own placeholders

`deploy/keycloak/realm-prod.json` uses a related but separate mechanism: Keycloak's own
`${env.VAR_NAME}` substitution, resolved at Keycloak's own startup — not by SOPS. See
`deploy/keycloak/README.md` for why that needs its own allowlist configuration.

## Hands-on

```
cat deploy/k8s/overlays/prod/secrets.sops.yaml   # once real — ciphertext only, key names readable
sops deploy/k8s/overlays/prod/secrets.sops.yaml  # rotate one value (needs a real age key first)
```
