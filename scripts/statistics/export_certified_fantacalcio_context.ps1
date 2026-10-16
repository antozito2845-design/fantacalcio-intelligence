[CmdletBinding()]
param(
    [string]$DatabaseName = 'fantacalcio_intelligence',
    [string]$OutputPath = ''
)
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
if (-not $OutputPath) {
    $OutputPath = Join-Path $root 'data\statistics\fantacalcio\existing-certified-mappings.csv'
}
$properties = @{}
Get-Content (Join-Path $root 'backend\src\main\resources\application.properties') | ForEach-Object {
    if ($_ -match '^([^#=]+)=(.*)$') { $properties[$matches[1].Trim()] = $matches[2].Trim() }
}
function Default-Value([string]$value, [string]$name) {
    if ($value -match ('^\$\{' + [regex]::Escape($name) + ':(.*)\}$')) { return $matches[1] }
    return $value
}
$url = Default-Value $properties['spring.datasource.url'] 'DATABASE_URL'
$username = Default-Value $properties['spring.datasource.username'] 'DATABASE_USERNAME'
$password = Default-Value $properties['spring.datasource.password'] 'DATABASE_PASSWORD'
if ($url -notmatch '^jdbc:postgresql://([^/:?]+)(?::([0-9]+))?/([^?]+)') { throw 'Datasource JDBC non supportata' }
$dbHost = $matches[1]; $dbPort = if ($matches[2]) { $matches[2] } else { '5432' }
if ($DatabaseName -ne $matches[3]) { throw "Target DB inatteso: $DatabaseName" }
$oldPassword = $env:PGPASSWORD
try {
    $env:PGPASSWORD = $password
    $query = @"
SELECT m.external_id AS fantacalcio_player_id, m.player_id,
       p.display_name, COALESCE(ip.birth_date::text, p.birth_date::text, '') AS birth_date
FROM player_source_mappings m
JOIN players p ON p.id=m.player_id
LEFT JOIN player_identity_profiles ip ON ip.player_id=p.id
WHERE m.source='FANTACALCIO'
ORDER BY m.external_id
"@
    $lines = & psql -X --csv -v ON_ERROR_STOP=1 -h $dbHost -p $dbPort -U $username -d $DatabaseName -c $query
    if ($LASTEXITCODE -ne 0) { throw 'Export psql fallito' }
    $records = $lines | ConvertFrom-Csv
    $records | Export-Csv -LiteralPath $OutputPath -NoTypeInformation -Encoding utf8
    Write-Output "Exported $($records.Count) certified mappings"
}
finally {
    if ($null -eq $oldPassword) { Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue }
    else { $env:PGPASSWORD = $oldPassword }
}
