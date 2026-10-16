[CmdletBinding()]
param(
    [string]$Season = "2026/2027",

    [string]$BaseUrl = "http://localhost:8080",

    [string]$AdminUsername = $env:ADMIN_USERNAME,

    [string]$AdminPassword = $env:ADMIN_PASSWORD,

    [switch]$DryRun,

    [switch]$SkipReconciliation,

    [switch]$Force,

    [ValidateSet(
        "FANTACALCIO",
        "GAZZETTA",
        "FANTAMARKT"
    )]
    [string[]]$OnlySource = @()
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$ScriptRoot = $PSScriptRoot
$ProjectRoot = Split-Path `
    (Split-Path $ScriptRoot -Parent) `
    -Parent

$OutputDirectory = Join-Path `
    $ScriptRoot `
    "output"

$LogsDirectory = Join-Path `
    $ScriptRoot `
    "logs"

$StateFile = Join-Path `
    $LogsDirectory `
    "player-import-state.json"

$ValidatorPath = Join-Path `
    $ScriptRoot `
    "validate_player_csv.py"

$Sources = @(
    @{
        Name = "FANTACALCIO"
        Pattern = "players-fantacalcio-*.csv"
    },
    @{
        Name = "GAZZETTA"
        Pattern = "players-gazzetta-*.csv"
    },
    @{
        Name = "FANTAMARKT"
        Pattern = "players-fantamarkt-*.csv"
    }
)

function ConvertTo-Hashtable {
    param(
        [Parameter(ValueFromPipeline)]
        $InputObject
    )

    process {
        if ($null -eq $InputObject) {
            return $null
        }

        if ($InputObject -is [System.Collections.IDictionary]) {
            $result = @{}

            foreach ($key in $InputObject.Keys) {
                $result[$key] = ConvertTo-Hashtable `
                    -InputObject $InputObject[$key]
            }

            return $result
        }

        if (
            $InputObject -is [System.Collections.IEnumerable] -and
            $InputObject -isnot [string]
        ) {
            $items = @()

            foreach ($item in $InputObject) {
                $items += ConvertTo-Hashtable `
                    -InputObject $item
            }

            return $items
        }

        if ($InputObject -is [PSCustomObject]) {
            $result = @{}

            foreach ($property in $InputObject.PSObject.Properties) {
                $result[$property.Name] = ConvertTo-Hashtable `
                    -InputObject $property.Value
            }

            return $result
        }

        return $InputObject
    }
}
function Write-Section {
    param(
        [Parameter(Mandatory)]
        [string]$Title
    )

    Write-Host ""
    Write-Host ("=" * 70) -ForegroundColor Cyan
    Write-Host $Title -ForegroundColor Yellow
    Write-Host ("=" * 70) -ForegroundColor Cyan
}

function Assert-Command {
    param(
        [Parameter(Mandatory)]
        [string]$Name
    )

    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "Comando non disponibile: $Name"
    }
}

function Get-BasicAuthorizationHeader {
    param(
        [Parameter(Mandatory)]
        [string]$Username,

        [Parameter(Mandatory)]
        [string]$Password
    )

    $plainText = "${Username}:${Password}"

    $bytes = [System.Text.Encoding]::UTF8.GetBytes(
        $plainText
    )

    $encoded = [Convert]::ToBase64String(
        $bytes
    )

    return "Basic $encoded"
}

function Read-State {
    if (-not (Test-Path $StateFile)) {
        return @{
            imports = @{}
        }
    }

    $raw = Get-Content `
        $StateFile `
        -Raw

    if ([string]::IsNullOrWhiteSpace($raw)) {
        return @{
            imports = @{}
        }
    }

    $object = $raw |
        ConvertFrom-Json |
        ConvertTo-Hashtable

    if (-not $object.ContainsKey("imports")) {
        $object["imports"] = @{}
    }

    return $object
}

function Save-State {
    param(
        [Parameter(Mandatory)]
        [hashtable]$State
    )

    New-Item `
        -ItemType Directory `
        -Path $LogsDirectory `
        -Force |
        Out-Null

    $json = $State |
        ConvertTo-Json `
            -Depth 20

    [System.IO.File]::WriteAllText(
        $StateFile,
        $json,
        [System.Text.UTF8Encoding]::new($false)
    )
}

function Get-LatestSourceFile {
    param(
        [Parameter(Mandatory)]
        [string]$Pattern
    )

    $files = @(
        Get-ChildItem `
            -Path $OutputDirectory `
            -Filter $Pattern `
            -File `
            -ErrorAction SilentlyContinue |
        Sort-Object `
            LastWriteTime `
            -Descending
    )

    if ($files.Count -eq 0) {
        return $null
    }

    return $files[0]
}

function Invoke-CsvValidation {
    param(
        [Parameter(Mandatory)]
        [string]$Source,

        [Parameter(Mandatory)]
        [System.IO.FileInfo]$File
    )

    $safeSeason = $Season.Replace("/", "-")

    $reportPath = Join-Path `
        $LogsDirectory `
        (
            "validation-" +
            $Source.ToLowerInvariant() +
            "-" +
            $safeSeason +
            ".json"
        )

    $validatorOutput = @(
        & python `
            $ValidatorPath `
            --file $File.FullName `
            --source $Source `
            --season $Season `
            --report $reportPath
    )

    $validatorExitCode = $LASTEXITCODE

    foreach ($outputLine in $validatorOutput) {
        Write-Host $outputLine
    }

    if ($validatorExitCode -ne 0) {
        throw (
            "Validazione fallita per " +
            "${Source}: $($File.FullName)"
        )
    }

    $validationReport = Get-Content `
        $reportPath `
        -Raw |
        ConvertFrom-Json |
        ConvertTo-Hashtable

    return $validationReport
}

function Test-Backend {
    $statusUrl = "$BaseUrl/api/v1/system/status"

    try {
        $response = Invoke-RestMethod `
            -Method Get `
            -Uri $statusUrl `
            -TimeoutSec 15

        if ($response.status -ne "UP") {
            throw (
                "Backend raggiungibile ma non UP. " +
                "Stato: $($response.status)"
            )
        }

        Write-Host `
            "Backend: UP" `
            -ForegroundColor Green
    }
    catch {
        throw (
            "Backend non raggiungibile su $statusUrl. " +
            $_.Exception.Message
        )
    }
}

function Invoke-PlayerImport {
    param(
        [Parameter(Mandatory)]
        [string]$Source,

        [Parameter(Mandatory)]
        [System.IO.FileInfo]$File,

        [Parameter(Mandatory)]
        [string]$Authorization
    )

    $escapedSource = [Uri]::EscapeDataString(
        $Source
    )

    $escapedSeason = [Uri]::EscapeDataString(
        $Season
    )

    $url = (
        "$BaseUrl/api/v1/imports/players/csv" +
        "?source=$escapedSource" +
        "&season=$escapedSeason"
    )

    $responseFile = New-TemporaryFile

    try {
        $arguments = @(
            "--silent",
            "--show-error",
            "--fail-with-body",
            "--request", "POST",
            "--header", "Authorization: $Authorization",
            "--form", "file=@$($File.FullName);type=text/csv",
            "--output", $responseFile.FullName,
            "--write-out", "%{http_code}",
            $url
        )

        $httpCode = & curl.exe @arguments

        $responseBody = Get-Content `
            $responseFile.FullName `
            -Raw

        if (
            $LASTEXITCODE -ne 0 -or
            [int]$httpCode -lt 200 -or
            [int]$httpCode -ge 300
        ) {
            throw (
                "Import $Source fallito. " +
                "HTTP=$httpCode. " +
                "Risposta=$responseBody"
            )
        }

        return $responseBody |
            ConvertFrom-Json |
            ConvertTo-Hashtable
    }
    finally {
        Remove-Item `
            $responseFile.FullName `
            -Force `
            -ErrorAction SilentlyContinue
    }
}

function Invoke-Reconciliation {
    param(
        [Parameter(Mandatory)]
        [string]$Authorization
    )

    $escapedSeason = [Uri]::EscapeDataString(
        $Season
    )

    $url = (
        "$BaseUrl/api/v1/reconciliation/players" +
        "?season=$escapedSeason"
    )

    try {
        return Invoke-RestMethod `
            -Method Post `
            -Uri $url `
            -Headers @{
                Authorization = $Authorization
            } `
            -TimeoutSec 180
    }
    catch {
        $details = $null

        if (
            $null -ne $_.ErrorDetails -and
            -not [string]::IsNullOrWhiteSpace(
                [string]$_.ErrorDetails.Message
            )
        ) {
            $details = [string]$_.ErrorDetails.Message
        }

        if (
            [string]::IsNullOrWhiteSpace($details) -and
            $null -ne $_.Exception
        ) {
            $details = [string]$_.Exception.Message
        }

        if ([string]::IsNullOrWhiteSpace($details)) {
            $details = "Errore sconosciuto"
        }

        throw (
            "Riconciliazione fallita: $details"
        )
    }
}

Write-Section "BLOCCO 7 - PIPELINE IMPORT GIOCATORI"

Assert-Command "python"
Assert-Command "curl.exe"

if (-not (Test-Path $ValidatorPath)) {
    throw "Validatore non trovato: $ValidatorPath"
}

New-Item `
    -ItemType Directory `
    -Path $LogsDirectory `
    -Force |
    Out-Null

Write-Host "Stagione: $Season"
Write-Host "Backend: $BaseUrl"
Write-Host "DryRun: $DryRun"
Write-Host "Force: $Force"
Write-Host "SkipReconciliation: $SkipReconciliation"

if ($OnlySource.Count -gt 0) {
    Write-Host (
        "OnlySource: " +
        ($OnlySource -join ", ")
    )
}
else {
    Write-Host "OnlySource: TUTTE"
}

Write-Section "RICERCA FILE NORMALIZZATI"

$availableSources = @()

foreach ($sourceConfiguration in $Sources) {
    if (
        $OnlySource.Count -gt 0 -and
        $OnlySource -notcontains $sourceConfiguration.Name
    ) {
        continue
    }

    $file = Get-LatestSourceFile `
        -Pattern $sourceConfiguration.Pattern

    if ($null -eq $file) {
        Write-Host (
            "$($sourceConfiguration.Name): " +
            "file non disponibile"
        ) -ForegroundColor DarkYellow

        continue
    }

    Write-Host (
        "$($sourceConfiguration.Name): " +
        $file.Name
    ) -ForegroundColor Green

    $availableSources += @{
        Source = $sourceConfiguration.Name
        File = $file
    }
}

if ($availableSources.Count -eq 0) {
    throw (
        "Nessun CSV normalizzato trovato in " +
        $OutputDirectory
    )
}

Write-Section "VALIDAZIONE FILE"

$validatedSources = @()

foreach ($item in $availableSources) {
    Write-Host ""
    Write-Host (
        "Validazione " +
        $item.Source
    ) -ForegroundColor Yellow

    $validation = Invoke-CsvValidation `
        -Source $item.Source `
        -File $item.File

    $validatedSources += @{
        Source = $item.Source
        File = $item.File
        Validation = $validation
    }
}

Write-Section "RIEPILOGO PRE-IMPORT"

foreach ($item in $validatedSources) {
    Write-Host (
        "$($item.Source): " +
        "$($item.Validation.total_rows) righe, " +
        "SHA-256 $($item.Validation.sha256)"
    )
}

if ($validatedSources.Count -lt 2) {
    if ($OnlySource.Count -gt 0) {
        Write-Host (
            "Import selettivo attivo. " +
            "La riconciliazione usera anche gli ultimi " +
            "batch COMPLETED disponibili nel backend."
        ) -ForegroundColor DarkYellow
    }
    else {
        Write-Warning (
            "E disponibile una sola fonte. " +
            "L'import puo essere eseguito, " +
            "ma la riconciliazione richiede almeno " +
            "due fonti compatibili."
        )
    }
}

if ($DryRun) {
    Write-Section "DRY RUN COMPLETATO"

    Write-Host (
        "Nessuna modifica e stata eseguita " +
        "nel database."
    ) -ForegroundColor Green

    exit 0
}

if (
    [string]::IsNullOrWhiteSpace($AdminUsername) -or
    [string]::IsNullOrWhiteSpace($AdminPassword)
) {
    throw @"
Credenziali amministrative mancanti.

Impostale nel terminale TEST SERVER:

`$env:ADMIN_USERNAME = "admin"
`$env:ADMIN_PASSWORD = "AdminDev2026!"
"@
}

Test-Backend

$authorization = Get-BasicAuthorizationHeader `
    -Username $AdminUsername `
    -Password $AdminPassword

$state = Read-State
$importResults = @()

Write-Section "IMPORT NEL BACKEND"

foreach ($item in $validatedSources) {
    $source = $item.Source
    $hash = $item.Validation.sha256

    $stateKey = (
        $Season +
        "|" +
        $source +
        "|" +
        $hash
    )

    if (
        -not $Force -and
        $state.imports.ContainsKey($stateKey)
    ) {
        $previous = $state.imports[$stateKey]

        Write-Host (
            "$source gia importato: " +
            "batchId=$($previous.batchId). " +
            "Import saltato."
        ) -ForegroundColor DarkYellow

        $importResults += $previous

        continue
    }

    Write-Host (
        "Importazione $source da " +
        $item.File.Name
    ) -ForegroundColor Yellow

    $response = Invoke-PlayerImport `
        -Source $source `
        -File $item.File `
        -Authorization $authorization

    if ($response.status -ne "COMPLETED") {
        throw (
            "Import $source non completato. " +
            "Stato=$($response.status), " +
            "errore=$($response.errorMessage)"
        )
    }

    if ([int]$response.invalidRows -gt 0) {
        throw (
            "Import $source completato con " +
            "$($response.invalidRows) righe non valide."
        )
    }

    $stateEntry = @{
        batchId = $response.batchId
        source = $response.source
        season = $response.season
        filename = $response.filename
        totalRows = $response.totalRows
        validRows = $response.validRows
        invalidRows = $response.invalidRows
        status = $response.status
        sha256 = $hash
        importedAt = (
            Get-Date
        ).ToString("o")
    }

    $state.imports[$stateKey] = $stateEntry
    Save-State -State $state

    $importResults += $stateEntry

    Write-Host (
        "$source importato: " +
        "batchId=$($response.batchId), " +
        "righe=$($response.validRows)"
    ) -ForegroundColor Green
}

Write-Section "RIEPILOGO IMPORT"

$importResults |
    ForEach-Object {
        [PSCustomObject]$_
    } |
    Select-Object `
        source,
        batchId,
        status,
        totalRows,
        validRows,
        invalidRows,
        filename |
    Format-Table -AutoSize

if ($SkipReconciliation) {
    Write-Section "PIPELINE COMPLETATA"

    Write-Host (
        "Import completato. " +
        "Riconciliazione saltata."
    ) -ForegroundColor Green

    exit 0
}

if (
    $validatedSources.Count -lt 2 -and
    $OnlySource.Count -eq 0
) {
    Write-Section "PIPELINE COMPLETATA PARZIALMENTE"

    Write-Host (
        "Import completato, ma riconciliazione " +
        "non eseguita perche e disponibile " +
        "una sola fonte."
    ) -ForegroundColor DarkYellow

    exit 0
}

Write-Section "RICONCILIAZIONE"

$reconciliation = Invoke-Reconciliation `
    -Authorization $authorization

$reconciliation |
    ConvertTo-Json `
        -Depth 10

if ($reconciliation.status -ne "COMPLETED") {
    throw (
        "Riconciliazione terminata con stato " +
        $reconciliation.status
    )
}

Write-Section "PIPELINE COMPLETATA"

Write-Host (
    "Giocatori riconciliati: " +
    $reconciliation.matchedPlayers
) -ForegroundColor Green

Write-Host (
    "Tre fonti: " +
    $reconciliation.matchedWithThreeSources
)

Write-Host (
    "Due fonti: " +
    $reconciliation.matchedWithTwoSources
)

Write-Host (
    "Esclusi perche presenti in una sola fonte: " +
    $reconciliation.excludedSingleSourcePlayers
)

Write-Host (
    "Creati: " +
    $reconciliation.createdPlayers
)

Write-Host (
    "Aggiornati: " +
    $reconciliation.updatedPlayers
)

