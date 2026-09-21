-- ============================================================================
-- SEMILLA DE CATALOGOS (bootstrap de una base nueva)
-- ============================================================================
-- Objetivo:
--   Dejar una base recien creada en estado USABLE. Sin estos catalogos la
--   aplicacion arranca pero no hay menu que pintar ni roles que asignar.
--
-- Contenido:
--   * sih.roles       (5)  - ADMIN, DIRECTOR, COORDINADOR, MAESTRO, VIEWER
--   * sih.menu        (30) - arbol de navegacion (6 padres + 24 hijos)
--   * sih.rol_menu    (57) - que ve cada rol
--   * sih.escuelas    (1)  - escuela base de ejemplo, solo si no hay ninguna
--
-- NO CONTIENE USUARIOS, a proposito:
--   Este repositorio es PUBLICO y los hashes bcrypt de las contrasenas no
--   deben versionarse. El administrador se crea en el despliegue con
--   db\crear_admin.ps1, que genera el hash localmente y no lo guarda.
--
-- Datos operativos (semestres, turnos, grupos, materias, maestros, aulas,
-- asignaciones) NO van aqui: son datos del cliente, no del sistema.
--
-- Idempotente: ON CONFLICT DO NOTHING + setval. Se puede reejecutar.
-- ============================================================================

BEGIN;

-- ── ROLES ──────────────────────────────────────────────────────────────────
INSERT INTO sih.roles (rol_id, nombre, descripcion) OVERRIDING SYSTEM VALUE VALUES (1, 'ADMIN', 'Administrador del sistema') ON CONFLICT DO NOTHING;
INSERT INTO sih.roles (rol_id, nombre, descripcion) OVERRIDING SYSTEM VALUE VALUES (2, 'DIRECTOR', 'Director de la escuela') ON CONFLICT DO NOTHING;
INSERT INTO sih.roles (rol_id, nombre, descripcion) OVERRIDING SYSTEM VALUE VALUES (3, 'COORDINADOR', 'Coordinador académico') ON CONFLICT DO NOTHING;
INSERT INTO sih.roles (rol_id, nombre, descripcion) OVERRIDING SYSTEM VALUE VALUES (4, 'MAESTRO', 'Maestro con acceso a su horario') ON CONFLICT DO NOTHING;
INSERT INTO sih.roles (rol_id, nombre, descripcion) OVERRIDING SYSTEM VALUE VALUES (5, 'VIEWER', 'Solo lectura') ON CONFLICT DO NOTHING;

-- ── MENU (el orden no importa: pariente_id no tiene FK) ────────────────────
INSERT INTO sih.menu (menu_id, pariente_id, nombre, ruta, icono, menu_orden, activo, creado, nivel) OVERRIDING SYSTEM VALUE VALUES (29, 25, 'Usuarios', '/administracion/usuarios', 'MdPerson', 4, true, '15:30:34.729', 2) ON CONFLICT DO NOTHING;
INSERT INTO sih.menu (menu_id, pariente_id, nombre, ruta, icono, menu_orden, activo, creado, nivel) OVERRIDING SYSTEM VALUE VALUES (30, 25, 'Escuelas', '/administracion/escuelas', 'PiBuildingApartment', 3, true, '19:20:33.422', 2) ON CONFLICT DO NOTHING;
INSERT INTO sih.menu (menu_id, pariente_id, nombre, ruta, icono, menu_orden, activo, creado, nivel) OVERRIDING SYSTEM VALUE VALUES (25, 0, 'Administracion', NULL, 'MdAdminPanelSettings', 1, true, '10:44:41.351556', 1) ON CONFLICT DO NOTHING;
INSERT INTO sih.menu (menu_id, pariente_id, nombre, ruta, icono, menu_orden, activo, creado, nivel) OVERRIDING SYSTEM VALUE VALUES (56, 0, 'Vistas', '', 'MdVisibility', 4, true, '11:28:27.345662', 1) ON CONFLICT DO NOTHING;
INSERT INTO sih.menu (menu_id, pariente_id, nombre, ruta, icono, menu_orden, activo, creado, nivel) OVERRIDING SYSTEM VALUE VALUES (28, 0, 'Reportes', NULL, 'MdPrint', 5, true, '13:52:12.535', 1) ON CONFLICT DO NOTHING;
INSERT INTO sih.menu (menu_id, pariente_id, nombre, ruta, icono, menu_orden, activo, creado, nivel) OVERRIDING SYSTEM VALUE VALUES (52, 26, 'Semestres', '/catalogo/semestres', 'PiFolder', 0, true, '23:31:29.096', 2) ON CONFLICT DO NOTHING;
INSERT INTO sih.menu (menu_id, pariente_id, nombre, ruta, icono, menu_orden, activo, creado, nivel) OVERRIDING SYSTEM VALUE VALUES (54, 56, 'Vista Aulas', '/horarios/aula', 'PiHouse', 2, true, '18:31:30.409', 2) ON CONFLICT DO NOTHING;
INSERT INTO sih.menu (menu_id, pariente_id, nombre, ruta, icono, menu_orden, activo, creado, nivel) OVERRIDING SYSTEM VALUE VALUES (33, 26, 'Materias', '/catalogo/materias', 'MdMenuBook', 5, true, '13:52:30.534', 2) ON CONFLICT DO NOTHING;
INSERT INTO sih.menu (menu_id, pariente_id, nombre, ruta, icono, menu_orden, activo, creado, nivel) OVERRIDING SYSTEM VALUE VALUES (43, 26, 'Aulas', '/catalogo/aulas', 'MdOutlineDoorFront', 7, true, '18:10:49.116', 2) ON CONFLICT DO NOTHING;
INSERT INTO sih.menu (menu_id, pariente_id, nombre, ruta, icono, menu_orden, activo, creado, nivel) OVERRIDING SYSTEM VALUE VALUES (24, 0, 'Dashboard', '/dashboard', 'MdDashboard', 0, true, '13:52:12.535', 1) ON CONFLICT DO NOTHING;
INSERT INTO sih.menu (menu_id, pariente_id, nombre, ruta, icono, menu_orden, activo, creado, nivel) OVERRIDING SYSTEM VALUE VALUES (26, 0, 'Catalogos', NULL, 'MdListAlt', 2, true, '13:52:12.535', 1) ON CONFLICT DO NOTHING;
INSERT INTO sih.menu (menu_id, pariente_id, nombre, ruta, icono, menu_orden, activo, creado, nivel) OVERRIDING SYSTEM VALUE VALUES (27, 0, 'Horarios', NULL, 'MdSchedule', 3, true, '13:52:12.535', 1) ON CONFLICT DO NOTHING;
INSERT INTO sih.menu (menu_id, pariente_id, nombre, ruta, icono, menu_orden, activo, creado, nivel) OVERRIDING SYSTEM VALUE VALUES (31, 26, 'Especialidades', '/catalogo/especialidades', 'MdListAlt', 3, true, '17:40:38.764', 2) ON CONFLICT DO NOTHING;
INSERT INTO sih.menu (menu_id, pariente_id, nombre, ruta, icono, menu_orden, activo, creado, nivel) OVERRIDING SYSTEM VALUE VALUES (32, 26, 'Maestros', '/catalogo/maestros', 'MdPerson', 4, true, '13:52:30.534', 2) ON CONFLICT DO NOTHING;
INSERT INTO sih.menu (menu_id, pariente_id, nombre, ruta, icono, menu_orden, activo, creado, nivel) OVERRIDING SYSTEM VALUE VALUES (34, 26, 'Grupos', '/catalogo/grupos', 'MdPeople', 6, true, '13:52:30.534', 2) ON CONFLICT DO NOTHING;
INSERT INTO sih.menu (menu_id, pariente_id, nombre, ruta, icono, menu_orden, activo, creado, nivel) OVERRIDING SYSTEM VALUE VALUES (42, 26, 'Turnos', '/catalogo/turnos', 'MdOutlineShapeLine', 1, true, '16:45:37.294', 2) ON CONFLICT DO NOTHING;
INSERT INTO sih.menu (menu_id, pariente_id, nombre, ruta, icono, menu_orden, activo, creado, nivel) OVERRIDING SYSTEM VALUE VALUES (48, 26, 'Turno-Horario', '/catalogo/turnos/horarios', 'PiTable', 2, true, '11:33:56.229', 2) ON CONFLICT DO NOTHING;
INSERT INTO sih.menu (menu_id, pariente_id, nombre, ruta, icono, menu_orden, activo, creado, nivel) OVERRIDING SYSTEM VALUE VALUES (50, 27, 'Disponib. Maestros', '/horarios/disponibilidad-maestro', 'AiTwotoneSchedule', 0, true, '15:29:34.796', 2) ON CONFLICT DO NOTHING;
INSERT INTO sih.menu (menu_id, pariente_id, nombre, ruta, icono, menu_orden, activo, creado, nivel) OVERRIDING SYSTEM VALUE VALUES (49, 27, 'Asignacion', '/horarios/asignacion', 'MdAssignment', 2, true, '18:55:57.144', 2) ON CONFLICT DO NOTHING;
INSERT INTO sih.menu (menu_id, pariente_id, nombre, ruta, icono, menu_orden, activo, creado, nivel) OVERRIDING SYSTEM VALUE VALUES (36, 56, 'Vista Grupos', '/horarios/view', 'MdCategory', 0, true, '13:52:30.534', 2) ON CONFLICT DO NOTHING;
INSERT INTO sih.menu (menu_id, pariente_id, nombre, ruta, icono, menu_orden, activo, creado, nivel) OVERRIDING SYSTEM VALUE VALUES (51, 56, 'Vista Mestros', '/horarios/maestro', 'PiUsersThree', 1, true, '20:53:41.157', 2) ON CONFLICT DO NOTHING;
INSERT INTO sih.menu (menu_id, pariente_id, nombre, ruta, icono, menu_orden, activo, creado, nivel) OVERRIDING SYSTEM VALUE VALUES (45, 25, 'Menus', '/administracion/menus', 'PiRowsPlusBottomDuotone', 0, true, '11:25:08.972893', 2) ON CONFLICT DO NOTHING;
INSERT INTO sih.menu (menu_id, pariente_id, nombre, ruta, icono, menu_orden, activo, creado, nivel) OVERRIDING SYSTEM VALUE VALUES (53, 27, 'Disponib. Grupos', '/horarios/disponibilidad-grupo', 'AiTwotoneSchedule', 1, true, '23:50:33.191244', 2) ON CONFLICT DO NOTHING;
INSERT INTO sih.menu (menu_id, pariente_id, nombre, ruta, icono, menu_orden, activo, creado, nivel) OVERRIDING SYSTEM VALUE VALUES (40, 27, 'Gen. Horario Autom.', '/horarios/generador/automatico', 'MdRocket', 4, true, '13:52:45.358', 2) ON CONFLICT DO NOTHING;
INSERT INTO sih.menu (menu_id, pariente_id, nombre, ruta, icono, menu_orden, activo, creado, nivel) OVERRIDING SYSTEM VALUE VALUES (57, 27, 'Gen. Horario Manual', '/horarios/generador/manual', 'MdEdit', 4, true, '14:14:25.538832', 2) ON CONFLICT DO NOTHING;
INSERT INTO sih.menu (menu_id, pariente_id, nombre, ruta, icono, menu_orden, activo, creado, nivel) OVERRIDING SYSTEM VALUE VALUES (37, 28, 'Horario por Grupo', '/reportes/horario-grupos', 'MdTableRows', 0, true, '13:52:30.534', 2) ON CONFLICT DO NOTHING;
INSERT INTO sih.menu (menu_id, pariente_id, nombre, ruta, icono, menu_orden, activo, creado, nivel) OVERRIDING SYSTEM VALUE VALUES (41, 25, 'Roles', '/administracion/roles', 'MdOutlinePersonalInjury', 1, true, '12:45:49.648', 2) ON CONFLICT DO NOTHING;
INSERT INTO sih.menu (menu_id, pariente_id, nombre, ruta, icono, menu_orden, activo, creado, nivel) OVERRIDING SYSTEM VALUE VALUES (58, 27, 'Gen. Horario IA', '/horarios/generador/ia', 'MdAutoAwesome', 5, true, '23:33:01.812304', 2) ON CONFLICT DO NOTHING;
INSERT INTO sih.menu (menu_id, pariente_id, nombre, ruta, icono, menu_orden, activo, creado, nivel) OVERRIDING SYSTEM VALUE VALUES (38, 28, 'Materias x Especialidad', '/reportes/materias-especialidad', 'MdAssignment', 1, true, '13:52:30.534', 2) ON CONFLICT DO NOTHING;
INSERT INTO sih.menu (menu_id, pariente_id, nombre, ruta, icono, menu_orden, activo, creado, nivel) OVERRIDING SYSTEM VALUE VALUES (44, 25, 'Rol-Menu', '/administracion/rolmenu', 'MdOutlinePersonPin', 2, true, '19:40:27.509', 2) ON CONFLICT DO NOTHING;

-- ── ROL_MENU (despues de roles y menu por sus FK) ──────────────────────────
INSERT INTO sih.rol_menu (rol_id, menu_id) VALUES (5, 28) ON CONFLICT DO NOTHING;
INSERT INTO sih.rol_menu (rol_id, menu_id) VALUES (5, 38) ON CONFLICT DO NOTHING;
INSERT INTO sih.rol_menu (rol_id, menu_id) VALUES (5, 37) ON CONFLICT DO NOTHING;
INSERT INTO sih.rol_menu (rol_id, menu_id) VALUES (1, 29) ON CONFLICT DO NOTHING;
INSERT INTO sih.rol_menu (rol_id, menu_id) VALUES (1, 30) ON CONFLICT DO NOTHING;
INSERT INTO sih.rol_menu (rol_id, menu_id) VALUES (1, 25) ON CONFLICT DO NOTHING;
INSERT INTO sih.rol_menu (rol_id, menu_id) VALUES (1, 56) ON CONFLICT DO NOTHING;
INSERT INTO sih.rol_menu (rol_id, menu_id) VALUES (1, 28) ON CONFLICT DO NOTHING;
INSERT INTO sih.rol_menu (rol_id, menu_id) VALUES (1, 52) ON CONFLICT DO NOTHING;
INSERT INTO sih.rol_menu (rol_id, menu_id) VALUES (1, 54) ON CONFLICT DO NOTHING;
INSERT INTO sih.rol_menu (rol_id, menu_id) VALUES (1, 33) ON CONFLICT DO NOTHING;
INSERT INTO sih.rol_menu (rol_id, menu_id) VALUES (1, 43) ON CONFLICT DO NOTHING;
INSERT INTO sih.rol_menu (rol_id, menu_id) VALUES (1, 24) ON CONFLICT DO NOTHING;
INSERT INTO sih.rol_menu (rol_id, menu_id) VALUES (1, 26) ON CONFLICT DO NOTHING;
INSERT INTO sih.rol_menu (rol_id, menu_id) VALUES (1, 27) ON CONFLICT DO NOTHING;
INSERT INTO sih.rol_menu (rol_id, menu_id) VALUES (1, 31) ON CONFLICT DO NOTHING;
INSERT INTO sih.rol_menu (rol_id, menu_id) VALUES (1, 32) ON CONFLICT DO NOTHING;
INSERT INTO sih.rol_menu (rol_id, menu_id) VALUES (1, 34) ON CONFLICT DO NOTHING;
INSERT INTO sih.rol_menu (rol_id, menu_id) VALUES (1, 42) ON CONFLICT DO NOTHING;
INSERT INTO sih.rol_menu (rol_id, menu_id) VALUES (1, 48) ON CONFLICT DO NOTHING;
INSERT INTO sih.rol_menu (rol_id, menu_id) VALUES (1, 50) ON CONFLICT DO NOTHING;
INSERT INTO sih.rol_menu (rol_id, menu_id) VALUES (1, 38) ON CONFLICT DO NOTHING;
INSERT INTO sih.rol_menu (rol_id, menu_id) VALUES (1, 49) ON CONFLICT DO NOTHING;
INSERT INTO sih.rol_menu (rol_id, menu_id) VALUES (1, 36) ON CONFLICT DO NOTHING;
INSERT INTO sih.rol_menu (rol_id, menu_id) VALUES (1, 51) ON CONFLICT DO NOTHING;
INSERT INTO sih.rol_menu (rol_id, menu_id) VALUES (1, 45) ON CONFLICT DO NOTHING;
INSERT INTO sih.rol_menu (rol_id, menu_id) VALUES (1, 53) ON CONFLICT DO NOTHING;
INSERT INTO sih.rol_menu (rol_id, menu_id) VALUES (1, 40) ON CONFLICT DO NOTHING;
INSERT INTO sih.rol_menu (rol_id, menu_id) VALUES (1, 57) ON CONFLICT DO NOTHING;
INSERT INTO sih.rol_menu (rol_id, menu_id) VALUES (1, 37) ON CONFLICT DO NOTHING;
INSERT INTO sih.rol_menu (rol_id, menu_id) VALUES (1, 41) ON CONFLICT DO NOTHING;
INSERT INTO sih.rol_menu (rol_id, menu_id) VALUES (1, 58) ON CONFLICT DO NOTHING;
INSERT INTO sih.rol_menu (rol_id, menu_id) VALUES (1, 44) ON CONFLICT DO NOTHING;
INSERT INTO sih.rol_menu (rol_id, menu_id) VALUES (3, 56) ON CONFLICT DO NOTHING;
INSERT INTO sih.rol_menu (rol_id, menu_id) VALUES (3, 28) ON CONFLICT DO NOTHING;
INSERT INTO sih.rol_menu (rol_id, menu_id) VALUES (3, 52) ON CONFLICT DO NOTHING;
INSERT INTO sih.rol_menu (rol_id, menu_id) VALUES (3, 54) ON CONFLICT DO NOTHING;
INSERT INTO sih.rol_menu (rol_id, menu_id) VALUES (3, 33) ON CONFLICT DO NOTHING;
INSERT INTO sih.rol_menu (rol_id, menu_id) VALUES (3, 43) ON CONFLICT DO NOTHING;
INSERT INTO sih.rol_menu (rol_id, menu_id) VALUES (3, 24) ON CONFLICT DO NOTHING;
INSERT INTO sih.rol_menu (rol_id, menu_id) VALUES (3, 26) ON CONFLICT DO NOTHING;
INSERT INTO sih.rol_menu (rol_id, menu_id) VALUES (3, 27) ON CONFLICT DO NOTHING;
INSERT INTO sih.rol_menu (rol_id, menu_id) VALUES (3, 31) ON CONFLICT DO NOTHING;
INSERT INTO sih.rol_menu (rol_id, menu_id) VALUES (3, 32) ON CONFLICT DO NOTHING;
INSERT INTO sih.rol_menu (rol_id, menu_id) VALUES (3, 34) ON CONFLICT DO NOTHING;
INSERT INTO sih.rol_menu (rol_id, menu_id) VALUES (3, 42) ON CONFLICT DO NOTHING;
INSERT INTO sih.rol_menu (rol_id, menu_id) VALUES (3, 48) ON CONFLICT DO NOTHING;
INSERT INTO sih.rol_menu (rol_id, menu_id) VALUES (3, 50) ON CONFLICT DO NOTHING;
INSERT INTO sih.rol_menu (rol_id, menu_id) VALUES (3, 38) ON CONFLICT DO NOTHING;
INSERT INTO sih.rol_menu (rol_id, menu_id) VALUES (3, 49) ON CONFLICT DO NOTHING;
INSERT INTO sih.rol_menu (rol_id, menu_id) VALUES (3, 36) ON CONFLICT DO NOTHING;
INSERT INTO sih.rol_menu (rol_id, menu_id) VALUES (3, 51) ON CONFLICT DO NOTHING;
INSERT INTO sih.rol_menu (rol_id, menu_id) VALUES (3, 53) ON CONFLICT DO NOTHING;
INSERT INTO sih.rol_menu (rol_id, menu_id) VALUES (3, 40) ON CONFLICT DO NOTHING;
INSERT INTO sih.rol_menu (rol_id, menu_id) VALUES (3, 57) ON CONFLICT DO NOTHING;
INSERT INTO sih.rol_menu (rol_id, menu_id) VALUES (3, 37) ON CONFLICT DO NOTHING;
INSERT INTO sih.rol_menu (rol_id, menu_id) VALUES (3, 58) ON CONFLICT DO NOTHING;

-- ── ESCUELA BASE (solo si la base no tiene ninguna) ────────────────────────
INSERT INTO sih.escuelas (escuela_id, nombre, clave, activo)
OVERRIDING SYSTEM VALUE
SELECT 1, 'Escuela Principal', 'ESC001', true
 WHERE NOT EXISTS (SELECT 1 FROM sih.escuelas)
    ON CONFLICT DO NOTHING;

-- ── AVANZAR SECUENCIAS ────────────────────────────────────────────────────
-- Los INSERT llevan ID explicito (OVERRIDING SYSTEM VALUE), asi que la
-- secuencia de identidad se queda atras. Sin esto, el primer INSERT de la
-- aplicacion fallaria con "llave duplicada" en el PRIMARY KEY.
SELECT setval(pg_get_serial_sequence('sih.roles','rol_id'),      (SELECT COALESCE(MAX(rol_id),1)      FROM sih.roles));
SELECT setval(pg_get_serial_sequence('sih.menu','menu_id'),      (SELECT COALESCE(MAX(menu_id),1)     FROM sih.menu));
SELECT setval(pg_get_serial_sequence('sih.escuelas','escuela_id'),(SELECT COALESCE(MAX(escuela_id),1)  FROM sih.escuelas));

COMMIT;

-- Resumen de lo sembrado
SELECT 'roles' AS catalogo, count(*) AS filas FROM sih.roles
UNION ALL SELECT 'menu', count(*) FROM sih.menu
UNION ALL SELECT 'rol_menu', count(*) FROM sih.rol_menu
UNION ALL SELECT 'escuelas', count(*) FROM sih.escuelas ORDER BY 1;
