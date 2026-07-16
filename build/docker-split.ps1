$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

docker compose -f docker/compose/compose.dev.yml -f docker/compose/compose.mysql.yml -f docker/compose/compose.frontend.yml up -d --build mysql jobclaw jobclaw-web jobclaw-gateway
