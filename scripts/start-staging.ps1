param(
    [switch]$Down
)

$ErrorActionPreference = "Stop"
$composeFile = Join-Path $PSScriptRoot "..\docker-compose.staging.yml"

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw "Docker CLI não encontrado. Instale Docker Desktop e tente novamente."
}

if ($Down) {
    docker compose -f $composeFile down
    exit $LASTEXITCODE
}

$requiredVariables = @(
    "COTANI_REDIS_PASSWORD",
    "COTANI_MYSQL_PASSWORD",
    "COTANI_MYSQL_ROOT_PASSWORD",
    "COTANI_MARIADB_PASSWORD",
    "COTANI_MARIADB_ROOT_PASSWORD"
)

foreach ($variable in $requiredVariables) {
    $value = [Environment]::GetEnvironmentVariable($variable)
    if ([string]::IsNullOrWhiteSpace($value)) {
        throw "Variável $variable não definida. Carregue .env.staging ou exporte as variáveis antes de iniciar."
    }
}

docker compose -f $composeFile up -d
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

docker compose -f $composeFile ps
Write-Host "Staging iniciado: Redis 127.0.0.1:16379, MySQL 127.0.0.1:13306, MariaDB 127.0.0.1:13307"
