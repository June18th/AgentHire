$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

docker compose -f docker/compose/compose.mysql.yml -f docker/compose/compose.minio.yml up --build -d mysql minio jobclaw
