#!/usr/bin/env sh
set -eu

# AIDEV-NOTE: Backup named Docker volumes for single-host production.

ENV_FILE="${JOBCLAW_ENV_FILE:-.env.production}"
BACKUP_ROOT="${BACKUP_ROOT:-./backups/jobclaw-prod}"
RETENTION_DAYS="${RETENTION_DAYS:-14}"
STAMP="$(date +%Y%m%d-%H%M%S)"
BACKUP_DIR="${BACKUP_ROOT}/${STAMP}"
COMPOSE="docker compose --env-file ${ENV_FILE} -f docker/compose/compose.prod.yml"

mkdir -p "${BACKUP_DIR}"

echo "Creating MySQL dump..."
${COMPOSE} exec -T mysql sh -c 'exec mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction --routines --triggers "$MYSQL_DATABASE"' > "${BACKUP_DIR}/mysql.sql"

backup_volume() {
  volume_name="$1"
  output_name="$2"
  echo "Archiving ${volume_name}..."
  docker run --rm \
    -v "${volume_name}:/source:ro" \
    -v "$(pwd)/${BACKUP_DIR}:/backup" \
    alpine:3.20 \
    tar -czf "/backup/${output_name}.tgz" -C /source .
}

backup_volume jobclaw-prod-minio-data minio
backup_volume jobclaw-prod-workspace workspace
backup_volume jobclaw-prod-elasticsearch-data elasticsearch
backup_volume jobclaw-prod-redis-data redis
backup_volume jobclaw-prod-kafka-data kafka

cat > "${BACKUP_DIR}/README.txt" <<EOF
JobClaw production backup
Created: ${STAMP}
Env file: ${ENV_FILE}

Files:
- mysql.sql
- minio.tgz
- workspace.tgz
- elasticsearch.tgz
- redis.tgz
- kafka.tgz
EOF

if [ "${RETENTION_DAYS}" -gt 0 ]; then
  find "${BACKUP_ROOT}" -mindepth 1 -maxdepth 1 -type d -mtime +"${RETENTION_DAYS}" -exec rm -rf {} \;
fi

echo "Backup written to ${BACKUP_DIR}"
