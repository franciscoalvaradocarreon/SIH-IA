-- ============================================================================
-- RETIRADA DEL GENERADOR AUTOMATICO (SOLVER)
-- ============================================================================
-- La pantalla /horarios/generador/automatico ya no existe: el solver de Timefold se
-- retiro del programa y el horario se arma con el motor propio del modulo IA o a
-- mano con el tablero de pines.
--
-- Esa entrada del menu NO esta en el codigo, vive en la tabla sih.menu. Si no se
-- apaga aqui, queda un enlace que no lleva a ninguna parte.
--
-- Se DESACTIVA (activo = false) en vez de borrarla, a proposito:
--   - Deja de mostrarse, que es lo que se busca.
--   - Es reversible con otro UPDATE (activo = true).
--   - No hay que tocar rol_menu ni pelearse con las claves foraneas.
--
-- Idempotente: se puede ejecutar varias veces sin error.
--
-- ORDEN DE DESPLIEGUE: va con el codigo que retira la pantalla. Si se aplica antes,
-- el menu simplemente deja de ofrecer una pantalla que todavia existe (molesto pero
-- inocuo); al reves, queda un enlace roto.
-- ============================================================================

UPDATE sih.menu
   SET activo = false
 WHERE ruta = '/horarios/generador/automatico';


-- Verificacion inmediata: las tres entradas del bloque de generacion, con la
-- automatica ya apagada y las otras dos (IA y manual) todavia encendidas.
SELECT menu_id, nombre, ruta, activo
  FROM sih.menu
 WHERE ruta IN ('/horarios/generador/automatico',
                '/horarios/generador/ia',
                '/horarios/generador/manual')
 ORDER BY menu_id;
