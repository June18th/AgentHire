param(
    [string]$EnvFile = ".env.production",
    [string]$BackupRoot = ".\backups\jobclaw-prod",
    [int]$RetentionDays = 14
)

$ErrorActionPreference = "Stop"

# AIDEV-NOTE: Windows helper mirrors the Linux backup script.

$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$backupDir = Join-Path $BackupRoot $stamp
New-Item -ItemType Directory -Force -Path $backupDir | Out-Null

$compose = @("compose", "--env-file", $EnvFile, "-f", "docker/compose/compose.prod.yml")

Write-Host "Creating MySQL dump..."
$mysqlDump = docker @compose exec -T mysql sh -c 'exec mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction --routines --triggers "$MYSQL_DATABASE"'
$mysqlDump | Set-Content -Encoding UTF8 -Path (Join-Path $backupDir "mysql.sql")

function Backup-Volume {
    param(
        [string]$VolumeName,
        [string]$OutputName
    )

    Write-Host "Archiving $VolumeName..."
    $mountTarget = ((Resolve-Path $backupDir).Path).Replace("\", "/")
    docker run --rm `
        -v "${VolumeName}:/source:ro" `
        -v "${mountTarget}:/backup" `
        alpine:3.20 `
        tar -czf "/backup/${OutputName}.tgz" -C /source .
}

Backup-Volume "jobclaw-prod-minio-data" "minio"
Backup-Volume "jobclaw-prod-workspace" "workspace"
Backup-Volume "jobclaw-prod-elasticsearch-data" "elasticsearch"
Backup-Volume "jobclaw-prod-redis-data" "redis"
Backup-Volume "jobclaw-prod-kafka-data" "kafka"

@"
JobClaw production backup
Created: $stamp
Env file: $EnvFile

Files:
- mysql.sql
- minio.tgz
- workspace.tgz
- elasticsearch.tgz
- redis.tgz
- kafka.tgz
"@ | Set-Content -Encoding UTF8 -Path (Join-Path $backupDir "README.txt")

if ($RetentionDays -gt 0 -and (Test-Path $BackupRoot)) {
    Get-ChildItem -Path $BackupRoot -Directory |
        Where-Object { $_.LastWriteTime -lt (Get-Date).AddDays(-$RetentionDays) } |
        Remove-Item -Recurse -Force
}

Write-Host "Backup written to $backupDir"
