$ErrorActionPreference = 'Stop'
function Get-ProjectJava {
  param([switch]$RequireJdk)
  $candidates = @()
  $portableJava = Join-Path (Split-Path $PSScriptRoot) 'runtime\bin\java.exe'
  if (!$RequireJdk -and (Test-Path -LiteralPath $portableJava)) { $candidates += $portableJava }
  if ($env:JAVA_HOME) { $candidates += (Join-Path $env:JAVA_HOME 'bin\java.exe') }
  $candidates += 'C:\Program Files\Java\jdk-17\bin\java.exe'
  $found = Get-Command java -ErrorAction SilentlyContinue
  if ($found) { $candidates += $found.Source }
  foreach ($candidate in $candidates) {
    if ((Test-Path -LiteralPath $candidate) -and (!$RequireJdk -or (Test-Path -LiteralPath (Join-Path (Split-Path $candidate) 'javac.exe')))) {
      $versionInfo = New-Object System.Diagnostics.ProcessStartInfo
      $versionInfo.FileName = $candidate
      $versionInfo.Arguments = '-version'
      $versionInfo.UseShellExecute = $false
      $versionInfo.CreateNoWindow = $true
      $versionInfo.RedirectStandardError = $true
      $versionInfo.RedirectStandardOutput = $true
      $versionProcess = [System.Diagnostics.Process]::Start($versionInfo)
      $versionText = $versionProcess.StandardError.ReadToEnd() + $versionProcess.StandardOutput.ReadToEnd()
      $versionProcess.WaitForExit()
      $versionProcess.Dispose()
      if ($versionText -match '(?:version "|(?:openjdk|java) )(\d+)(?:\.(\d+))?') {
        $major = [int]$Matches[1]
        if ($major -ge 17) { return $candidate }
      }
    }
  }
  throw '需要 JDK 17 或更新版本。请设置 JAVA_HOME 指向已安装的 JDK。'
}
function Get-ProjectMaven {
  param([string]$Root)
  $maven = Join-Path $Root '.tools\apache-maven-3.9.11\bin\mvn.cmd'
  if (!(Test-Path -LiteralPath $maven)) {
    $toolsDirectory = Join-Path $Root '.tools'
    New-Item -ItemType Directory -Force -Path $toolsDirectory | Out-Null
    $archive = Join-Path $toolsDirectory 'maven.zip'
    Invoke-WebRequest -Uri 'https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.11/apache-maven-3.9.11-bin.zip' -OutFile $archive
    Expand-Archive -LiteralPath $archive -DestinationPath $toolsDirectory -Force
  }
  return $maven
}
