$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

$envFile = ".env.production"
if (-not (Test-Path -LiteralPath $envFile)) {
    Write-Host "Missing $envFile. Create it from .env.production.example first:" -ForegroundColor Yellow
    Write-Host "Copy-Item .env.production.example .env.production"
    exit 1
}

if (Select-String -LiteralPath $envFile -Pattern "CHANGE_ME" -Quiet) {
    Write-Host "$envFile still contains CHANGE_ME placeholders. Replace production secrets before starting." -ForegroundColor Red
    exit 1
}

if (Select-String -LiteralPath $envFile -Pattern "job\.example\.com" -Quiet) {
    Write-Host "$envFile still uses the example domain. Set JOBCLAW_SITE_WEB_SITE_URL to your real domain." -ForegroundColor Red
    exit 1
}

$mavenRepo = Join-Path $HOME ".m2\repository"
if (-not $env:MAVEN_LOCAL_REPO -and (Test-Path -LiteralPath $mavenRepo)) {
    $env:MAVEN_LOCAL_REPO = (Resolve-Path -LiteralPath $mavenRepo).Path
    Write-Host "Using host Maven repository: $env:MAVEN_LOCAL_REPO"
}

docker compose --env-file $envFile -f docker/compose/compose.prod.yml up --build -d
