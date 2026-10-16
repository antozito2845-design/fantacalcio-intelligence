[CmdletBinding()]
param(
 [ValidateSet('fantacalcio_intelligence_import_test','fantacalcio_intelligence')][string]$DatabaseName='fantacalcio_intelligence',
 [ValidateSet('current-player-feature-context.csv','sandbox-player-feature-context.csv')][string]$OutputFileName='current-player-feature-context.csv'
)
$ErrorActionPreference='Stop'
$root=(Resolve-Path(Join-Path $PSScriptRoot '..\..')).Path
$config=Get-Content -Raw (Join-Path $root 'backend\src\main\resources\application.properties')
function Property([string]$name){$m=[regex]::Match($config,"(?m)^\s*$([regex]::Escape($name))\s*=\s*(.+?)\s*$");if(-not $m.Success){throw "Missing $name"};return $m.Groups[1].Value.Trim()}
function ResolveValue([string]$raw){if($raw -match '^\$\{([A-Za-z_][A-Za-z0-9_]*)(?::(.*))?\}$'){$envValue=[Environment]::GetEnvironmentVariable($Matches[1]);if(-not[string]::IsNullOrWhiteSpace($envValue)){return $envValue};if(-not[string]::IsNullOrWhiteSpace($Matches[2])){return $Matches[2]};return $null};return $raw}
$url=ResolveValue(Property 'spring.datasource.url');$user=ResolveValue(Property 'spring.datasource.username');$password=ResolveValue(Property 'spring.datasource.password')
$jdbc=[regex]::Match($url,'^jdbc:postgresql://([^/:?]+)(?::([0-9]+))?/');if(-not $jdbc.Success){throw 'Invalid JDBC URL'}
$dbHost=$jdbc.Groups[1].Value;$dbPort=if($jdbc.Groups[2].Success){$jdbc.Groups[2].Value}else{'5432'}
$output=Join-Path $root "data\statistics\features\$OutputFileName";New-Item -ItemType Directory -Force (Split-Path $output)|Out-Null
$sql=@"
COPY (
 SELECT p.id player_id,p.display_name player_name,pr.fantasy_role current_role,c.id current_club_id,c.name current_club
 FROM player_registrations r
 JOIN seasons s ON s.id=r.season_id AND s.code='2026/2027'
 JOIN players p ON p.id=r.player_id
 JOIN clubs c ON c.id=r.club_id
 JOIN player_roles pr ON pr.player_id=p.id AND pr.season_id=s.id AND pr.source='FANTACALCIO'
 WHERE r.registration_status='ACTIVE'
 ORDER BY p.id
) TO STDOUT WITH (FORMAT CSV,HEADER TRUE)
"@
$previous=$env:PGPASSWORD
try{$env:PGPASSWORD=$password;& psql -X -v ON_ERROR_STOP=1 -h $dbHost -p $dbPort -U $user -d $DatabaseName -c $sql | Set-Content -LiteralPath $output -Encoding utf8;if($LASTEXITCODE-ne 0){throw 'Context export failed'}}finally{if($null-eq$previous){Remove-Item Env:PGPASSWORD -EA SilentlyContinue}else{$env:PGPASSWORD=$previous}}
$rows=(Import-Csv $output).Count;if($rows-ne495){throw "Expected 495 context rows, got $rows"};Write-Output "current_player_context=$rows"
