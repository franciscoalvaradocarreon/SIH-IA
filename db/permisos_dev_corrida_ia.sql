-- ============================================================================
-- PERMISOS DE DESARROLLO para las tablas de corridas IA
-- ============================================================================
-- Ejecutar como el DUENO de las tablas (postgres), NO como User_app:
--   User_app no puede otorgarse permisos a si mismo, porque no es el dueno.
--
-- Solo hace falta en una base restaurada, donde las tablas quedaron de
-- 'postgres'. En produccion el usuario de la aplicacion creo las tablas, es su
-- dueno, y tiene todos los permisos implicitos: alli NO hay que ejecutar esto.
--
-- El nombre "User_app" va entre comillas dobles porque distingue mayusculas.
-- ============================================================================

\pset border 2

GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE sih.corrida_ia         TO "User_app";
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE sih.corrida_ia_detalle TO "User_app";

-- La aplicacion no inserta a mano en las secuencias: Hibernate pide el
-- siguiente valor, y eso exige USAGE.
GRANT USAGE, SELECT ON SEQUENCE sih.corrida_ia_corrida_ia_id_seq                  TO "User_app";
GRANT USAGE, SELECT ON SEQUENCE sih.corrida_ia_detalle_corrida_ia_detalle_id_seq  TO "User_app";

-- Comprobacion: las cuatro columnas tienen que salir en 't'.
SELECT has_table_privilege('"User_app"', 'sih.corrida_ia', 'SELECT')          AS corrida_select,
       has_table_privilege('"User_app"', 'sih.corrida_ia', 'INSERT')          AS corrida_insert,
       has_table_privilege('"User_app"', 'sih.corrida_ia_detalle', 'INSERT')  AS detalle_insert,
       has_sequence_privilege('"User_app"', 'sih.corrida_ia_corrida_ia_id_seq', 'USAGE') AS secuencia_usage;
