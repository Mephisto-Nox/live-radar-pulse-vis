$ErrorActionPreference = 'Stop'
$env:BACKEND_IMAGE = 'ghcr.io/example/backend:validation'
$env:FRONTEND_IMAGE = 'ghcr.io/example/frontend:validation'

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
