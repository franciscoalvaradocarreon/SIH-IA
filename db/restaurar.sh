#!/usr/bin/env bash
# ============================================================================
# PRUEBA DE RESTAURACION DE UN RESPALDO
# ============================================================================
# Objetivo:
#   Demostrar que un respaldo SIRVE. Un respaldo que nunca se ha restaurado no
#   es un respaldo: es una esperanza. Este script crea una base APARTE, carga el
#   archivo ahi, COMPARA lo cargado contra la base real y la borra.
#
#   NUNCA toca la base de produccion: el nombre de la base de prueba es distinto
#   y hay un candado que aborta si no coincide.
#
# Uso:
#   ./db/restaurar.sh ~/respaldos/SIH-20260921-020000.sql.gz
#   ./db/restaurar.sh                -> usa el respaldo mas reciente
#
# CUIDADO: el respaldo se genera con pg_dump --clean --if-exists, o sea que al
# cargarlo BORRA y RECREA los objetos. Por eso todo se hace contra la base de
# prueba: cargar un respaldo sobre la base en uso la dejaria como el respaldo,
# perdiendo todo lo posterior.
#
# Medido el 14/10/2026 con el respaldo de produccion: restauracion correcta en
# 1 segundo, 0 errores, 21 de 21 tablas con las mismas filas y huellas md5
# identicas en horario, disponibilidad_maestro y corrida_ia_detalle.
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

# CANDADO: si alguien cambia el nombre de la base de prueba por el de produccion,
# este script borraria la base buena. Se comprueba antes de hacer nada.
if [ "$PRUEBA" = "$BASE" ] || [ "$PRUEBA" != "sih_prueba_restauracion" ]; then
  echo "ABORTADO: la base de prueba debe llamarse sih_prueba_restauracion y ser distinta de $BASE."
  exit 1
fi

# Si no se indica archivo, se toma el mas reciente.
ARCHIVO="${1:-$(ls -1t "$HOME/respaldos"/SIH-*.sql.gz 2>/dev/null | head -1 || true)}"
if [ -z "$ARCHIVO" ] || [ ! -f "$ARCHIVO" ]; then
  echo "ERROR: no hay respaldo que restaurar. Uso: ./db/restaurar.sh <archivo.sql.gz>"
  exit 1
fi

# Limpieza garantizada: pase lo que pase (fallo, interrupcion, Ctrl-C), la base
# de prueba no se queda.
trap 'docker compose exec -T db psql -U "$USUARIO" -d postgres -q -c "DROP DATABASE IF EXISTS $PRUEBA WITH (FORCE);" >/dev/null 2>&1; echo "[limpieza] base de prueba borrada"' EXIT

# Un respaldo no debe cambiar de base por su cuenta: si llevara un \connect,
# cargarlo podria escribir donde no toca.
if gunzip -c "$ARCHIVO" | grep -qE '^\\connect|^\\c '; then
  echo "ABORTADO: el respaldo contiene una orden \\connect."
  exit 1
fi

echo "[$(date '+%F %T')] probando el respaldo: $ARCHIVO ($(stat -c%s "$ARCHIVO") bytes)"

# La base de prueba se recrea desde cero para que el resultado no dependa de
# restos de una prueba anterior.
docker compose exec -T db psql -U "$USUARIO" -d postgres -v ON_ERROR_STOP=1 -q \
  -c "DROP DATABASE IF EXISTS $PRUEBA WITH (FORCE);"
docker compose exec -T db psql -U "$USUARIO" -d postgres -v ON_ERROR_STOP=1 -q \
  -c "CREATE DATABASE $PRUEBA;"

echo "[$(date '+%F %T')] cargando el respaldo en la base de prueba '$PRUEBA'..."
INICIO=$(date +%s)
gunzip -c "$ARCHIVO" \
  | docker compose exec -T db psql -U "$USUARIO" -d "$PRUEBA" -v ON_ERROR_STOP=1 -q
echo "[$(date '+%F %T')] carga terminada en $(( $(date +%s) - INICIO )) segundos"

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

# ----------------------------------------------------------------------------
# COMPARACION ESTRICTA: no basta con que cargue, tiene que coincidir
# ----------------------------------------------------------------------------
echo ""
echo "===== COMPARACION CONTRA LA BASE REAL ($BASE) ====="

cat > /tmp/filas_comparar.sql <<'SQL'
SELECT format('SELECT %L AS tabla, COUNT(*) AS filas FROM sih.%I;', table_name, table_name)
FROM information_schema.tables WHERE table_schema='sih' AND table_type='BASE TABLE' ORDER BY table_name;
\gexec
SQL

docker compose exec -T db psql -U "$USUARIO" -d "$BASE" -A -F'|' -t -f - < /tmp/filas_comparar.sql 2>/dev/null | tr -d ' \r' | grep '|' | sort > /tmp/filas_reales.txt || true
docker compose exec -T db psql -U "$USUARIO" -d "$PRUEBA" -A -F'|' -t -f - < /tmp/filas_comparar.sql 2>/dev/null | tr -d ' \r' | grep '|' | sort > /tmp/filas_prueba.txt || true

echo "  tablas: real=$(wc -l < /tmp/filas_reales.txt)  restaurada=$(wc -l < /tmp/filas_prueba.txt)"
if diff -q /tmp/filas_reales.txt /tmp/filas_prueba.txt >/dev/null 2>&1; then
  echo "  FILAS: todas coinciden"
else
  echo "  FILAS: hay diferencias ->"
  diff /tmp/filas_reales.txt /tmp/filas_prueba.txt | head -20 || true
fi

echo "  huellas del contenido de tres tablas grandes:"
for t in horario disponibilidad_maestro corrida_ia_detalle; do
  q="SELECT COALESCE(md5(string_agg(x::text, '|' ORDER BY x::text)), 'vacia') FROM sih.$t x;"
  a="$(docker compose exec -T db psql -U "$USUARIO" -d "$BASE" -A -t -c "$q" | tr -d ' \r')"
  b="$(docker compose exec -T db psql -U "$USUARIO" -d "$PRUEBA" -A -t -c "$q" | tr -d ' \r')"
  if [ "$a" = "$b" ]; then
    echo "    $t: IDENTICA  $a"
  else
    echo "    $t: DIFERENTE  $a vs $b"
  fi
done

rm -f /tmp/filas_comparar.sql /tmp/filas_reales.txt /tmp/filas_prueba.txt

echo ""
echo "[$(date '+%F %T')] PRUEBA TERMINADA. Si las filas y las huellas coinciden, el respaldo sirve."
