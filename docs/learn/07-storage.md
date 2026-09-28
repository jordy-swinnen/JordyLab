# 7. PVC, StorageClass, local-path

## The problem containers have with storage

A container's own filesystem is ephemeral — it's discarded the moment the container restarts.
Game artwork, uploaded APKs, and (as you'll see in Chapter 8) the database all need to survive a
restart, so they need to live outside the container.

## StorageClass

A `StorageClass` is a *policy* for how a volume gets provisioned when something asks for one. k3s
bundles exactly one, `local-path`, which — as the name says — provisions storage as a plain
directory on the node's own disk (`/var/lib/rancher/k3s/storage/` on the VPS's NVMe). There's no
`StorageClass` resource you need to write yourself; it already exists once k3s is installed.

## PersistentVolumeClaim (PVC)

A **PVC** is a request: "give me N gigabytes from this StorageClass." `deploy/k8s/base/artwork-pvc.yaml`
declares two — `gamecatalog-artwork` (5Gi) and `mobile-releases` (2Gi) — and `deploy/k8s/base/ntfy.yaml`
declares a third for ntfy's cache. `deploy/k8s/base/backend.yaml`'s Deployment mounts the first two
via `volumes` + `volumeMounts`, at the same filesystem paths (`/var/jordylab/artwork`,
`/var/jordylab/mobile-releases`) the application already expects locally — no application code
change was needed for this.

## The consequence of "local"

`local-path` ties a volume to **one specific node's disk**. On a single-node cluster this doesn't
matter for scheduling (there's only one node to schedule to anyway) — but it does matter for
disaster recovery: if the VPS's disk is lost, these volumes are lost with it. There is no
automatic off-site copy. This is precisely why the database (which also sits on `local-path`,
Chapter 8) needs its own, independent backup mechanism to Object Storage — local-path storage
alone is not durable against a whole-VPS failure. Game artwork and APKs, by contrast, are treated
as re-derivable-or-acceptable-loss in that scenario (spec.md's edge case: "the VPS disk or host is
lost... recovery uses the rebuild runbook").

## Hands-on

```
kubectl get storageclass
kubectl -n jordylab get pvc
kubectl -n jordylab describe pvc gamecatalog-artwork
```
On the VPS itself (once bootstrapped): `du -sh /var/lib/rancher/k3s/storage/*` — see
`docs/runbook.md` §18 for the same command as a disk-cleanup check.
