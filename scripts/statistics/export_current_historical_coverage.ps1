[CmdletBinding()]
param(
 [ValidateSet('fantacalcio_intelligence_import_test','fantacalcio_intelligence')]
 [string]$DatabaseName='fantacalcio_intelligence_import_test'
)
$ErrorActionPreference='Stop';$root=(Resolve-Path(Join-Path $PSScriptRoot '..\..')).Path
$props=@{};Get-Content(Join-Path $root 'backend\src\main\resources\application.properties')|ForEach-Object{if($_-match'^([^#=]+)=(.*)$'){$props[$matches[1].Trim()]=$matches[2].Trim()}}
function Def([string]$v,[string]$n){if($v-match('^\$\{'+[regex]::Escape($n)+':(.*)\}$')){return $matches[1]};return $v}
$url=Def $props['spring.datasource.url'] 'DATABASE_URL';$user=Def $props['spring.datasource.username'] 'DATABASE_USERNAME';$password=Def $props['spring.datasource.password'] 'DATABASE_PASSWORD'
if($url-notmatch'^jdbc:postgresql://([^/:?]+)(?::([0-9]+))?/([^?]+)'){throw'JDBC URL non supportata'};$dbHost=$matches[1];$dbPort=if($matches[2]){$matches[2]}else{'5432'}
$query=@"
WITH current_players AS (
 SELECT p.id player_id,p.display_name player_name,c.name current_club
 FROM player_registrations r JOIN seasons s ON s.id=r.season_id
 JOIN players p ON p.id=r.player_id JOIN clubs c ON c.id=r.club_id
 WHERE s.code='2026/2027' AND r.registration_status='ACTIVE'
), coverage AS (
 SELECT cp.*,
  max(f.external_id) AS fantacalcio_id,
  replace(max(tm.external_id),'TRANSFERMARKT-','') AS transfermarkt_id,
  bool_or(s.code='2024/2025' AND st.source='FANTACALCIO' AND st.appearances>0) AS has_24,
  bool_or(s.code='2025/2026' AND st.source='FANTACALCIO' AND st.appearances>0) AS has_25
 FROM current_players cp
 LEFT JOIN player_source_mappings f ON f.player_id=cp.player_id AND f.source='FANTACALCIO'
 LEFT JOIN player_source_mappings tm ON tm.player_id=cp.player_id AND tm.source='FANTAMARKT'
 LEFT JOIN player_season_statistics st ON st.player_id=cp.player_id AND st.source='FANTACALCIO'
 LEFT JOIN seasons s ON s.id=st.season_id AND s.code IN ('2024/2025','2025/2026')
 GROUP BY cp.player_id,cp.player_name,cp.current_club
)
SELECT player_id,player_name,current_club,fantacalcio_id,transfermarkt_id,
 CASE WHEN has_24 THEN 'YES' ELSE 'NO' END has_fantacalcio_2024_25,
 CASE WHEN has_25 THEN 'YES' ELSE 'NO' END has_fantacalcio_2025_26,
 CASE WHEN has_24 AND has_25 THEN 'NO' ELSE 'YES' END needs_fbref_history,
 concat_ws('|',CASE WHEN NOT has_24 THEN '2024/2025' END,CASE WHEN NOT has_25 THEN '2025/2026' END) historical_seasons_missing,
 '' previous_clubs,'' previous_competitions,
 CASE WHEN has_24 AND has_25 THEN 'COMPLETE_FANTACALCIO' ELSE 'FBREF_ACQUISITION_BLOCKED' END resolution_status,
 CASE WHEN has_24 AND has_25 THEN '' ELSE 'Identity certified before FBref; public FBref HTTP acquisition returned 403' END notes
FROM coverage ORDER BY player_id
"@
$old=$env:PGPASSWORD;try{$env:PGPASSWORD=$password;$lines=& psql -X --csv -v ON_ERROR_STOP=1 -h $dbHost -p $dbPort -U $user -d $DatabaseName -c $query;if($LASTEXITCODE-ne0){ throw 'Coverage export fallito' };$rows=$lines|ConvertFrom-Csv;if($rows.Count-ne495){ throw "Expected 495 current operational, got $($rows.Count)" };$rows|Export-Csv -LiteralPath(Join-Path $root 'data\statistics\current-2026-2027-historical-coverage.csv') -NoTypeInformation -Encoding utf8;Write-Output "coverage_rows=$($rows.Count) needs_fbref=$(@($rows|Where-Object needs_fbref_history -eq 'YES').Count)"}finally{if($null-eq$old){Remove-Item Env:PGPASSWORD -EA SilentlyContinue}else{$env:PGPASSWORD=$old}}
