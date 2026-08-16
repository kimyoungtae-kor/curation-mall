$ErrorActionPreference = 'Stop'

$repositoryRoot = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $repositoryRoot '.env'
$importScript = Join-Path $PSScriptRoot 'Import-DotEnv.ps1'
$frontendDirectory = Join-Path $repositoryRoot 'frontend'

if (-not (Test-Path -LiteralPath $envFile)) {
    throw 'Create .env from .env.example before starting the frontend.'
}

if (-not (Test-Path -LiteralPath (Join-Path $frontendDirectory 'package.json'))) {
    throw 'frontend/package.json was not found.'
}

& $importScript -Path $envFile
& npm.cmd --prefix $frontendDirectory run dev
exit $LASTEXITCODE

