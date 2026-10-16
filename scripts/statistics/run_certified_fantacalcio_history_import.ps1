[CmdletBinding()]
param(
    [ValidateSet('fantacalcio_intelligence_import_test','fantacalcio_intelligence')]
    [string]$DatabaseName = 'fantacalcio_intelligence_import_test',
    [switch]$Preflight
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$configPath = Join-Path $root 'backend\src\main\resources\application.properties'
$sqlPath = Join-Path $PSScriptRoot 'certified_fantacalcio_history_import.sql'
$stateSqlPath = Join-Path $PSScriptRoot 'certified_fantacalcio_history_state.sql'
$dataPath = Join-Path $root 'data\statistics\fantacalcio'
$required = @(
    $configPath, $sqlPath, $stateSqlPath,
    (Join-Path $dataPath 'historical-fantacalcio-profiles.csv'),
    (Join-Path $dataPath 'fantacalcio-serie-a-2024-25.csv'),
    (Join-Path $dataPath 'fantacalcio-serie-a-2025-26.csv')
)
foreach ($path in $required) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "File richiesto non trovato: $path"
    }
}
if (Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue) {
    throw 'Backend in ascolto sulla porta 8080: arrestarlo prima dell import.'
}

function Get-PropertyValue {
    param([string]$Content,[string]$PropertyName)
    $match = [regex]::Match($Content,"(?m)^\s*$([regex]::Escape($PropertyName))\s*=\s*(.+?)\s*$")
    if ($match.Success) { return $match.Groups[1].Value.Trim() }
    return $null
}
function Resolve-SpringProperty {
    param([string]$RawValue)
    if ($RawValue -match '^\$\{([A-Za-z_][A-Za-z0-9_]*)(?::(.*))?\}$') {
        $environmentValue = [Environment]::GetEnvironmentVariable($Matches[1])
        if (-not [string]::IsNullOrWhiteSpace($environmentValue)) { return $environmentValue }
        if ($null -ne $Matches[2] -and -not [string]::IsNullOrWhiteSpace($Matches[2])) { return $Matches[2] }
        return $null
    }
    return $RawValue
}

$content = Get-Content -Raw -LiteralPath $configPath
$dbUrl = Resolve-SpringProperty (Get-PropertyValue $content 'spring.datasource.url')
$dbUser = Resolve-SpringProperty (Get-PropertyValue $content 'spring.datasource.username')
$dbPassword = Resolve-SpringProperty (Get-PropertyValue $content 'spring.datasource.password')
if ([string]::IsNullOrWhiteSpace($dbUrl) -or [string]::IsNullOrWhiteSpace($dbUser) -or
    [string]::IsNullOrWhiteSpace($dbPassword)) {
    throw 'Configurazione database locale non risolvibile.'
}
$jdbc = [regex]::Match($dbUrl,'^jdbc:postgresql://([^/:?]+)(?::([0-9]+))?/([^?]+)(?:\?.*)?$')
if (-not $jdbc.Success) { throw 'spring.datasource.url non usa un formato JDBC PostgreSQL supportato.' }
$dbHost = $jdbc.Groups[1].Value
$dbPort = if ($jdbc.Groups[2].Success) { $jdbc.Groups[2].Value } else { '5432' }
$psql = (Get-Command psql -ErrorAction Stop).Source
$previousPassword = $env:PGPASSWORD

function Invoke-StateSnapshot {
    $output = & $psql -X -v ON_ERROR_STOP=1 -h $dbHost -p $dbPort -U $dbUser -d $DatabaseName -At -f $stateSqlPath
    if ($LASTEXITCODE -ne 0 -or @($output).Count -ne 2) { throw 'Snapshot stato database fallito.' }
    return @($output)
}

try {
    $env:PGPASSWORD = $dbPassword
    $before = if ($Preflight) { Invoke-StateSnapshot } else { @() }
    $arguments = @(
        '-X','-v','ON_ERROR_STOP=1','-v',"preflight=$(if($Preflight){'true'}else{'false'})",
        '-h',$dbHost,'-p',$dbPort,'-U',$dbUser,'-d',$DatabaseName,'-f',$sqlPath
    )
    Push-Location $root
    try { & $psql @arguments; $exitCode = $LASTEXITCODE }
    finally { Pop-Location }
    if ($exitCode -ne 0) { throw "Import certificato fallito (ExitCode $exitCode)." }
    if ($Preflight) {
        $after = Invoke-StateSnapshot
        if (($before -join "`n") -ne ($after -join "`n")) {
            throw "Preflight mutante: stato persistente o sequence modificati.`nBEFORE=$($before -join ';')`nAFTER=$($after -join ';')"
        }
        Write-Output "PREFLIGHT CERTIFICATO: PASS (rows unchanged; sequences unchanged; $($after -join '; '))"
    }
    else { Write-Output 'IMPORT CERTIFICATO: PASS (COMMIT)' }
}
finally {
    if ($null -eq $previousPassword) { Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue }
    else { $env:PGPASSWORD = $previousPassword }
}
