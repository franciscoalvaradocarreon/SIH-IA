#!/usr/bin/env bash
# ============================================================================
# VERIFICAR QUE LOS RESPALDOS SE ESTEN GENERANDO
# ============================================================================
# Responde a una sola pregunta: el respaldo automatico, funciona?
#
# Nace de un fallo real y silencioso: db/respaldar.sh estaba versionado sin
# permiso de ejecucion, asi que el cron fallaba cada noche con "Permission
# denied" ANTES de que el script arrancara. El log crecia y no tenia ni un OK ni
# un ERROR dentro, solo la linea del permiso: tres semanas sin respaldos.
# Un respaldo que no se comprueba no es un respaldo.
#
# Uso:
#   ./db/verificar-respaldos.sh            -> exige un respaldo de menos de 48 h
#   ./db/verificar-respaldos.sh 24         -> menos de 24 h
#   DESTINO=/otra/ruta ./db/verificar-respaldos.sh
#
# Sale 0 si todo esta bien y 1 si hay algo que revisar, para que lo pueda usar
# el cron, la CI o una persona.
# ============================================================================
set -uo pipefail

DESTINO="${DESTINO:-$HOME/respaldos}"
MAX_HORAS="${1:-48}"
MIN_BYTES="${MIN_BYTES:-20000}"

echo "Verificando respaldos en $DESTINO"

ULTIMO="$(ls -t "$DESTINO"/SIH-*.sql.gz 2>/dev/null | head -1)"
if [ -z "$ULTIMO" ]; then
  echo "ERROR: no hay ningun respaldo SIH-*.sql.gz en $DESTINO"
  exit 1
fi

AHORA="$(date +%s)"
FECHA="$(stat -c %Y "$ULTIMO")"
HORAS=$(( (AHORA - FECHA) / 3600 ))
TAMANO="$(stat -c %s "$ULTIMO")"
TOTAL="$(ls -1 "$DESTINO"/SIH-*.sql.gz 2>/dev/null | wc -l)"

echo "  mas reciente: $(basename "$ULTIMO")"
echo "  antiguedad:   ${HORAS} h (maximo ${MAX_HORAS})"
echo "  tamano:       $((TAMANO / 1024)) KB (minimo $((MIN_BYTES / 1024)) KB)"
echo "  respaldos:    $TOTAL"

fallos=0

if [ "$HORAS" -gt "$MAX_HORAS" ]; then
  echo "ERROR: el respaldo mas reciente tiene ${HORAS} horas. El respaldo automatico no esta corriendo."
  fallos=1
fi

if [ "$TAMANO" -lt "$MIN_BYTES" ]; then
  echo "ERROR: el respaldo pesa $TAMANO bytes, menos del minimo. Puede estar truncado."
  fallos=1
fi

if ! gzip -t "$ULTIMO" 2>/dev/null; then
  echo "ERROR: el archivo comprimido esta corrupto."
  fallos=1
fi

# El log del cron es donde se ve el fallo mas comun (permiso de ejecucion) y la
# razon exacta cuando el respaldo no se pudo generar.
#
# Se mira SOLO desde el ultimo OK hacia adelante: los "Permission denied" viejos
# que ya se arreglaron siguen en el archivo y no deben contar.
LOG="$DESTINO/respaldo.log"
if [ -f "$LOG" ]; then
  DESDE="$(grep -n "OK:" "$LOG" 2>/dev/null | tail -1 | cut -d: -f1)"
  if [ -n "$DESDE" ]; then
    COLA="$(tail -n +$((DESDE + 1)) "$LOG")"
  else
    COLA="$(cat "$LOG")"
  fi
  if printf '%s\n' "$COLA" | grep -q "Permission denied"; then
    echo "ERROR: el cron no puede ejecutar el script de respaldo (le falta el permiso de ejecucion)."
    echo "       Arreglo: chmod +x db/respaldar.sh, y que el modo 100755 este en git."
    fallos=1
  fi
  echo "  ultima linea del log: $(tail -1 "$LOG")"
else
  echo "  AVISO: no encuentro $LOG, no puedo revisar el resultado del cron."
fi

if [ "$fallos" -ne 0 ]; then
  echo "RESULTADO: REVISAR"
  exit 1
fi

echo "RESULTADO: OK"
