# ============================================================
# PUBLICAR: arma la carpeta de distribucion del SIH (programa -jar)
# ============================================================
#   .\publicar.ps1                 -> compila front + backend y arma la carpeta
#   .\publicar.ps1 -ConPostgres    -> ademas descarga PostgreSQL portable y lo inicializa
#
# Resultado en  ..\distribucion\SIH\   (o donde diga -Destino)
param(
  [string]$Destino = '',
  [switch]$ConPostgres,
  [string]$PgVersion = '17.6-1',
  [string]$PgUser = 'postgres',
  [string]$PgPassword = 'postgres',
  [string]$DbName = 'SIH',
  [string]$DbUser = 'User_app',
  [string]$DbPassword = 'Admin2026!'
)

$ErrorActionPreference = 'Stop'
$raiz   = $PSScriptRoot
$front  = Join-Path $raiz 'FrontendReact'
$back   = Join-Path $raiz 'BackendJava'
$static = Join-Path $back 'src\main\resources\static'
if (-not $Destino) { $Destino = Join-Path (Split-Path $raiz -Parent) 'distribucion\SIH' }

function Paso($t) { Write-Host "`n=== $t ===" -ForegroundColor Cyan }

# ── 1) FRONT ──────────────────────────────────────────────
Paso '1/5 Compilando el front (React + Vite)'
Push-Location $front
& node 'node_modules/vite/bin/vite.js' build
if ($LASTEXITCODE -ne 0) { throw 'Fallo el build del front' }
Pop-Location

# ── 2) FRONT EMBEBIDO ─────────────────────────────────────
Paso '2/5 Copiando el front dentro del backend (classpath:/static)'
if (Test-Path $static) { Remove-Item $static -Recurse -Force }   # sin copias viejas
New-Item -ItemType Directory -Force -Path $static | Out-Null
Copy-Item (Join-Path $front 'dist\*') $static -Recurse -Force
Write-Host ("  archivos copiados: " + (Get-ChildItem $static -Recurse -File).Count)

# ── 3) BACKEND (WAR ejecutable) ───────────────────────────
Paso '3/5 Empaquetando el backend (java -jar SIH.war)'
Push-Location $back
& mvn -o clean package -DskipTests
if ($LASTEXITCODE -ne 0) { throw 'Fallo el package del backend' }
Pop-Location
$war = Join-Path $back 'target\SIH-IA-1.0.war'
if (-not (Test-Path $war)) { throw "No se genero $war" }

# ── 4) CARPETA DE DISTRIBUCION ────────────────────────────
Paso "4/5 Armando la carpeta de distribucion en $Destino"
if (Test-Path $Destino) { Remove-Item $Destino -Recurse -Force }
New-Item -ItemType Directory -Force -Path $Destino | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $Destino 'uploads\maestros') | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $Destino 'respaldos') | Out-Null
Copy-Item $war (Join-Path $Destino 'SIH.war')
Copy-Item $war (Join-Path $Destino 'SIH-Tomcat.war')   # version desplegable en Tomcat

# Secreto JWT nuevo, distinto por instalacion
$alfabeto = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/'
$secreto = -join (1..44 | ForEach-Object { $alfabeto[(Get-Random -Maximum $alfabeto.Length)] })

$iniciar = @"
@echo off
setlocal
cd /d %~dp0
title SIH - Servidor de horarios

rem ==================== EDITE ESTOS DATOS ====================
set DB_URL=jdbc:postgresql://localhost:5432/$DbName?currentSchema=sih
set DB_USER=$DbUser
set DB_PASSWORD=$DbPassword
set JWT_SECRETO=$secreto
set MAIL_HOST=smtp.tudominio.com
set MAIL_PORT=587
set MAIL_USERNAME=
set MAIL_PASSWORD=
set MAIL_FROM=noreply@tudominio.com
rem ===========================================================

set UPLOAD_DIR=uploads/maestros/
set SPRING_PROFILES_ACTIVE=prod

rem --- Base de datos ---
if not exist "postgres\bin\pg_ctl.exe" goto sinpostgres
if not exist "data\PG_VERSION" (
  echo Inicializando la base de datos por primera vez...
  postgres\bin\initdb -D data -U $PgUser -E UTF8 --locale=es-MX
  echo Arrancando para crear la base y cargar el esquema...
  postgres\bin\pg_ctl -D data -l postgres.log -o "-p 5432" start
  timeout /t 4 >nul
  postgres\bin\psql -U $PgUser -h localhost -c "CREATE DATABASE \"$DbName\";"
  if exist esquema.sql postgres\bin\psql -U $PgUser -h localhost -d $DbName -f esquema.sql
  postgres\bin\psql -U $PgUser -h localhost -d $DbName -c "CREATE USER \"$DbUser\" WITH PASSWORD '$DbPassword';"
  postgres\bin\psql -U $PgUser -h localhost -d $DbName -c "GRANT ALL ON SCHEMA sih TO \"$DbUser\";"
) else (
  postgres\bin\pg_ctl -D data -l postgres.log -o "-p 5432" start
  timeout /t 3 >nul
)
goto arrancar

:sinpostgres
echo [AVISO] No se encontro PostgreSQL portable en la carpeta postgres\.
echo         Se usara el PostgreSQL que ya este instalado en esta PC.

:arrancar
echo Abriendo el SIH en el navegador...
start "" http://localhost:8080
echo.
echo ==========================================================
echo   SIH en marcha. Para DETENERLO cierre esta ventana (o Ctrl+C).
echo   Desde otras PC de la escuela: http://ESTA-PC:8080
echo ==========================================================
java -jar SIH.war

if exist "postgres\bin\pg_ctl.exe" postgres\bin\pg_ctl -D data stop
endlocal
"@

$detener = @"
@echo off
cd /d %~dp0
if exist "postgres\bin\pg_ctl.exe" (
  echo Deteniendo la base de datos...
  postgres\bin\pg_ctl -D data stop
)
echo Cierre la ventana del SIH (o pulse Ctrl+C en ella) para detener la aplicacion.
pause
"@

$respaldar = @"
@echo off
setlocal
cd /d %~dp0
for /f "tokens=1-4 delims=/: " %%a in ("%date%") do set FECHA=%%d-%%b-%%c
if not exist respaldos mkdir respaldos
if exist "postgres\bin\pg_dump.exe" (
  postgres\bin\pg_dump -U $PgUser -h localhost -d $DbName -f "respaldos\$DbName-%FECHA%.sql"
) else (
  pg_dump -U $PgUser -h localhost -d $DbName -f "respaldos\$DbName-%FECHA%.sql"
)
xcopy /E /I /Y uploads "respaldos\uploads-%FECHA%" >nul
echo Respaldo creado en la carpeta respaldos.
pause
"@

$leeme = @"
SIH - Sistema de Horarios  (programa local)
===========================================

1) INSTALAR (solo la primera vez)
   - Instale Java 21 (o copie una carpeta jre\ y cambie iniciar.bat para usarla).
   - Edite iniciar.bat y ponga sus datos: contrasena de la base y del correo.
   - Doble clic en iniciar.bat. La primera vez crea la base y carga esquema.sql.

2) USO DIARIO
   - Doble clic en iniciar.bat  ->  se abre el navegador en http://localhost:8080
   - Otras PC de la escuela entran con  http://IP-DE-ESTA-PC:8080
   - Para detener: cierre la ventana del SIH (Ctrl+C).

3) RESPALDOS
   - Doble clic en respaldar.bat  ->  deja la base y las fotos en la carpeta respaldos.
   - Copie esa carpeta a una USB o a otra PC al menos una vez por semana.

4) ARCHIVOS
   - SIH.war          el programa (backend + front)
   - SIH-Tomcat.war   la misma version para desplegar en Tomcat, si se prefiere
   - postgres\        base de datos portable (si se incluyo)
   - data\            los datos de la base (NO borrar)
   - uploads\         fotos de maestros (NO borrar)
   - esquema.sql      estructura de la base para la primera instalacion

5) ACTUALIZAR
   - Reemplace SIH.war por el nuevo y vuelva a arrancar. Sus datos viven en data\ y uploads\.
"@

$utf8 = New-Object System.Text.UTF8Encoding($false)
[IO.File]::WriteAllText((Join-Path $Destino 'iniciar.bat'), $iniciar, $utf8)
[IO.File]::WriteAllText((Join-Path $Destino 'detener.bat'), $detener, $utf8)
[IO.File]::WriteAllText((Join-Path $Destino 'respaldar.bat'), $respaldar, $utf8)
[IO.File]::WriteAllText((Join-Path $Destino 'LEEME.txt'), $leeme, $utf8)

# ── 5) POSTGRES PORTABLE (opcional) ───────────────────────
Paso '5/5 PostgreSQL portable'
if (-not $ConPostgres) {
  Write-Host '  Omitido (use -ConPostgres para descargarlo e inicializarlo).' -ForegroundColor Yellow
} else {
  $pgDir  = Join-Path $Destino 'postgres'
  $zip    = Join-Path $env:TEMP "postgresql-$PgVersion-windows-x64-binaries.zip"
  $url    = "https://get.enterprisedb.com/postgresql/postgresql-$PgVersion-windows-x64-binaries.zip"
  if (-not (Test-Path $zip)) {
    Write-Host "  Descargando $url"
    Invoke-WebRequest -Uri $url -OutFile $zip
  }
  New-Item -ItemType Directory -Force -Path $pgDir | Out-Null
  Expand-Archive -Path $zip -DestinationPath $pgDir -Force
  # El zip trae una carpeta pgsql\ ; se aplana para que quede postgres\bin\...
  $pgsql = Join-Path $pgDir 'pgsql'
  if (Test-Path $pgsql) {
    Get-ChildItem $pgsql | Move-Item -Destination $pgDir -Force
    Remove-Item $pgsql -Recurse -Force
  }
  Write-Host '  PostgreSQL portable listo en postgres\ (iniciar.bat lo inicializa en el primer arranque).'
  Write-Host '  Recuerde generar esquema.sql desde su base actual:  pg_dump --schema-only ...'
}

Write-Host "`nLISTO. Distribucion en: $Destino" -ForegroundColor Green
Get-ChildItem $Destino | Select-Object Name, Length | Format-Table -AutoSize
