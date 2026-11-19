$ErrorActionPreference = 'Stop'
$serviceFile = Join-Path $PSScriptRoot '.runtime\services.json'
if (!(Test-Path -LiteralPath $serviceFile)) { Write-Host '没有本项目记录的运行进程。'; return }
$managed = @(Get-Content -LiteralPath $serviceFile -Raw | ConvertFrom-Json)
foreach ($service in $managed) {
  $process = Get-Process -Id $service.id -ErrorAction SilentlyContinue
  if ($process -and $process.StartTime.ToUniversalTime().Ticks -eq [long]$service.startTicks) {
    Stop-Process -Id $process.Id
    Write-Host "已停止 $($service.name) (PID $($process.Id))"
  }
}
Set-Content -LiteralPath $serviceFile -Value '[]' -Encoding utf8

