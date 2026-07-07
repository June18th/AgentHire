# JobClaw Docker Deployment

This directory contains the Docker assets for JobClaw's local development and single-host production deployment.

The target is a practical single-server production baseline, not an enterprise cluster. The deployment should be easy to start, easy to inspect, and safe enough for a small public service when paired with backups, HTTPS, and a locked-down host firewall.

## Layout

- `api/`: Spring Boot API image.
- `web/`: static Next.js web image served by Nginx.
- `nginx/`: gateway Nginx configuration.
- `compose/`: environment-specific Docker Compose files.
- `elasticsearch/`: Elasticsearch runtime config.

## Deployment Modes

Local development stays lightweight:

```bash
docker compose -f docker/compose/compose.dev.yml -f docker/compose/compose.mysql.yml -f docker/compose/compose.frontend.yml up -d --build mysql jobclaw jobclaw-web jobclaw-gateway
```

Single-host production uses the dedicated production stack:

```bash
docker compose --env-file .env.production -f docker/compose/compose.prod.yml up --build -d
```

Common startup commands are wrapped by scripts in `build/`.

## Production Boundary

The production stack is intentionally modest:

- Public traffic enters through `jobclaw-prod-gateway`.
- MySQL, Redis, Kafka, Elasticsearch, MinIO, API, and web services stay on the Docker network.
- Data is persisted in named Docker volumes.
- Logs use Docker `json-file` rotation.
- Services define health checks and restart policies.
- Secrets come from `.env.production`, which must not be committed.

For higher availability, move stateful services to managed or clustered infrastructure instead of stretching this Compose setup beyond its design.
