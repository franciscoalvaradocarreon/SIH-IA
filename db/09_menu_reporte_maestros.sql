-- ============================================================================
-- MENU: REPORTE DE MAESTROS POR ESPECIALIDAD
-- ============================================================================
-- La pantalla /reportes/maestros-especialidad es codigo, pero la entrada del menu
-- NO: vive en la tabla sih.menu, igual que el resto de reportes. Sin esta fila la
-- pantalla existe y nadie llega a ella.
--
-- Dos decisiones que no son casualidad:
--
--   1. El padre NO se escribe a mano (nada de 'pariente_id = 28'). Se copia el de
--      la entrada 'Horario por Grupo', que ya cuelga de 'Reportes'. Si en otra base
--      el arbol del menu cambia, esto sigue apuntando al padre correcto.
--   2. Los roles TAMPOCO se escriben a mano. Se dan los mismos que ya tienen los
--      otros reportes (hoy ADMIN, COORDINADOR y VIEWER), leidos de rol_menu. Asi no
--      hay que tocar esto si manana cambian los roles.
--
-- menu_id NO se escribe: es una columna de identidad GENERATED ALWAYS y la asigna
-- Postgres sola. (Si se le pasa un valor, Postgres lo rechaza salvo que se use
-- OVERRIDING SYSTEM VALUE, que aqui no hace ninguna falta.) Con el NOT EXISTS de
-- abajo, ejecutarlo dos veces no duplica nada.
--
-- Idempotente: se puede ejecutar las veces que haga falta.
-- ============================================================================

INSERT INTO sih.menu (pariente_id, nombre, ruta, icono, menu_orden, activo, nivel)
SELECT (SELECT pariente_id FROM sih.menu WHERE ruta = '/reportes/horario-grupos'),
       'Maestros x Especialidad',
       '/reportes/maestros-especialidad',
       'MdPeople',
       2,
       true,
       2
 WHERE NOT EXISTS (SELECT 1 FROM sih.menu WHERE ruta = '/reportes/maestros-especialidad')
   AND EXISTS     (SELECT 1 FROM sih.menu WHERE ruta = '/reportes/horario-grupos');

-- Mismos roles que ya ven los otros reportes.
INSERT INTO sih.rol_menu (rol_id, menu_id)
SELECT DISTINCT rm.rol_id, nuevo.menu_id
  FROM sih.rol_menu rm
  JOIN sih.menu otro ON otro.menu_id = rm.menu_id AND otro.ruta = '/reportes/horario-grupos'
  CROSS JOIN (SELECT menu_id FROM sih.menu WHERE ruta = '/reportes/maestros-especialidad') AS nuevo
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
 WHERE m.ruta = '/reportes/maestros-especialidad';
