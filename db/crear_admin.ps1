<#
.SYNOPSIS
    Crea (o resetea la contrasena de) un usuario administrador de SIH-IA.

.DESCRIPTION
    Para que sirve:
      Una base recien creada con 03_semilla.sql tiene roles, menu y una escuela,
      pero NINGUN usuario: sin esto no se puede iniciar sesion. Este script crea
      el primer administrador y lo vincula a una escuela con su rol.

    Como funciona:
      1. Genera el hash bcrypt de la contrasena con la MISMA libreria que usa el
         backend (spring-security-crypto), asi que el login lo acepta.
      2. Inserta el usuario y su vinculo escuela-rol con psql.

    El hash no se guarda en ningun archivo: se calcula en memoria, se pasa a
    psql y se descarta. Por eso este script SI puede versionarse en un
    repositorio publico, al contrario que db/04_datos_cliente.sql.

    Es idempotente: si el usuario ya existe, le actualiza la contrasena y se
    asegura de que tenga el rol en la escuela indicada.

.PARAMETER EnDocker
    Ejecuta psql dentro del contenedor 'db' (docker compose exec). Usalo cuando
    la base vive en Docker y no quieres publicar el puerto 5432.

.EXAMPLE
    # Base local (PostgreSQL instalado en el PC)
    .\db\crear_admin.ps1 -Password 'MiClaveSegura2026!'

.EXAMPLE
    # Base dentro del stack de Docker
    .\db\crear_admin.ps1 -Password 'MiClaveSegura2026!' -EnDocker

.EXAMPLE
    # Apuntando por tunel SSH a la VM de produccion
    .\db\crear_admin.ps1 -Servidor 127.0.0.1 -Puerto 15432 -UsuarioBd postgres `
                         -PasswordBd '...' -Password 'MiClaveSegura2026!'
#>
[CmdletBinding()]
param(
    [string]$Usuario        = 'admin',
    [string]$NombreCompleto = 'Administrador',
    [string]$Email          = 'admin@escuela.local',
    [string]$Password       = '',
    [string]$Rol            = 'ADMIN',
    [long]  $EscuelaId      = 1,
    [string]$Servidor       = 'localhost',
    [int]   $Puerto         = 5432,
    [string]$Base           = 'SIH',
    [string]$UsuarioBd      = 'postgres',
    [string]$PasswordBd     = '',
    [string]$JarBcrypt      = '',
    [int]   $Coste          = 12,
    [string]$Psql           = '',
    [switch]$EnDocker
)

$ErrorActionPreference = 'Stop'
$raiz = Split-Path -Parent $PSScriptRoot

function Fallar([string]$msg) { Write-Host "ERROR: $msg" -ForegroundColor Red; exit 1 }

# ── 1) Contrasena ───────────────────────────────────────────────────────────
if (-not $Password) {
    $seg = Read-Host -AsSecureString "Contrasena para el usuario '$Usuario'"
    $Password = [Runtime.InteropServices.Marshal]::PtrToStringAuto(
                    [Runtime.InteropServices.Marshal]::SecureStringToBSTR($seg))
}
if ($Password.Length -lt 8) { Fallar 'la contrasena debe tener al menos 8 caracteres' }

# ── 2) Java y la libreria bcrypt ────────────────────────────────────────────
if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    Fallar 'no encontre java en el PATH. Instala un JRE 21 o pasa el hash ya calculado.'
}
if (-not $JarBcrypt) {
    $cand = Get-ChildItem "$env:USERPROFILE\.m2\repository\org\springframework\security\spring-security-crypto" `
                          -Recurse -Filter 'spring-security-crypto-*.jar' -ErrorAction SilentlyContinue |
            Sort-Object { [version]($_.BaseName -replace '^spring-security-crypto-','') } -Descending |
            Select-Object -First 1
    if ($cand) { $JarBcrypt = $cand.FullName }
}
if (-not $JarBcrypt -or -not (Test-Path $JarBcrypt)) {
    Fallar "no encontre spring-security-crypto.jar. Pasa su ruta con -JarBcrypt."
}

# ── 3) Hash bcrypt (mismo formato que el backend: $2a$12$...) ───────────────
$fuente = Join-Path $PSScriptRoot '_HashBcrypt.java'
$hash = (& java -cp "$JarBcrypt" "$fuente" "$Password" "$Coste" 2>&1 | Out-String).Trim()
if ($hash -notmatch '^\$2[aby]\$\d{2}\$') { Fallar "java no devolvio un hash bcrypt valido: $hash" }
Write-Host "hash bcrypt generado ($($hash.Length) caracteres, coste $Coste)" -ForegroundColor DarkGray

# ── 4) SQL (una sola linea: se pasa con -c para no dejar el hash en disco) ──
$sql = @"
INSERT INTO sih.usuarios (usuario, password_hash, nombre_completo, email, activo)
SELECT '$Usuario', '$hash', '$NombreCompleto', '$Email', true
 WHERE NOT EXISTS (SELECT 1 FROM sih.usuarios WHERE usuario = '$Usuario');
UPDATE sih.usuarios SET password_hash = '$hash', activo = true, nombre_completo = '$NombreCompleto'
 WHERE usuario = '$Usuario';
INSERT INTO sih.usuario_escuela_rol (usuario_id, escuela_id, rol_id, activo)
SELECT u.usuario_id, e.escuela_id, r.rol_id, true
  FROM sih.usuarios u, sih.escuelas e, sih.roles r
 WHERE u.usuario = '$Usuario' AND e.escuela_id = $EscuelaId AND upper(r.nombre) = upper('$Rol')
   AND NOT EXISTS (SELECT 1 FROM sih.usuario_escuela_rol x
                    WHERE x.usuario_id = u.usuario_id AND x.escuela_id = e.escuela_id AND x.rol_id = r.rol_id);
SELECT u.usuario, u.nombre_completo, e.nombre AS escuela, r.nombre AS rol, u.activo
  FROM sih.usuarios u
  JOIN sih.usuario_escuela_rol x ON x.usuario_id = u.usuario_id
  JOIN sih.escuelas e ON e.escuela_id = x.escuela_id
  JOIN sih.roles r ON r.rol_id = x.rol_id
 WHERE u.usuario = '$Usuario';
"@

# ── 5) Ejecutar ─────────────────────────────────────────────────────────────
if ($EnDocker) {
    Write-Host "ejecutando en el contenedor 'db' (docker compose exec)..." -ForegroundColor Cyan
    $salida = & docker compose --project-directory $raiz exec -T db psql -U $UsuarioBd -d $Base `
                -v ON_ERROR_STOP=1 -c $sql 2>&1
} else {
    if (-not $Psql) {
        $Psql = (Get-Command psql -ErrorAction SilentlyContinue).Source
        if (-not $Psql) {
            foreach ($p in 'D:\Program Files\PostgreSQL\17\bin\psql.exe',
                           'C:\Program Files\PostgreSQL\17\bin\psql.exe') {
                if (Test-Path $p) { $Psql = $p; break }
            }
        }
    }
    if (-not $Psql) { Fallar 'no encontre psql. Pasalo con -Psql <ruta> o usa -EnDocker.' }

    if ($PasswordBd) { $env:PGPASSWORD = $PasswordBd }
    Write-Host "conectando a $Servidor`:$Puerto/$Base como $UsuarioBd..." -ForegroundColor Cyan
    $salida = & $Psql -h $Servidor -p $Puerto -U $UsuarioBd -d $Base `
                -v ON_ERROR_STOP=1 -c $sql 2>&1
}

$codigo = $LASTEXITCODE
$salida | ForEach-Object { Write-Host $_ }
if ($codigo -ne 0) { Fallar "psql termino con codigo $codigo" }

if ($salida -match $Usuario) {
    Write-Host ''
    Write-Host "Listo: '$Usuario' puede iniciar sesion con la contrasena indicada." -ForegroundColor Green
    Write-Host 'Cambiala desde la aplicacion si la compartiste para esta prueba.' -ForegroundColor DarkGray
} else {
    Fallar 'no se pudo confirmar la creacion del usuario (revisa que la escuela y el rol existan)'
}
