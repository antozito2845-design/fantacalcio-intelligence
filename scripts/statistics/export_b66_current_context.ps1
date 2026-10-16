param(
    [string]$Database = "fantacalcio_intelligence",
    [string]$DatabaseUser = "fantacalcio_app",
    [string]$DatabaseHost = "localhost"
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)

$statisticsDir = Join-Path $root "data\statistics"
$featuresDir = Join-Path $statisticsDir "features"

New-Item -ItemType Directory -Force -Path $statisticsDir | Out-Null
New-Item -ItemType Directory -Force -Path $featuresDir | Out-Null

$contextPath = Join-Path $featuresDir "current-player-feature-context.csv"
$coveragePath = Join-Path $statisticsDir "current-2026-2027-historical-coverage.csv"

$contextPathSql = $contextPath.Replace("\", "/")
$coveragePathSql = $coveragePath.Replace("\", "/")

Write-Host "=== B66 - EXPORT CURRENT OPERATIONAL CONTEXT ==="

$contextSql = @"
SELECT
    p.id AS player_id,
    p.display_name AS player_name,
    role_data.fantasy_role AS current_role,
    r.club_id AS current_club_id,
    c.name AS current_club
FROM player_registrations r
JOIN seasons se
    ON se.id = r.season_id
JOIN players p
    ON p.id = r.player_id
JOIN clubs c
    ON c.id = r.club_id
JOIN LATERAL (
    SELECT pr.fantasy_role
    FROM player_roles pr
    WHERE pr.player_id = p.id
      AND pr.season_id = se.id
      AND pr.source = 'FANTACALCIO'
    ORDER BY pr.id DESC
    LIMIT 1
) role_data ON TRUE
WHERE se.code = '2026/2027'
  AND r.registration_status = 'ACTIVE'
ORDER BY p.id
"@

$contextCopy = "\copy ($contextSql) TO '$contextPathSql' WITH (FORMAT csv, HEADER true, ENCODING 'UTF8')"

& psql `
    -h $DatabaseHost `
    -U $DatabaseUser `
    -d $Database `
    -v ON_ERROR_STOP=1 `
    -c $contextCopy

if ($LASTEXITCODE -ne 0) {
    throw "Export current-player-feature-context.csv fallito."
}

Write-Host ""
Write-Host "=== B66 - EXPORT CURRENT HISTORICAL COVERAGE ==="

$coverageSql = @"
WITH operational AS (
    SELECT DISTINCT
        p.id AS player_id,
        p.display_name AS player_name
    FROM players p
    JOIN player_registrations r
        ON r.player_id = p.id
    JOIN seasons se
        ON se.id = r.season_id
    WHERE se.code = '2026/2027'
      AND r.registration_status = 'ACTIVE'
),
fantacalcio_mapping AS (
    SELECT DISTINCT ON (m.player_id)
        m.player_id,
        m.external_id AS fantacalcio_id
    FROM player_source_mappings m
    WHERE m.source = 'FANTACALCIO'
      AND m.verified = TRUE
    ORDER BY m.player_id, m.id DESC
),
coverage AS (
    SELECT
        o.player_id,
        o.player_name,
        fm.fantacalcio_id,

        CASE WHEN EXISTS (
            SELECT 1
            FROM player_season_statistics pss
            JOIN seasons hs
                ON hs.id = pss.season_id
            WHERE pss.player_id = o.player_id
              AND pss.source = 'FANTACALCIO'
              AND hs.code = '2024/2025'
        )
        THEN 'YES' ELSE 'NO' END AS has_fantacalcio_2024_25,

        CASE WHEN EXISTS (
            SELECT 1
            FROM player_season_statistics pss
            JOIN seasons hs
                ON hs.id = pss.season_id
            WHERE pss.player_id = o.player_id
              AND pss.source = 'FANTACALCIO'
              AND hs.code = '2025/2026'
        )
        THEN 'YES' ELSE 'NO' END AS has_fantacalcio_2025_26,

        CASE WHEN EXISTS (
            SELECT 1
            FROM player_competition_season_statistics pcs
            JOIN seasons hs
                ON hs.id = pcs.season_id
            WHERE pcs.player_id = o.player_id
              AND pcs.source = 'TRANSFERMARKT'
              AND pcs.competition_type = 'DOMESTIC_LEAGUE'
              AND hs.code = '2024/2025'
        )
        THEN 'YES' ELSE 'NO' END AS has_transfermarkt_2024_25,

        CASE WHEN EXISTS (
            SELECT 1
            FROM player_competition_season_statistics pcs
            JOIN seasons hs
                ON hs.id = pcs.season_id
            WHERE pcs.player_id = o.player_id
              AND pcs.source = 'TRANSFERMARKT'
              AND pcs.competition_type = 'DOMESTIC_LEAGUE'
              AND hs.code = '2025/2026'
        )
        THEN 'YES' ELSE 'NO' END AS has_transfermarkt_2025_26

    FROM operational o

    LEFT JOIN fantacalcio_mapping fm
        ON fm.player_id = o.player_id
)
SELECT
    player_id,
    player_name,
    COALESCE(fantacalcio_id, '') AS fantacalcio_id,
    has_fantacalcio_2024_25,
    has_fantacalcio_2025_26,
    has_transfermarkt_2024_25,
    has_transfermarkt_2025_26
FROM coverage
ORDER BY player_id
"@

$coverageCopy = "\copy ($coverageSql) TO '$coveragePathSql' WITH (FORMAT csv, HEADER true, ENCODING 'UTF8')"

& psql `
    -h $DatabaseHost `
    -U $DatabaseUser `
    -d $Database `
    -v ON_ERROR_STOP=1 `
    -c $coverageCopy

if ($LASTEXITCODE -ne 0) {
    throw "Export current-2026-2027-historical-coverage.csv fallito."
}

$contextRows = @(Import-Csv $contextPath)
$coverageRows = @(Import-Csv $coveragePath)

if ($contextRows.Count -ne $coverageRows.Count) {
    throw "Mismatch context/coverage: $($contextRows.Count) vs $($coverageRows.Count)"
}

$contextIds = @($contextRows.player_id | Sort-Object)
$coverageIds = @($coverageRows.player_id | Sort-Object)

$contextFingerprint = $contextIds -join ","
$coverageFingerprint = $coverageIds -join ","

if ($contextFingerprint -ne $coverageFingerprint) {
    throw "Le popolazioni context e coverage non coincidono."
}

$withoutAnyHistory = @(
    $coverageRows |
    Where-Object {
        $_.has_fantacalcio_2024_25 -eq "NO" -and
        $_.has_fantacalcio_2025_26 -eq "NO" -and
        $_.has_transfermarkt_2024_25 -eq "NO" -and
        $_.has_transfermarkt_2025_26 -eq "NO"
    }
)

Write-Host ""
Write-Host "=== B66 EXPORT COMPLETATO ==="
Write-Host "Operational context : $($contextRows.Count)"
Write-Host "Historical coverage : $($coverageRows.Count)"
Write-Host "No usable history   : $($withoutAnyHistory.Count)"
Write-Host ""
Write-Host "Context:"
Write-Host $contextPath
Write-Host ""
Write-Host "Coverage:"
Write-Host $coveragePath

if ($withoutAnyHistory.Count -gt 0) {
    Write-Host ""
    Write-Host "=== GIOCATORI SENZA STORICO UTILIZZABILE ==="

    $withoutAnyHistory |
        Select-Object player_id, player_name |
        Format-Table -AutoSize
}