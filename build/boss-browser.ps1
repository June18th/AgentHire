param(
    [ValidateSet("open", "search", "profile", "urls")]
    [string]$Action = "open",

    [string]$Query = "Java",

    [string[]]$Cities = @("beijing"),

    [string]$BrowserPath = "",

    [int]$DebugPort = 0
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$profileDir = Join-Path $root "workspace\browser-profiles\boss"

$cityCodes = @{}
$cityCodes["beijing"] = "101010100"
$cityCodes["tianjin"] = "101030100"
$cityCodes["shanghai"] = "101020100"
$cityCodes["chongqing"] = "101040100"
$cityCodes["hangzhou"] = "101210100"
$cityCodes["shenzhen"] = "101280600"
$cityCodes["guangzhou"] = "101280100"
$cityCodes["zhengzhou"] = "101180100"
$cityCodes["chengdu"] = "101270100"
$cityCodes["nanjing"] = "101190100"
$cityCodes["wuhan"] = "101200100"
$cityCodes["xian"] = "101110100"
$cityCodes["suzhou"] = "101190400"
$cityCodes["hefei"] = "101220100"
$cityCodes["changsha"] = "101250100"
$cityCodes["qingdao"] = "101120200"
$cityCodes["xiamen"] = "101230200"
$cityCodes["ningbo"] = "101210400"
$cityCodes[([string][char]0x5317 + [string][char]0x4EAC)] = "101010100"
$cityCodes[([string][char]0x5929 + [string][char]0x6D25)] = "101030100"
$cityCodes[([string][char]0x4E0A + [string][char]0x6D77)] = "101020100"
$cityCodes[([string][char]0x91CD + [string][char]0x5E86)] = "101040100"
$cityCodes[([string][char]0x676D + [string][char]0x5DDE)] = "101210100"
$cityCodes[([string][char]0x6DF1 + [string][char]0x5733)] = "101280600"
$cityCodes[([string][char]0x5E7F + [string][char]0x5DDE)] = "101280100"
$cityCodes[([string][char]0x90D1 + [string][char]0x5DDE)] = "101180100"
$cityCodes[([string][char]0x6210 + [string][char]0x90FD)] = "101270100"
$cityCodes[([string][char]0x5357 + [string][char]0x4EAC)] = "101190100"
$cityCodes[([string][char]0x6B66 + [string][char]0x6C49)] = "101200100"
$cityCodes[([string][char]0x897F + [string][char]0x5B89)] = "101110100"
$cityCodes[([string][char]0x82CF + [string][char]0x5DDE)] = "101190400"
$cityCodes[([string][char]0x5408 + [string][char]0x80A5)] = "101220100"
$cityCodes[([string][char]0x957F + [string][char]0x6C99)] = "101250100"
$cityCodes[([string][char]0x9752 + [string][char]0x5C9B)] = "101120200"
$cityCodes[([string][char]0x53A6 + [string][char]0x95E8)] = "101230200"
$cityCodes[([string][char]0x5B81 + [string][char]0x6CE2)] = "101210400"

function Resolve-BrowserPath {
    param([string]$PreferredPath)

    if ($PreferredPath -and (Test-Path -LiteralPath $PreferredPath)) {
        return (Resolve-Path -LiteralPath $PreferredPath).Path
    }

    if ($env:BOSS_BROWSER_PATH -and (Test-Path -LiteralPath $env:BOSS_BROWSER_PATH)) {
        return (Resolve-Path -LiteralPath $env:BOSS_BROWSER_PATH).Path
    }

    $candidates = @(
        "$env:ProgramFiles\Microsoft\Edge\Application\msedge.exe",
        "${env:ProgramFiles(x86)}\Microsoft\Edge\Application\msedge.exe",
        "$env:LocalAppData\Microsoft\Edge\Application\msedge.exe",
        "$env:ProgramFiles\Google\Chrome\Application\chrome.exe",
        "${env:ProgramFiles(x86)}\Google\Chrome\Application\chrome.exe",
        "$env:LocalAppData\Google\Chrome\Application\chrome.exe"
    )

    foreach ($candidate in $candidates) {
        if ($candidate -and (Test-Path -LiteralPath $candidate)) {
            return (Resolve-Path -LiteralPath $candidate).Path
        }
    }

    throw "Edge/Chrome was not found. Use -BrowserPath or set BOSS_BROWSER_PATH."
}

function New-BossSearchUrl {
    param(
        [string]$Keyword,
        [string]$City
    )

    if (-not $cityCodes.ContainsKey($City)) {
        throw "Unsupported city: $City. Add its code to build\boss-browser.ps1 cityCodes."
    }

    $encodedQuery = [uri]::EscapeDataString($Keyword)
    $cityCode = $cityCodes[$City]
    return "https://www.zhipin.com/web/geek/job?query=$encodedQuery&city=$cityCode"
}

function Normalize-Cities {
    param([string[]]$RawCities)

    $result = @()
    foreach ($rawCity in $RawCities) {
        if (-not $rawCity) {
            continue
        }
        $normalized = $rawCity.Replace(([string][char]0xFF0C), ",")
        $parts = $normalized -split ","
        foreach ($part in $parts) {
            $city = $part.Trim()
            if ($city) {
                $result += $city
            }
        }
    }
    return $result
}

# AIDEV-NOTE: Manual login browser profile
New-Item -ItemType Directory -Force -Path $profileDir | Out-Null

if ($Action -eq "profile") {
    Write-Host "BOSS browser profile: $profileDir"
    Write-Host "This stores your manual login session. Do not commit or share it."
    exit 0
}

$browser = Resolve-BrowserPath -PreferredPath $BrowserPath
$urls = @()

if ($Action -eq "open") {
    $urls += "https://www.zhipin.com/web/user/?ka=header-login"
} elseif ($Action -eq "search" -or $Action -eq "urls") {
    foreach ($city in (Normalize-Cities -RawCities $Cities)) {
        $urls += New-BossSearchUrl -Keyword $Query -City $city
    }
}

if ($Action -eq "urls") {
    $urls | ForEach-Object { Write-Host $_ }
    exit 0
}

$args = @(
    "--user-data-dir=$profileDir",
    "--profile-directory=Default",
    "--new-window"
)

if ($DebugPort -gt 0) {
    $args += "--remote-debugging-port=$DebugPort"
}

$args += $urls

Write-Host "Launching: $browser"
Write-Host "Profile: $profileDir"
Write-Host "URLs:"
$urls | ForEach-Object { Write-Host "  $_" }

Start-Process -FilePath $browser -ArgumentList $args
