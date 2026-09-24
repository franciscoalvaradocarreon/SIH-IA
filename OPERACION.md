# Operación del SIH en producción

Guía de lo que hay que hacer para publicar cambios y para volver atrás cuando algo falla.
Todo lo que aparece aquí está probado en este despliegue.

---

## Datos del despliegue

| Dato | Valor |
|---|---|
| Servidor | Oracle Cloud Always Free · ARM Ampere A1 · 4 OCPU / 24 GB |
| IP pública | `140.84.191.170` (**reservada**: no cambia) |
| Usuario SSH | `opc` (alias `sih` configurado en el PC) |
| Carpeta del proyecto | `~/SIH-IA` |
| Imagen | `ghcr.io/franciscoalvaradocarreon/sih-ia` |
| Contenedores | `sih-ia-app-1`, `sih-ia-db-1`, `sih-ia-caddy-1` |
| Respaldos | `~/respaldos`, diarios a las 02:00, se conservan 14 |
| Comprobación de salud | `http://140.84.191.170/api/version` |

**Antes de empezar**, comprueba que tu atajo SSH funciona:

```powershell
ssh sih "echo conectado"
```

Si no funciona, revisa `C:\Users\USER\.ssh\config` (debe tener el bloque `Host sih`).

---

## 1. Publicar un cambio (flujo normal)

### Paso 1 · Probar en local

```powershell
cd D:\Cursos\Java\ProyectoEE\SIH-IA
docker compose up -d --build
```

Abre `http://localhost:9080` y **prueba el cambio de verdad**. Publicar algo que no has visto funcionar es la forma más rápida de tener que hacer una reversa.

### Paso 2 · Subir y esperar el CI

```powershell
git add -A
git status --short
```

⚠️ **Revisa la lista**: no deben aparecer `.env` ni `db/04_datos_cliente.sql` (llevan secretos y hashes de contraseñas).

```powershell
git commit -m "descripcion clara del cambio"
git push origin main
```

Ve a **Actions** y espera a que el workflow **CI** termine en verde. Sus 4 trabajos son la aduana: front, backend, esquema (con la prueba de que el índice de solape muerde) e imagen Docker. **Si el CI está en rojo, no publiques**: arréglalo primero.

### Paso 3 · Publicar la versión

```powershell
git tag -a v1.0.2 -m "Descripcion de lo que trae esta version"
git push origin v1.0.2
```

Usa una versión **nueva** cada vez (`v1.0.2`, `v1.0.3`...). Una versión publicada no se reescribe nunca.

### Paso 4 · Esperar (es automático)

| Workflow | Qué hace | Cuánto tarda |
|---|---|---|
| **Release** | Construye la imagen **solo ARM64** (runner ARM nativo, sin emulación) y la publica en GHCR, y adjunta el WAR | unos minutos |
| **CD** | Entra por SSH, descarga la imagen, reinicia solo la aplicación y **verifica la versión** | ~2 min |

No hay que hacer nada. El Release **llama** al CD como último trabajo, así que el CD
no puede ejecutarse antes de que la imagen exista: si el build falla, no se
despliega y producción se queda como estaba.

> Este encadenamiento antes se hacía con el disparador `workflow_run` y **no
> funcionaba**: los cuatro despliegues que había registrados se habían lanzado a
> mano, y por eso `v1.1.1` se construyó y se publicó en GHCR pero producción se
> quedó en `v1.0.1`. Ahora el Release llama al CD directamente
> (`uses: ./.github/workflows/cd.yml`), que es una garantía por construcción y no
> un evento que pueda fallar en silencio.

### Paso 5 · Confirmar

```powershell
ssh -n sih "curl -s http://127.0.0.1/api/version"
```

O desde el navegador: `http://140.84.191.170/api/version`

Debe responder con **la versión que acabas de publicar**:

```json
{"aplicacion":"SIH-IA","version":"v1.0.2","commit":"...","estado":"ok"}
```

Si la versión no coincide, el CD se habrá puesto en rojo y te habrá mostrado los logs del servidor.

### Redesplegar sin publicar una versión nueva

**Actions → CD (desplegar en el servidor) → Run workflow.** En el campo
`version` escribe el tag que esperas ver en producción (por ejemplo `v1.1.1`); si
lo dejas vacío, solo comprueba que el servicio responde. Sirve para volver a
desplegar lo último publicado o para diagnosticar sin tocar nada.

---

## 2. Reversa del código (cuando la versión nueva falla)

Hay dos formas. La primera es para **salir del apuro en un minuto**; la segunda deja todo ordenado.

### Opción A · Volver a la imagen anterior (emergencia, ~1 minuto)

Las versiones anteriores siguen publicadas en GHCR, así que se puede volver a cualquiera sin recompilar nada.

```powershell
ssh sih "cd ~/SIH-IA && sed -i 's|^IMAGEN_APP=.*|IMAGEN_APP=ghcr.io/franciscoalvaradocarreon/sih-ia:v1.0.1|' .env && docker compose pull app && docker compose up -d --no-build app && sleep 20 && curl -s http://127.0.0.1/api/version"
```

Cambia `v1.0.1` por **la última versión que sabes que funcionaba**.

Verifica qué versiones existen:

```powershell
ssh sih "docker images --format 'table {{.Repository}}\t{{.Tag}}\t{{.CreatedSince}}' | grep sih-ia"
```

⚠️ **Importante y fácil de olvidar**: este atajo deja el `.env` *anclado* a esa versión. Mientras esté así, el CD automático **no podrá desplegar versiones nuevas** (fallará con un mensaje de versión que no coincide). Cuando arregles el problema, devuélvelo a `latest`:

```powershell
ssh sih "cd ~/SIH-IA && sed -i 's|^IMAGEN_APP=.*|IMAGEN_APP=ghcr.io/franciscoalvaradocarreon/sih-ia:latest|' .env && grep IMAGEN_APP .env"
```

### Opción B · Revertir en git y publicar la corrección (~10 min)

Es la forma ordenada: el historial queda coherente y el CD hace el resto solo.

```powershell
cd D:\Cursos\Java\ProyectoEE\SIH-IA
git log --oneline -5                     # localiza el commit que rompio algo
git revert <hash-del-commit>             # crea un commit que lo deshace
git push origin main
git tag -a v1.0.6 -m "Revierte el cambio que rompio X"
git push origin v1.0.6
```

El Release y el CD se encargan del resto, igual que en una publicación normal.

### Cual opción usar

| Situación | Opción |
|---|---|
| La aplicación no arranca o da errores graves y hay prisa | **A** (1 minuto) |
| Un fallo menor, sin urgencia, o varios cambios que revertir | **B** |
| No sabes qué commit lo rompió | **A** primero para calmar la situación, luego **B** |

### Aviso importante sobre cambios de esquema

La reversa de imagen es segura **mientras la versión nueva no haya cambiado la estructura de la base de datos**. Este proyecto no usa migraciones automáticas: el esquema se versiona en `db/`. Si algún día una publicación cambia tablas o columnas, volver a la imagen anterior **no** revierte ese cambio, y la versión vieja podría encontrar una base que no espera. En ese caso hay que coordinar código y datos, y restaurar un respaldo (sección 3).

---

## 3. Reversa de datos (restaurar un respaldo)

⚠️ **Esto sobrescribe los datos actuales.** Solo cuando haya corrupción, un borrado accidental, o un cambio de esquema que haya que deshacer. **Todo lo ocurrido después del respaldo se pierde.**

### Paso 1 · Ver qué respaldos hay

```powershell
ssh sih "ls -la ~/respaldos/"
```

### Paso 2 · Comprobar que el respaldo sirve (antes de tocar producción)

```powershell
ssh sih "cd ~/SIH-IA && ./db/restaurar.sh ~/respaldos/SIH-20260922-020000.sql.gz"
```

Esto lo carga en una base **aparte**, cuenta las filas y la borra. Si los números cuadran (escuelas, grupos, clases en horario), el respaldo es bueno.

### Paso 3 · Bajar copia a tu PC (por si acaso)

```powershell
scp sih:~/respaldos/SIH-20260922-020000.sql.gz D:\RespaldosSIH\
```

### Paso 4 · Restaurar en producción

Se para la aplicación primero para que no haya conexiones abiertas que bloqueen los `DROP`:

```powershell
ssh sih "cd ~/SIH-IA && docker compose stop app"
```

```powershell
ssh sih "cd ~/SIH-IA && gunzip -c ~/respaldos/SIH-20260922-020000.sql.gz | docker compose exec -T db psql -U User_app -d SIH -v ON_ERROR_STOP=1 -q"
```

```powershell
ssh sih "cd ~/SIH-IA && docker compose start app"
```

### Paso 5 · Verificar

```powershell
ssh sih "cd ~/SIH-IA && docker compose exec -T db psql -U User_app -d SIH -f - < db/qa_esquema.sql | tail -12"
```

Debe decir `OK -> base lista para produccion`. Y luego entra en la aplicación y comprueba los datos a mano.

---

## 4. Diagnóstico rápido

```powershell
# Estado de los contenedores
ssh sih "cd ~/SIH-IA && docker compose ps"

# Logs de la aplicacion (lo mas util)
ssh sih "cd ~/SIH-IA && docker compose logs app --tail 50"

# Logs de Caddy (errores 502, certificados)
ssh sih "cd ~/SIH-IA && docker compose logs caddy --tail 30"

# Logs de la base
ssh sih "cd ~/SIH-IA && docker compose logs db --tail 30"

# Version desplegada
ssh sih "curl -s http://127.0.0.1/api/version"

# Espacio en disco
ssh sih "df -h / && du -sh ~/SIH-IA ~/respaldos"

# Memoria
ssh sih "free -h"

# Recursos de la aplicacion en vivo (CPU y memoria)
ssh -n sih 'docker stats --no-stream sih-ia-app-1'
```

### Cómo leer `docker stats`

- **`CPU %` se mide POR NÚCLEO**: 100% = un núcleo entero ocupado. El servidor tiene 4.
- **`IMAGE` distingue el servidor de tu PC**: `ghcr.io/franciscoalvaradocarreon/sih-ia:latest` es
  el servidor; `sih-ia:local` es tu PC. Ojo, porque el contenedor se llama `sih-ia-app-1` **en los
  dos sitios** (el nombre sale del `name: sih-ia` del compose), así que el nombre no distingue nada:
  lo único que separa a los dos es el `ssh`.
- **`MEM USAGE` es una marca alta, no un valor fijo**: el recolector de la JVM devuelve memoria al
  sistema y la cifra baja sola. Hay que mirar varias muestras para ver la tendencia.
- **La app en reposo cuesta ~1,4 GiB.** Si ves eso y `CPU %` a 0, no hay nada generando.

### El motor de horarios y la CPU

Una generación de **una sola escuela** debe mostrar **`CPU %` cerca de 300%**: son los 3 hilos de
`app.ia.hilos` trabajando en paralelo.

| Qué ves | Qué significa |
|---|---|
| ~300% con una escuela generando | Correcto: 3 intentos a la vez |
| ~100% con una escuela generando | `app.ia.hilos=1`, o el pool no se está usando |
| ~0% y 1,4 GiB | No hay nada generando |

Cuánto tarda: una generación de 6 intentos de 200 s tarda **~400 s** con 3 hilos (2 tandas de 3) en
vez de ~1200 s. Con tres escuelas a la vez, se reparten los 3 hilos y las tres terminan en torno a
1200 s, en lugar de 1200 / 2400 / 3600.

Los mandos están en `BackendJava/src/main/resources/application.properties`: `app.ia.hilos`
(intentos en paralelo, 3 por defecto) y `app.ia.intentos` (cuántos intentos). El tope de memoria del
contenedor está en `docker-compose.yml` (`app.mem_limit: 8g`), y es lo que impide que la JVM calcule
su heap sobre los 24 GB de la máquina en vez de sobre un límite decidido.

**Generar un horario no escribe en la base de datos**: el horario real solo se toca al pulsar
**Registrar**, que es una acción aparte. Por eso varias escuelas pueden generar a la vez.

### Si el servidor no responde en absoluto

1. Consola de Oracle → **Compute → Instances**: mira si está `Running`.
2. Si está `Stopped`, pulsa **Start**.
3. Si está `Running` pero no responde, reiníciala desde la consola.
4. Tras el arranque, los contenedores se levantan solos (`restart: unless-stopped`). Dale un minuto.
5. Comprueba: `ssh sih "cd ~/SIH-IA && docker compose ps"`

---

## 5. Respaldos

| Tarea | Comando |
|---|---|
| Automático | Diario a las 02:00 (cron). Registro en `~/respaldos/respaldo.log` |
| Forzar uno ahora | `ssh sih "cd ~/SIH-IA && ./db/respaldar.sh"` |
| Probar que un respaldo sirve | `ssh sih "cd ~/SIH-IA && ./db/restaurar.sh"` |
| Ver el registro | `ssh sih "tail -20 ~/respaldos/respaldo.log"` |
| Bajar copia al PC | `scp sih:~/respaldos/*.sql.gz D:\RespaldosSIH\` |
| Liberar el servidor | `ssh sih "ls -1t ~/respaldos/SIH-*.sql.gz \| tail -n +2 \| xargs -r rm -f"` (conserva el último) |

**Regla de oro**: no borres nada del servidor hasta haber verificado la copia local
(verificar que el archivo pesa ~44 KB y que su firma gzip es correcta).

---

## 6. Comprobaciones periódicas recomendadas

| Frecuencia | Qué revisar |
|---|---|
| Semanal | Que el cron de respaldos sigue funcionando (`tail ~/respaldos/respaldo.log`) |
| Semanal | Bajar una copia de respaldo al PC |
| Mensual | Probar una restauración completa (`./db/restaurar.sh`) |
| Mensual | Consola de Oracle: consumo dentro del Always Free y estado de la instancia |
| Tras cada publicación | `http://140.84.191.170/api/version` responde con la versión correcta |
