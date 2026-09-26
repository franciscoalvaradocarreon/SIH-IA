-- ============================================================================
-- LA VISTA POR GRUPO CAMBIA DE URL
-- ============================================================================
--   antes:  /horarios/view
--   ahora:  /vistas/grupo
--
-- La ruta vive en DOS sitios y hay que tocar los dos:
--   1. El enrutador del front  -> eso es codigo (App.tsx), va en el despliegue.
--   2. La entrada del menu     -> eso es dato, vive en sih.menu, y es esta migracion.
--
-- Si se cambia solo el codigo, la entrada del menu sigue llevando a la direccion vieja
-- y el usuario ve "Pagina no encontrada".
--
-- Para una instalacion nueva, db/03_semilla.sql sigue insertando la ruta vieja en la
-- entrada 36 ("Vista Grupos") y esta migracion (13 > 03, orden alfabetico) la corrige.
-- No se reescribe la semilla porque ya esta aplicada en dev y en produccion.
--
-- ORDEN DE DESPLIEGUE: va DESPUES del despliegue, igual que la 12. Si se aplica antes,
-- el menu apunta a una ruta que la version que esta corriendo todavia no sirve.
--
-- Idempotente: la segunda ejecucion ya no encuentra la ruta vieja y no cambia nada.
-- ============================================================================

UPDATE sih.menu
   SET ruta = '/vistas/grupo'
 WHERE ruta = '/horarios/view';


-- Verificacion: como quedan las rutas de horarios, vistas y reportes.
SELECT menu_id, nombre, ruta, menu_orden, activo
  FROM sih.menu
 WHERE ruta LIKE '/horarios%' OR ruta LIKE '/vistas%' OR ruta LIKE '/reportes%'
 ORDER BY ruta;
