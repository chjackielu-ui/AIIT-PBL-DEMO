#Requires -Version 7.4
param([switch]$InitAdmin, [switch]$DemoAccounts)
& (Join-Path $PSScriptRoot 'start.ps1') @PSBoundParameters
