param(
    [ValidateSet("paper", "folia")]
    [string]$ServerType = "paper",
    [ValidateRange(30, 86400)]
    [int]$DurationSeconds = 120
)

$ErrorActionPreference = "Stop"
$root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$stagingRoot = Join-Path $root "build\staging-server"
$runId = Get-Date -Format "yyyyMMdd-HHmmss"
$serverDirectory = Join-Path $stagingRoot "$ServerType-$runId"
$classesDirectory = Join-Path $stagingRoot "$ServerType-plugin-classes"
$serverJar = Join-Path $serverDirectory "server.jar"
$serverJarCache = Join-Path $stagingRoot "$ServerType-server.jar"
$pluginJar = Join-Path $serverDirectory "CotaniStaging.jar"
$java = (Get-Command java.exe -ErrorAction Stop).Source
$dockerPath = Join-Path $env:ProgramFiles "Docker\Docker\resources\bin\docker.exe"

if (-not (Test-Path -LiteralPath $dockerPath)) {
    throw "Docker Desktop não foi encontrado. Instale-o antes de executar o staging."
}

New-Item -ItemType Directory -Force -Path $serverDirectory, $classesDirectory | Out-Null

& (Join-Path $root "gradlew.bat") :core:jar :task:jar --no-daemon
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

$headers = @{ "User-Agent" = "Cotani-staging/1.1.1 (https://github.com/HanielCota/Cotani)" }
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
Remove-Item -LiteralPath $classesDirectory -Recurse -Force
New-Item -ItemType Directory -Force -Path $classesDirectory | Out-Null

$paperCoreJar = Join-Path $root "cotani-core\build\libs\core-1.1.1.jar"
$paperTaskJar = Join-Path $root "cotani-task\build\libs\task-1.1.1.jar"
$paperApiJar = Get-ChildItem -Path (Join-Path $env:USERPROFILE ".gradle\caches\modules-2\files-2.1\io.papermc.paper\paper-api") -Recurse -Filter "paper-api-*.jar" |
    Where-Object Name -notmatch "-sources\.jar$" |
    Sort-Object LastWriteTime |
    Select-Object -Last 1
if ($null -eq $paperApiJar) {
    throw "paper-api não encontrado no cache do Gradle. Execute ./gradlew.bat :task:compileJava primeiro."
}
$annotationCacheRoots = @(
    "net.kyori",
    "org.jspecify",
    "org.jetbrains",
    "org.intellij",
    "net.md-5"
) | ForEach-Object {
    Join-Path $env:USERPROFILE ".gradle\caches\modules-2\files-2.1\$_"
}
$transitiveApiJars = Get-ChildItem -Path $annotationCacheRoots -Recurse -Filter "*.jar" -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -notmatch "-sources\.jar$" } |
    Select-Object -ExpandProperty FullName
$compileClasspath = (@($paperApiJar.FullName) + $transitiveApiJars + @($paperCoreJar, $paperTaskJar)) -join ";"
$sourceFile = Join-Path $root "staging\src\main\java\com\cotani\staging\CotaniStagingPlugin.java"
& javac.exe -cp $compileClasspath -d $classesDirectory $sourceFile
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

if (Test-Path -LiteralPath $pluginJar) {
    Remove-Item -LiteralPath $pluginJar -Force
}
& jar.exe --create --file $pluginJar -C $classesDirectory . -C (Join-Path $root "staging\src\main\resources") plugin.yml
& jar.exe --update --file $pluginJar -C (Join-Path $root "cotani-core\build\classes\java\main") .
& jar.exe --update --file $pluginJar -C (Join-Path $root "cotani-task\build\classes\java\main") .

$pluginsDirectory = Join-Path $serverDirectory "plugins"
New-Item -ItemType Directory -Force -Path $pluginsDirectory | Out-Null
Copy-Item $pluginJar (Join-Path $pluginsDirectory "CotaniStaging.jar") -Force

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
        throw "O servidor $ServerType encerrou antes do smoke test."
    }

    $serverProcess.StandardInput.WriteLine("cotani-staging")
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
    foreach ($marker in @("Cotani staging enabled", "Cotani staging command handled")) {
        if ($logContent -notmatch [regex]::Escape($marker)) {
            throw "O log do servidor $ServerType não contém o marcador esperado: $marker"
        }
    }
    Write-Host "$ServerType staging concluído após $DurationSeconds segundos. exit=$($serverProcess.ExitCode)"
} finally {
    if (-not $serverProcess.HasExited) {
        $serverProcess.Kill($true)
        $serverProcess.WaitForExit()
    }
    $serverProcess.Dispose()
}
