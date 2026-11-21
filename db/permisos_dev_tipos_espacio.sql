-- ============================================================================
-- PERMISOS DE DESARROLLO para el catalogo de laboratorios y talleres
-- ============================================================================
-- Ejecutar como el DUENO de las tablas (postgres), NO como User_app:
--   User_app no puede otorgarse permisos a si mismo, porque no es el dueno.
--
-- Cuando hace falta:
--   * siempre que la migracion 18 se aplique con un rol administrador distinto
--     del que usa la aplicacion (el caso de desarrollo con DBeaver como
--     postgres). La tabla nace siendo de 'postgres' y la aplicacion no la ve.
--   * si la aplicacion falla con "permiso denegado a la tabla tipos_espacio".
--
-- En PRODUCCION el rol de la aplicacion es "sih_app", no "User_app": si alla se
-- aplica la migracion como administrador, hay que ejecutar lo mismo cambiando el
-- nombre. Vista la migracion 18, lo mas probable es que haga falta; para eso
-- estan las dos lineas comentadas del final.
--
-- El nombre "User_app" va entre comillas dobles porque distingue mayusculas.
-- ============================================================================

\pset border 2

GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE sih.tipos_espacio TO "User_app";

-- Hibernate pide el siguiente valor de la secuencia al insertar, y eso exige USAGE.
GRANT USAGE, SELECT ON SEQUENCE sih.tipos_espacio_tipo_espacio_id_seq TO "User_app";

-- Comprobacion: las cuatro columnas tienen que salir en 't'.
SELECT has_table_privilege('"User_app"', 'sih.tipos_espacio', 'SELECT')  AS tabla_select,
       has_table_privilege('"User_app"', 'sih.tipos_espacio', 'INSERT')  AS tabla_insert,
       has_table_privilege('"User_app"', 'sih.tipos_espacio', 'UPDATE')  AS tabla_update,
       has_sequence_privilege('"User_app"', 'sih.tipos_espacio_tipo_espacio_id_seq', 'USAGE') AS secuencia_usage;

-- ============================================================================
-- PRODUCCION: mismas lineas con el rol de la aplicacion (descomentar y ejecutar
-- como administrador de la base del servidor)
-- ============================================================================
-- GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE sih.tipos_espacio TO "sih_app";
-- GRANT USAGE, SELECT ON SEQUENCE sih.tipos_espacio_tipo_espacio_id_seq TO "sih_app";
