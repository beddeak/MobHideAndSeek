param(
    [switch]$Offline,
    [switch]$Deploy,
    [string]$ServerPath,
    [string]$JavaHome = 'C:\Program Files\Java\jdk-21',
    [string]$MavenPath
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$projectRoot = $PSScriptRoot
if (-not $MavenPath) {
    $mavenCommand = Get-Command mvn.cmd -ErrorAction SilentlyContinue
    if ($mavenCommand) { $MavenPath = $mavenCommand.Source }
    else { $MavenPath = 'C:\dev\RandomMorph\work\apache-maven-3.9.11\bin\mvn.cmd' }
}
if (-not (Test-Path -LiteralPath $MavenPath)) { throw 'Maven was not found. Pass -MavenPath.' }
if (-not (Test-Path -LiteralPath (Join-Path $JavaHome 'bin\java.exe'))) { throw 'Java 21 was not found. Pass -JavaHome.' }
$env:JAVA_HOME = $JavaHome
Push-Location $projectRoot
try {
    $mavenArguments = @('-B', '-ntp', "-Dmaven.repo.local=$projectRoot\.build-cache\m2")
    if ($Offline) { $mavenArguments += '-o' }
    # Windows PowerShell 5 turns native stderr warnings into ErrorRecords when redirected.
    # Maven's exit code, rather than harmless JVM warnings, determines build success.
    try {
        $ErrorActionPreference = 'Continue'
        & $MavenPath @mavenArguments clean verify
        $buildExit = $LASTEXITCODE
    } finally { $ErrorActionPreference = 'Stop' }
    if ($buildExit -ne 0) { throw 'Build or tests failed. Server was not changed.' }
    [xml]$pom = Get-Content -LiteralPath (Join-Path $projectRoot 'pom.xml') -Raw
    $version = $pom.project.version
    $artifact = Join-Path $projectRoot "MobHideAndSeek-$version.jar"
    Copy-Item -LiteralPath (Join-Path $projectRoot "target\mob-hide-and-seek-$version.jar") -Destination $artifact -Force
    Write-Output "Built $artifact"
    if ($Deploy) {
        if (-not $ServerPath) {
            $localSettings = Join-Path $projectRoot 'server.local.json'
            if (Test-Path -LiteralPath $localSettings) { $ServerPath = (Get-Content -LiteralPath $localSettings -Raw -Encoding UTF8 | ConvertFrom-Json).serverPath }
        }
        if (-not $ServerPath) { throw 'Pass -ServerPath or create server.local.json. The JAR was built.' }
        $serverRoot = (Resolve-Path -LiteralPath $ServerPath).Path
        $properties = Join-Path $serverRoot 'server.properties'
        if (-not (Test-Path -LiteralPath $properties)) { throw 'The target is not a Minecraft server directory.' }
        $levelLine = Get-Content -LiteralPath $properties | Where-Object { $_ -match '^level-name=' } | Select-Object -First 1
        $levelName = if ($levelLine) { $levelLine.Substring(11) } else { 'world' }
        $worldPath = [IO.Path]::GetFullPath((Join-Path $serverRoot $levelName))
        if (-not $worldPath.StartsWith($serverRoot.TrimEnd('\') + '\', [StringComparison]::OrdinalIgnoreCase)) { throw 'World path must be inside the selected server.' }
        $lockPath = Join-Path $worldPath 'session.lock'
        if (-not (Test-Path -LiteralPath $lockPath)) { throw 'Cannot verify that this server is stopped: world/session.lock was not found.' }
        if (Test-Path -LiteralPath $lockPath) {
            try { $lock = [IO.File]::Open($lockPath, [IO.FileMode]::Open, [IO.FileAccess]::ReadWrite, [IO.FileShare]::None); $lock.Dispose() }
            catch { throw 'Stop the Minecraft server before deploying. Its world is still locked.' }
        }
        $pluginRoot = Join-Path $serverRoot 'plugins'
        if (-not (Test-Path -LiteralPath $pluginRoot)) { throw 'The server plugins directory does not exist.' }
        $backup = Join-Path $serverRoot ('mobhide-backups\' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
        New-Item -ItemType Directory -Path $backup | Out-Null
        $pluginConfig = Join-Path $pluginRoot 'MobHideAndSeek'
        if (Test-Path -LiteralPath $pluginConfig) { Copy-Item -LiteralPath $pluginConfig -Destination $backup -Recurse }
        $oldJars = @(Get-ChildItem -LiteralPath $pluginRoot -Filter 'MobHideAndSeek*.jar' -File)
        foreach ($jar in $oldJars) {
            if ($jar.Directory.FullName -ne $pluginRoot) { throw 'Unexpected plugin path.' }
            Move-Item -LiteralPath $jar.FullName -Destination (Join-Path $backup $jar.Name)
        }
        try {
            $destination = Join-Path $pluginRoot ([IO.Path]::GetFileName($artifact))
            Copy-Item -LiteralPath $artifact -Destination $destination -Force
            if ((Get-FileHash -LiteralPath $artifact).Hash -ne (Get-FileHash -LiteralPath $destination).Hash) { throw 'Deployed JAR hash mismatch.' }
        } catch {
            if (Test-Path -LiteralPath $destination) { Remove-Item -LiteralPath $destination }
            foreach ($jar in $oldJars) { Copy-Item -LiteralPath (Join-Path $backup $jar.Name) -Destination $pluginRoot -Force }
            throw
        }
        Write-Output "Deployed $destination"
        Write-Output "Backup: $backup"
    }
} finally { Pop-Location }
