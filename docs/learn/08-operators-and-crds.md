# 8. Operators, CRDs, and Helm

## Custom Resource Definition (CRD)

Kubernetes ships with built-in resource types (`Deployment`, `Service`, `PersistentVolumeClaim`...).
A **CRD** teaches the API server a *new* type. `deploy/k8s/cluster/cnpg-cluster.yaml`'s `kind:
Cluster` isn't a built-in Kubernetes concept — it's a CRD that the CloudNativePG (CNPG) operator
installs, meaning `apiVersion: postgresql.cnpg.io/v1` is CNPG's own API, layered on top of
Kubernetes' own.

## Operator

A CRD alone is just a schema — something has to *act* on instances of it. An **operator** is a
controller (itself just a Pod, running in the cluster) that watches for `Cluster` objects and
does whatever "reconcile the real world to match this spec" means for that domain: for CNPG, that's
provisioning a PostgreSQL `StatefulSet`, managing failover (moot with 1 instance here, but the same
machinery), running backups, and exposing a `cnpg-cluster-rw` Service that `deploy/k8s/base/backend.yaml`
and `keycloak.yaml` both connect to. This is the same pattern cert-manager uses for `Certificate`/
`ClusterIssuer` (Chapter 5) — an operator watching its own CRDs.

## The Barman Cloud Plugin — a CRD you install *separately* from the operator it extends

`deploy/k8s/cluster/ovh-object-storage.yaml`'s `kind: ObjectStore` comes from a second, separately
installed piece: the **Barman Cloud Plugin** (`barmancloud.cnpg.io`), not CNPG's core operator.
CNPG deliberately moved backup-to-S3 functionality out of its own core into this plugin
(research.md §6) — the CNPG operator and the Barman Cloud Plugin are two separate controllers
cooperating through the `Cluster`'s `spec.plugins` field, each with its own CRDs.

## Helm

Where do operators themselves come from? Usually a **Helm chart** — a packaged, parameterised
bundle of Kubernetes manifests. `docs/runbook.md` §5 installs both CNPG and cert-manager via
`helm install`, each pinned to an exact chart version (never "latest"). `deploy/k8s/cluster/barman-cloud-plugin-values.yaml`
is a Helm **values file** — the parameters passed to a chart at install time — as opposed to a
plain Kubernetes manifest you'd `kubectl apply`.

## `HelmChartConfig` — a k3s-specific twist

`deploy/host/traefik-helmchartconfig.yaml` isn't a Helm values file used with the `helm` CLI at
all — it's a k3s-specific CRD (`helm.cattle.io/v1`) that tells k3s's *own* internal
Helm-controller (which manages k3s's bundled components, including Traefik) to re-run that
component's Helm install with your extra values merged in. Same underlying idea as a values file,
different delivery mechanism, because Traefik here isn't something you installed with `helm
install` yourself — k3s did, at boot.

## Hands-on

```
kubectl get crds | grep -E "cnpg|barmancloud|cert-manager|gateway"
kubectl get cluster -n jordylab cnpg-cluster -o yaml
kubectl get pods -n cnpg-system
```
