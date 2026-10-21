param(
    [Parameter(Mandatory = $true)]
    [string]$BackupFile,
    [string]$TargetDatabase = "image_db_restore_check"
)

$ErrorActionPreference = "Stop"
if ($TargetDatabase -notmatch "^[A-Za-z0-9_]+$" -or $TargetDatabase -eq "image_db") {
    throw "TargetDatabase must be a safe name and cannot be image_db."
}
$resolvedBackup = (Resolve-Path -LiteralPath $BackupFile).Path
$containerFile = "/tmp/image-db-restore.sql.gz"
docker cp $resolvedBackup "image-manager-mysql:$containerFile"
if ($LASTEXITCODE -ne 0) { throw "Could not copy backup to MySQL container." }
docker cp (Join-Path $PSScriptRoot "restore-db.sh") "image-manager-mysql:/tmp/restore-db.sh"
if ($LASTEXITCODE -ne 0) { throw "Could not copy restore helper to MySQL container." }
try {
    docker exec image-manager-mysql sh /tmp/restore-db.sh $TargetDatabase $containerFile
    if ($LASTEXITCODE -ne 0) { throw "Restore failed. See MySQL output above." }
} finally {
    docker exec image-manager-mysql rm -f $containerFile /tmp/restore-db.sh | Out-Null
}

Write-Output "Backup restored to '$TargetDatabase'. The source database was not changed."
