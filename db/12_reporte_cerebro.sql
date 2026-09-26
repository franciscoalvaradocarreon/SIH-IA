-- ============================================================================
-- MENU: EL REPORTE DE MAESTROS POR ESPECIALIDAD PASA A "REPORTE CEREBRO"
-- ============================================================================
-- Cambia el nombre visible y la ruta de la entrada del menu:
--   antes:  Maestros x Especialidad   /reportes/maestros-especialidad
--   ahora:  Reporte Cerebro           /reportes/cerebro
--
-- El nombre y la ruta van en la MISMA sentencia a proposito: el menu apunta a la
-- ruta, asi que cambiar solo el nombre dejaria la entrada llevando a la direccion
-- vieja, que ya no existe en el enrutador.
--
-- Para una instalacion nueva, db/09 sigue insertando la entrada con el nombre viejo y
-- esta migracion (12 > 09, orden alfabetico) la renombra al final. No se reescribe la
-- 09 porque ya esta aplicada en dev y en produccion.
--
-- Idempotente: la segunda ejecucion ya no encuentra la ruta vieja y no cambia nada.
-- ============================================================================

UPDATE sih.menu
   SET nombre = 'Reporte Cerebro',
       ruta   = '/reportes/cerebro'
 WHERE ruta = '/reportes/maestros-especialidad';


-- Verificacion: como queda el bloque de reportes.
SELECT menu_id, nombre, ruta, menu_orden, activo
  FROM sih.menu
 WHERE ruta LIKE '/reportes%'
 ORDER BY menu_orden, menu_id;
