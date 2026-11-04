<#
Starts a local test session of the tabletop mod inside the steam-n-punk-kingdom modpack: the dev server, then one or
two players that join it automatically (Dev and Dev2), in creative mode. Close a game window to quit that player;
close the "Minecraft server" window (or type stop in it) to stop the server and save the world. Each also has a
minimized "Tabletop dev ..." console on the taskbar showing its output; it stays open with the error if one fails.

Run from the project folder:
    powershell -ExecutionPolicy Bypass -File tools\play.ps1              server + 2 players
    powershell -ExecutionPolicy Bypass -File tools\play.ps1 -Players 1   server + 1 player
    powershell -ExecutionPolicy Bypass -File tools\play.ps1 -Sync        update the modpack first (after pack changes)
The modpack is set up automatically the first time.
#>
param(
    [ValidateRange(0, 2)][int]$Players = 2,
    [switch]$Sync
)
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Set-Location $root

# Gradle and Minecraft need Java 21. Unless one is already set, use the one Prism Launcher installs for Minecraft.
if (-not $env:JAVA_HOME) {
    $env:JAVA_HOME = Join-Path $env:APPDATA 'PrismLauncher\java\java-runtime-delta'
}
if (-not (Test-Path (Join-Path $env:JAVA_HOME 'bin\javac.exe'))) {
    throw "No Java 21 JDK at $env:JAVA_HOME. Set JAVA_HOME to a Java 21 JDK and try again."
}

if ($Sync -or -not (Test-Path 'run\server\mods')) {
    & (Join-Path $PSScriptRoot 'sync-pack.ps1')
}

# Compile once up front, so the server and players don't all try to build at the same moment.
Write-Host 'Building...'
& .\gradlew.bat classes devtoolsClasses --quiet
if ($LASTEXITCODE) { throw 'Build failed, see above.' }

# Opens a minimized console window running a Gradle task (see run-task.ps1). The games open their own windows.
function Start-Window([string]$title, [string]$task, [switch]$Join) {
    # Windows PowerShell passes these on without quoting them, so keep spaces out: the script path is relative to the
    # project folder (whose full path has a space in it) and the title is quoted by hand.
    $arguments = @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', 'tools\run-task.ps1', '-Title', "`"$title`"", '-Task', $task)
    if ($Join) {
        $arguments += '-Join'
    }
    Start-Process powershell -WorkingDirectory $root -WindowStyle Minimized -ArgumentList $arguments
}

$listening = Get-NetTCPConnection -LocalPort 25565 -State Listen -ErrorAction SilentlyContinue
if ($listening) {
    Write-Host 'A server is already running on this machine; the players will join that one.'
} else {
    Write-Host 'Starting the server...'
    $started = Get-Date
    Start-Window 'Tabletop dev server' 'runServer'
    $log = 'run\server\logs\latest.log'
    $deadline = $started.AddMinutes(10)
    while ($true) {
        Start-Sleep -Seconds 3
        $fresh = (Test-Path $log) -and (Get-Item $log).LastWriteTime -gt $started
        if ($fresh -and (Select-String -Path $log -Pattern 'Done \(' -Quiet)) { break }
        if ($fresh -and (Select-String -Path $log -Pattern 'Failed to start|crash report has been saved' -Quiet)) {
            throw "The server failed to start. See its window, or $root\$log"
        }
        if ((Get-Date) -gt $deadline) { throw "The server didn't start within 10 minutes. See its window, or $root\$log" }
    }
    Write-Host "Server ready after $([int]((Get-Date) - $started).TotalSeconds)s."
}

$tasks = @('runClient', 'runClient2') | Select-Object -First $Players
foreach ($task in $tasks) {
    Write-Host "Starting player $($tasks.IndexOf($task) + 1); it joins by itself once loaded (a few minutes with the full pack)."
    Start-Window "Tabletop dev $task" $task -Join
}
