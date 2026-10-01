-- ============================================================================
-- MENU: REPORTE ASIGNACION
-- ============================================================================
-- La pantalla /reportes/asignacion es CODIGO (ReporteAsignacion.tsx y su ruta en App.tsx), pero
-- la entrada del menu NO: vive en la tabla sih.menu, como el resto de reportes. Sin esta fila la
-- pantalla existe y nadie llega a ella; con la ruta escrita y sin la fila, el menu queda vacio.
--
-- Mismos dos criterios que en la migracion 09, que son los que evitan tener que tocar esto:
--
--   1. El padre NO se escribe a mano (nada de 'pariente_id = 30'). Se copia el de la entrada
--      'Horario por Grupo', que ya cuelga de 'Reportes'. Si en otra base el arbol del menu
--      cambia, esto sigue apuntando al padre correcto.
--   2. Los roles TAMPOCO se escriben a mano: se dan los mismos que ya ven los otros reportes
--      (hoy ADMIN, COORDINADOR y VIEWER), leidos de rol_menu.
--
-- menu_id no se escribe: es una columna de identidad GENERATED ALWAYS y la asigna Postgres sola.
-- Con el NOT EXISTS de abajo, ejecutarla dos veces no duplica nada: es idempotente.
--
-- El icono es PiRowsPlusBottomDuotone a proposito: Menu.tsx resuelve nombres tanto de Md* como
-- de Pi*, y asi la entrada de produccion queda igual a la que ya existe en desarrollo.
-- ============================================================================

INSERT INTO sih.menu (pariente_id, nombre, ruta, icono, menu_orden, activo, nivel)
SELECT (SELECT pariente_id FROM sih.menu WHERE ruta = '/reportes/horario-grupos'),
       'Reporte Asignación',
       '/reportes/asignacion',
       'PiRowsPlusBottomDuotone',
       3,
       true,
       2
 WHERE NOT EXISTS (SELECT 1 FROM sih.menu WHERE ruta = '/reportes/asignacion')
   AND EXISTS     (SELECT 1 FROM sih.menu WHERE ruta = '/reportes/horario-grupos');

-- Mismos roles que ya ven los otros reportes.
INSERT INTO sih.rol_menu (rol_id, menu_id)
SELECT DISTINCT rm.rol_id, nuevo.menu_id
  FROM sih.rol_menu rm
  JOIN sih.menu otro ON otro.menu_id = rm.menu_id AND otro.ruta = '/reportes/horario-grupos'
  CROSS JOIN (SELECT menu_id FROM sih.menu WHERE ruta = '/reportes/asignacion') AS nuevo
 WHERE NOT EXISTS (SELECT 1 FROM sih.rol_menu x
                    WHERE x.rol_id = rm.rol_id AND x.menu_id = nuevo.menu_id);


-- Verificacion: la entrada con su padre y los roles que la ven.
SELECT m.menu_id, m.nombre, m.ruta, p.nombre AS padre, m.menu_orden, m.activo,
       (SELECT string_agg(r.nombre, ', ' ORDER BY r.nombre)
          FROM sih.rol_menu rm
          JOIN sih.roles r ON r.rol_id = rm.rol_id
         WHERE rm.menu_id = m.menu_id) AS roles
  FROM sih.menu m
  LEFT JOIN sih.menu p ON p.menu_id = m.pariente_id
 WHERE m.ruta = '/reportes/asignacion';
