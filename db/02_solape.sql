-- ============================================================================
-- RED DE SEGURIDAD CONTRA SOLAPES (indices UNIQUE parciales)
-- ============================================================================
-- Objetivo:
--   Que la base de datos rechace dos clases del mismo grupo, del mismo maestro
--   o del mismo aula en el mismo bloque. Es la ultima linea de defensa: la
--   aplicacion valida antes de escribir, pero si dos peticiones concurrentes
--   leen "libre" y ambas escriben, solo la base puede impedir el choque.
--
-- Por que NO se usa btree_gist ni EXCLUDE USING gist:
--   horario.turno_horario_id es un bigint que apunta a un bloque YA resuelto
--   del catalogo turno_horario. La comparacion es por IGUALDAD exacta, no por
--   rangos horarios que se solapen parcialmente. Con igualdad, un indice
--   UNIQUE parcial es funcionalmente identico a una exclusion constraint,
--   usa btree (mas barato que GiST) y no requiere ninguna extension.
--
-- Compatibilidad con el backend:
--   El mensaje de error de Postgres conserva el nombre del indice
--   ("llave duplicada viola restriccion de unicidad «no_solape_grupo_bloque»"),
--   y MensajeErrorUtil lo traduce buscando el patron no_solape_* en el texto.
--   Por eso NO hay que modificar ni una linea de Java.
--
-- Idempotente: se puede ejecutar varias veces sin error.
-- ============================================================================

-- Grupo: un grupo no puede tener dos clases en el mismo bloque.
CREATE UNIQUE INDEX IF NOT EXISTS no_solape_grupo_bloque
    ON sih.horario (grupo_id, turno_horario_id)
    WHERE version = 1;

-- Maestro: un maestro no puede estar en dos clases en el mismo bloque.
CREATE UNIQUE INDEX IF NOT EXISTS no_solape_maestro_bloque
    ON sih.horario (maestro_id, turno_horario_id)
    WHERE version = 1;

-- Aula: un aula no puede alojar dos clases en el mismo bloque.
CREATE UNIQUE INDEX IF NOT EXISTS no_solape_aula_bloque
    ON sih.horario (aula_id, turno_horario_id)
    WHERE version = 1;

-- Verificacion inmediata: deben aparecer los 3 indices marcados como UNIQUE.
SELECT indexname AS indice,
       CASE WHEN indexdef ILIKE '%UNIQUE%' THEN 'UNIQUE ok' ELSE 'NO ES UNIQUE' END AS estado,
       CASE WHEN indexdef ILIKE '%WHERE (version = 1)%' THEN 'parcial ok' ELSE 'sin filtro version' END AS filtro
  FROM pg_indexes
 WHERE schemaname = 'sih'
   AND tablename  = 'horario'
   AND indexname IN ('no_solape_grupo_bloque','no_solape_maestro_bloque','no_solape_aula_bloque')
 ORDER BY indexname;
