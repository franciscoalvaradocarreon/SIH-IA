#!/usr/bin/env bash
# ============================================================================
# PRUEBA DE RESTAURACION DE UN RESPALDO
# ============================================================================
# Objetivo:
#   Demostrar que un respaldo SIRVE. Un respaldo que nunca se ha restaurado no
#   es un respaldo: es una esperanza. Este script crea una base APARTE, carga el
#   archivo ahi, cuenta las filas y la borra.
#
#   NUNCA toca la base de produccion: el nombre de la base de prueba es distinto.
#
# Uso:
#   ./db/restaurar.sh ~/respaldos/SIH-20260921-020000.sql.gz
#   ./db/restaurar.sh                -> usa el respaldo mas reciente
# ============================================================================
set -euo pipefail

RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$RAIZ"

# Del .env solo se leen el nombre de la base y el usuario (ver la explicacion
# completa en db/respaldar.sh: hacer 'source' rompe docker compose si el archivo
# tiene finales de linea de Windows).
leer_env() { grep -E "^$1=" .env 2>/dev/null | head -1 | cut -d= -f2- | tr -d '\r'; }

if [ ! -f .env ]; then
  echo "ERROR: falta el archivo .env en $RAIZ"
  exit 1
fi

USUARIO="$(leer_env DB_USER)"; USUARIO="${USUARIO:-User_app}"
BASE="$(leer_env DB_NAME)";    BASE="${BASE:-SIH}"
PRUEBA="sih_prueba_restauracion"

# Si no se indica archivo, se toma el mas reciente.
ARCHIVO="${1:-$(ls -1t "$HOME/respaldos"/SIH-*.sql.gz 2>/dev/null | head -1)}"
if [ -z "$ARCHIVO" ] || [ ! -f "$ARCHIVO" ]; then
  echo "ERROR: no hay respaldo que restaurar. Uso: ./db/restaurar.sh <archivo.sql.gz>"
  exit 1
fi

echo "[$(date '+%F %T')] probando el respaldo: $ARCHIVO"

# La base de prueba se recrea desde cero para que el resultado no dependa de
# restos de una prueba anterior.
docker compose exec -T db psql -U "$USUARIO" -d postgres -v ON_ERROR_STOP=1 -q \
  -c "DROP DATABASE IF EXISTS $PRUEBA WITH (FORCE);"
docker compose exec -T db psql -U "$USUARIO" -d postgres -v ON_ERROR_STOP=1 -q \
  -c "CREATE DATABASE $PRUEBA;"

echo "[$(date '+%F %T')] cargando el respaldo en la base de prueba '$PRUEBA'..."
gunzip -c "$ARCHIVO" \
  | docker compose exec -T db psql -U "$USUARIO" -d "$PRUEBA" -v ON_ERROR_STOP=1 -q

echo ""
echo "===== QUE HAY DENTRO DEL RESPALDO ====="
docker compose exec -T db psql -U "$USUARIO" -d "$PRUEBA" -t -A -F ' | ' -c "
SELECT 'escuelas', count(*) FROM sih.escuelas
UNION ALL SELECT 'grupos', count(*) FROM sih.grupos
UNION ALL SELECT 'maestros', count(*) FROM sih.maestros
UNION ALL SELECT 'asignaciones', count(*) FROM sih.asignacion
UNION ALL SELECT 'clases en horario', count(*) FROM sih.horario
UNION ALL SELECT 'usuarios', count(*) FROM sih.usuarios
ORDER BY 1;"

echo ""
echo "===== INDICES DE SOLAPE EN EL RESPALDO ====="
docker compose exec -T db psql -U "$USUARIO" -d "$PRUEBA" -t -A -c "
SELECT count(*) || ' de 3 indices UNIQUE parciales'
  FROM pg_indexes
 WHERE schemaname = 'sih' AND tablename = 'horario'
   AND indexname IN ('no_solape_grupo_bloque','no_solape_maestro_bloque','no_solape_aula_bloque')
   AND indexdef ILIKE '%UNIQUE%';"

echo ""
echo "[$(date '+%F %T')] limpiando la base de prueba..."
docker compose exec -T db psql -U "$USUARIO" -d postgres -v ON_ERROR_STOP=1 -q \
  -c "DROP DATABASE $PRUEBA WITH (FORCE);"

echo "[$(date '+%F %T')] PRUEBA TERMINADA. Si los numeros coinciden con produccion, el respaldo sirve."
