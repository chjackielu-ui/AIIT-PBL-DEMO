#Requires -Version 7.4
param([Parameter(Mandatory)][ValidatePattern('^pbl_verify_[A-Za-z0-9_]+$')][string]$Database, [string]$BrowserCheck)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot
. (Join-Path $PSScriptRoot 'runtime.ps1')
$java = Get-ProjectJava
$jar = Join-Path $root 'backend/target/project-platform-1.0.0.jar'
$verification = (New-Item -ItemType Directory -Force (Join-Path $root ".runtime/$Database")).FullName
$credentialFile = Join-Path $root '.runtime/mysql-credential.xml'
$databaseUser = $env:PBL_DB_USER
$databasePassword = $env:PBL_DB_PASSWORD
if (!$databasePassword -and (Test-Path -LiteralPath $credentialFile)) {
  $credential = Import-Clixml -LiteralPath $credentialFile
  $databaseUser = $credential.UserName
  $databasePassword = $credential.GetNetworkCredential().Password
}
$adminPassword = [guid]::NewGuid().ToString()
$backendConfig = @{
  PBL_DB_URL = "jdbc:mysql://127.0.0.1:3306/$Database" + '?connectTimeout=5000&socketTimeout=10000'
  PBL_DB_USER = $databaseUser
  PBL_DB_PASSWORD = $databasePassword
  PBL_ADMIN_PASSWORD = $adminPassword
  PBL_STORAGE = 'mysql'
  PBL_DEMO_ACCOUNTS = 'true'
  PBL_FILES_DIR = Join-Path $verification 'files'
}

function Invoke-Check([string[]]$Flags) {
  $stdout = Join-Path $verification 'server.log'
  $stderr = Join-Path $verification 'server-error.log'
  $server = Start-Process -FilePath $java -ArgumentList @('-jar', ('"' + $jar + '"'), '--server.port=0') `
    -Environment $backendConfig -WindowStyle Hidden -PassThru -RedirectStandardOutput $stdout -RedirectStandardError $stderr
  try {
    $port = $null
    for ($attempt = 0; $attempt -lt 40; $attempt++) {
      if ($server.HasExited) { throw "Verification server exited. See $stdout and $stderr" }
      $line = Select-String -LiteralPath $stdout -Pattern 'Tomcat started on port (\d+)' | Select-Object -Last 1
      if ($line) { $port = $line.Matches.Groups[1].Value; break }
      Start-Sleep -Milliseconds 500
    }
    if (!$port) { throw "Verification server did not start. See $stdout" }
    $env:API_URL = "http://127.0.0.1:$port/api"
    $env:PBL_TEST_RUN = 'isolated'
    $env:PBL_VERIFY_ADMIN_PASSWORD = $adminPassword
    $ready = $false
    for ($attempt = 0; $attempt -lt 10; $attempt++) {
      try { $ready = (Invoke-RestMethod "$env:API_URL/health" -TimeoutSec 1).storage -eq 'mysql' } catch {}
      if ($ready) { break }
      Start-Sleep -Milliseconds 250
    }
    if (!$ready) { throw "Verification server failed health check. See $stdout" }
    node (Join-Path $PSScriptRoot 'verify-mysql.mjs') @Flags
    if ($LASTEXITCODE) { throw 'MySQL HTTP verification failed' }
    if ($BrowserCheck -and !$Flags) {
      node $BrowserCheck
      if ($LASTEXITCODE) { throw 'Browser verification failed' }
    }
  } finally {
    if (!$server.HasExited) { Stop-Process -InputObject $server; $server.WaitForExit() }
  }
}

Invoke-Check
Invoke-Check -Flags '--restart'
