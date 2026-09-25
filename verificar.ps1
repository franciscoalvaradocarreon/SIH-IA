# ============================================================================
# VERIFICAR (QA de puesta en produccion) - corre ANTES de abrir a los usuarios
# ============================================================================
#   .\verificar.ps1                      -> revisa esta PC y el programa instalado
#   .\verificar.ps1 -Url http://otra:8080
#
# Devuelve una lista PASS/FALLA y termina con el veredicto. No modifica nada.
param(
  [string]$Url = 'http://localhost:8080',
  [string]$Dist = '',
  [string]$PgBin = '',
  [string]$DbName = 'SIH',
  [string]$PgUser = 'postgres'
)

$ErrorActionPreference = 'Continue'
$raiz = if ($PSScriptRoot) { $PSScriptRoot } else { (Get-Location).Path }
if (-not $Dist) { $Dist = Join-Path (Split-Path $raiz -Parent) 'distribucion\SIH' }
if (-not $PgBin -and (Test-Path (Join-Path $Dist 'postgres\bin'))) {
  $PgBin = Join-Path $Dist 'postgres\bin'
}

$fallas = 0
function Chequeo($nombre, $ok, $detalle = '') {
  $sufijo = ''
  if ($detalle) { $sufijo = " - $detalle" }
  if ($ok) {
    Write-Host ("  [PASS]  " + $nombre + $sufijo) -ForegroundColor Green
  } else {
    Write-Host ("  [FALLA] " + $nombre + $sufijo) -ForegroundColor Red
    $script:fallas++
  }
}

Write-Host "`n== 1) Entorno de la PC ==" -ForegroundColor Cyan
$javaVer = ((& java -version 2>&1) | Select-Object -First 1) -join ''
Chequeo 'Java 21 instalado' ($javaVer -match '21|22|23|24') $javaVer
Chequeo 'Carpeta de distribucion' (Test-Path $Dist) $Dist
Chequeo 'Programa SIH.war' (Test-Path (Join-Path $Dist 'SIH.war'))
$uploads = Join-Path $Dist 'uploads\maestros'
if (-not (Test-Path $uploads)) { New-Item -ItemType Directory -Force -Path $uploads | Out-Null }
$escritura = $true
$detalleEscritura = $uploads
try {
  $prueba = Join-Path $uploads '.prueba-escritura'
  [IO.File]::WriteAllText($prueba, 'ok'); Remove-Item $prueba -Force
} catch { $escritura = $false; $detalleEscritura = $_.Exception.Message }
Chequeo 'Carpeta uploads escribible' $escritura $detalleEscritura

Write-Host "`n== 2) Base de datos ==" -ForegroundColor Cyan
if ($PgBin -and (Test-Path (Join-Path $PgBin 'psql.exe'))) {
  $qa = Join-Path $raiz 'db\qa_esquema.sql'
  if (Test-Path $qa) {
    $salida = & (Join-Path $PgBin 'psql.exe') -U $PgUser -h localhost -d $DbName -f $qa 2>&1
    $texto = ($salida | Out-String)
    $texto | Out-File (Join-Path $raiz 'qa-esquema-salida.txt') -Encoding utf8
    $veredicto = (($texto -split "`n") | Select-String -Pattern 'OK  ->|ERROR ->' | Select-Object -First 1)
    Chequeo 'QA del esquema (DDL)' ($texto -match 'OK  ->') ("$veredicto".Trim())
    Write-Host '         (reporte completo en qa-esquema-salida.txt)'
  } else {
    Chequeo 'QA del esquema (DDL)' $false "no se encontro $qa"
  }
} else {
  Chequeo 'psql para el QA del esquema' $false 'no hay postgres\bin\psql.exe: se omite la revision DDL'
}

Write-Host "`n== 3) Aplicacion en marcha ($Url) ==" -ForegroundColor Cyan
function Codigo($ruta) { (& curl.exe -s -o NUL -w '%{http_code}' --max-time 10 "$Url$ruta" 2>$null) }
$raizApp = Codigo '/'
Chequeo 'Sirve la aplicacion en /' ($raizApp -eq '200') "http $raizApp"
$html = ((& curl.exe -s --max-time 10 "$Url/" 2>$null) -join '')
Chequeo 'El index referencia el bundle' ($html -match '/assets/index-')
$rutaSpa = Codigo '/reportes/maestros-especialidad'
Chequeo 'Fallback de SPA (ruta profunda)' ($rutaSpa -eq '200') "http $rutaSpa"
$api = Codigo '/api/menu'
Chequeo 'El API pide autenticacion (401)' ($api -eq '401') "http $api"
$uploadsHttp = Codigo '/uploads/maestros/no-existe.jpg'
Chequeo '/uploads responde' ($uploadsHttp -ne '000') "http $uploadsHttp"

Write-Host "`n== 4) Secretos y configuracion ==" -ForegroundColor Cyan
$bat = Join-Path $Dist 'iniciar.bat'
if (Test-Path $bat) {
  $contenido = Get-Content $bat -Raw
  Chequeo 'JWT_SECRETO con 32+ caracteres' ($contenido -match 'set JWT_SECRETO=(.{32,})')
  Chequeo 'Perfil prod activado' ($contenido -match 'SPRING_PROFILES_ACTIVE=prod')
  Chequeo 'Contrasena de BD distinta a la de ejemplo' (-not ($contenido -match 'set DB_PASSWORD=Admin2026!'))
} else {
  Chequeo 'iniciar.bat presente' $false $bat
}

Write-Host "`n============================================"
if ($fallas -eq 0) {
  Write-Host ' VEREDICTO: TODO OK - se puede poner en produccion' -ForegroundColor Green
} else {
  Write-Host (" VEREDICTO: " + $fallas + " FALLA(S) - corregir antes de publicar") -ForegroundColor Red
}
Write-Host "============================================`n"
