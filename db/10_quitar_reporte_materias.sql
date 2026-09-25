-- ============================================================================
-- RETIRADA DEL REPORTE "MATERIAS X ESPECIALIDAD"
-- ============================================================================
-- La pantalla /reportes/materias-especialidad ya no existe: su contenido quedo
-- cubierto por el reporte nuevo /reportes/maestros-especialidad, que da lo mismo
-- (materia, horas y maestro) y ademas lo agrupa por grado de semestre y pone los
-- grupos en columnas.
--
-- Esa entrada del menu NO esta en el codigo: vive en la tabla sih.menu, y
-- db/03_semilla.sql la vuelve a insertar en una instalacion nueva. Si no se apaga
-- aqui, queda un enlace que no lleva a ninguna parte.
--
-- Se DESACTIVA (activo = false) en vez de borrarla, por lo mismo que en la 07:
--   - Deja de mostrarse, que es lo que se busca.
--   - Es reversible con otro UPDATE (activo = true).
--   - No hay que tocar rol_menu ni pelearse con las claves foraneas.
--
-- Idempotente: se puede ejecutar varias veces sin error.
-- ============================================================================

UPDATE sih.menu
   SET activo = false
 WHERE ruta = '/reportes/materias-especialidad';


-- Verificacion: como queda el bloque de reportes.
SELECT menu_id, nombre, ruta, menu_orden, activo
  FROM sih.menu
 WHERE ruta LIKE '/reportes%'
 ORDER BY menu_orden, menu_id;
