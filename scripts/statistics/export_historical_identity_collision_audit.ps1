[CmdletBinding()]
param([ValidateSet('fantacalcio_intelligence_import_test','fantacalcio_intelligence')][string]$DatabaseName='fantacalcio_intelligence')
$ErrorActionPreference='Stop';$root=(Resolve-Path(Join-Path $PSScriptRoot '..\..')).Path;$content=Get-Content -Raw(Join-Path $root 'backend\src\main\resources\application.properties')
function Prop([string]$name){$m=[regex]::Match($content,"(?m)^\s*$([regex]::Escape($name))\s*=\s*(.+?)\s*$");if(-not$m.Success){throw"Missing $name"};$m.Groups[1].Value.Trim()}
function ResolveValue([string]$v){if($v-match'^\$\{([A-Za-z_][A-Za-z0-9_]*)(?::(.*))?\}$'){$e=[Environment]::GetEnvironmentVariable($Matches[1]);if(-not[string]::IsNullOrWhiteSpace($e)){return $e};if(-not[string]::IsNullOrWhiteSpace($Matches[2])){return $Matches[2]};return $null};return $v}
$url=ResolveValue (Prop 'spring.datasource.url');$user=ResolveValue (Prop 'spring.datasource.username');$password=ResolveValue (Prop 'spring.datasource.password');$jdbc=[regex]::Match($url,'^jdbc:postgresql://([^/:?]+)(?::([0-9]+))?/');if(-not$jdbc.Success){throw 'Invalid JDBC URL'};$dbHost=$jdbc.Groups[1].Value;$dbPort=if($jdbc.Groups[2].Success){$jdbc.Groups[2].Value}else{'5432'}
$query=@"
WITH created AS (
 SELECT m.player_id,m.external_id,ip.canonical_full_name,ip.birth_date
 FROM player_source_mappings m JOIN player_identity_profiles ip ON ip.player_id=m.player_id
 WHERE m.source='FANTACALCIO' AND m.external_id IN
  (SELECT source_external_id FROM player_identity_provenance WHERE source='FANTACALCIO' AND evidence->>'block'='48')
)
SELECT c.external_id fantacalcio_player_id,c.player_id,c.canonical_full_name,c.birth_date,
 (SELECT count(DISTINCT m.player_id) FROM player_source_mappings m WHERE m.source='FANTACALCIO' AND m.external_id=c.external_id) exact_fantacalcio_mapping_players,
 COALESCE((SELECT tm.external_id FROM player_source_mappings tm WHERE tm.player_id=c.player_id AND tm.source='FANTAMARKT' LIMIT 1),'') transfermarkt_id,
 (SELECT count(*) FROM player_identity_profiles o WHERE o.player_id<>c.player_id AND lower(o.canonical_full_name)=lower(c.canonical_full_name) AND o.birth_date=c.birth_date) exact_canonical_dob_other_players,
 (SELECT count(*) FROM player_identity_aliases a JOIN player_identity_profiles o ON o.player_id=a.player_id WHERE a.player_id<>c.player_id AND a.verified AND o.birth_date=c.birth_date AND lower(a.normalized_alias)=lower(regexp_replace(c.canonical_full_name,'[^[:alnum:] ]','','g'))) verified_alias_dob_other_players,
 CASE WHEN (SELECT count(DISTINCT m.player_id) FROM player_source_mappings m WHERE m.source='FANTACALCIO' AND m.external_id=c.external_id)<>1
        OR EXISTS(SELECT 1 FROM player_identity_profiles o WHERE o.player_id<>c.player_id AND lower(o.canonical_full_name)=lower(c.canonical_full_name) AND o.birth_date=c.birth_date)
        OR EXISTS(SELECT 1 FROM player_identity_aliases a JOIN player_identity_profiles o ON o.player_id=a.player_id WHERE a.player_id<>c.player_id AND a.verified AND o.birth_date=c.birth_date AND lower(a.normalized_alias)=lower(regexp_replace(c.canonical_full_name,'[^[:alnum:] ]','','g')))
      THEN 'REVIEW' ELSE 'CLEAR' END status,
 CASE WHEN NOT EXISTS(SELECT 1 FROM player_source_mappings tm WHERE tm.player_id=c.player_id AND tm.source='FANTAMARKT') THEN 'No certified Transfermarkt ID available; exact Fantacalcio, canonical+DOB and verified alias+DOB checks passed' ELSE 'All certified identity collision checks passed' END notes
FROM created c ORDER BY c.external_id
"@
$old=$env:PGPASSWORD;try{$env:PGPASSWORD=$password;$lines=& psql -X --csv -v ON_ERROR_STOP=1 -h $dbHost -p $dbPort -U $user -d $DatabaseName -c $query;if($LASTEXITCODE-ne0){throw 'Audit query failed'};$rows=$lines|ConvertFrom-Csv;if($rows.Count-ne485){throw "Expected 485 audit rows, got $($rows.Count)"};$path=Join-Path $root 'data\statistics\fantacalcio\historical-identity-collision-audit.csv';$rows|Export-Csv -LiteralPath $path -NoTypeInformation -Encoding utf8;Write-Output "collision_audit_rows=485 review=$(@($rows|Where-Object status -eq 'REVIEW').Count) clear=$(@($rows|Where-Object status -eq 'CLEAR').Count)"}finally{if($null-eq$old){Remove-Item Env:PGPASSWORD -EA SilentlyContinue}else{$env:PGPASSWORD=$old}}
