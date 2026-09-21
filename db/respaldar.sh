#!/usr/bin/env bash
# ============================================================================
# RESPALDO DE LA BASE DE DATOS SIH
# ============================================================================
# Objetivo:
#   Producir un archivo comprimido capaz de reconstruir la base COMPLETA
#   (esquema + datos) y conservar solo los ultimos N, para que el disco no se
#   llene solo.
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

# Los secretos y el nombre de la base salen del .env del despliegue.
if [ -f .env ]; then
  set -a
  # shellcheck disable=SC1091
  . ./.env
  set +a
fi

DESTINO="${1:-$HOME/respaldos}"
RETENER="${RETENER:-14}"
BASE="${DB_NAME:-SIH}"
USUARIO="${DB_USER:-User_app}"

mkdir -p "$DESTINO"
SELLO="$(date +%Y%m%d-%H%M%S)"
ARCHIVO="$DESTINO/SIH-$SELLO.sql.gz"

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

echo "[$(date '+%F %T')] OK: $ARCHIVO ($((TAMANO / 1024)) KB)"

# Rotacion: se conservan los RETENER mas recientes.
TOTAL="$(ls -1 "$DESTINO"/SIH-*.sql.gz 2>/dev/null | wc -l)"
if [ "$TOTAL" -gt "$RETENER" ]; then
  ls -1t "$DESTINO"/SIH-*.sql.gz | tail -n +$((RETENER + 1)) | xargs -r rm -f
  echo "[$(date '+%F %T')] rotacion: $((TOTAL - RETENER)) respaldo(s) antiguo(s) eliminado(s)"
fi

echo "[$(date '+%F %T')] respaldos disponibles: $(ls -1 "$DESTINO"/SIH-*.sql.gz 2>/dev/null | wc -l)"
