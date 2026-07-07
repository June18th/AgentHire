$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

$mavenRepo = Join-Path $HOME ".m2\repository"
if (-not $env:MAVEN_LOCAL_REPO -and (Test-Path -LiteralPath $mavenRepo)) {
    $env:MAVEN_LOCAL_REPO = (Resolve-Path -LiteralPath $mavenRepo).Path
    Write-Host "Using host Maven repository: $env:MAVEN_LOCAL_REPO"
}

docker compose -f docker-compose.mysql.yml up --build -d mysql jobclaw
