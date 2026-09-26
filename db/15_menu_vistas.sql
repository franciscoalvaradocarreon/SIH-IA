-- ============================================================================
-- AJUSTES DE LAS ENTRADAS DE MENU DE LAS VISTAS
-- ============================================================================
-- Dos arreglos sobre el mismo bloque de menu:
--
--  1. La vista por aula cambia de URL:
--       antes:  /horarios/aula
--       ahora:  /vistas/aula
--     Igual que la 13 (grupo) y la 14 (maestro): la ruta vive en el enrutador del front
--     (codigo) y en sih.menu (dato). Aqui se arregla la segunda.
--
--  2. La entrada de la vista por maestro se llama "Vista Mestros" y le falta una letra:
--       "Vista Mestros" -> "Vista Maestros"
--     El nombre visible sale de la base, no del codigo: hasta que no se corrija aqui, el
--     menu sigue mostrando el typo.
--
-- Para una instalacion nueva, db/03_semilla.sql sigue insertando los dos valores viejos
-- (ruta '/horarios/aula' y nombre 'Vista Mestros') y esta migracion (15 > 03) los corrige.
--
-- ORDEN DE DESPLIEGUE: DESPUES del despliegue, igual que la 12, la 13 y la 14.
--
-- Idempotente: la segunda ejecucion ya no encuentra ninguno de los dos valores viejos.
-- ============================================================================

-- 1) Ruta de la vista por aula.
UPDATE sih.menu
   SET ruta = '/vistas/aula'
 WHERE ruta = '/horarios/aula';

-- 2) Nombre de la vista por maestro (le faltaba la "a").
UPDATE sih.menu
   SET nombre = 'Vista Maestros'
 WHERE ruta = '/vistas/maestro'
   AND nombre = 'Vista Mestros';


-- Verificacion: como quedan las entradas de las vistas.
SELECT menu_id, nombre, ruta, activo
  FROM sih.menu
 WHERE ruta LIKE '/vistas%'
 ORDER BY ruta;
