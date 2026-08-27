param(
    [ValidateSet("paper", "folia")]
    [string]$ServerType = "paper",
    [ValidateRange(30, 86400)]
    [int]$DurationSeconds = 60
)

$ErrorActionPreference = "Stop"
$root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$stagingRoot = Join-Path $root "build\staging-server"
$runId = Get-Date -Format "yyyyMMdd-HHmmss"
$serverDirectory = Join-Path $stagingRoot "real-$ServerType-$runId"
$serverJar = Join-Path $serverDirectory "server.jar"
$serverJarCache = Join-Path $stagingRoot "$ServerType-server.jar"
$pluginJar = Join-Path $serverDirectory "CotaniQuickStart.jar"
$java = (Get-Command java.exe -ErrorAction Stop).Source

New-Item -ItemType Directory -Force -Path $serverDirectory | Out-Null

& (Join-Path $root "gradlew.bat") :examples:classes :core:classes :task:classes --no-daemon
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

$headers = @{ "User-Agent" = "Cotani-real-plugin-staging/1.1.2 (https://github.com/HanielCota/Cotani)" }
$projectVersion = if ($ServerType -eq "paper") { "26.2" } else { "26.1.2" }
$builds = Invoke-RestMethod -Headers $headers -Uri "https://fill.papermc.io/v3/projects/$ServerType/versions/$projectVersion/builds"
$build = $builds | Where-Object channel -eq "STABLE" | Select-Object -First 1
if ($null -eq $build) {
    throw "Nenhum build estável encontrado para $ServerType $projectVersion."
}
$downloadUrl = $build.downloads.'server:default'.url
if (-not (Test-Path -LiteralPath $serverJarCache) -or $ServerType -eq "folia") {
    Write-Host "Baixando $ServerType $projectVersion build $($build.id)..."
    Invoke-WebRequest -Headers $headers -Uri $downloadUrl -OutFile $serverJarCache
}

Copy-Item $serverJarCache $serverJar -Force
Copy-Item (Join-Path $root "staging\eula.txt") (Join-Path $serverDirectory "eula.txt") -Force

if (Test-Path -LiteralPath $pluginJar) {
    Remove-Item -LiteralPath $pluginJar -Force
}

$exampleClasses = Join-Path $root "docs-examples\build\classes\java\main"
$exampleResources = Join-Path $root "docs-examples\build\resources\main"
$coreClasses = Join-Path $root "cotani-core\build\classes\java\main"
$taskClasses = Join-Path $root "cotani-task\build\classes\java\main"
& jar.exe --create --file $pluginJar `
    -C $exampleClasses com\example\cotaniquickstart `
    -C $exampleResources plugin.yml
& jar.exe --update --file $pluginJar -C $coreClasses .
& jar.exe --update --file $pluginJar -C $taskClasses .

$pluginsDirectory = Join-Path $serverDirectory "plugins"
New-Item -ItemType Directory -Force -Path $pluginsDirectory | Out-Null
Copy-Item $pluginJar (Join-Path $pluginsDirectory "CotaniQuickStart.jar") -Force

$processInfo = [System.Diagnostics.ProcessStartInfo]::new()
$processInfo.FileName = $java
$processInfo.Arguments = "-jar `"$serverJar`" --nogui"
$processInfo.WorkingDirectory = $serverDirectory
$processInfo.UseShellExecute = $false
$processInfo.CreateNoWindow = $true
$processInfo.RedirectStandardInput = $true
$serverProcess = [System.Diagnostics.Process]::new()
$serverProcess.StartInfo = $processInfo

try {
    $serverProcess.Start() | Out-Null
    Start-Sleep -Seconds 45
    if ($serverProcess.HasExited) {
        throw "O servidor $ServerType encerrou antes do smoke test do plugin real."
    }

    $serverProcess.StandardInput.WriteLine("cotanihello")
    $serverProcess.StandardInput.Flush()
    Start-Sleep -Seconds $DurationSeconds
    $serverProcess.StandardInput.WriteLine("stop")
    $serverProcess.StandardInput.Flush()
    if (-not $serverProcess.WaitForExit(60000)) {
        throw "O servidor $ServerType não encerrou graciosamente dentro de 60 segundos."
    }

    $latestLog = Join-Path $serverDirectory "logs\latest.log"
    if (-not (Test-Path -LiteralPath $latestLog)) {
        throw "O log do servidor $ServerType não foi encontrado."
    }
    $logContent = Get-Content -LiteralPath $latestLog -Raw
    foreach ($marker in @(
        "Enabling CotaniQuickStart",
        "Done (",
        "This command can only be used by a player."
    )) {
        if ($logContent -notmatch [regex]::Escape($marker)) {
            throw "O log do servidor $ServerType não contém o marcador esperado: $marker"
        }
    }
    if ($logContent -match "Could not load 'plugins/CotaniQuickStart.jar'|Error occurred while enabling CotaniQuickStart") {
        throw "O plugin real apresentou erro de carregamento/ativação no $ServerType."
    }
    Write-Host "$ServerType plugin real concluído após $DurationSeconds segundos. exit=$($serverProcess.ExitCode)"
    Write-Host "Log: $latestLog"
} finally {
    if (-not $serverProcess.HasExited) {
        $serverProcess.Kill($true)
        $serverProcess.WaitForExit()
    }
    $serverProcess.Dispose()
}
