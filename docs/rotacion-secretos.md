# Rotación de secretos (procedimiento)

Guía para cambiar las tres credenciales que quedaron expuestas mientras el sitio servía por HTTP
(antes de v1.1.24 + el certificado TLS). **Ejecutar en una ventana de mantenimiento acordada.**

## Antes de empezar

**Orden recomendado** (de mayor a menor riesgo real):

| # | Qué | Por qué | Corte de servicio |
|---|---|---|---|
| 1 | Contraseña del usuario admin | Las contraseñas **sí** viajaron sin cifrar | **Ninguno** (se hace desde la aplicación) |
| 2 | Contraseña de la base de datos | Estaba en un `.env` legible por cualquiera | ~30 s (se recrea el contenedor de la app) |
| 3 | Secreto JWT | Los **tokens** viajaron sin cifrar (el secreto nunca salió) | ~30 s + todos deben volver a entrar |

**Reglas de oro**

- Nunca pegar secretos en el chat, en un ticket ni en el historial del shell.
- Antes de cambiar algo, **guardar el valor viejo** (es la vuelta atrás).
- Después de cada paso, **verificar** (abajo está cómo en cada uno).
- Los archivos con secretos: `chmod 600` (ya lo están `.env` y sus respaldos).
- Generar secretos nuevos: `openssl rand -base64 48` (para el JWT) y `openssl rand -hex 24`
  (para la base: **hex** y no base64, porque `$` y `/` se rompen dentro de `.env`).

**Respaldo previo**

```bash
ssh sih "cd ~/SIH-IA && cp -p .env .env.bak-$(date +%Y%m%d-%H%M%S) && chmod 600 .env.bak-*"
```

---

## Paso 1 · Contraseña del usuario admin

### Vía A (recomendada): desde la propia aplicación

No necesita servidor ni SQL, y la contraseña cumple la política nueva automáticamente
(12+ caracteres, con letras y números).

1. Entrar como **el otro administrador** (`Francisco` o `admin`, el que no se va a cambiar).
2. Ir a **Administración → Usuarios**, editar el usuario a cambiar.
3. Escribir la contraseña nueva y guardar.

> Nota: la aplicación impide que un administrador cambie **sus propias asignaciones** de escuela/rol.
> La contraseña sí se puede cambiar; si el formulario protesta, hazlo desde el otro admin.

**Verificar**: cerrar sesión y volver a entrar con la contraseña nueva.

### Vía B (si no hay acceso): con la herramienta del proyecto

`db/crear_admin.ps1` ya sabe actualizar la contraseña de un usuario existente. Se ejecuta en Windows
(necesita Java y el jar de Spring Security) y el hash nunca se escribe en disco.

> **Verificado** (13/10/2026): `db/_HashBcrypt.java` genera `$2a$12$…` (60 caracteres), cumple el
> patrón que valida `crear_admin.ps1` y la misma librería (`BCrypt.checkpw`) lo acepta con la clave
> correcta y lo rechaza con la incorrecta. Es el mismo algoritmo que usa la aplicación
> (`BCryptPasswordEncoder(12)`), así que el hash es 100% compatible.

**1. Guardar el hash actual** (la vuelta atrás), sin imprimirlo en pantalla:

```bash
ssh sih "cd ~/SIH-IA && docker compose exec -T db psql -U User_app -d SIH -A -t -c \"SELECT password_hash FROM sih.usuarios WHERE usuario='admin';\" > hash_admin_viejo.txt && chmod 600 hash_admin_viejo.txt && echo guardado"
```

**2. Generar el hash nuevo** (en Windows; el valor sale por pantalla, no lo pegues en ningún sitio):

```powershell
cd D:\proyectos\SIH-IA
$jar = (Get-ChildItem "$env:USERPROFILE\.m2\repository\org\springframework\security\spring-security-crypto" -Recurse -Filter 'spring-security-crypto-*.jar' | Sort-Object Name -Descending | Select-Object -First 1).FullName
java -cp $jar db\_HashBcrypt.java "LA_CLAVE_NUEVA" 12
```

**3. Aplicarlo** (ojo: el hash contiene `$`, así que se construye con plantilla de comillas simples
para que PowerShell no lo interprete):

```powershell
$hash = (java -cp $jar db\_HashBcrypt.java "LA_CLAVE_NUEVA" 12).Trim()
[System.IO.File]::WriteAllText("$env:TEMP\set_hash.sql", "UPDATE sih.usuarios SET password_hash = 'HASH', activo = true WHERE usuario = 'admin';".Replace('HASH', $hash))
Get-Content -Raw "$env:TEMP\set_hash.sql" | ssh sih "cd ~/SIH-IA && docker compose exec -T db psql -U User_app -d SIH -f -"
Remove-Item "$env:TEMP\set_hash.sql" -Force
```

**Verificar**: `POST /api/auth/login` con `{"correo": "...", "contrasenia": "LA_CLAVE_NUEVA"}` debe
devolver 200 y un token.

**Vuelta atrás**: aplicar el contenido de `hash_admin_viejo.txt` con el mismo `UPDATE`.

---

## Paso 2 · Contraseña de la base de datos

`POSTGRES_PASSWORD` solo se usa **al crear** la base, así que cambiar el `.env` no basta: hay que
cambiar la contraseña **en PostgreSQL** y después en el archivo. El orden importa: si el `.env` se
cambia antes, la aplicación no puede conectar al reiniciar.

**1. Generar la nueva** (hex, sin caracteres problemáticos):

```bash
openssl rand -hex 24
```

**2. Cambiarla en PostgreSQL** (las conexiones abiertas siguen funcionando: el sitio no se cae):

```bash
ssh sih "cd ~/SIH-IA && docker compose exec -T db psql -U User_app -d SIH -c \"ALTER ROLE \\\"User_app\\\" WITH PASSWORD 'LA_CLAVE_NUEVA';\""
```

**3. Actualizar el `.env`** (editar `DB_PASSWORD`; respaldo ya hecho arriba):

```bash
ssh sih "cd ~/SIH-IA && sed -i 's|^DB_PASSWORD=.*|DB_PASSWORD=LA_CLAVE_NUEVA|' .env && grep -c '^DB_PASSWORD=' .env && chmod 600 .env"
```

**4. Recrear la aplicación** (aquí sí hay ~30 s de corte):

```bash
ssh sih "cd ~/SIH-IA && docker compose up -d --no-deps app && sleep 20 && docker compose ps --format '{{.Service}}: {{.Status}}'"
```

**Verificar**:

```bash
ssh sih "cd ~/SIH-IA && curl -s -o /dev/null -w 'app: %{http_code}\n' http://127.0.0.1/api/version && docker compose logs app --tail 40 | grep -ci 'password authentication failed'"
```

El contador del final **debe ser 0**. Y entrar al sitio con el navegador.

**Vuelta atrás**: `ALTER ROLE` con la contraseña vieja + restaurar el `.env` + recrear la app.

---

## Paso 3 · Secreto JWT

Al cambiarlo, **todos los tokens emitidos dejan de valer**: hay que avisar a los usuarios de que
tendrán que volver a entrar (las sesiones activas se cierran).

**1. Generar uno nuevo** (mínimo 32 bytes; el que hay usa 48):

```bash
openssl rand -base64 48
```

**2. Actualizar el `.env`**:

```bash
ssh sih "cd ~/SIH-IA && sed -i 's|^JWT_SECRETO=.*|JWT_SECRETO=NUEVO_SECRETO|' .env && chmod 600 .env"
```

**3. Recrear la aplicación**:

```bash
ssh sih "cd ~/SIH-IA && docker compose up -d --no-deps app"
```

**Verificar**: el sitio responde, y un token emitido **antes** del cambio debe ser rechazado (401).
Entrar de nuevo con el navegador para confirmar que el login funciona.

**Vuelta atrás**: restaurar el `.env` (el valor viejo está en el respaldo) y recrear la app.

---

## Después de rotar

- [ ] Entrar al sitio con el navegador y comprobar que HTTPS sigue en verde.
- [ ] Comprobar que el despliegue automático sigue verde (verifica contra `http://127.0.0.1`).
- [ ] Borrar los respaldos que contienen secretos (`.env.bak-*`), o dejarlos con `chmod 600`.
- [ ] Borrar `hash_admin_viejo.txt` cuando ya no haga falta.
- [ ] Avisar a los usuarios de que deben volver a iniciar sesión (paso 3).
- [ ] Anotar la fecha de la rotación en las notas del proyecto.

## Pendientes relacionados (del informe de seguridad)

- `User_app` es **superusuario** de PostgreSQL porque es el `POSTGRES_USER`. Se arregla creando un rol
  de aplicación sin privilegios (Hibernate no crea tablas: `ddl-auto` está descomentado como
  `validate`, así que no necesita DDL).
- Faltan copias de seguridad **probadas** (restaurar en un entorno de prueba, no solo generarlas).
- `log_connections` y `log_statement` están apagados: sin rastro de auditoría en la base.
