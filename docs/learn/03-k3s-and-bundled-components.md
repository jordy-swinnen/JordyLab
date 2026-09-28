# 3. k3s and What It Bundles

**k3s** is a single ~70MB binary that IS a conformant Kubernetes distribution — not a toy or a
subset with missing features, just packaged and defaulted for smaller, simpler deployments
(edge devices, single-node setups like this one). Choosing it over OVH's Managed Kubernetes (MKS)
was a cost decision (`research.md`'s carried-forward §2: ≈€8.50–13/mo vs ≈€40–55/mo) with a side
effect: you now own the node/OS layer that a managed service would have hidden from you.

## What's bundled (and replaces an MKS equivalent)

| Component | Role | MKS gave you this via |
|---|---|---|
| **containerd** | The actual container runtime — runs the images from Chapter 1. Podman plays no part here (Chapter 2). | Managed nodes, same runtime |
| **Traefik v3** | The bundled ingress/Gateway API implementation — see Chapter 5 | A separately Helm-installed Traefik |
| **CoreDNS** | In-cluster DNS — `backend.jordylab.svc.cluster.local` resolves because of this | Same, bundled either way |
| **ServiceLB (Klipper)** | Binds a `Service`'s ports as hostPorts on the **VPS's own public IP** — this is how 80/443 reach Traefik with **no cloud load balancer** | OVH's Public Cloud Load Balancer (a separately billed product) |
| **local-path-provisioner** | The default `StorageClass` — PVCs (Chapter 7) become directories on the VPS's own NVMe disk | OVH Block Storage (network-attached, survives node loss) |

## The datastore

A single k3s server's cluster state (every `Deployment`, `Secret`, etc. you `kubectl apply`) lives
in an embedded **SQLite** database at `/var/lib/rancher/k3s/server/db/`. `docs/runbook.md`'s
"k3s / OS upgrades" section notes this is optional to back up separately, since everything in the
cluster is re-creatable from git plus the SOPS files — except the database (Chapter 8/9), which
has its own, mandatory backup story.

## What you now own as host operator

Everything MKS's control plane used to handle for you: OS patching (`docs/runbook.md` §2, §19),
firewall rules (§2), k3s version upgrades (§19, a pinned `INSTALL_K3S_VERSION`, never an
auto-tracked "latest"), and host security in general (SSH hardening, never exposing 6443/10250/
8472-udp to the internet). This is genuinely more to learn than MKS would have required — and also
genuinely more useful to know.

## Hands-on

Once the cluster is bootstrapped (`docs/runbook.md` §3):
```
kubectl get pods -n kube-system
```
Identify which pod is Traefik, which is CoreDNS, which `svclb-*` pods are ServiceLB, and where
`local-path-provisioner` is running.
