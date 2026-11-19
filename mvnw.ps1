param([Parameter(ValueFromRemainingArguments=$true)][string[]]$MavenArgs)
. (Join-Path $PSScriptRoot 'scripts\runtime.ps1')
$java = Get-ProjectJava -RequireJdk
$maven = Get-ProjectMaven -Root $PSScriptRoot
$previousJavaHome = $env:JAVA_HOME
try {
  $env:JAVA_HOME = Split-Path (Split-Path $java)
  & $maven -f (Join-Path $PSScriptRoot 'backend\pom.xml') @MavenArgs
  if ($LASTEXITCODE -ne 0) { throw 'Maven 构建失败，请检查上方输出。' }
} finally { $env:JAVA_HOME = $previousJavaHome }
