[CmdletBinding()]
param(
 [ValidateSet('fantacalcio_intelligence_import_test','fantacalcio_intelligence')]
 [string]$DatabaseName='fantacalcio_intelligence_import_test',
 [switch]$Preflight,
 [switch]$SandboxProjection
)
$ErrorActionPreference='Stop'
$root=(Resolve-Path(Join-Path $PSScriptRoot '..\..')).Path
$configPath=Join-Path $root 'backend\src\main\resources\application.properties'
$sqlPath=Join-Path $PSScriptRoot 'certified_player_performance_import.sql'
$statePath=Join-Path $PSScriptRoot 'player_performance_state.sql'
$profilePath=Join-Path $root 'data\statistics\features\player-performance-import.csv'
$sandboxProfilePath=Join-Path $root 'data\statistics\features\player-performance-import-sandbox.csv'
$definitionPath=Join-Path $root 'data\statistics\features\player-performance-feature-definitions.csv'
foreach($path in @($configPath,$sqlPath,$statePath,$profilePath,$definitionPath)){
 if(-not(Test-Path -LiteralPath $path -PathType Leaf)){throw "File richiesto non trovato: $path"}
}
if($SandboxProjection-and$DatabaseName-ne'fantacalcio_intelligence_import_test'){throw 'SandboxProjection consentita solo sul database import_test'}
if($SandboxProjection-and-not(Test-Path -LiteralPath $sandboxProfilePath -PathType Leaf)){throw 'Artefatto di proiezione sandbox mancante'}
if(Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue){throw 'Backend in ascolto sulla porta 8080: arrestarlo prima dell import.'}
function Property([string]$name){$content=Get-Content -Raw -LiteralPath $configPath;$m=[regex]::Match($content,"(?m)^\s*$([regex]::Escape($name))\s*=\s*(.+?)\s*$");if(-not$m.Success){throw "Missing $name"};$m.Groups[1].Value.Trim()}
function ResolveValue([string]$raw){if($raw-match'^\$\{([A-Za-z_][A-Za-z0-9_]*)(?::(.*))?\}$'){$v=[Environment]::GetEnvironmentVariable($Matches[1]);if(-not[string]::IsNullOrWhiteSpace($v)){return $v};if(-not[string]::IsNullOrWhiteSpace($Matches[2])){return $Matches[2]};return $null};return $raw}
$url=ResolveValue(Property 'spring.datasource.url');$dbUser=ResolveValue(Property 'spring.datasource.username');$password=ResolveValue(Property 'spring.datasource.password')
$jdbc=[regex]::Match($url,'^jdbc:postgresql://([^/:?]+)(?::([0-9]+))?/');if(-not$jdbc.Success){throw 'Invalid JDBC URL'}
$dbHost=$jdbc.Groups[1].Value;$dbPort=if($jdbc.Groups[2].Success){$jdbc.Groups[2].Value}else{'5432'};$psql=(Get-Command psql -ErrorAction Stop).Source
function Invoke-State{ $o=&$psql -X -v ON_ERROR_STOP=1 -h $dbHost -p $dbPort -U $dbUser -d $DatabaseName -At -f $statePath;if($LASTEXITCODE-ne 0-or@($o).Count-ne 3){throw 'Snapshot stato fallito'};@($o)}
$previous=$env:PGPASSWORD
try{
 $env:PGPASSWORD=$password
 $ready=&$psql -X -v ON_ERROR_STOP=1 -h $dbHost -p $dbPort -U $dbUser -d $DatabaseName -At -c "SELECT to_regclass('public.player_performance_feature_profiles') IS NOT NULL"
 if($LASTEXITCODE-ne 0-or$ready-ne't'){throw 'Migration V48 non applicata al database target.'}
 $before=Invoke-State
 Push-Location $root
 try{&$psql -X -v ON_ERROR_STOP=1 -v "preflight=$(if($Preflight){'true'}else{'false'})" -v "sandbox_projection=$(if($SandboxProjection){'true'}else{'false'})" -h $dbHost -p $dbPort -U $dbUser -d $DatabaseName -f $sqlPath;if($LASTEXITCODE-ne 0){throw "psql fallito (ExitCode $LASTEXITCODE)"}}finally{Pop-Location}
 $after=Invoke-State
 if($Preflight-and(($before-join"`n")-ne($after-join"`n"))){throw 'Preflight mutante'}
 if(($before|Where-Object{$_-like'RAW|*'})-ne($after|Where-Object{$_-like'RAW|*'})){throw 'Raw data mutated by feature import'}
 Write-Output "PLAYER PERFORMANCE $(if($Preflight){'PREFLIGHT'}else{'IMPORT'}) CERTIFICATO: PASS"
 Write-Output ($after-join'; ')
}finally{if($null-eq$previous){Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue}else{$env:PGPASSWORD=$previous}}
