$ErrorActionPreference = 'Stop'
$env:BACKEND_IMAGE = 'ghcr.io/mephisto-nox/radar-pulse-vis/backend:validation'
$env:FRONTEND_IMAGE = 'ghcr.io/mephisto-nox/radar-pulse-vis/frontend:validation'
$env:KAFKA_BOOTSTRAP_SERVERS = 'kafka:9092'
$env:KAFKA_REPLICATION_FACTOR = '1'
$env:RECEIVER_ID = 'RX-01'

$composeFiles = @(
    'deployment/docker-compose.yml',
    'deployment/docker-compose.prod.yml'
)

foreach ($composeFile in $composeFiles) {
    docker compose -f $composeFile config --quiet
    if ($LASTEXITCODE -ne 0) {
        throw "Compose validation failed: $composeFile"
    }
}

Write-Host 'Compose configuration is valid.'
