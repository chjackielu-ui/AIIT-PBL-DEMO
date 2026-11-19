#Requires -Version 7.4
param([switch]$Build, [switch]$Dev, [switch]$InitAdmin, [switch]$DemoAccounts)
. (Join-Path $PSScriptRoot 'scripts\runtime.ps1')
$runtimeDirectory = Join-Path $PSScriptRoot '.runtime'
New-Item -ItemType Directory -Force -Path $runtimeDirectory | Out-Null
$java = Get-ProjectJava
$backendEnvironment = @{PBL_STORAGE = 'mysql'; PBL_ADMIN_PASSWORD = $null; PBL_DEMO_ACCOUNTS = [string]$DemoAccounts.IsPresent}
$credentialFile = Join-Path $runtimeDirectory 'mysql-credential.xml'
if (-not $env:PBL_DB_PASSWORD -and (Test-Path -LiteralPath $credentialFile)) {
  $credential = Import-Clixml -LiteralPath $credentialFile
  $backendEnvironment.PBL_DB_USER = $credential.UserName
  $backendEnvironment.PBL_DB_PASSWORD = $credential.GetNetworkCredential().Password
}
if ($InitAdmin) {
  $adminPassword = Read-Host 'Initial administrator password (at least 8 characters)' -AsSecureString
  if ($adminPassword.Length -lt 8 -or $adminPassword.Length -gt 128) { throw '管理员密码应为 8–128 个字符。' }
  $backendEnvironment.PBL_ADMIN_PASSWORD = [pscredential]::new('admin', $adminPassword).GetNetworkCredential().Password
}
$node = if ($Dev) { (Get-Command node).Source } else { $null }
$jar = Join-Path $PSScriptRoot 'app\project-platform.jar'
$builtJar = Join-Path $PSScriptRoot 'backend\target\project-platform-1.0.0.jar'
Push-Location $PSScriptRoot
try {
  if (($Build -or $InitAdmin -or $DemoAccounts) -and (Test-Path -LiteralPath (Join-Path $runtimeDirectory 'services.json'))) { & (Join-Path $PSScriptRoot 'stop.ps1') }
  $needsApp = !(Test-Path -LiteralPath $jar)
  $needsBuild = $Build -or ($needsApp -and !(Test-Path -LiteralPath $builtJar))
  if (($Dev -or $needsBuild) -and !(Test-Path -LiteralPath 'node_modules\vite\bin\vite.js')) { npm ci; if ($LASTEXITCODE -ne 0) { throw '前端依赖安装失败。' } }
  if ($needsBuild) { npm run build; if ($LASTEXITCODE -ne 0) { throw '前端构建失败。' }; & (Join-Path $PSScriptRoot 'mvnw.ps1') -q clean package; if ($LASTEXITCODE -ne 0) { throw '后端构建失败。' } }
  if ($needsApp -or $needsBuild) {
    New-Item -ItemType Directory -Force -Path (Join-Path $PSScriptRoot 'app') | Out-Null
    Copy-Item -LiteralPath $builtJar -Destination $jar -Force
  }
  $managed = @()
  $serviceFile = Join-Path $runtimeDirectory 'services.json'
  if (Test-Path -LiteralPath $serviceFile) { $managed = @(Get-Content -LiteralPath $serviceFile -Raw | ConvertFrom-Json) }
  $services = @(@{name='backend';port=8080})
  if ($Dev) { $services += @{name='frontend';port=5173} }
  foreach ($service in $services) {
    $listener = Get-NetTCPConnection -State Listen -LocalPort $service.port -ErrorAction SilentlyContinue
    if ($listener) {
      $owner = Get-Process -Id $listener[0].OwningProcess -ErrorAction SilentlyContinue
      if (!($managed | Where-Object { $_.id -eq $listener[0].OwningProcess -and $owner -and [long]$_.startTicks -eq $owner.StartTime.ToUniversalTime().Ticks })) { throw "端口 $($service.port) 已被其他进程占用，未操作该进程。" }
      continue
    }
    if ($service.name -eq 'frontend') {
      $backendReady = $false
      for ($attempt = 0; $attempt -lt 30; $attempt++) {
        try { if ((Invoke-RestMethod 'http://127.0.0.1:8080/api/health' -TimeoutSec 2).status -eq 'UP') { $backendReady = $true; break } } catch { Start-Sleep -Milliseconds 500 }
      }
      if (!$backendReady) { throw '后端未就绪，请查看 .runtime/backend-error.log。' }
    }
    if ($service.name -eq 'backend') {
      $process = Start-Process -FilePath $java -ArgumentList @('-jar',('"'+$jar+'"')) -WorkingDirectory (Join-Path $PSScriptRoot 'backend') -WindowStyle Hidden -PassThru -Environment $backendEnvironment -RedirectStandardOutput (Join-Path $runtimeDirectory 'backend.log') -RedirectStandardError (Join-Path $runtimeDirectory 'backend-error.log')
    } else {
      $vite = Join-Path $PSScriptRoot 'node_modules\vite\bin\vite.js'
      $process = Start-Process -FilePath $node -ArgumentList @(('"'+$vite+'"'),'--host','127.0.0.1') -WorkingDirectory $PSScriptRoot -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $runtimeDirectory 'frontend.log') -RedirectStandardError (Join-Path $runtimeDirectory 'frontend-error.log')
    }
    $managed = @($managed | Where-Object { $_.name -ne $service.name }) + @{id=$process.Id;name=$service.name;startTicks=$process.StartTime.ToUniversalTime().Ticks}
    $managed | ConvertTo-Json -Depth 3 | Set-Content -LiteralPath $serviceFile -Encoding utf8
  }
  $ready = $false
  for ($attempt = 0; $attempt -lt 20; $attempt++) {
    try { $health = Invoke-RestMethod 'http://127.0.0.1:8080/api/health' -TimeoutSec 2; if ($health.status -eq 'UP') { $ready=$true; break } } catch { Start-Sleep -Seconds 1 }
  }
  if (!$ready) { throw '服务未就绪，请查看 .runtime 中的日志。' }
  Write-Host '平台已启动：http://127.0.0.1:8080/ （Java 同时提供编译后的 Vue 页面）'
  if ($Dev) { Write-Host '前端热更新开发地址：http://127.0.0.1:5173/' }
  Write-Host '停止服务：.\stop.ps1'
} finally { Pop-Location }

