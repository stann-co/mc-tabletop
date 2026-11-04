<#
Runs one Gradle task in its own console window; tools/play.ps1 starts the server and each player with this.
The window closes when the game exits normally and stays open to show the error if it fails.
#>
param(
    [string]$Title,
    [string]$Task,
    # Makes a client join the server on this machine by itself (Gradle's -Pjoin, see build.gradle).
    [switch]$Join
)
$host.UI.RawUI.WindowTitle = $Title
# Same Java as tools/play.ps1 uses, for when this is started on its own.
if (-not $env:JAVA_HOME) {
    $env:JAVA_HOME = Join-Path $env:APPDATA 'PrismLauncher\java\java-runtime-delta'
}

# Clicking inside a console window normally starts selecting text, which freezes whatever is printing to it, and so
# the game it is starting. Turn that off for this window.
Add-Type -Namespace TabletopDev -Name ConsoleMode -MemberDefinition @'
[DllImport("kernel32.dll")] public static extern IntPtr GetStdHandle(int handle);
[DllImport("kernel32.dll")] public static extern bool GetConsoleMode(IntPtr handle, out uint mode);
[DllImport("kernel32.dll")] public static extern bool SetConsoleMode(IntPtr handle, uint mode);
'@
$stdin = [TabletopDev.ConsoleMode]::GetStdHandle(-10)
$mode = [uint32]0
if ([TabletopDev.ConsoleMode]::GetConsoleMode($stdin, [ref]$mode)) {
    # Clear QUICK_EDIT (0x40); EXTENDED_FLAGS (0x80) has to be set for that to take effect.
    [void][TabletopDev.ConsoleMode]::SetConsoleMode($stdin, [uint32](($mode -band 0xFFBF) -bor 0x80))
}

$gradleArgs = @($Task)
if ($Join) {
    $gradleArgs += '-Pjoin'
}
& (Join-Path $PSScriptRoot '..\gradlew.bat') @gradleArgs
if ($LASTEXITCODE) {
    Read-Host 'It stopped with an error (see above). Press Enter to close'
}
