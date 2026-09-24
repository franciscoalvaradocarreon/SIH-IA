-- ============================================================================
-- BANDERAS DE GENERACION EN LA CORRIDA GUARDADA
-- ============================================================================
-- Objetivo:
--   Que la tabla de corridas guardadas diga con QUE ajustes se genero cada una:
--   si el motor repartio los maestros desde el stock y si eligio el taller desde
--   el stock de la materia. Sin esto, comparar dos opciones obliga a fiarse del
--   nombre que les pusiste, y a posteriori no hay forma de saber que combinacion
--   produjo cada una (que es exactamente lo que paso al investigar por que una
--   corrida salio con 10 adyacencias).
--
-- Por que NULL y no un valor por defecto:
--   Las corridas guardadas ANTES de esta migracion no tienen forma de saberlo: el
--   dato no existia. Ponerlas a false seria mentir, porque afirmaria que se
--   generaron sin stock. Se quedan en NULL y la pantalla las muestra como "sin
--   registrar".
--
-- Idempotente: se puede ejecutar varias veces sin error.
--
-- ORDEN DE DESPLIEGUE: va antes que el codigo que lee las columnas.
-- ============================================================================

ALTER TABLE sih.corrida_ia ADD COLUMN IF NOT EXISTS asignar_maestros boolean;
ALTER TABLE sih.corrida_ia ADD COLUMN IF NOT EXISTS asignar_aulas boolean;

COMMENT ON COLUMN sih.corrida_ia.asignar_maestros IS
    'true si el motor reparte los maestros desde el stock. NULL = guardada antes de registrarlo.';
COMMENT ON COLUMN sih.corrida_ia.asignar_aulas IS
    'true si el motor elige el taller desde el stock. NULL = guardada antes de registrarlo.';


-- Verificacion inmediata: deben salir las dos columnas, boolean y admitiendo NULL.
SELECT column_name AS columna, data_type AS tipo, is_nullable AS admite_null
  FROM information_schema.columns
 WHERE table_schema = 'sih'
   AND table_name = 'corrida_ia'
   AND column_name IN ('asignar_maestros', 'asignar_aulas')
 ORDER BY column_name;
