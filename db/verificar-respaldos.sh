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
# Comprueba las DOS mitades: la base de datos y las fotos subidas (el volumen de
# Docker). Desde el 15/10/2026 las fotos tambien se respaldan, asi que tambien
# hay que vigilarlas: si se quedaran sin respaldar otra vez, esto lo dice.
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
VOLUMEN="${VOLUMEN:-sih-ia_uploads}"

echo "Verificando respaldos en $DESTINO"

# ----------------------------------------------------------------------------
# 1) LA BASE DE DATOS
# ----------------------------------------------------------------------------
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

echo "  base mas reciente: $(basename "$ULTIMO")"
echo "    antiguedad: ${HORAS} h (maximo ${MAX_HORAS})   tamano: $((TAMANO / 1024)) KB   copias: $TOTAL"

fallos=0

if [ "$HORAS" -gt "$MAX_HORAS" ]; then
  echo "ERROR: el respaldo de la base tiene ${HORAS} horas. El respaldo automatico no esta corriendo."
  fallos=1
fi

if [ "$TAMANO" -lt "$MIN_BYTES" ]; then
  echo "ERROR: el respaldo de la base pesa $TAMANO bytes, menos del minimo. Puede estar truncado."
  fallos=1
fi

if ! gzip -t "$ULTIMO" 2>/dev/null; then
  echo "ERROR: el respaldo de la base esta corrupto."
  fallos=1
fi

# ----------------------------------------------------------------------------
# 2) LAS FOTOS SUBIDAS (volumen de Docker)
# ----------------------------------------------------------------------------
ULTIMAS_FOTOS="$(ls -t "$DESTINO"/fotos-*.tar.gz 2>/dev/null | head -1)"
if [ -z "$ULTIMAS_FOTOS" ]; then
  echo "ERROR: no hay ningun respaldo fotos-*.tar.gz: las fotos subidas NO estan respaldadas."
  fallos=1
else
  FECHA_F="$(stat -c %Y "$ULTIMAS_FOTOS")"
  HORAS_F=$(( (AHORA - FECHA_F) / 3600 ))
  TAMANO_F="$(stat -c %s "$ULTIMAS_FOTOS")"
  TOTAL_F="$(ls -1 "$DESTINO"/fotos-*.tar.gz 2>/dev/null | wc -l)"
  echo "  fotos mas reciente: $(basename "$ULTIMAS_FOTOS")"
  echo "    antiguedad: ${HORAS_F} h (maximo ${MAX_HORAS})   tamano: $((TAMANO_F / 1024)) KB   copias: $TOTAL_F"

  if [ "$HORAS_F" -gt "$MAX_HORAS" ]; then
    echo "ERROR: el respaldo de fotos tiene ${HORAS_F} horas."
    fallos=1
  fi

  if ! gzip -t "$ULTIMAS_FOTOS" 2>/dev/null; then
    echo "ERROR: el respaldo de fotos esta corrupto."
    fallos=1
  fi

  # Lo que de verdad delata un respaldo vacio: comparar el numero de ficheros
  # con los que hay AHORA en el volumen. Un tar sin contenido pesa poco y
  # "parece" correcto; contar lo canta.
  PUNTO="$(docker volume inspect "$VOLUMEN" --format '{{.Mountpoint}}' 2>/dev/null || true)"
  if [ -n "$PUNTO" ]; then
    EN_VOLUMEN="$(sudo -n find "$PUNTO" -type f 2>/dev/null | wc -l)"
    EN_RESPALDO="$(tar -tzf "$ULTIMAS_FOTOS" 2>/dev/null | grep -vc '/$' || true)"
    echo "    ficheros: $EN_RESPALDO en el respaldo | $EN_VOLUMEN en el volumen ahora"
    if [ "$EN_VOLUMEN" != "$EN_RESPALDO" ]; then
      echo "ERROR: el respaldo de fotos no coincide con el volumen ($EN_RESPALDO vs $EN_VOLUMEN)."
      echo "       Ojo: es normal que cambie si se subieron fotos DESPUES del respaldo; revisa la fecha."
      fallos=1
    fi
  else
    echo "  AVISO: no encuentro el volumen $VOLUMEN; no puedo comparar el contenido."
  fi
fi

# ----------------------------------------------------------------------------
# 3) EL LOG DEL CRON
#
# Es donde se ve el fallo mas comun (permiso de ejecucion) y la razon exacta
# cuando el respaldo no se pudo generar.
#
# Se mira SOLO desde el ultimo OK hacia adelante: los "Permission denied" viejos
# que ya se arreglaron siguen en el archivo y no deben contar.
# ----------------------------------------------------------------------------
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
