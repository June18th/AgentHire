# Docker Compose Layout

This directory keeps environment-specific Docker Compose files out of the project root.

The goal is a clean single-host production baseline plus lightweight local development. It deliberately avoids Kubernetes-grade complexity.

## Local Development

- `compose.dev.yml`: H2/local API baseline.
- `compose.mysql.yml`: MySQL + API baseline.
- `compose.frontend.yml`: split API, static web, and gateway.

Default split startup:

```bash
docker compose -f docker/compose/compose.dev.yml -f docker/compose/compose.mysql.yml -f docker/compose/compose.frontend.yml up -d --build mysql jobclaw jobclaw-web jobclaw-gateway
```

## Optional Middleware

Add these only when the task needs the capability:

- `compose.redis.yml`: Redis cache.
- `compose.kafka.yml`: Kafka message queue.
- `compose.elasticsearch.yml`: Elasticsearch search.
- `compose.minio.yml`: MinIO object storage.

## Production

- `compose.prod.yml`: single-host production baseline with internal MySQL, Redis, Kafka, Elasticsearch, MinIO, API, web, and gateway services.

Production is expected to run behind HTTPS termination from a cloud load balancer, CDN, Caddy, host-level Nginx, or the optional TLS gateway example.

Use the wrapper scripts in `build/` for common startup paths.
