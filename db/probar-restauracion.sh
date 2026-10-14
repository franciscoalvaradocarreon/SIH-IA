#!/usr/bin/env bash
# ============================================================================
# PRUEBA DE RESTAURACION
# ============================================================================
# Responde a la otra mitad de la pregunta: no basta con que el respaldo exista,
# tiene que poder RESTAURARSE. Este script lo demuestra sin tocar produccion.
#
# Que hace:
#   1. Crea una base temporal vacia (por defecto sih_restore_prueba).
#   2. Carga ahi el respaldo mas reciente (~/respaldos/SIH-*.sql.gz).
#   3. Compara las filas de TODAS las tablas contra la base real y, ademas, la
#      huella md5 del contenido de varias tablas grandes.
#   4. Borra la base temporal. Siempre, incluso si algo falla.
#
# Uso:
#   ./db/probar-restauracion.sh                  -> prueba con el respaldo mas reciente
#   ./db/probar-restauracion.sh /ruta/otro.sql.gz
#
# CUIDADO, esto es importante:
#   El respaldo se genera con pg_dump --clean --if-exists, o sea que al cargarlo
#   BORRA y RECREA los objetos. Por eso este script escribe SIEMPRE en una base
#   aparte y lleva un candado que aborta si el nombre del destino no es el de la
#   prueba. Nunca hay que cargar un respaldo sobre la base en uso.
#
# Medido el 14/10/2026 sobre el respaldo de produccion: restauracion correcta,
# 21 de 21 tablas coincidiendo y las huellas md5 identicas.
# ============================================================================
set -uo pipefail

RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$RAIZ"

ORIGEN="${ORIGEN:-SIH}"
PRUEBA="${PRUEBA:-sih_restore_prueba}"
RESPALDO="${1:-$(ls -t "${DESTINO:-$HOME/respaldos}"/SIH-*.sql.gz 2>/dev/null | head -1)}"

# CANDADO: si el destino no es exactamente el nombre de la prueba, no se hace nada.
if [ "$PRUEBA" != "sih_restore_prueba" ]; then
  echo "ABORTADO: el destino de la prueba debe llamarse sih_restore_prueba (vale '$PRUEBA')."
  exit 1
fi

if [ -z "$RESPALDO" ] || [ ! -f "$RESPALDO" ]; then
  echo "ERROR: no encuentro el respaldo a probar."
  exit 1
fi

# La limpieza va en un trap para que la base de prueba no se quede ni cuando el
# script falle o lo interrumpan.
trap 'docker compose exec -T db psql -U "$USUARIO_DB" -d postgres -c "DROP DATABASE IF EXISTS $PRUEBA;" >/dev/null 2>&1; echo "[limpieza] base de prueba borrada"' EXIT

USUARIO_DB="$(grep -E '^DB_USER=' .env 2>/dev/null | head -1 | cut -d= -f2- | tr -d '\r')"
USUARIO_DB="${USUARIO_DB:-User_app}"

echo "=== 0) comprobaciones previas ==="
# Un respaldo no debe cambiar de base por su cuenta: si llevara un \connect,
# cargarlo podria escribir donde no toca.
if gunzip -c "$RESPALDO" | grep -qE '^\\connect|^\\c '; then
  echo "ABORTADO: el respaldo contiene una orden \\connect."
  exit 1
fi
echo "  respaldo: $(basename "$RESPALDO") ($(stat -c%s "$RESPALDO") bytes comprimidos)"

echo "=== 1) base de prueba vacia ==="
docker compose exec -T db psql -U "$USUARIO_DB" -d postgres -c "DROP DATABASE IF EXISTS $PRUEBA;" >/dev/null 2>&1
docker compose exec -T db psql -U "$USUARIO_DB" -d postgres -c "CREATE DATABASE $PRUEBA;" 2>&1 | sed 's/^/  /'

echo "=== 2) restauracion (cronometrada) ==="
INICIO=$(date +%s)
if gunzip -c "$RESPALDO" | docker compose exec -T db psql -U "$USUARIO_DB" -d "$PRUEBA" -v ON_ERROR_STOP=1 -q -f - > /tmp/restauracion.log 2>&1; then
  echo "  RESTAURACION CORRECTA en $(( $(date +%s) - INICIO )) segundos"
else
  echo "  FALLO tras $(( $(date +%s) - INICIO )) segundos. Ultimas lineas:"
  tail -12 /tmp/restauracion.log
fi
echo "  errores en el log de carga: $(grep -ciE 'error|fatal' /tmp/restauracion.log || true)"

echo "=== 3) filas de TODAS las tablas: original vs restaurada ==="
cat > /tmp/filas_prueba.sql <<'SQL'
SELECT format('SELECT %L AS tabla, COUNT(*) AS filas FROM sih.%I;', table_name, table_name)
FROM information_schema.tables WHERE table_schema='sih' AND table_type='BASE TABLE' ORDER BY table_name;
\gexec
SQL
docker compose exec -T db psql -U "$USUARIO_DB" -d "$ORIGEN" -A -F'|' -t -f - < /tmp/filas_prueba.sql 2>/dev/null | tr -d ' \r' | grep '|' | sort > /tmp/filas_origen.txt
docker compose exec -T db psql -U "$USUARIO_DB" -d "$PRUEBA" -A -F'|' -t -f - < /tmp/filas_prueba.sql 2>/dev/null | tr -d ' \r' | grep '|' | sort > /tmp/filas_prueba.txt
echo "  tablas: original=$(wc -l < /tmp/filas_origen.txt)  restaurada=$(wc -l < /tmp/filas_prueba.txt)"
if diff -q /tmp/filas_origen.txt /tmp/filas_prueba.txt >/dev/null 2>&1; then
  echo "  TODAS COINCIDEN"
else
  echo "  DIFERENCIAS:"
  diff /tmp/filas_origen.txt /tmp/filas_prueba.txt | head -20
fi

echo "=== 4) contenido de tablas grandes (huella md5 de todas sus filas) ==="
for t in horario disponibilidad_maestro corrida_ia_detalle; do
  q="SELECT COALESCE(md5(string_agg(x::text, '|' ORDER BY x::text)), 'vacia') FROM sih.$t x;"
  a="$(docker compose exec -T db psql -U "$USUARIO_DB" -d "$ORIGEN" -A -t -c "$q" | tr -d ' \r')"
  b="$(docker compose exec -T db psql -U "$USUARIO_DB" -d "$PRUEBA" -A -t -c "$q" | tr -d ' \r')"
  if [ "$a" = "$b" ]; then
    echo "  $t: IDENTICA  $a"
  else
    echo "  $t: DIFERENTE  $a vs $b"
  fi
done

rm -f /tmp/filas_prueba.sql /tmp/filas_origen.txt /tmp/filas_prueba.txt /tmp/restauracion.log
echo "FIN"
