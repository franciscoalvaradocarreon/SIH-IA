-- ============================================================================
-- 16. VERSION DEL TOKEN: invalidar sesiones de verdad
-- ============================================================================
-- Que hace:
--   Anade a 'usuarios' un contador que viaja dentro del JWT (reclamo "tv"). El
--   filtro de la aplicacion compara el contador que trae el token con el que hay
--   en la base: si no coinciden, el token ya no vale.
--
-- Por que:
--   Un JWT no tiene estado. Hasta ahora, cerrar sesion solo borraba el token del
--   navegador: si alguien lo hubiera copiado, le seguia sirviendo hasta 24 horas
--   despues, y cambiar la contrasena tampoco lo invalidaba.
--
--   Con este contador, cerrar sesion o cambiar la contrasena lo incrementa y
--   TODOS los tokens anteriores de ese usuario dejan de valer al instante, en
--   todos sus dispositivos.
--
-- Idempotente: se puede ejecutar mas de una vez sin efecto.
--
-- En el servidor:
--   docker compose exec -T db psql -U User_app -d SIH -c "\i /ruta/16_token_version.sql"
--   o, mas comodo, copiarlo al contenedor y ejecutarlo con -f.
--   En una instalacion nueva no hace falta: esquema.sql ya trae la columna.
-- ============================================================================
BEGIN;

ALTER TABLE sih.usuarios
    ADD COLUMN IF NOT EXISTS token_version integer NOT NULL DEFAULT 0;

COMMENT ON COLUMN sih.usuarios.token_version IS
    'Contador que viaja en el JWT (reclamo tv). Se incrementa al cerrar sesion y al cambiar la contrasena, para invalidar los tokens ya emitidos.';

COMMIT;
