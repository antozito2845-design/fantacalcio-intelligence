[CmdletBinding()]
param([ValidateSet('fantacalcio_intelligence_import_test','fantacalcio_intelligence')][string]$DatabaseName='fantacalcio_intelligence_import_test',[switch]$Preflight)
$ErrorActionPreference='Stop';$root=(Resolve-Path(Join-Path $PSScriptRoot '..\..')).Path
if(Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue){throw 'Backend in ascolto sulla porta 8080'}
$config=Get-Content -Raw (Join-Path $root 'backend\src\main\resources\application.properties')
function Prop($n){$m=[regex]::Match($config,"(?m)^\s*$([regex]::Escape($n))\s*=\s*(.+?)\s*$");if(!$m.Success){throw "Missing $n"};$m.Groups[1].Value.Trim()}
function Val($v){if($v-match'^\$\{([A-Za-z_][A-Za-z0-9_]*)(?::(.*))?\}$'){$x=[Environment]::GetEnvironmentVariable($Matches[1]);if($x){return $x};return $Matches[2]};$v}
$url=Val(Prop 'spring.datasource.url');$user=Val(Prop 'spring.datasource.username');$password=Val(Prop 'spring.datasource.password');$m=[regex]::Match($url,'^jdbc:postgresql://([^/:?]+)(?::([0-9]+))?/');$hostName=$m.Groups[1].Value;$port=if($m.Groups[2].Success){$m.Groups[2].Value}else{'5432'};$old=$env:PGPASSWORD
try{$env:PGPASSWORD=$password;Push-Location $root;try{& psql -X -v ON_ERROR_STOP=1 -v "preflight=$(if($Preflight){'true'}else{'false'})" -h $hostName -p $port -U $user -d $DatabaseName -f scripts/statistics/certified_fantasy_player_value_import.sql;if($LASTEXITCODE){throw 'psql failed'}}finally{Pop-Location}}finally{if($null-eq$old){Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue}else{$env:PGPASSWORD=$old}}
