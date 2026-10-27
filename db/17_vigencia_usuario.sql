-- ============================================================================
-- 17. VIGENCIA DEL USUARIO
-- ============================================================================
-- Que hace:
--   Anade a 'usuarios' la fecha hasta la que la cuenta puede iniciar sesion.
--
-- Por que:
--   Hacia falta poder dar acceso por periodos (un maestro de un semestre, una
--   suplencia, una cuenta de prueba) sin depender de acordarse de desactivarla.
--
-- Reglas:
--   * NULL = SIN LIMITE. Los usuarios que ya existen quedan asi, de modo que
--     nadie se queda fuera al aplicar este cambio.
--   * Se compara por DIA: 'vigente_hasta = 2026-12-31' vale todo el 31, en la
--     zona horaria de la aplicacion (America/Mexico_City), no la del servidor,
--     que esta en GMT.
--   * Quien tiene el rol ADMIN no esta restringido: el administrador no puede
--     quedarse fuera del sistema.
--   * Solo se comprueba AL INICIAR SESION. Una sesion ya abierta sigue hasta
--     que caduque su token (24 h).
--
-- Idempotente: se puede ejecutar mas de una vez sin efecto.
-- ============================================================================
BEGIN;

ALTER TABLE sih.usuarios
    ADD COLUMN IF NOT EXISTS vigente_hasta date;

COMMENT ON COLUMN sih.usuarios.vigente_hasta IS
    'Ultimo dia en que la cuenta puede iniciar sesion. NULL = sin limite. No aplica a quien tiene rol ADMIN.';

COMMIT;
