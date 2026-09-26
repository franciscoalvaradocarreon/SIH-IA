-- ============================================================================
-- HORARIO: TURNO, ESPECIALIDAD Y GRADO EN LA PROPIA FILA
-- ============================================================================
-- Se añaden tres columnas derivadas a sih.horario:
--   turno_id        <- grupos.turno_id
--   especialidad_id <- grupos.especialidad_id
--   grado           <- grupos.grado
--
-- POR QUE SE DUPLICAN SI SE PUEDEN SACAR CON JOINS:
--   La tabla YA lo hace con grupo_id, semestre_id y escuela_id, que son igual de
--   derivables. El horario es una tabla de hechos que se lee mucho (rejillas,
--   reportes, consultas sueltas) y se escribe en dos sitios; se prefiere leer sin
--   joins. Comprobado antes de migrar: en las 729 filas, turno, especialidad y grado
--   coinciden con los del grupo (0 discrepancias).
--
-- NULABLES A PROPOSITO, aunque en grupos turno_id y grado sean NOT NULL:
--   El codigo que las rellena se despliega DESPUES de esta migracion. Si fueran
--   NOT NULL, la version que esta corriendo (que no las conoce) fallaria al insertar
--   la primera fila. Asi se puede migrar primero y desplegar despues sin ventana de
--   caida. OJO con especialidad_id: en grupos SI es nulable (hay grupos sin
--   especialidad), asi que aqui tambien.
--
-- ORDEN DE DESPLIEGUE: va ANTES del codigo que las escribe. Si se aplica despues, la
--   version nueva falla al insertar. Y si mientras tanto la version vieja escribe
--   filas, se quedan en NULL: volver a ejecutar este archivo despues del despliegue
--   las rellena, porque el UPDATE solo toca las que no coinciden.
--
-- Idempotente: se puede ejecutar las veces que haga falta.
-- ============================================================================

ALTER TABLE sih.horario ADD COLUMN IF NOT EXISTS turno_id        bigint;
-- bigint para que empate con especialidad.especialidad_id (la PK a la que apunta),
-- igual que grupo_id y turno_id, que tambien son bigint aunque en su tabla sean int.
ALTER TABLE sih.horario ADD COLUMN IF NOT EXISTS especialidad_id bigint;
ALTER TABLE sih.horario ADD COLUMN IF NOT EXISTS grado           integer;

COMMENT ON COLUMN sih.horario.turno_id        IS 'Copia de grupos.turno_id. La escribe la aplicacion; el QA comprueba que coincida (db/11).';
COMMENT ON COLUMN sih.horario.especialidad_id IS 'Copia de grupos.especialidad_id. Nulable: hay grupos sin especialidad.';
COMMENT ON COLUMN sih.horario.grado           IS 'Copia de grupos.grado (grado de semestre).';

-- Relleno de las filas que ya existen. Solo toca las que no coinciden, asi que
-- ejecutarlo otra vez no hace trabajo de mas.
UPDATE sih.horario h
   SET turno_id        = g.turno_id,
       especialidad_id = g.especialidad_id,
       grado           = g.grado
  FROM sih.grupos g
 WHERE g.grupo_id = h.grupo_id
   AND (h.turno_id        IS DISTINCT FROM g.turno_id
     OR h.especialidad_id IS DISTINCT FROM g.especialidad_id
     OR h.grado           IS DISTINCT FROM g.grado);

-- Claves foraneas: el valor tiene que existir, pero borrar un turno o una especialidad
-- NO debe llevarse por delante clases del horario. De ahi SET NULL, en vez del CASCADE
-- que usan grupo/aula: alli borrar el padre invalida la fila entera, aqui solo invalida
-- la copia.
ALTER TABLE sih.horario DROP CONSTRAINT IF EXISTS horario_turno_id_fkey;
ALTER TABLE sih.horario ADD  CONSTRAINT horario_turno_id_fkey
      FOREIGN KEY (turno_id) REFERENCES sih.turno(turno_id) ON DELETE SET NULL;

ALTER TABLE sih.horario DROP CONSTRAINT IF EXISTS horario_especialidad_id_fkey;
ALTER TABLE sih.horario ADD  CONSTRAINT horario_especialidad_id_fkey
      FOREIGN KEY (especialidad_id) REFERENCES sih.especialidad(especialidad_id) ON DELETE SET NULL;


-- Verificacion 1: las columnas quedaron y sin huecos (0 esperado en las tres).
SELECT count(*) AS filas,
       count(*) FILTER (WHERE turno_id        IS NULL) AS sin_turno,
       count(*) FILTER (WHERE grado           IS NULL) AS sin_grado,
       count(*) FILTER (WHERE especialidad_id IS NULL) AS sin_especialidad
  FROM sih.horario;

-- Verificacion 2: ninguna fila discrepa de su grupo (0 esperado).
SELECT count(*) AS discrepancias_con_el_grupo
  FROM sih.horario h
  JOIN sih.grupos g ON g.grupo_id = h.grupo_id
 WHERE h.turno_id        IS DISTINCT FROM g.turno_id
    OR h.especialidad_id IS DISTINCT FROM g.especialidad_id
    OR h.grado           IS DISTINCT FROM g.grado;
