<#
.SYNOPSIS
    Revisa los resultados de las pruebas del backend y falla si no se probo nada.

.DESCRIPTION
    Hallazgo H-02 de QA: sin Docker encendido, las pruebas de integracion se SALTAN
    (BasePruebas usa @Testcontainers(disabledWithoutDocker = true)) y Maven termina con
    "Tests run: 9, Skipped: 9 ... BUILD SUCCESS". Ese verde no prueba nada.

    Este guion lee los reportes de Surefire (BackendJava\target\surefire-reports\*.xml) y sale con
    codigo 1 si:
      - no hay reportes (no se corrio nada), o
      - el total de pruebas es 0, o
      - TODAS las pruebas se saltaron (el caso de "no hay Docker").

    Si hay algunas saltadas pero otras ejecutadas, avisa con una advertencia y sale con 0: eso ya es
    una decision de quien revisa, no un falso verde.

.PARAMETER Reportes
    Carpeta de reportes de Surefire. Por defecto, la del proyecto.

.PARAMETER SoloAvisar
    Si se indica, nunca sale con error: solo informa (util para revisar a mano).

.EXAMPLE
    .\herramientas\revisar-pruebas.ps1
    .\herramientas\revisar-pruebas.ps1 -Reportes BackendJava\target\surefire-reports
#>
[CmdletBinding()]
param(
    [string]$Reportes = (Join-Path $PSScriptRoot '..\BackendJava\target\surefire-reports'),
    [switch]$SoloAvisar
)

$ErrorActionPreference = 'Stop'

function Fallar([string]$mensaje) {
    Write-Host ''
    Write-Host ('!' * 78) -ForegroundColor Red
    Write-Host "!!  $mensaje" -ForegroundColor Red
    Write-Host ('!' * 78) -ForegroundColor Red
    Write-Host ''
    if (-not $SoloAvisar) { exit 1 }
}

Write-Host "Revisando pruebas del backend en: $Reportes"

if (-not (Test-Path $Reportes)) {
    Fallar "No existe la carpeta de reportes. Corre 'mvn test' en BackendJava antes de revisar."
}

$archivos = @(Get-ChildItem -Path $Reportes -Filter 'TEST-*.xml' -ErrorAction SilentlyContinue)
if ($archivos.Count -eq 0) {
    Fallar "No hay reportes de Surefire (*.xml). No se ejecuto ninguna prueba."
}

$total = 0
$saltadas = 0
$fallidas = 0
$errores = 0
$detalle = @()

foreach ($archivo in $archivos) {
    # Se lee el XML de verdad (no expresiones regulares): los atributos del <testsuite> son la
    # fuente autorizada de los conteos y asi no dependemos del formato del archivo.
    [xml]$xml = Get-Content $archivo.FullName -Raw
    $raiz = $xml.testsuite
    $t = [int]$raiz.tests
    $s = [int]$raiz.skipped
    $f = [int]$raiz.failures
    $e = [int]$raiz.errors
    $total += $t; $saltadas += $s; $fallidas += $f; $errores += $e
    $detalle += [pscustomobject]@{
        Clase     = ($archivo.BaseName -replace '^TEST-', '')
        Pruebas   = $t
        Saltadas  = $s
        Fallidas  = $f
        Errores   = $e
    }
}

Write-Host ''
$detalle | Format-Table -AutoSize | Out-String | Write-Host
Write-Host "TOTAL: $total pruebas | ejecutadas: $($total - $saltadas) | saltadas: $saltadas | fallidas: $fallidas | errores: $errores"

if ($total -eq 0) {
    Fallar 'El total de pruebas es 0: los reportes existen pero no traen ninguna prueba.'
}

if ($saltadas -eq $total) {
    Fallar "LAS $total PRUEBAS SE SALTARON: este resultado no prueba nada. Enciende Docker y vuelve a correr 'mvn test'."
}

if ($saltadas -gt 0) {
    Write-Host ''
    Write-Host "ADVERTENCIA: $saltadas de $total pruebas se saltaron. Revisa por que antes de dar por bueno el resultado." -ForegroundColor Yellow
}

if ($fallidas -gt 0 -or $errores -gt 0) {
    Write-Host ''
    Write-Host "Hay $fallidas fallidas y $errores errores: revisa los reportes." -ForegroundColor Yellow
    if (-not $SoloAvisar) { exit 1 }
}

Write-Host ''
Write-Host 'OK: las pruebas se ejecutaron.' -ForegroundColor Green
exit 0
