-- ============================================================================
-- REPORTE: MAESTRO (APODO) POR MATERIA, EN HORIZONTAL
-- ============================================================================
-- Que hace: toma el horario VIGENTE (version = 1) de una escuela y un semestre y
-- devuelve UNA FILA POR MATERIA, con una COLUMNA POR GRUPO. En cada celda va el
-- apodo del maestro que da esa materia a ese grupo; si la materia no se da en ese
-- grupo, la celda sale vacia.
--
-- Ejemplo (recortado, con las columnas reales de la escuela 1 / semestre 11):
--
--    clave   | materia                   | 1°A    | 1°B    | 5°A-P
--   ---------+---------------------------+--------+--------+--------
--    Algebra | Algebra                   | Carlos | Manuel |
--    ASO     | Administra Sistemas Oper.|        |        | Ovidio
--
-- POR QUE DEVUELVE UN CURSOR Y NO UNA TABLA:
--   Las columnas del pivote son los grupos, y los grupos cambian de una escuela a
--   otra (aqui son 21). Una funcion no puede declarar un numero de columnas que no
--   se conoce al escribirla, asi que la consulta se arma con SQL dinamico y se
--   entrega abierta en un cursor. Se lee asi, todo dentro de UNA transaccion:
--
--     BEGIN;
--     SELECT sih.reporte_maestro_por_materia(1, 11, 'cur');
--     FETCH ALL FROM cur;
--     COMMIT;
--
--   El tercer parametro es el nombre que se le da al cursor: sin el no hay forma
--   de pedirle las filas a Postgres. Si se quiere ver comodo, '\x on' antes del
--   FETCH muestra cada renglon en vertical.
--
-- APODO: se usa maestros.apodo y, si esta vacio, el titulo + nombre + apellidos.
--   Es la MISMA regla que aplica la pantalla (HorarioServicio, nombre corto del
--   maestro), para que el reporte diga exactamente lo que dice el horario.
--
-- SOLO GRUPOS CON CLASES: no se generan columnas para grupos sin ninguna clase en
--   ese horario; serian columnas vacias de lado a lado.
--
-- Idempotente: CREATE OR REPLACE, se puede ejecutar las veces que haga falta.
--
-- En una base RESTAURADA (donde la funcion quede propiedad de 'postgres'), el
-- usuario de la aplicacion necesita permiso explicito:
--   GRANT EXECUTE ON FUNCTION sih.reporte_maestro_por_materia(bigint, bigint, refcursor)
--     TO "User_app";
-- En produccion no hace falta: la crea el propio usuario de la aplicacion.
-- ============================================================================

CREATE OR REPLACE FUNCTION sih.reporte_maestro_por_materia(
    p_escuela_id  bigint,
    p_semestre_id bigint,
    p_cursor      refcursor
) RETURNS refcursor
LANGUAGE plpgsql
AS $funcion$
DECLARE
    -- Nombre corto del maestro. En una variable para no repetir el mismo texto en
    -- los dos huecos del string_agg: con DISTINCT, Postgres exige que la expresion
    -- del ORDER BY sea IDENTICA a la que se agrega.
    v_apodo    text := $apodo$coalesce(nullif(btrim(ma.apodo), ''), btrim(concat_ws(' ', ma.titulo, ma.nombre, ma.apellidos)))$apodo$;
    v_columnas text;
    v_sql      text;
BEGIN
    -- 1) Encabezados del pivote: un campo por grupo CON clases en ese horario, en el
    --    orden natural de lectura (grado y luego nombre: 1°A, 1°B, ... 5°A-P).
    SELECT string_agg(
               format('max(case when d.grupo = %L then d.maestros end) as %I', g.nombre, g.nombre),
               E',\n               ' ORDER BY g.grado NULLS LAST, g.nombre)
      INTO v_columnas
      FROM sih.grupos g
     WHERE g.escuela_id  = p_escuela_id
       AND g.semestre_id = p_semestre_id
       AND EXISTS (SELECT 1
                     FROM sih.horario h
                    WHERE h.grupo_id    = g.grupo_id
                      AND h.version     = 1
                      AND h.escuela_id  = p_escuela_id
                      AND h.semestre_id = p_semestre_id);

    -- Sin horario no hay columnas que armar: mejor decirlo claro que devolver vacio.
    IF v_columnas IS NULL THEN
        RAISE EXCEPTION 'No hay horario vigente para la escuela % y el semestre %', p_escuela_id, p_semestre_id;
    END IF;

    -- 2) El detalle (materia + grupo + apodos) se calcula UNA vez y despues se voltea.
    --    El string_agg con DISTINCT cubre el caso de una materia repartida entre dos
    --    maestros en el mismo grupo: la celda sale con los dos apodos separados por coma.
    v_sql := format($sql$
        WITH detalle AS (
            SELECT m.materia_id,
                   m.clave,
                   m.nombre AS materia,
                   g.nombre AS grupo,
                   string_agg(DISTINCT %1$s, ', ' ORDER BY %1$s) AS maestros
              FROM sih.horario h
              JOIN sih.asignacion a  ON a.asignacion_id = h.asignacion_id
              JOIN sih.materias   m  ON m.materia_id    = a.materia_id
              JOIN sih.grupos     g  ON g.grupo_id      = a.grupo_id
              JOIN sih.maestros   ma ON ma.maestro_id   = h.maestro_id
             WHERE h.version     = 1
               AND h.escuela_id  = %2$s
               AND h.semestre_id = %3$s
             GROUP BY m.materia_id, m.clave, m.nombre, g.nombre
        )
        SELECT d.clave,
               d.materia,
               %4$s
          FROM detalle d
         GROUP BY d.materia_id, d.clave, d.materia
         ORDER BY d.clave
    $sql$, v_apodo, p_escuela_id, p_semestre_id, v_columnas);

    OPEN p_cursor FOR EXECUTE v_sql;
    RETURN p_cursor;
END;
$funcion$;


-- Verificacion: la funcion queda creada con sus tres parametros.
SELECT p.proname AS funcion,
       pg_get_function_arguments(p.oid) AS argumentos
  FROM pg_proc p
  JOIN pg_namespace n ON n.oid = p.pronamespace
 WHERE n.nspname = 'sih'
   AND p.proname = 'reporte_maestro_por_materia';
