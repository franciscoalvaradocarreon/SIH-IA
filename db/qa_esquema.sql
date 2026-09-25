-- ============================================================================
-- QA DEL ESQUEMA (DDL) - se puede correr antes y despues de instalar
-- ============================================================================
-- Uso:
--   psql -U postgres -h localhost -d SIH -f db/qa_esquema.sql
--   (o dentro de Docker:  psql -U postgres -d SIH -f /db/qa_esquema.sql)
--
-- No modifica NADA: solo revisa y devuelve un reporte con veredicto final.
-- Si el veredicto es ERROR, NO ponga en produccion esa base.
--
-- El veredicto cuenta TODOS los hallazgos: tablas faltantes, indices de solape
-- faltantes, columnas criticas nulables, choques reales de horario y problemas
-- de integridad de datos. Los chequeos que consultan datos usan SQL dinamico
-- dentro de un bloque protegido: si una tabla falta, el QA reporta
-- 'no revisable' en lugar de abortar con un error de sintaxis.
-- ============================================================================

\pset border 2
\pset pager off
SET client_min_messages = warning;

DROP TABLE IF EXISTS pg_temp.qa_problemas;
CREATE TEMP TABLE qa_problemas (seccion text, detalle text);

-- ── 1) TABLAS OBLIGATORIAS ──────────────────────────────────────────────────
INSERT INTO qa_problemas (seccion, detalle)
SELECT '1. TABLA FALTANTE', 'sih.' || e.n
  FROM (VALUES ('escuelas'),('semestre'),('turno'),('turno_horario'),('especialidad'),
               ('grupos'),('aulas'),('materias'),('maestros'),('asignacion'),
               ('disponibilidad_grupo'),('disponibilidad_maestro'),('horario'),
               ('menu'),('roles'),('rol_menu'),('usuarios'),('usuario_escuela_rol'),
               ('password_reset_token'),
               -- Corridas guardadas del generador IA (db/05_corridas_ia.sql).
               -- Estan aqui a proposito: si la migracion no se aplico en esta
               -- base, el QA tiene que decirlo en vez de dar el visto bueno.
               ('corrida_ia'),('corrida_ia_detalle')) e(n)
 WHERE NOT EXISTS (SELECT 1 FROM information_schema.tables t
                    WHERE t.table_schema = 'sih' AND t.table_name = e.n);

-- ── 1b) FUNCIONES OBLIGATORIAS ─────────────────────────────────────────────
-- El reporte de maestros por materia es una FUNCION (db/08_reporte_maestro_por_materia.sql).
-- Mismo criterio que con las tablas: si la migracion no se aplico en esta base, el QA
-- tiene que decirlo en vez de dar el visto bueno.
INSERT INTO qa_problemas (seccion, detalle)
SELECT '1. FUNCION FALTANTE', 'sih.' || e.n
  FROM (VALUES ('reporte_maestro_por_materia')) e(n)
 WHERE NOT EXISTS (SELECT 1 FROM pg_proc p
                     JOIN pg_namespace ns ON ns.oid = p.pronamespace
                    WHERE ns.nspname = 'sih' AND p.proname = e.n);

-- ── 2) RED DE SEGURIDAD CONTRA SOLAPES ─────────────────────────────────────
-- Se exige indice UNIQUE PARCIAL por (columna, turno_horario_id) sobre version = 1.
-- Equivale a la exclusion constraint, pero sin necesidad de btree_gist.
INSERT INTO qa_problemas (seccion, detalle)
SELECT '2. RED DE SOLAPE', 'falta el indice UNIQUE parcial ' || e.n
  FROM (VALUES ('no_solape_grupo_bloque'),('no_solape_maestro_bloque'),('no_solape_aula_bloque')) e(n)
 WHERE NOT EXISTS (SELECT 1 FROM pg_indexes i
                    WHERE i.schemaname = 'sih'
                      AND i.tablename  = 'horario'
                      AND i.indexname  = e.n
                      AND i.indexdef ILIKE '%UNIQUE%'
                      AND i.indexdef ILIKE '%WHERE (version = 1)%');

-- ── 3) COLUMNAS CRITICAS ───────────────────────────────────────────────────
WITH criticas(tabla, columna) AS (
  VALUES ('horario','aula_id'), ('horario','maestro_id'), ('horario','turno_horario_id'),
         ('horario','asignacion_id'), ('horario','grupo_id'),
         ('asignacion','horas'), ('asignacion','grupo_id'), ('asignacion','materia_id'),
         ('disponibilidad_grupo','turno_horario_id'), ('disponibilidad_maestro','turno_horario_id')
)
INSERT INTO qa_problemas (seccion, detalle)
SELECT '3. COLUMNA AUSENTE', k.tabla || '.' || k.columna
  FROM criticas k
 WHERE NOT EXISTS (SELECT 1 FROM information_schema.columns c
                    WHERE c.table_schema = 'sih' AND c.table_name = k.tabla
                      AND c.column_name = k.columna);

WITH criticas(tabla, columna) AS (
  VALUES ('horario','aula_id'), ('horario','maestro_id'), ('horario','turno_horario_id'),
         ('horario','asignacion_id'), ('horario','grupo_id'),
         ('asignacion','horas'), ('asignacion','grupo_id'), ('asignacion','materia_id'),
         ('disponibilidad_grupo','turno_horario_id'), ('disponibilidad_maestro','turno_horario_id')
)
INSERT INTO qa_problemas (seccion, detalle)
SELECT '3. COLUMNA NULABLE', k.tabla || '.' || k.columna || ' permite NULL'
  FROM criticas k
  JOIN information_schema.columns c
    ON c.table_schema = 'sih' AND c.table_name = k.tabla AND c.column_name = k.columna
 WHERE c.is_nullable <> 'NO';

-- Columnas que anaden las migraciones posteriores al volcado. Van en un bloque APARTE del de arriba
-- a proposito: estas SI pueden ser NULL (una corrida guardada antes de que existiera el dato no
-- tiene forma de saberlo), asi que no pueden entrar en el chequeo de nulabilidad.
WITH nuevas(tabla, columna) AS (
  VALUES ('corrida_ia','asignar_maestros'), ('corrida_ia','asignar_aulas')
)
INSERT INTO qa_problemas (seccion, detalle)
SELECT '3. COLUMNA AUSENTE', n.tabla || '.' || n.columna
  FROM nuevas n
 WHERE NOT EXISTS (SELECT 1 FROM information_schema.columns c
                    WHERE c.table_schema = 'sih' AND c.table_name = n.tabla
                      AND c.column_name = n.columna);

-- ── 4 y 5) DATOS: choques reales e integridad ──────────────────────────────
-- Cada consulta se ejecuta en un subbloque con EXCEPTION: si el esquema no es
-- el esperado, se marca 'no revisable' y el QA continua.
DO $$
DECLARE
  chk record;
  n   bigint;
BEGIN
  FOR chk IN
    SELECT * FROM (VALUES
      ('4a. Choques GRUPO',
       'SELECT count(*) FROM (SELECT grupo_id, turno_horario_id FROM sih.horario WHERE version = 1 GROUP BY 1,2 HAVING count(*) > 1) x',
       'bloque(s) con 2+ clases del mismo grupo'),
      ('4b. Choques MAESTRO',
       'SELECT count(*) FROM (SELECT maestro_id, turno_horario_id FROM sih.horario WHERE version = 1 GROUP BY 1,2 HAVING count(*) > 1) x',
       'bloque(s) con 2+ clases del mismo maestro'),
      ('4c. Choques AULA',
       'SELECT count(*) FROM (SELECT aula_id, turno_horario_id FROM sih.horario WHERE version = 1 GROUP BY 1,2 HAVING count(*) > 1) x',
       'bloque(s) con 2+ clases en la misma aula'),
      ('5a. Asignaciones con horas <= 0',
       'SELECT count(*) FROM sih.asignacion WHERE horas IS NULL OR horas <= 0',
       'asignacion(es) con horas invalidas'),
      ('5b. Grupos con asignaciones sin disponibilidad',
       'SELECT count(*) FROM sih.grupos g WHERE g.activo AND EXISTS (SELECT 1 FROM sih.asignacion a WHERE a.grupo_id = g.grupo_id) AND NOT EXISTS (SELECT 1 FROM sih.disponibilidad_grupo d WHERE d.grupo_id = g.grupo_id AND d.disponible)',
       'grupo(s) activo(s) con asignaciones y sin ningun bloque disponible: la IA no podra programarlos nunca'),
      ('5c. Asignaciones sin maestro o aula',
       'SELECT count(*) FROM sih.asignacion WHERE maestro_id IS NULL OR aula_id IS NULL',
       'asignacion(es) sin maestro o sin aula'),
      ('5d. Usuarios sin rol asignado',
       'SELECT count(*) FROM sih.usuarios u WHERE NOT EXISTS (SELECT 1 FROM sih.usuario_escuela_rol r WHERE r.usuario_id = u.usuario_id)',
       'usuario(s) sin rol (no podran usar el sistema)'),
      ('5e. Menus activos sin ruta',
       'SELECT count(*) FROM sih.menu WHERE activo AND (ruta IS NULL OR length(trim(ruta)) = 0) AND COALESCE(pariente_id, 0) <> 0',
       'menu(s) activo(s) sin ruta'),
      ('5f. Horario de asignaciones inexistentes',
       'SELECT count(*) FROM sih.horario h WHERE NOT EXISTS (SELECT 1 FROM sih.asignacion a WHERE a.asignacion_id = h.asignacion_id)',
       'fila(s) de horario huerfanas'),
      -- Migracion 07: la pantalla del generador automatico se retiro con el solver, y su
      -- entrada del menu vive en la base. Si sigue activa, es un enlace roto.
      ('5g. Menu hacia la pantalla retirada',
       'SELECT count(*) FROM sih.menu WHERE activo AND ruta = ''/horarios/generador/automatico''',
       'menu(s) activo(s) que apuntan al generador automatico, que ya no existe')
    ) v(titulo, sql, texto)
  LOOP
    n := NULL;
    BEGIN
      EXECUTE chk.sql INTO n;
    EXCEPTION WHEN OTHERS THEN
      n := NULL;
    END;

    IF n IS NULL THEN
      INSERT INTO qa_problemas VALUES (chk.titulo, 'no revisable: el esquema no tiene la forma esperada');
    ELSIF n > 0 THEN
      INSERT INTO qa_problemas VALUES (chk.titulo, n || ' ' || chk.texto);
    END IF;
  END LOOP;
END $$;

-- ── 6) REPORTE INFORMATIVO ─────────────────────────────────────────────────
SELECT '1. TABLAS' AS revision,
       (SELECT count(*) FROM information_schema.tables WHERE table_schema = 'sih') || ' tablas en el esquema sih' AS resultado;

SELECT '2. RED DE SOLAPE' AS revision,
       (SELECT count(*) FROM pg_indexes
         WHERE schemaname = 'sih' AND tablename = 'horario'
           AND indexname IN ('no_solape_grupo_bloque','no_solape_maestro_bloque','no_solape_aula_bloque')
           AND indexdef ILIKE '%UNIQUE%') || ' de 3 indices UNIQUE parciales' AS resultado;

SELECT '3. FILAS EN HORARIO' AS revision,
       COALESCE((SELECT count(*)::text FROM sih.horario WHERE version = 1), 'no revisable') || ' clase(s) en la version vigente' AS resultado;

SELECT '4. REPORTE MAESTRO/MATERIA' AS revision,
       CASE WHEN EXISTS (SELECT 1 FROM pg_proc p
                           JOIN pg_namespace ns ON ns.oid = p.pronamespace
                          WHERE ns.nspname = 'sih' AND p.proname = 'reporte_maestro_por_materia')
            THEN 'funcion presente' ELSE 'NO existe la funcion' END AS resultado;

-- ── 7) PROBLEMAS DETECTADOS ────────────────────────────────────────────────
SELECT seccion, detalle FROM qa_problemas ORDER BY seccion, detalle;

-- ── 8) VEREDICTO ───────────────────────────────────────────────────────────
SELECT CASE WHEN count(*) = 0
            THEN 'OK  -> base lista para produccion'
            ELSE 'ERROR -> hay ' || count(*) || ' problema(s): NO publicar esta base'
       END AS veredicto
  FROM qa_problemas;
