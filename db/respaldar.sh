#!/usr/bin/env bash
# ============================================================================
# RESPALDO DE LA BASE DE DATOS Y DE LAS FOTOS SUBIDAS
# ============================================================================
# Objetivo:
#   Producir los archivos comprimidos capaces de reconstruir TODO:
#     * SIH-<sello>.sql.gz    -> la base COMPLETA (esquema + datos)
#     * fotos-<sello>.tar.gz  -> el volumen 'uploads' (las fotos de los maestros)
#   y conservar solo los ultimos N de cada uno, para que el disco no se llene.
#
#   Las fotos son la mitad que se olvidaba: el pg_dump NO las incluye porque no
#   estan en la base, estan en un volumen de Docker. Si se perdia ese volumen,
#   las filas sobrevivian pero las imagenes no tenian vuelta atras.
#
# Uso:
#   ./db/respaldar.sh                    -> respalda en ~/respaldos
#   ./db/respaldar.sh /ruta/alterna      -> respalda en otra carpeta
#   RETENER=30 ./db/respaldar.sh         -> conserva 30 en vez de 14
#
# Instalacion en el cron (diario a las 02:00):
#   0 2 * * * /home/opc/SIH-IA/db/respaldar.sh >> /home/opc/respaldos/respaldo.log 2>&1
#
# Dos decisiones que importan:
#   * El dump lo hace el pg_dump de DENTRO del contenedor: es la version exacta
#     del servidor, y asi un pg_dump de otra version no puede inventarse nada.
#   * Se usa --clean --if-exists para poder cargar el respaldo sobre una base
#     que ya existe sin borrarla a mano antes.
# ============================================================================
set -euo pipefail

RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$RAIZ"

# Del .env solo se leen el nombre de la base y el usuario.
#
# OJO: aqui NO se hace 'source .env'. Motivo real, nos paso en el despliegue: si
# el .env llega con finales de linea de Windows (CRLF), 'source' deja un \r
# pegado al valor, y como las variables del entorno tienen prioridad sobre el
# .env, docker compose aborta con "invalid hostPort: 80" (el puerto del host
# queda como "80\r"). Leyendo solo lo necesario con 'tr -d "\r"', y sin exportar
# nada, docker compose lee el .env por su cuenta y lo interpreta bien.
leer_env() { grep -E "^$1=" .env 2>/dev/null | head -1 | cut -d= -f2- | tr -d '\r'; }

if [ ! -f .env ]; then
  echo "ERROR: falta el archivo .env en $RAIZ"
  exit 1
fi

DESTINO="${1:-$HOME/respaldos}"
RETENER="${RETENER:-14}"
BASE="$(leer_env DB_NAME)";    BASE="${BASE:-SIH}"
USUARIO="$(leer_env DB_USER)"; USUARIO="${USUARIO:-User_app}"

mkdir -p "$DESTINO"
SELLO="$(date +%Y%m%d-%H%M%S)"
ARCHIVO="$DESTINO/SIH-$SELLO.sql.gz"

# ----------------------------------------------------------------------------
# 1) LA BASE DE DATOS
# ----------------------------------------------------------------------------
echo "[$(date '+%F %T')] respaldando $BASE -> $ARCHIVO"

docker compose exec -T db pg_dump -U "$USUARIO" -d "$BASE" --clean --if-exists \
  | gzip > "$ARCHIVO"

# Un dump truncado por un fallo a mitad es PEOR que no tener respaldo, porque
# parece que existe. Por eso se comprueba el tamano y la integridad del gzip.
TAMANO="$(stat -c%s "$ARCHIVO")"
if [ "$TAMANO" -lt 20000 ]; then
  echo "ERROR: el respaldo pesa solo $TAMANO bytes; algo fallo. Se conserva para diagnostico."
  exit 1
fi

if ! gzip -t "$ARCHIVO"; then
  echo "ERROR: el archivo comprimido esta corrupto."
  exit 1
fi

echo "[$(date '+%F %T')] OK base: $ARCHIVO ($((TAMANO / 1024)) KB)"

# Rotacion: se conservan los RETENER mas recientes.
TOTAL="$(ls -1 "$DESTINO"/SIH-*.sql.gz 2>/dev/null | wc -l)"
if [ "$TOTAL" -gt "$RETENER" ]; then
  ls -1t "$DESTINO"/SIH-*.sql.gz | tail -n +$((RETENER + 1)) | xargs -r rm -f
  echo "[$(date '+%F %T')] rotacion de base: $((TOTAL - RETENER)) respaldo(s) antiguo(s) eliminado(s)"
fi

# ----------------------------------------------------------------------------
# 2) LAS FOTOS SUBIDAS (volumen de Docker)
#
# El pg_dump no las incluye: los archivos viven en el volumen 'uploads' (fotos
# de maestros). Se comprime desde el punto de montaje que reporta Docker, sin
# escribir la ruta a mano (si algun dia cambia, esto sigue funcionando).
#
# Necesita 'sudo' porque el directorio pertenece a root. En el servidor, 'opc'
# lo tiene sin contrasena. Si no lo tuviera, se avisa y se continua: la base ya
# quedo respaldada, y es mejor un respaldo parcial avisado que ninguno.
#
# La comprobacion que de verdad importa: el archivo debe tener EXACTAMENTE los
# mismos ficheros que el volumen. Un tar vacio por un problema de montaje pesa
# poco y "parece" un respaldo: contar los ficheros lo delata.
# ----------------------------------------------------------------------------
VOLUMEN="sih-ia_uploads"
FOTOS="$DESTINO/fotos-$SELLO.tar.gz"
PUNTO="$(docker volume inspect "$VOLUMEN" --format '{{.Mountpoint}}' 2>/dev/null || true)"

if [ -z "$PUNTO" ]; then
  echo "[$(date '+%F %T')] AVISO: no encuentro el volumen $VOLUMEN; no se respaldaron las fotos."
else
  echo "[$(date '+%F %T')] respaldando fotos ($VOLUMEN) -> $FOTOS"
  if sudo -n tar -czf "$FOTOS" -C "$PUNTO" . 2>/dev/null; then
    EN_VOLUMEN="$(sudo -n find "$PUNTO" -type f 2>/dev/null | wc -l)"
    EN_RESPALDO="$(tar -tzf "$FOTOS" | grep -vc '/$' || true)"
    TAMANO_FOTOS="$(stat -c%s "$FOTOS")"

    if [ "$EN_VOLUMEN" != "$EN_RESPALDO" ]; then
      echo "ERROR: el volumen tiene $EN_VOLUMEN archivo(s) y el respaldo $EN_RESPALDO. Se conserva para diagnostico."
      exit 1
    fi

    if ! gzip -t "$FOTOS"; then
      echo "ERROR: el archivo de fotos esta corrupto."
      exit 1
    fi

    echo "[$(date '+%F %T')] OK fotos: $FOTOS ($((TAMANO_FOTOS / 1024)) KB, $EN_RESPALDO archivo(s))"

    TOTAL_FOTOS="$(ls -1 "$DESTINO"/fotos-*.tar.gz 2>/dev/null | wc -l)"
    if [ "$TOTAL_FOTOS" -gt "$RETENER" ]; then
      ls -1t "$DESTINO"/fotos-*.tar.gz | tail -n +$((RETENER + 1)) | xargs -r rm -f
      echo "[$(date '+%F %T')] rotacion de fotos: $((TOTAL_FOTOS - RETENER)) antiguo(s) eliminado(s)"
    fi
  else
    echo "AVISO: no se pudieron respaldar las fotos (¿falta 'sudo' sin contrasena?). La base SI quedo respaldada."
  fi
fi

echo "[$(date '+%F %T')] disponibles: $(ls -1 "$DESTINO"/SIH-*.sql.gz 2>/dev/null | wc -l) base(s) y $(ls -1 "$DESTINO"/fotos-*.tar.gz 2>/dev/null | wc -l) de fotos"
