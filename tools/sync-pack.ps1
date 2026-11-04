<#
Fills the dev run folders (run/client, run/client2, run/server) with the steam-n-punk-kingdom modpack, so
runClient, runClient2 and runServer test the tabletop mod alongside everything else in the pack.

It reads the pack's index.toml (run "packwiz refresh" in the pack first if you changed it), then:
- for each metafile (*.pw.toml: mods, resource packs, shader packs, seasonal mods) puts the file in the run
  folders for its side. Files come from the Prism instance that already has the pack downloaded, or are
  downloaded when the metafile has a direct URL. They are stored once in run/pack-cache and hard-linked.
- copies every other file (config, kubejs, local jars) as-is.
- turns off Neruina's built-in test crashes in its cached copy (see Disable-NeruinaTests).
- applies the dev settings at the end: creative/peaceful server with Dev and Dev2 as operators, and no first-launch
  screens in the clients so they can join by themselves.

The mods, resourcepacks, shaderpacks and seasonal folders of each run folder are rebuilt from scratch every
time. Any tabletop jar in the pack is skipped, so the mod always runs from this project's own build.

Run from the project folder:  powershell -ExecutionPolicy Bypass -File tools\sync-pack.ps1
#>
param(
    [string]$Pack = (Join-Path $PSScriptRoot '..\..\steam-n-punk-kingdom'),
    [string]$Instance = (Join-Path $env:APPDATA 'PrismLauncher\instances\Steam-N-Punk-Kingdom-testing\minecraft')
)
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$Pack = (Resolve-Path $Pack).Path
$run = Join-Path (Resolve-Path (Join-Path $PSScriptRoot '..')).Path 'run'
$cache = Join-Path $run 'pack-cache'
$runsBySide = @{ client = @('client', 'client2'); server = @('server') }
$allRuns = @('client', 'client2', 'server')

# index.toml is a list of [[files]] blocks, each with a path and, for metafiles, "metafile = true".
$index = Get-Content -Raw -LiteralPath (Join-Path $Pack 'index.toml')
$files = foreach ($block in ($index -split '\[\[files\]\]' | Select-Object -Skip 1)) {
    [pscustomobject]@{
        Path = [regex]::Match($block, '(?m)^file = "([^"]+)"').Groups[1].Value
        Meta = $block -match '(?m)^metafile = true'
    }
}

function Get-TomlValue([string]$toml, [string]$key) {
    $match = [regex]::Match($toml, "(?m)^$key = `"([^`"]*)`"")
    if ($match.Success) { $match.Groups[1].Value } else { $null }
}

# Folders that hold metafile downloads get rebuilt; config and kubejs are only overwritten, since mods add files there.
$rebuilt = @('mods') + ($files | Where-Object Meta | ForEach-Object { ($_.Path -split '/')[0] }) | Sort-Object -Unique
foreach ($runDir in $allRuns) {
    foreach ($folder in $rebuilt) {
        $path = Join-Path $run "$runDir\$folder"
        if (Test-Path $path) { Remove-Item -Recurse -Force -LiteralPath $path }
    }
}

Add-Type -AssemblyName System.IO.Compression.FileSystem

# Neruina ships "test" mixins that make every zombie, campfire, compass and farmland throw on purpose, and turns
# them on whenever the game isn't a normal install, which a dev run isn't. The first zombie then errors every tick
# until Neruina gives up and stops the server. Use a copy without them; the real pack never applies them.
# Returns the jar to use. The copy sits in run/pack-cache/patched, so a running game holding the original open
# doesn't get in the way.
function Disable-NeruinaTests([string]$jar) {
    $zip = [IO.Compression.ZipFile]::OpenRead($jar)
    try {
        $reader = New-Object IO.StreamReader($zip.GetEntry('neruina.mixins.json').Open())
        $config = $reader.ReadToEnd() | ConvertFrom-Json
        $reader.Dispose()
    } finally { $zip.Dispose() }
    $tests = @($config.mixins | Where-Object { $_ -like 'test.*' })
    if (-not $tests) { return $jar }
    $patched = Join-Path $cache "patched\$(Split-Path $jar -Leaf)"
    if (Test-Path -LiteralPath $patched) { return $patched }
    New-Item -ItemType Directory -Force -Path (Split-Path $patched) | Out-Null
    Copy-Item -LiteralPath $jar -Destination $patched
    $config.mixins = @($config.mixins | Where-Object { $_ -notlike 'test.*' })
    $zip = [IO.Compression.ZipFile]::Open($patched, 'Update')
    try {
        $zip.GetEntry('neruina.mixins.json').Delete()
        $writer = New-Object IO.StreamWriter($zip.CreateEntry('neruina.mixins.json').Open())
        $writer.Write(($config | ConvertTo-Json -Depth 5))
        $writer.Dispose()
    } finally { $zip.Dispose() }
    Write-Host "dev only: turned off Neruina's test mixins ($($tests -join ', '))"
    return $patched
}

function Place([string]$source, [string]$relative, [string[]]$runDirs, [switch]$Link) {
    foreach ($runDir in $runDirs) {
        $target = Join-Path $run "$runDir\$relative"
        New-Item -ItemType Directory -Force -Path (Split-Path $target) | Out-Null
        if ($Link) {
            try { New-Item -ItemType HardLink -Path $target -Target $source | Out-Null; continue } catch { }
        }
        Copy-Item -Force -LiteralPath $source -Destination $target
    }
}

$placed = 0
$missing = @()
foreach ($file in $files) {
    $folder = Split-Path $file.Path -Parent
    if (-not $file.Meta) {
        if ($file.Path -like 'mods/tabletop-*') { continue }
        Place (Join-Path $Pack $file.Path) $file.Path $allRuns
        continue
    }
    $toml = Get-Content -Raw -LiteralPath (Join-Path $Pack $file.Path)
    $name = Get-TomlValue $toml 'name'
    $filename = Get-TomlValue $toml 'filename'
    $side = Get-TomlValue $toml 'side'
    $url = Get-TomlValue $toml 'url'
    if ($filename -like 'tabletop-*') { continue }
    $relative = Join-Path $folder $filename
    $cached = Join-Path $cache $relative
    if (-not (Test-Path -LiteralPath $cached)) {
        New-Item -ItemType Directory -Force -Path (Split-Path $cached) | Out-Null
        $fromInstance = Join-Path $Instance $relative
        if (Test-Path -LiteralPath $fromInstance) {
            Copy-Item -LiteralPath $fromInstance -Destination $cached
        } elseif ($url) {
            Write-Host "downloading $filename"
            Invoke-WebRequest -UseBasicParsing -Uri $url -OutFile $cached
        } else {
            $missing += "$name ($relative)"
            continue
        }
    }
    if ($filename -like 'neruina-*') { $cached = Disable-NeruinaTests $cached }
    $runDirs =if ($side -eq 'both') { $allRuns } else { $runsBySide[$side] }
    Place $cached $relative $runDirs -Link
    $placed++
}

# --- Dev settings, applied every time ---------------------------------------------------------------------------
# The dev server: offline logins so the dev players (Dev, Dev2) can join, creative and peaceful so testers can take
# tabletop items from the creative tab and don't get killed while idle. Change the values here to test differently.
$serverSettings = [ordered]@{
    'online-mode' = 'false'; 'gamemode' = 'creative'; 'force-gamemode' = 'true'; 'difficulty' = 'peaceful'; 'spawn-protection' = '0'
}
# Dev players join the server by themselves (tools/play.ps1); vanilla's first-launch welcome screen and its
# multiplayer warning would stop them, so mark both as already seen.
$clientSettings = [ordered]@{ 'onboardAccessibility' = 'false'; 'skipMultiplayerWarning' = 'true'; 'joinedFirstServer' = 'true' }
$operators = @('Dev', 'Dev2')

# Sets "key<separator>value" lines in a settings file, keeping everything else.
function Set-Settings([string]$file, $settings, [string]$separator, [switch]$Create) {
    if (-not (Test-Path -LiteralPath $file)) {
        if (-not $Create) { return }
        $lines = @()
    } else {
        $lines = @(Get-Content -LiteralPath $file | Where-Object { $line = $_; -not ($settings.Keys | Where-Object { $line.StartsWith("$_$separator") }) })
    }
    $lines += $settings.Keys | ForEach-Object { "$_$separator$($settings[$_])" }
    [IO.File]::WriteAllLines($file, [string[]]$lines) # UTF-8 without a byte order mark, which Minecraft needs
}

# Offline players' UUIDs come from their name, worked out the same way the server does.
function Get-OfflineUuid([string]$name) {
    $hash = [Security.Cryptography.MD5]::Create().ComputeHash([Text.Encoding]::UTF8.GetBytes("OfflinePlayer:$name"))
    $hash[6] = ($hash[6] -band 0x0f) -bor 0x30
    $hash[8] = ($hash[8] -band 0x3f) -bor 0x80
    $hex = -join ($hash | ForEach-Object { $_.ToString('x2') })
    '{0}-{1}-{2}-{3}-{4}' -f $hex.Substring(0, 8), $hex.Substring(8, 4), $hex.Substring(12, 4), $hex.Substring(16, 4), $hex.Substring(20)
}

Set-Settings (Join-Path $run 'server\server.properties') $serverSettings '=' -Create
$opsFile = Join-Path $run 'server\ops.json'
# ForEach-Object unpacks the array, which Windows PowerShell's ConvertFrom-Json hands back as a single object.
$ops = @(if (Test-Path $opsFile) { Get-Content -Raw $opsFile | ConvertFrom-Json | ForEach-Object { $_ } | Where-Object { $_.name } })
foreach ($name in $operators) {
    if (-not ($ops | Where-Object { $_.name -eq $name })) {
        $ops += [pscustomobject]@{ uuid = (Get-OfflineUuid $name); name = $name; level = 4; bypassesPlayerLimit = $false }
    }
}
[IO.File]::WriteAllText($opsFile, (ConvertTo-Json -InputObject @($ops) -Depth 3))
foreach ($client in $runsBySide.client) {
    # The pack's default-options mod copies config/defaultoptions/options.txt into place on a client's first launch.
    Set-Settings (Join-Path $run "$client\config\defaultoptions\options.txt") $clientSettings ':'
    Set-Settings (Join-Path $run "$client\options.txt") $clientSettings ':'
}

Write-Host "Placed $placed pack files and copied $(@($files | Where-Object { -not $_.Meta }).Count) others into run/client, run/client2 and run/server."
if ($missing) {
    Write-Warning ("Not found in the Prism instance and no direct download link (CurseForge-only). Launch the " +
        "Steam-N-Punk-Kingdom-testing instance once so packwiz downloads them, then run this again:`n  " + ($missing -join "`n  "))
}
