# Talos deployment (ArgoCD)

The timesheet mailer runs as a **resident Deployment**: one always-on pod whose in-app
cron scheduler (`timesheet.schedule`, Quartz syntax) fires the job, with health and
Prometheus endpoints on port 8080. ArgoCD keeps the cluster in sync with this directory.

## Layout

| File | Kind | Notes |
| --- | --- | --- |
| `namespace.yaml` | Namespace | `timesheet-mailer` |
| `pvc.yaml` | PersistentVolumeClaim | template + outbox + dead-letter state, `local-path` |
| `deployment.yaml` | Deployment | resident profile, Gitea image, `Recreate` strategy |
| `service.yaml` | Service | ClusterIP for actuator endpoints |
| `secret.example.yaml` | Secret | reference only; the real secrets are created out-of-band |

## One-time prerequisites

Two secrets live only in the cluster, never in Git. Create both in the `timesheet-mailer`
namespace before (or right after) the first sync — the pod will not start without them:

```bash
# 1. Pull credential for the private Gitea registry.
kubectl -n timesheet-mailer create secret docker-registry gitea-regcred \
  --docker-server=gitea.home.net \
  --docker-username="$REGISTRY_USERNAME" \
  --docker-password="$REGISTRY_PASSWORD"

# 2. SMTP credentials. Copy secret.example.yaml to a private secret.yaml, fill it in.
cp secret.example.yaml secret.yaml   # then edit; keep it out of version control
kubectl -n timesheet-mailer apply -f secret.yaml
```

## Build and push the image

The Deployment pins an immutable, SHA-tagged image. Rebuild and push it whenever the app
changes, then update the tag in `deployment.yaml`:

```bash
SHA=$(git rev-parse --short HEAD)
docker build -t "gitea.home.net/daniel/camel-timesheet-mailer:$SHA" .
docker push "gitea.home.net/daniel/camel-timesheet-mailer:$SHA"
```

## Register with ArgoCD

The source is the public GitHub repo, so ArgoCD needs no repository credential:

```bash
kubectl apply -f deploy/argocd/application.yaml
kubectl -n argocd get application camel-timesheet-mailer -o wide
```

ArgoCD then creates the Namespace (via `CreateNamespace=true`), PVC, Deployment and
Service, and self-heals any drift. Because the image tag is pinned, a new build is rolled
out by changing the tag in `deployment.yaml` and pushing — not by re-tagging `latest`.

## Operating it

```bash
# Health and metrics
kubectl -n timesheet-mailer port-forward svc/camel-timesheet-mailer 8080:8080
curl -s localhost:8080/actuator/health
curl -s localhost:8080/actuator/prometheus | grep timesheet

# Logs
kubectl -n timesheet-mailer logs deploy/camel-timesheet-mailer -f

# Inspect produced workbooks / dead letters
kubectl -n timesheet-mailer exec deploy/camel-timesheet-mailer -- ls -R /app/data
```

## Firing a run on demand

The resident profile only fires on its cron schedule, which defaults to the first and
third Friday of the month. To exercise the whole path sooner without waiting, override the
schedule to fire shortly from now and let `Recreate` restart the pod (the trigger URI is
built at startup, so the env change only takes effect on a new pod):

```bash
kubectl -n timesheet-mailer set env deploy/camel-timesheet-mailer \
  TIMESHEET_SCHEDULE="0 * * ? * * *"   # every minute, Quartz 7-field form
kubectl -n timesheet-mailer rollout status deploy/camel-timesheet-mailer
kubectl -n timesheet-mailer logs deploy/camel-timesheet-mailer -f
# ...then restore the real schedule
kubectl -n timesheet-mailer set env deploy/camel-timesheet-mailer \
  TIMESHEET_SCHEDULE="0 0 9 ? * FRI#1,FRI#3 *"
```

`set env` on a Deployment is a live edit that ArgoCD's `selfHeal` will revert on the next
sync, which is the intended behaviour: the Git manifest stays the source of truth.
