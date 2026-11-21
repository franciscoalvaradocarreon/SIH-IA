-- ============================================================================
-- 19. EL CATALOGO DE LAB/TALLER PASA A DEPENDER DEL TURNO
-- ============================================================================
-- Que hace:
--   La migracion 18 dejo el catalogo de tipos de espacio por ESCUELA Y SEMESTRE.
--   Eso lo dejaba fuera del patron del resto de catalogos (aulas, grupos, maestros,
--   materias, especialidades), que son por SEMESTRE Y TURNO. Esta migracion lo
--   alinea: cada turno tiene su propia lista de laboratorios y talleres, y puede
--   tener los mismos o distintos que el otro turno del semestre.
--
-- Que pasa con los datos que ya existen:
--   * Se crea la rejilla semestre x turno x los cuatro tipos (pasa de 20 a 24 tipos).
--   * Las aulas que ya tenian tipo se repuntan al tipo equivalente DE SU PROPIO TURNO,
--     emparejando por clase y nombre. Ninguna queda sin tipo.
--   * Los tipos viejos que nadie usa se borran. En concreto, los 4 del semestre 15,
--     que no tiene turnos y por tanto no puede usar ninguno.
--
-- La clave foranea compuesta pasa de (semestre_id, tipo_espacio_id) a
-- (turno_id, tipo_espacio_id): mas estricta, porque un aula de VESPERTINO ya no
-- puede apuntar a un tipo de MATUTINO.
--
-- Idempotente: se puede ejecutar mas de una vez sin efecto.
-- ============================================================================
BEGIN;

-- ---------------------------------------------------------------------------
-- 1) La columna, al principio nullable para poder migrar
-- ---------------------------------------------------------------------------
ALTER TABLE sih.tipos_espacio ADD COLUMN IF NOT EXISTS turno_id bigint;

-- La unicidad vieja (semestre, clase, nombre) estorba: impide tener el mismo
-- nombre en dos turnos del mismo semestre. Se quita antes de sembrar.
ALTER TABLE sih.tipos_espacio DROP CONSTRAINT IF EXISTS tipos_espacio_unico;

-- ---------------------------------------------------------------------------
-- 2) La rejilla: los cuatro tipos en cada turno de cada semestre
-- ---------------------------------------------------------------------------
INSERT INTO sih.tipos_espacio (escuela_id, semestre_id, turno_id, clase, nombre)
SELECT s.escuela_id, s.semestre_id, t.turno_id, v.clase, v.nombre
  FROM sih.semestre s
  JOIN sih.turno t ON t.semestre_id = s.semestre_id
 CROSS JOIN (VALUES
        ('LABORATORIO', 'Laboratorio de Cómputo'),
        ('LABORATORIO', 'Laboratorio de Química'),
        ('TALLER',      'Taller de Producción'),
        ('TALLER',      'Taller de Mecánica')
     ) AS v(clase, nombre)
 WHERE NOT EXISTS (
        SELECT 1 FROM sih.tipos_espacio x
         WHERE x.turno_id = t.turno_id AND x.clase = v.clase AND x.nombre = v.nombre);

-- ---------------------------------------------------------------------------
-- 3) Las aulas que ya tenian tipo se repuntan al tipo de SU turno
-- ---------------------------------------------------------------------------
UPDATE sih.aulas a
   SET tipo_espacio_id = (
        SELECT nuevo.tipo_espacio_id
          FROM sih.tipos_espacio viejo
          JOIN sih.tipos_espacio nuevo
            ON nuevo.clase = viejo.clase
           AND nuevo.nombre = viejo.nombre
           AND nuevo.semestre_id = viejo.semestre_id
           AND nuevo.turno_id = a.turno_id
         WHERE viejo.tipo_espacio_id = a.tipo_espacio_id
       )
 WHERE a.tipo_espacio_id IS NOT NULL
   AND a.turno_id IS NOT NULL
   AND EXISTS (SELECT 1 FROM sih.tipos_espacio v
                WHERE v.tipo_espacio_id = a.tipo_espacio_id AND v.turno_id IS NULL);

-- ---------------------------------------------------------------------------
-- 4) Fuera los tipos viejos que nadie usa (los del semestre sin turnos, incluidos)
-- ---------------------------------------------------------------------------
DELETE FROM sih.tipos_espacio t
 WHERE t.turno_id IS NULL
   AND NOT EXISTS (SELECT 1 FROM sih.aulas a WHERE a.tipo_espacio_id = t.tipo_espacio_id);

-- ---------------------------------------------------------------------------
-- 5) Ahora si: obligatorio, unico por turno y clave foranea por turno
-- ---------------------------------------------------------------------------
ALTER TABLE sih.tipos_espacio ALTER COLUMN turno_id SET NOT NULL;

ALTER TABLE sih.tipos_espacio DROP CONSTRAINT IF EXISTS tipos_espacio_turno_unico;
ALTER TABLE sih.tipos_espacio ADD CONSTRAINT tipos_espacio_turno_unico
    UNIQUE (turno_id, clase, nombre);

ALTER TABLE sih.tipos_espacio DROP CONSTRAINT IF EXISTS tipos_espacio_turno_tipo_unico;
ALTER TABLE sih.tipos_espacio ADD CONSTRAINT tipos_espacio_turno_tipo_unico
    UNIQUE (turno_id, tipo_espacio_id);

ALTER TABLE sih.tipos_espacio DROP CONSTRAINT IF EXISTS tipos_espacio_turno_fk;
ALTER TABLE sih.tipos_espacio ADD CONSTRAINT tipos_espacio_turno_fk
    FOREIGN KEY (turno_id) REFERENCES sih.turno (turno_id);

-- la restriccion vieja por semestre se sustituye por la de turno
ALTER TABLE sih.aulas DROP CONSTRAINT IF EXISTS aulas_tipo_del_semestre_fk;
ALTER TABLE sih.aulas DROP CONSTRAINT IF EXISTS aulas_tipo_del_turno_fk;
ALTER TABLE sih.aulas ADD CONSTRAINT aulas_tipo_del_turno_fk
    FOREIGN KEY (turno_id, tipo_espacio_id)
    REFERENCES sih.tipos_espacio (turno_id, tipo_espacio_id);

COMMENT ON COLUMN sih.tipos_espacio.turno_id IS
    'Turno al que pertenece el tipo. El catalogo es por semestre y turno, como el resto.';

-- ---------------------------------------------------------------------------
-- 6) Comprobacion. Sin bloques $$ a proposito: se lanzan como consultas normales
--    y el resultado se lee a simple vista. Lo esperado, con los datos de desarrollo:
--      tipos: 24 (4 por cada turno de cada semestre)
--      aulas con tipo: 6
--      aulas cuyo tipo es de otro turno: 0
--      tipos sin turno: 0
-- ---------------------------------------------------------------------------
SELECT 'tipos: ' || count(*) AS tipos FROM sih.tipos_espacio;

SELECT 'aulas con tipo: ' || count(*) AS aulas_con_tipo
  FROM sih.aulas WHERE tipo_espacio_id IS NOT NULL;

SELECT 'aulas cuyo tipo es de otro turno (debe ser 0): ' || count(*) AS incoherentes
  FROM sih.aulas a
  JOIN sih.tipos_espacio t ON t.tipo_espacio_id = a.tipo_espacio_id
 WHERE a.turno_id IS DISTINCT FROM t.turno_id;

SELECT 'tipos sin turno (debe ser 0): ' || count(*) AS huerfanos
  FROM sih.tipos_espacio WHERE turno_id IS NULL;

COMMIT;
