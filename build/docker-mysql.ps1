$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

docker compose -f docker/compose/compose.mysql.yml up --build -d mysql jobclaw
