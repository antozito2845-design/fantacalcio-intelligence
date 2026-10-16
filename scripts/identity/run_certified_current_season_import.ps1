[CmdletBinding()]
param(
    [switch]$PreflightOnly
)

$ErrorActionPreference = 'Stop'

$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$configPath = Join-Path $root 'backend\src\main\resources\application.properties'
$sqlPath = Join-Path $PSScriptRoot 'certified_current_season_import.sql'

$identityPath = Join-Path `
    $root `
    'data\identity\comparison-2026-2027\fantacalcio-transfermarkt-certified-final.csv'

$reconciliationPath = Join-Path `
    $root `
    'data\identity\comparison-2026-2027\catalog-reconciliation-certified-final.csv'

foreach ($path in @(
    $configPath,
    $sqlPath,
    $identityPath,
    $reconciliationPath
)) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "File richiesto non trovato: $path"
    }
}

if (
    Get-NetTCPConnection `
        -LocalPort 8080 `
        -State Listen `
        -ErrorAction SilentlyContinue
) {
    throw 'Backend in ascolto sulla porta 8080: arrestarlo prima dell import.'
}

function Get-PropertyValue {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Content,

        [Parameter(Mandatory = $true)]
        [string]$PropertyName
    )

    $escapedName = [regex]::Escape($PropertyName)

    $match = [regex]::Match(
        $Content,
        "(?m)^\s*$escapedName\s*=\s*(.+?)\s*$"
    )

    if (-not $match.Success) {
        return $null
    }

    return $match.Groups[1].Value.Trim()
}

function Resolve-SpringProperty {
    param(
        [Parameter(Mandatory = $true)]
        [string]$RawValue
    )

    # Supporta:
    # ${VARIABLE}
    # ${VARIABLE:fallback}
    # testo normale

    if ($RawValue -match '^\$\{([A-Za-z_][A-Za-z0-9_]*)(?::(.*))?\}$') {

        $environmentName = $Matches[1]
        $fallback = $Matches[2]

        $environmentValue = [Environment]::GetEnvironmentVariable(
            $environmentName
        )

        if (-not [string]::IsNullOrWhiteSpace($environmentValue)) {
            return $environmentValue
        }

        if (-not [string]::IsNullOrWhiteSpace($fallback)) {
            return $fallback
        }

        return $null
    }

    return $RawValue
}

$properties = Get-Content -Raw -LiteralPath $configPath

$rawDbUrl = Get-PropertyValue `
    -Content $properties `
    -PropertyName 'spring.datasource.url'

$rawDbUser = Get-PropertyValue `
    -Content $properties `
    -PropertyName 'spring.datasource.username'

$rawDbPassword = Get-PropertyValue `
    -Content $properties `
    -PropertyName 'spring.datasource.password'

if ([string]::IsNullOrWhiteSpace($rawDbUrl)) {
    throw 'spring.datasource.url non trovato in application.properties.'
}

if ([string]::IsNullOrWhiteSpace($rawDbUser)) {
    throw 'spring.datasource.username non trovato in application.properties.'
}

if ([string]::IsNullOrWhiteSpace($rawDbPassword)) {
    throw 'spring.datasource.password non trovato in application.properties.'
}

$dbUrl = Resolve-SpringProperty -RawValue $rawDbUrl
$dbUser = Resolve-SpringProperty -RawValue $rawDbUser
$dbPassword = Resolve-SpringProperty -RawValue $rawDbPassword

if (
    [string]::IsNullOrWhiteSpace($dbUrl) -or
    [string]::IsNullOrWhiteSpace($dbUser) -or
    [string]::IsNullOrWhiteSpace($dbPassword)
) {
    throw 'Configurazione database locale non risolvibile.'
}

# Formato JDBC PostgreSQL supportato:
# jdbc:postgresql://host:port/database
# jdbc:postgresql://host/database
#
# Sono ammessi anche eventuali parametri:
# jdbc:postgresql://host:port/database?param=value

$jdbcPattern = '^jdbc:postgresql://([^/:?]+)(?::([0-9]+))?/([^?]+)(?:\?.*)?$'

$jdbcMatch = [regex]::Match(
    $dbUrl,
    $jdbcPattern,
    [System.Text.RegularExpressions.RegexOptions]::IgnoreCase
)

if (-not $jdbcMatch.Success) {
    throw 'spring.datasource.url non usa un formato JDBC PostgreSQL supportato.'
}

$dbHost = $jdbcMatch.Groups[1].Value

$dbPort = if ($jdbcMatch.Groups[2].Success) {
    $jdbcMatch.Groups[2].Value
}
else {
    '5432'
}

$dbName = $jdbcMatch.Groups[3].Value

if (
    [string]::IsNullOrWhiteSpace($dbHost) -or
    [string]::IsNullOrWhiteSpace($dbPort) -or
    [string]::IsNullOrWhiteSpace($dbName)
) {
    throw 'Host, porta o database PostgreSQL non risolvibili dalla datasource URL.'
}

# Sicurezza Blocco 47:
# questo runner deve operare esclusivamente sul DEV previsto.
if ($dbName -ne 'fantacalcio_intelligence') {
    throw "Database non autorizzato per Blocco 47: $dbName"
}

$psql = (Get-Command psql -ErrorAction Stop).Source

$apply = if ($PreflightOnly) {
    'false'
}
else {
    'true'
}

$previousPassword = $env:PGPASSWORD

try {

    # La password resta solo nell'ambiente del processo corrente.
    # Non viene scritta nell'output.
    $env:PGPASSWORD = $dbPassword

    $arguments = @(
        '-X',
        '-v',
        'ON_ERROR_STOP=1',
        '-h',
        $dbHost,
        '-p',
        $dbPort,
        '-U',
        $dbUser,
        '-d',
        $dbName,
        '-v',
        "apply_import=$apply",
        '-f',
        $sqlPath
    )

    Push-Location $root

    try {
        & $psql @arguments
        $exitCode = $LASTEXITCODE
    }
    finally {
        Pop-Location
    }

    if ($exitCode -ne 0) {
        throw "Import certificato fallito (ExitCode $exitCode)."
    }

    if ($PreflightOnly) {
        Write-Output 'PREFLIGHT CERTIFICATO: PASS (ROLLBACK)'
    }
    else {
        Write-Output 'IMPORT CERTIFICATO: PASS (COMMIT)'
    }
}
finally {

    if ($null -eq $previousPassword) {
        Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
    }
    else {
        $env:PGPASSWORD = $previousPassword
    }
}