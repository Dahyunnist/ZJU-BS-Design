param(
    [string]$OutputDirectory = (Join-Path $PSScriptRoot "..\backups")
)

$ErrorActionPreference = "Stop"
$resolvedDirectory = [System.IO.Path]::GetFullPath($OutputDirectory)
New-Item -ItemType Directory -Force -Path $resolvedDirectory | Out-Null
$containerFile = "/tmp/image-db-backup.sql.gz"
$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$destination = Join-Path $resolvedDirectory "image-db-$timestamp.sql.gz"

docker compose exec -T mysql sh -lc 'MYSQL_PWD="$MYSQL_PASSWORD" mysqldump --single-transaction --no-tablespaces --routines --triggers -u"$MYSQL_USER" image_db > /tmp/image-db-backup.sql && gzip -f /tmp/image-db-backup.sql'
if ($LASTEXITCODE -ne 0) { throw "Database dump failed." }
try {
    docker cp "image-manager-mysql:$containerFile" $destination
    if ($LASTEXITCODE -ne 0) { throw "Could not copy backup from MySQL container." }
} finally {
    docker compose exec -T mysql rm -f $containerFile /tmp/image-db-backup.sql | Out-Null
}

Write-Output "Backup written to $destination"
