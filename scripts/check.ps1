param([switch]$ApiOnly)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'runtime.ps1')
Push-Location (Split-Path $PSScriptRoot)
try {
  if (!$ApiOnly) {
    npm run build
    if ($LASTEXITCODE -ne 0) { throw 'Frontend build failed' }
    & ./mvnw.ps1 -q clean package
    if ($LASTEXITCODE -ne 0) { throw 'Backend build or tests failed' }
  }
  $java = Get-ProjectJava
  node scripts/check-api.mjs $java
  if ($LASTEXITCODE -ne 0) { throw 'API verification failed' }
} finally { Pop-Location }
