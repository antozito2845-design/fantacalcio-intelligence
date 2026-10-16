[CmdletBinding()]
param(
    [string]$Season = "2026/2027",

    [string]$BaseUrl = "http://localhost:8080",

    [ValidateSet(
        "auto",
        "chrome",
        "edge"
    )]
    [string]$Browser = "auto",

    [int]$BrowserTimeout = 180,

    [switch]$SkipPipeline
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$ScriptRoot = $PSScriptRoot

$NormalizerPath = Join-Path `
    $ScriptRoot `
    "normalize_fantamarkt.py"

$ValidatorPath = Join-Path `
    $ScriptRoot `
    "validate_player_csv.py"

$PipelinePath = Join-Path `
    $ScriptRoot `
    "run_player_import_pipeline.ps1"

$OutputPath = Join-Path `
    $ScriptRoot `
    "output\players-fantamarkt-2026-2027.csv"

$ReportPath = Join-Path `
    $ScriptRoot `
    "logs\fantamarkt-normalization-report.json"

$ValidationReportPath = Join-Path `
    $ScriptRoot `
    "logs\validation-fantamarkt-2026-2027.json"


function Write-Section {

    param(
        [Parameter(Mandatory)]
        [string]$Title
    )

    Write-Host ""
    Write-Host ("=" * 72) -ForegroundColor Cyan
    Write-Host $Title -ForegroundColor Yellow
    Write-Host ("=" * 72) -ForegroundColor Cyan
}


function Assert-FileExists {

    param(
        [Parameter(Mandatory)]
        [string]$Path
    )

    if (-not (Test-Path $Path)) {

        throw "File richiesto non trovato: $Path"
    }
}


Write-Section "BLOCCO 40 - FANTAMARKT REFRESH"

Write-Host "Stagione: $Season"
Write-Host "Backend: $BaseUrl"
Write-Host "Browser: $Browser"
Write-Host "BrowserTimeout: $BrowserTimeout"

Assert-FileExists $NormalizerPath
Assert-FileExists $ValidatorPath
Assert-FileExists $PipelinePath


Write-Section "0 - CONTROLLO SELENIUM"

& python -c "import selenium"

if ($LASTEXITCODE -ne 0) {

    throw @"
Selenium non e installato.

Nel terminale TEST SERVER esegui:

python -m pip install selenium

Poi rilancia questo script.
"@
}

Write-Host "Selenium disponibile." -ForegroundColor Green


Write-Section "1 - ACQUISIZIONE FANTAMARKT"

Write-Host (
    "Verra aperto un browser visibile. " +
    "Se Transfermarkt richiede una verifica, " +
    "completala manualmente nella finestra del browser."
) -ForegroundColor DarkYellow

& python `
    $NormalizerPath `
    --fetch-mode browser `
    --browser $Browser `
    --browser-timeout $BrowserTimeout

$normalizerExitCode = $LASTEXITCODE

if ($normalizerExitCode -ne 0) {

    throw (
        "Normalizzazione Fantamarkt fallita. " +
        "Nessun import e stato eseguito."
    )
}


if (-not (Test-Path $OutputPath)) {

    throw (
        "Il normalizzatore e terminato senza creare " +
        "il CSV Fantamarkt."
    )
}


Write-Section "2 - VALIDAZIONE CSV"

& python `
    $ValidatorPath `
    --file $OutputPath `
    --source FANTAMARKT `
    --season $Season `
    --report $ValidationReportPath

$validatorExitCode = $LASTEXITCODE

if ($validatorExitCode -ne 0) {

    throw (
        "Validazione Fantamarkt fallita. " +
        "Il CSV NON verra importato."
    )
}


Write-Section "3 - CONTROLLO REPORT"

$validation = Get-Content `
    $ValidationReportPath `
    -Raw |
    ConvertFrom-Json

if (-not $validation.valid) {

    throw (
        "Il report di validazione indica valid=false. " +
        "Import bloccato."
    )
}

if ($validation.invalid_rows -ne 0) {

    throw (
        "Sono presenti righe Fantamarkt non valide: " +
        $validation.invalid_rows
    )
}

Write-Host (
    "CSV Fantamarkt valido: " +
    $validation.total_rows +
    " giocatori"
) -ForegroundColor Green


if ($SkipPipeline) {

    Write-Section "COMPLETATO SENZA IMPORT"

    Write-Host (
        "Acquisizione e validazione completate. " +
        "Pipeline non eseguita per richiesta."
    ) -ForegroundColor Green

    exit 0
}


Write-Section "4 - CONTROLLO CREDENZIALI"

if (
    [string]::IsNullOrWhiteSpace(
        $env:ADMIN_USERNAME
    ) -or
    [string]::IsNullOrWhiteSpace(
        $env:ADMIN_PASSWORD
    )
) {

    throw @"
Credenziali amministrative mancanti.

Nel terminale TEST SERVER esegui:

`$env:ADMIN_USERNAME = "admin"
`$env:ADMIN_PASSWORD = "<password admin>"

Poi rilancia lo script.
"@
}


Write-Section "5 - PIPELINE IMPORT"

& $PipelinePath `
    -Season $Season `
    -BaseUrl $BaseUrl `
    -AdminUsername $env:ADMIN_USERNAME `
    -AdminPassword $env:ADMIN_PASSWORD `
    -OnlySource FANTAMARKT `
    -Force

$pipelineExitCode = $LASTEXITCODE

if ($pipelineExitCode -ne 0) {

    throw (
        "Pipeline player-import fallita. " +
        "Exit code: " +
        $pipelineExitCode
    )
}


Write-Section "BLOCCO 40 FANTAMARKT COMPLETATO"

Write-Host (
    "Fantamarkt acquisito, validato, importato " +
    "e passato alla riconciliazione."
) -ForegroundColor Green

Write-Host ""
Write-Host "CSV:"
Write-Host $OutputPath

Write-Host ""
Write-Host "Report normalizzazione:"
Write-Host $ReportPath

Write-Host ""
Write-Host "Report validazione:"
Write-Host $ValidationReportPath
