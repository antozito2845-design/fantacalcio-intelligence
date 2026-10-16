[CmdletBinding()]
param()
$ErrorActionPreference='Stop'
$root=(Resolve-Path(Join-Path $PSScriptRoot '..\..')).Path
$database='fantacalcio_intelligence_import_test'
$runner=Join-Path $PSScriptRoot 'run_certified_fantacalcio_history_import.ps1'
$stateSql=Join-Path $PSScriptRoot 'certified_fantacalcio_history_state.sql'
$content=Get-Content -Raw (Join-Path $root 'backend\src\main\resources\application.properties')
function Prop([string]$name){$m=[regex]::Match($content,"(?m)^\s*$([regex]::Escape($name))\s*=\s*(.+?)\s*$");if(-not $m.Success){throw "Missing $name"};return $m.Groups[1].Value.Trim()}
function ResolveValue([string]$value){if($value-match'^\$\{([A-Za-z_][A-Za-z0-9_]*)(?::(.*))?\}$'){$e=[Environment]::GetEnvironmentVariable($Matches[1]);if(-not[string]::IsNullOrWhiteSpace($e)){return $e};if(-not[string]::IsNullOrWhiteSpace($Matches[2])){return $Matches[2]};return $null};return $value}
$url=ResolveValue (Prop 'spring.datasource.url');$user=ResolveValue (Prop 'spring.datasource.username');$password=ResolveValue (Prop 'spring.datasource.password')
$jdbc=[regex]::Match($url,'^jdbc:postgresql://([^/:?]+)(?::([0-9]+))?/');if(-not $jdbc.Success){throw 'Invalid JDBC URL'};$dbHost=$jdbc.Groups[1].Value;$dbPort=if($jdbc.Groups[2].Success){$jdbc.Groups[2].Value}else{'5432'};$psql=(Get-Command psql -EA Stop).Source
$previous=$env:PGPASSWORD
function Scalar([string]$sql){$v=& $psql -X -v ON_ERROR_STOP=1 -h $dbHost -p $dbPort -U $user -d $database -At -c $sql;if($LASTEXITCODE -ne 0){throw 'psql scalar failed'};return ($v|Select-Object -First 1)}
function Exec([string]$sql){& $psql -X -v ON_ERROR_STOP=1 -h $dbHost -p $dbPort -U $user -d $database -q -c $sql;if($LASTEXITCODE -ne 0){throw 'psql update failed'}}
function Expect-Conflict([string]$field){
 $id=Scalar "SELECT id FROM player_season_statistics WHERE source='FANTACALCIO' AND $field IS NOT NULL ORDER BY id LIMIT 1";$original=Scalar "SELECT $field FROM player_season_statistics WHERE id=$id"
 try{Exec "UPDATE player_season_statistics SET $field=$field+1 WHERE id=$id";$rejected=$false;try{& $runner -DatabaseName $database -Preflight *> $null}catch{$rejected=$true};if(-not $rejected){throw "Conflict not rejected for $field"}}
 finally{Exec "UPDATE player_season_statistics SET $field=$original WHERE id=$id"}
 Write-Output "CONFLICT PROBE PASS: $field"
}
try{
 $env:PGPASSWORD=$password;$before=&$psql -X -v ON_ERROR_STOP=1 -h $dbHost -p $dbPort -U $user -d $database -At -f $stateSql
 foreach($field in @('assists','yellow_cards','penalties_taken','penalties_saved')){Expect-Conflict $field}
 $roleId=Scalar "SELECT pr.id FROM player_roles pr JOIN player_identity_provenance pp ON pp.player_id=pr.player_id WHERE pr.source='FANTACALCIO' AND pp.source='FANTACALCIO' AND pp.evidence->>'block'='48' ORDER BY pr.id LIMIT 1";$oldRole=Scalar "SELECT fantasy_role FROM player_roles WHERE id=$roleId";$newRole=if($oldRole -ne 'P'){'P'}else{'D'}
 try{Exec "UPDATE player_roles SET fantasy_role='$newRole' WHERE id=$roleId";$rejected=$false;try{& $runner -DatabaseName $database -Preflight *> $null}catch{$rejected=$true};if(-not $rejected){throw 'Role conflict not rejected'}}finally{Exec "UPDATE player_roles SET fantasy_role='$oldRole' WHERE id=$roleId"};Write-Output 'CONFLICT PROBE PASS: historical role'
 & $runner -DatabaseName $database -Preflight *> $null;Write-Output 'IDEMPOTENCY PROBE PASS: equal values'
 $nullId=Scalar "SELECT id FROM player_season_statistics WHERE source='FANTACALCIO' AND starts IS NULL ORDER BY id LIMIT 1"
 try{Exec "UPDATE player_season_statistics SET starts=0 WHERE id=$nullId";&$runner -DatabaseName $database -Preflight *> $null;&$runner -DatabaseName $database *> $null;$preserved=Scalar "SELECT starts FROM player_season_statistics WHERE id=$nullId";if($preserved-ne'0'){throw'Incoming NULL did not preserve existing value'}}finally{Exec "UPDATE player_season_statistics SET starts=NULL WHERE id=$nullId"};Write-Output 'NULL PROBE PASS: incoming NULL preserves existing'
 $after=&$psql -X -v ON_ERROR_STOP=1 -h $dbHost -p $dbPort -U $user -d $database -At -f $stateSql
 if(($before -join "`n") -ne ($after -join "`n")){throw 'Sandbox probe did not restore exact logical/sequence state'}
 Write-Output 'HARDENING SANDBOX PROBES: PASS (state restored)'
}finally{if($null-eq$previous){Remove-Item Env:PGPASSWORD -EA SilentlyContinue}else{$env:PGPASSWORD=$previous}}
