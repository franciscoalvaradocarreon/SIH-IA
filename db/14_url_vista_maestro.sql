-- ============================================================================
-- LA VISTA POR MAESTRO CAMBIA DE URL
-- ============================================================================
--   antes:  /horarios/maestro
--   ahora:  /vistas/maestro
--
-- Igual que la 13 con la vista por grupo: la ruta vive en el enrutador del front
-- (codigo, va en el despliegue) y en la entrada del menu (dato, en sih.menu). Esta
-- migracion arregla la segunda; si se cambia solo el codigo, la entrada del menu sigue
-- llevando a la direccion vieja y da "Pagina no encontrada".
--
-- Para una instalacion nueva, db/03_semilla.sql sigue insertando la ruta vieja en la
-- entrada 51 ("Vista Mestros") y esta migracion (14 > 03) la corrige.
--
-- ORDEN DE DESPLIEGUE: DESPUES del despliegue, igual que la 12 y la 13.
--
-- Idempotente: la segunda ejecucion ya no encuentra la ruta vieja y no cambia nada.
-- ============================================================================

UPDATE sih.menu
   SET ruta = '/vistas/maestro'
 WHERE ruta = '/horarios/maestro';


-- Verificacion: como quedan las rutas de vistas, horarios y reportes.
SELECT menu_id, nombre, ruta, menu_orden, activo
  FROM sih.menu
 WHERE ruta LIKE '/horarios%' OR ruta LIKE '/vistas%' OR ruta LIKE '/reportes%'
 ORDER BY ruta;
