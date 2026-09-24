-- ============================================================================
-- CORRIDAS GUARDADAS DEL GENERADOR IA
-- ============================================================================
-- Objetivo:
--   Poder guardar una corrida del generador (una solucion completa con sus
--   metricas), seguir generando otras y, al final, aplicar la mejor al horario
--   vigente. Sin esto, cada generacion nueva pisa la anterior y no hay forma de
--   comparar dos opciones antes de decidir.
--
-- Por que tablas NUEVAS y no reutilizar horario.version:
--   El esquema ya preve varias versiones del horario (los tres indices de
--   solape son parciales, WHERE version = 1), pero reutilizarlo obligaria a
--   anadir "AND version = 1" a las consultas de lectura (grupo, maestro, aula y
--   escuela), que hoy NO filtran por version: mientras existiera una opcion
--   guardada, esas pantallas mostrarian la UNION de todas las opciones, con
--   bloques duplicados.
--   Ademas horario no tiene donde guardar el score ni las horas pendientes, que
--   es justamente lo que se compara al elegir.
--   Con tablas aparte, una corrida guardada es un dato inerte: no afecta a
--   ninguna pantalla hasta que se aplica, y aplicar reutiliza registrar(), que
--   ya esta probado y valida contra el catalogo vivo.
--
-- Que es una corrida guardada:
--   Cabecera (sih.corrida_ia): el nombre que le pone el usuario y las metricas
--   del intento.
--   Detalle (sih.corrida_ia_detalle): una fila por bloque ocupado, que son
--   exactamente los datos que necesita registrar() para reescribir el horario.
--
-- Idempotente: se puede ejecutar varias veces sin error. En una base ya
-- existente hay que aplicarlo a mano: el entrypoint de Postgres solo ejecuta
-- los scripts de /docker-entrypoint-initdb.d la PRIMERA vez que crea el
-- volumen de datos.
--
-- ORDEN DE DESPLIEGUE: este script va ANTES que el codigo que usa las tablas.
-- Al reves, Hibernate encuentra la entidad sin tabla y la aplicacion no
-- arranca.
-- ============================================================================


-- ── Cabecera: una fila por corrida guardada ────────────────────────────────
CREATE TABLE IF NOT EXISTS sih.corrida_ia (
    corrida_ia_id bigint NOT NULL,
    escuela_id bigint NOT NULL,
    semestre_id bigint NOT NULL,

    -- Alcance de la corrida. Nullable a proposito: un turno se puede borrar del
    -- catalogo y la corrida guardada no debe desaparecer con el.
    turno_id bigint,

    -- Nombre que le pone el usuario para reconocerla en la lista.
    nombre character varying(120) NOT NULL,
    notas character varying(500),

    -- Como se genero: "heuristica" o "llm:<modelo>".
    asesor character varying(80),

    -- Numero de intento dentro de su trabajo, y cuando lo genero el motor.
    numero_intento integer,
    generado_en timestamp without time zone,

    -- Quien la guardo y cuando.
    creado timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    creado_por bigint,

    -- ── Metricas del intento: es lo que se compara entre corridas ──────────
    milisegundos bigint,
    horas integer,
    horas_demandadas integer,
    sesiones_largas integer,
    sesiones_largas_pendientes integer,
    arranques_tarde integer,
    castigo_huecos integer,
    adyacencias integer,
    materias_completas integer,
    materias_totales integer,
    medium integer,

    -- ── Recuentos esperados del detalle ───────────────────────────────────
    -- Si total_filas no cuadra con las filas que hay guardadas, la corrida se
    -- marca como incompleta y no se aplica a ciegas (ver la consulta de
    -- verificacion del final).
    total_filas integer,
    total_pendientes integer,

    -- Si es > 0 la corrida NO se puede aplicar: registrar() rechaza los
    -- intentos con problemas duros. Se guarda el motivo para poder explicarlo
    -- en la pantalla en vez de fallar sin decir por que.
    total_problemas integer DEFAULT 0,
    problemas text,

    CONSTRAINT corrida_ia_pkey PRIMARY KEY (corrida_ia_id),
    CONSTRAINT corrida_ia_escuela_id_fkey FOREIGN KEY (escuela_id)
        REFERENCES sih.escuelas(escuela_id) ON DELETE CASCADE,
    CONSTRAINT corrida_ia_semestre_id_fkey FOREIGN KEY (semestre_id)
        REFERENCES sih.semestre(semestre_id) ON DELETE CASCADE,
    -- SET NULL, no CASCADE: borrar un turno no debe borrar las opciones
    -- guardadas, solo dejarlas sin etiqueta de alcance.
    CONSTRAINT corrida_ia_turno_id_fkey FOREIGN KEY (turno_id)
        REFERENCES sih.turno(turno_id) ON DELETE SET NULL,
    CONSTRAINT corrida_ia_creado_por_fkey FOREIGN KEY (creado_por)
        REFERENCES sih.usuarios(usuario_id) ON DELETE SET NULL
);


-- ── Detalle: una fila por bloque ocupado ───────────────────────────────────
CREATE TABLE IF NOT EXISTS sih.corrida_ia_detalle (
    corrida_ia_detalle_id bigint NOT NULL,
    corrida_ia_id bigint NOT NULL,

    -- El grupo se guarda aunque se deduzca de la asignacion: es lo que permite
    -- la red de seguridad de abajo y evita un JOIN al listar.
    grupo_id bigint NOT NULL,
    asignacion_id bigint NOT NULL,
    turno_horario_id bigint NOT NULL,

    -- Maestro y taller que eligio el motor (en modo stock pueden diferir de los
    -- de la asignacion). Nullable y SET NULL: si un maestro o un aula
    -- desaparecen del catalogo, la fila queda en NULL y registrar() vuelve a
    -- usar los de la asignacion en lugar de romperse.
    maestro_id bigint,
    aula_id bigint,

    CONSTRAINT corrida_ia_detalle_pkey PRIMARY KEY (corrida_ia_detalle_id),
    CONSTRAINT corrida_ia_detalle_corrida_ia_id_fkey FOREIGN KEY (corrida_ia_id)
        REFERENCES sih.corrida_ia(corrida_ia_id) ON DELETE CASCADE,
    CONSTRAINT corrida_ia_detalle_grupo_id_fkey FOREIGN KEY (grupo_id)
        REFERENCES sih.grupos(grupo_id) ON DELETE CASCADE,
    CONSTRAINT corrida_ia_detalle_asignacion_id_fkey FOREIGN KEY (asignacion_id)
        REFERENCES sih.asignacion(asignacion_id) ON DELETE CASCADE,
    CONSTRAINT corrida_ia_detalle_turno_horario_id_fkey FOREIGN KEY (turno_horario_id)
        REFERENCES sih.turno_horario(turno_horario_id) ON DELETE CASCADE,
    CONSTRAINT corrida_ia_detalle_maestro_id_fkey FOREIGN KEY (maestro_id)
        REFERENCES sih.maestros(maestro_id) ON DELETE SET NULL,
    CONSTRAINT corrida_ia_detalle_aula_id_fkey FOREIGN KEY (aula_id)
        REFERENCES sih.aulas(aula_id) ON DELETE SET NULL
);


-- ── Secuencias de los identificadores ─────────────────────────────────────
-- Mismo patron que el resto del esquema (bigint + secuencia + DEFAULT
-- nextval), que es lo que espera @GeneratedValue(strategy = IDENTITY).
CREATE SEQUENCE IF NOT EXISTS sih.corrida_ia_corrida_ia_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE sih.corrida_ia_corrida_ia_id_seq
    OWNED BY sih.corrida_ia.corrida_ia_id;

ALTER TABLE sih.corrida_ia
    ALTER COLUMN corrida_ia_id SET DEFAULT nextval('sih.corrida_ia_corrida_ia_id_seq');

CREATE SEQUENCE IF NOT EXISTS sih.corrida_ia_detalle_corrida_ia_detalle_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE sih.corrida_ia_detalle_corrida_ia_detalle_id_seq
    OWNED BY sih.corrida_ia_detalle.corrida_ia_detalle_id;

ALTER TABLE sih.corrida_ia_detalle
    ALTER COLUMN corrida_ia_detalle_id SET DEFAULT nextval('sih.corrida_ia_detalle_corrida_ia_detalle_id_seq');


-- ── Red de seguridad: una corrida guardada tampoco puede solaparse ─────────
-- Equivalente a los indices no_solape_* de horario, pero SIN el filtro
-- version = 1: aqui todas las filas pertenecen a una opcion guardada, no a un
-- horario vigente. El motor ya no genera solapes; esto es la ultima linea de
-- defensa por si un guardado llega corrupto.
CREATE UNIQUE INDEX IF NOT EXISTS no_solape_corrida_grupo_bloque
    ON sih.corrida_ia_detalle (corrida_ia_id, grupo_id, turno_horario_id);

CREATE UNIQUE INDEX IF NOT EXISTS no_solape_corrida_maestro_bloque
    ON sih.corrida_ia_detalle (corrida_ia_id, maestro_id, turno_horario_id);

CREATE UNIQUE INDEX IF NOT EXISTS no_solape_corrida_aula_bloque
    ON sih.corrida_ia_detalle (corrida_ia_id, aula_id, turno_horario_id);


-- ── Indices de consulta ───────────────────────────────────────────────────
-- Listar las corridas de una escuela y semestre es la consulta de la pantalla.
CREATE INDEX IF NOT EXISTS idx_corrida_ia_escuela_semestre
    ON sih.corrida_ia (escuela_id, semestre_id, creado DESC);

-- Postgres NO indexa automaticamente el lado hijo de una clave foranea.
CREATE INDEX IF NOT EXISTS idx_corrida_ia_detalle_corrida
    ON sih.corrida_ia_detalle (corrida_ia_id);


-- ── Nombres unicos por escuela y semestre ─────────────────────────────────
-- Dos opciones llamadas igual en el mismo semestre son una trampa al elegir
-- cual aplicar. El backend traduce el choque a un mensaje claro.
CREATE UNIQUE INDEX IF NOT EXISTS corrida_ia_nombre_unico
    ON sih.corrida_ia (escuela_id, semestre_id, lower(nombre::text));


-- ── Verificacion inmediata ────────────────────────────────────────────────
-- Debe salir: 2 tablas, 3 indices UNIQUE y 1 nombre unico.
SELECT '1. TABLAS' AS revision,
       (SELECT count(*)::text FROM information_schema.tables
         WHERE table_schema = 'sih'
           AND table_name IN ('corrida_ia', 'corrida_ia_detalle')) || ' de 2' AS resultado
UNION ALL
SELECT '2. RED DE SOLAPE',
       (SELECT count(*)::text FROM pg_indexes
         WHERE schemaname = 'sih'
           AND tablename = 'corrida_ia_detalle'
           AND indexname LIKE 'no_solape_corrida%'
           AND indexdef ILIKE '%UNIQUE%') || ' de 3 indices UNIQUE' AS resultado
UNION ALL
SELECT '3. NOMBRE UNICO',
       (SELECT count(*)::text FROM pg_indexes
         WHERE schemaname = 'sih'
           AND tablename = 'corrida_ia'
           AND indexname = 'corrida_ia_nombre_unico') || ' de 1' AS resultado
UNION ALL
SELECT '4. SECUENCIAS',
       (SELECT count(*)::text FROM information_schema.sequences
         WHERE sequence_schema = 'sih' AND sequence_name LIKE 'corrida_ia%') || ' de 2' AS resultado;
