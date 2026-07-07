# JobClaw Production Deployment

This document defines the single-host production baseline for JobClaw.

The target is **single-server production ready**: real public traffic, persistent data, clear security boundaries, recoverable backups, and practical operations. It is not a toy demo, and it is not a multi-node enterprise cluster.

## Scope

This deployment is intended for one VPS or one small cloud host. Keep it boring and inspectable:

- Use Docker Compose for process orchestration.
- Keep public traffic behind one gateway.
- Keep stateful services internal to the Docker network.
- Back up named volumes before risky changes.
- Move to managed services or a cluster only when traffic, availability, or compliance requirements justify it.

## Readiness Target

A deployment is considered online-ready when all of these are true:

- A real domain is configured in `JOBCLAW_SITE_WEB_SITE_URL`.
- Public traffic enters through the gateway only.
- HTTPS is enabled by a cloud load balancer, CDN, Caddy, host-level Nginx, or the optional Nginx TLS example.
- MySQL, Redis, Kafka, Elasticsearch, and MinIO are not exposed to the public Internet.
- `.env.production` contains no `CHANGE_ME` placeholders and is not committed.
- MySQL, MinIO, workspace, Redis, Kafka, and Elasticsearch data use Docker volumes.
- A backup can be created and restored before every risky upgrade.
- `/actuator/health` is reachable for health checks, but `/actuator/**` is otherwise hidden.
- Admin APIs are protected by backend authentication and permissions.

## Architecture

```text
Browser / IM callback / API client
  |
  v
TLS termination
  |
  v
jobclaw-prod-gateway  Nginx public entry
  |-- /                  -> jobclaw-prod-web
  |-- /api/**            -> jobclaw-prod-api
  |-- /oc/img/**         -> jobclaw-prod-api
  |-- /actuator/health   -> jobclaw-prod-api
  |-- /actuator/**       -> hidden

Internal Docker services:
  jobclaw-prod-api
  jobclaw-prod-web
  jobclaw-prod-mysql
  jobclaw-prod-redis
  jobclaw-prod-kafka
  jobclaw-prod-elasticsearch
  jobclaw-prod-minio
```

This is a production-ready single-host baseline. For higher traffic or strict availability requirements, move MySQL, Redis, Kafka, Elasticsearch, and object storage to managed or clustered services.

## First Deployment

Create the production environment file:

```powershell
Copy-Item .env.production.example .env.production
```

Edit `.env.production` and replace every `CHANGE_ME_*` value. At minimum, set:

```text
MYSQL_ROOT_PASSWORD
REDIS_PASSWORD
MINIO_ACCESS_KEY
MINIO_SECRET_KEY
JOBCLAW_JWT_SECRET
JOBCLAW_SITE_WEB_SITE_URL
```

Use a strong JWT secret:

```powershell
[Convert]::ToBase64String((1..48 | ForEach-Object { Get-Random -Maximum 256 }))
```

Start production:

```powershell
.\build\docker-prod.ps1
```

Equivalent command:

```powershell
docker compose --env-file .env.production -f docker/compose/compose.prod.yml up --build -d
```

The production compose project name is fixed to `jobclaw-prod`, so it is isolated from the local development compose project.

## HTTPS

`jobclaw-prod-gateway` listens on HTTP internally. For public traffic, use one of these patterns:

```text
Recommended: Cloud Load Balancer / CDN / WAF terminates HTTPS -> gateway:80
Simple VPS: Caddy or host-level Nginx terminates HTTPS -> gateway:80
Direct Nginx: copy docker/nginx/prod-tls.conf.example to docker/nginx/prod.conf and mount certificates
```

For a real domain:

```env
JOBCLAW_SITE_WEB_SITE_URL=https://job.example.com
JOBCLAW_PUBLIC_PORT=80
```

If another reverse proxy owns ports 80 and 443 on the host, map the gateway to a private local port instead:

```env
JOBCLAW_PUBLIC_PORT=18080
```

Then configure the host proxy to forward to `http://127.0.0.1:18080`.

## Gateway Baseline

Production compose mounts [docker/nginx/prod.conf](../docker/nginx/prod.conf). It provides:

- API and frontend reverse proxying.
- Basic per-IP request limiting.
- Upload size and proxy timeout limits.
- Gzip compression.
- Static asset cache headers.
- Security headers.
- WebSocket/SSE-friendly proxy headers.
- `/actuator/health` only, with the rest of `/actuator/**` hidden.

This is intentionally modest and maintainable. For Internet-facing production, put CDN/WAF or cloud security rules in front when possible.

## Production Defaults

`docker/compose/compose.prod.yml` intentionally differs from development compose files:

- Only `jobclaw-prod-gateway` exposes a host port.
- MySQL, Redis, Kafka, Elasticsearch, and MinIO are internal Docker services.
- Persistent data uses named Docker volumes.
- Services have health checks and `restart: unless-stopped`.
- Container logs use Docker `json-file` rotation.
- Core containers have default memory limits that can be tuned in `.env.production`.
- The API uses the `prod` Maven profile and split frontend/backend deployment.
- Gateway, API, and web containers use `no-new-privileges`.
- Containers use `init: true` and explicit stop grace periods for cleaner shutdowns.

## Firewall

On the production host, allow only public web traffic and SSH from trusted sources:

```text
Allow: 22/tcp from your IP
Allow: 80/tcp and 443/tcp from the Internet
Deny public access: 3306, 6379, 9000, 9001, 9092, 9200, 8087, 8099
```

If you expose `JOBCLAW_PUBLIC_PORT=80`, the gateway is the only Docker service that should be reachable from outside.

## Operations

Check service status:

```powershell
docker compose --env-file .env.production -f docker/compose/compose.prod.yml ps
```

View logs:

```powershell
docker logs --tail 200 jobclaw-prod-api
docker logs --tail 200 jobclaw-prod-gateway
docker logs --tail 200 jobclaw-prod-elasticsearch
```

Restart after config changes:

```powershell
docker compose --env-file .env.production -f docker/compose/compose.prod.yml up -d --force-recreate
```

Stop services without deleting data:

```powershell
docker compose --env-file .env.production -f docker/compose/compose.prod.yml stop
```

Destroying volumes deletes production data. Do not run `down -v` unless you have a verified backup and intentionally want to reset the environment.

## Backup

Create a production backup on Windows:

```powershell
.\ops\backup-prod.ps1
```

Create a production backup on Linux:

```bash
sh ./ops/backup-prod.sh
```

Backups are written to:

```text
backups/jobclaw-prod/<timestamp>/
```

The backup contains:

```text
mysql.sql
minio.tgz
workspace.tgz
elasticsearch.tgz
redis.tgz
kafka.tgz
README.txt
```

The scripts retain 14 days by default. Override when needed:

```powershell
.\ops\backup-prod.ps1 -RetentionDays 30
```

```bash
RETENTION_DAYS=30 sh ./ops/backup-prod.sh
```

## Restore Drill

Do a restore drill before trusting the backup process.

Minimum restore sequence:

```text
1. Stop production containers.
2. Create a fresh server or fresh Docker volumes.
3. Start MySQL only and import mysql.sql.
4. Restore MinIO/workspace tarballs into their named volumes.
5. Start the full compose stack.
6. Verify login, admin pages, job list, image access, and IM callback endpoints.
```

Example MySQL import:

```powershell
Get-Content .\backups\jobclaw-prod\<timestamp>\mysql.sql |
  docker compose --env-file .env.production -f docker/compose/compose.prod.yml exec -T mysql sh -c 'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"'
```

Volume tarballs can be restored with a temporary container:

```bash
docker run --rm -v jobclaw-prod-minio-data:/target -v "$PWD/backups/jobclaw-prod/<timestamp>:/backup" alpine:3.20 sh -c "cd /target && tar -xzf /backup/minio.tgz"
```

## Security Checklist

- Replace all `CHANGE_ME_*` values in `.env.production`.
- Do not commit `.env.production` or backup artifacts.
- Use a real `JOBCLAW_JWT_SECRET` with at least 32 random characters.
- Keep infrastructure ports closed on the server firewall.
- Use HTTPS for public traffic.
- Disable unused IM channels until credentials are configured.
- Keep payment disabled unless merchant certificates and callback URLs are configured.
- Confirm `/actuator/health` works and `/actuator/metrics` returns 404 through the gateway.
- Confirm anonymous users cannot access `/api/admin/**`.
- Rotate secrets if `.env.production` is ever copied to an untrusted machine.

## Pre-Upgrade Checklist

Before every risky upgrade:

```text
1. Run a backup.
2. Export docker compose config for audit.
3. Pull or build new images.
4. Recreate services.
5. Check health and logs.
6. Verify key workflows.
```

Commands:

```powershell
.\ops\backup-prod.ps1
docker compose --env-file .env.production -f docker/compose/compose.prod.yml config
docker compose --env-file .env.production -f docker/compose/compose.prod.yml up --build -d
docker compose --env-file .env.production -f docker/compose/compose.prod.yml ps
```

Validate gateway config after editing Nginx files:

```powershell
docker run --rm -v ${PWD}\docker\nginx\prod.conf:/etc/nginx/conf.d/default.conf:ro nginx:1.27-alpine nginx -t
```

## Authentication and RBAC

Production deployments must enforce permissions on the backend API, not only in the frontend UI.

The current backend uses two permission layers:

- `SecurityConfig` protects API paths at the Spring Security layer.
- `PermissionCheckInterceptor` enforces controller-level `@Permission` annotations.

Default API boundaries:

| API path | Access rule |
|---|---|
| `/api/common/**` | Public |
| `/api/wx/**` | Public login and callback endpoints |
| `/api/oc/list` | Public job list |
| `/api/admin/**` | Admin only |
| `/api/user/**` | Logged-in user |
| `/api/chat/**` | Logged-in user |
| `/api/recharge/**` | Logged-in user |

Role compatibility:

- Legacy `UserRoleEnum.ADMIN` is treated as admin.
- RBAC roles `PLATFORM_ADMIN` and `SUPER_ADMIN` are treated as admin.
- Legacy `UserRoleEnum.VIP` is treated as VIP.
- RBAC role `VIP_USER` is treated as VIP.
- Admin users are allowed to access VIP-only resources.

HTTP status behavior:

- Missing or invalid login returns HTTP `401`.
- Logged-in users without sufficient permission return HTTP `403`.

When adding new APIs:

- Put admin APIs under `/api/admin/**`.
- Put user-private APIs under `/api/user/**`, `/api/chat/**`, or another authenticated path explicitly configured in `SecurityConfig`.
- Add `@Permission(role = UserRoleEnum.ADMIN)` for admin controllers.
- Add `@Permission(role = UserRoleEnum.NORMAL)` or `VIP` for user-facing protected controllers.
- Add focused tests for permission-sensitive behavior.
