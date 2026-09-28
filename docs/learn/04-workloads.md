# 4. Pods, Deployments, Services

## Pod

The smallest deployable unit — one or more containers that always run together on the same node,
sharing a network namespace (they can reach each other on `localhost`) and, optionally, volumes.
JordyLab's Pods are all single-container (`deploy/k8s/base/backend.yaml`,
`frontend.yaml`, `keycloak.yaml`, `ntfy.yaml`) — nothing here needs a sidecar.

## Deployment

You never create a Pod directly in this repo — you create a **Deployment**, which is a
"desired state" wrapper: "keep `replicas: 1` Pods matching this template running, always."
Look at `deploy/k8s/base/backend.yaml`: the `spec.replicas: 1` plus `strategy.type: Recreate`
means an update tears down the old Pod fully before starting the new one (rather than
`RollingUpdate`'s overlap) — a deliberate choice for a single-instance database-backed app where
running two versions against the same schema briefly isn't safe.

## Service

A Deployment's Pods get recreated (new IP each time) whenever they restart. A **Service** gives
them a stable name and IP that doesn't change: `backend.jordylab.svc.cluster.local`, or just
`backend` from another Pod in the same namespace. `deploy/k8s/base/backend.yaml`'s Service
`selector: {app: backend}` means it automatically load-balances (trivially, with 1 replica) across
every Pod carrying that label — this is how the Gateway (Chapter 5) reaches the backend without
ever knowing a Pod IP.

## Namespace

Everything in this repo lives in one Namespace, `jordylab` (`deploy/k8s/base/namespace.yaml`) — a
logical partition inside the cluster. The CI deploy credential's RBAC (Chapter 9) is scoped to
exactly this namespace, so a compromised CI token can't touch anything else on the cluster (there
isn't anything else on this cluster, but the principle holds if that ever changes).

## Hands-on

```
kubectl -n jordylab get deploy,svc,pods
kubectl -n jordylab describe deploy backend
```
Note the Deployment's `Events` section after a rollout — it shows the ReplicaSet transitions that
implement the `Recreate` strategy.
