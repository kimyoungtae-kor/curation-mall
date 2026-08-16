$ErrorActionPreference = 'Stop'

$repositoryRoot = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $repositoryRoot '.env'
$importScript = Join-Path $PSScriptRoot 'Import-DotEnv.ps1'
$mavenWrapper = Join-Path $repositoryRoot 'backend\mvnw.cmd'
$pomFile = Join-Path $repositoryRoot 'backend\pom.xml'

if (-not (Test-Path -LiteralPath $envFile)) {
    throw 'Create .env from .env.example before starting the backend.'
}

if (-not (Test-Path -LiteralPath $mavenWrapper)) {
    throw 'backend/mvnw.cmd was not found.'
}

& $importScript -Path $envFile
if ([string]::IsNullOrWhiteSpace($env:SPRING_PROFILES_ACTIVE)) {
    $env:SPRING_PROFILES_ACTIVE = 'local'
}
if ([string]::IsNullOrWhiteSpace($env:MEDIA_STORAGE_ROOT)) {
    $env:MEDIA_STORAGE_ROOT = Join-Path $repositoryRoot 'media'
}
& $mavenWrapper -f $pomFile spring-boot:run
exit $LASTEXITCODE
