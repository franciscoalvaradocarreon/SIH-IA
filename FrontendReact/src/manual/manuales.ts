/**
 * MANUAL DE USUARIO DEL SISTEMA (SIH-IA).
 *
 * Una sola fuente de contenido con dos versiones:
 *
 *   - COORDINADOR: todo el trabajo del dia a dia (catalogos, disponibilidad, asignacion,
 *     generacion, tablero manual, vistas y reportes).
 *   - ADMINISTRADOR: lo mismo, mas las secciones marcadas `soloAdmin` (usuarios, escuelas, menus,
 *     correo y operacion). Las secciones son las mismas piezas de texto, asi que no hay dos
 *     documentos que se puedan contradecir.
 *
 * La version se decide por los roles del usuario: ADMIN ve la de administrador y cualquier otro
 * rol ve la de coordinador (ver versionParaRol). El indice sigue el menu real del sistema.
 */

export type TipoBloque = 'parrafo' | 'lista' | 'pasos' | 'aviso';

export interface BloqueManual {
  tipo: TipoBloque;
  /** Texto del parrafo o del aviso. */
  texto?: string;
  /** Vinetas (lista) o pasos numerados. */
  items?: string[];
  /** Color del aviso: informativo, ojo (algo que se rompe si se ignora) o truco. */
  tono?: 'info' | 'ojo' | 'tip';
}

export interface SeccionManual {
  id: string;
  titulo: string;
  /** Una linea que resume para que sirve. */
  resumen?: string;
  /** Ruta real de la pantalla dentro de la app. */
  ruta?: string;
  /** Si es true, la seccion solo aparece en la version del administrador. */
  soloAdmin?: boolean;
  bloques: BloqueManual[];
}

export const MANUAL: SeccionManual[] = [
  {
    id: 'como-funciona',
    titulo: 'Cómo funciona el sistema',
    resumen: 'La idea general y el orden en que se hace todo.',
    ruta: '/dashboard',
    bloques: [
      {
        tipo: 'parrafo',
        texto:
          'El sistema arma el horario de una escuela para un SEMESTRE y un TURNO a la vez. Arriba, en la barra lateral, siempre están visibles la escuela y el semestre activos: todo lo que hagas aplica a esa combinación.',
      },
      {
        tipo: 'pasos',
        items: [
          'Llena los catálogos (semestres, turnos, maestros, materias, grupos y aulas).',
          'Define los bloques del turno: qué días y a qué horas hay clase y dónde van los descansos.',
          'Captura la disponibilidad de maestros y de grupos.',
          'Crea las asignaciones: qué materia lleva cada grupo, con qué maestro, en qué aula y con qué distribución por días.',
          'Genera el horario (motor propio o tablero manual) y revísalo.',
          'Imprime o exporta los reportes.',
        ],
      },
      {
        tipo: 'aviso',
        tono: 'info',
        texto:
          'El sistema nunca guarda un horario "a medias": o entra completo o no entra. Si algo no cumple las reglas, te dice exactamente qué lo impide.',
      },
    ],
  },
  {
    id: 'catalogos',
    titulo: 'Catálogos',
    resumen: 'Los datos base: semestres, turnos, especialidades, maestros, materias, grupos y aulas.',
    ruta: '/catalogo/semestres',
    bloques: [
      {
        tipo: 'lista',
        items: [
          'Semestres: el periodo (por ejemplo "Ago 2026 - Ene 2027"). Es el contenedor de todo lo demás.',
          'Turnos: matutino, vespertino… Cada turno tiene sus propios grupos, bloques y disponibilidad.',
          'Especialidades: la carrera o área (Programación, Recursos Humanos, Mecánica…).',
          'Maestros: datos de contacto y el apodo, que es el nombre corto que se usa en el horario y en los reportes.',
          'Materias: clave, nombre, horas por semana y color. El color es el que pinta las clases en las vistas.',
          'Grupos: el grupo escolar (1°A, 5°B-PI…) con su grado, turno y especialidad.',
          'Aulas: nombre, edificio, piso y una casilla muy importante: "taller".',
        ],
      },
      {
        tipo: 'aviso',
        tono: 'tip',
        texto:
          'Varias pantallas tienen un botón para TRAER los datos de otro semestre (especialidades, turnos, bloques o disponibilidad). Sirve para no capturar dos veces lo mismo: se copia lo que falte y se te reporta qué se saltó y por qué.',
      },
      {
        tipo: 'aviso',
        tono: 'ojo',
        texto:
          'Antes de borrar un registro, revisa que no esté en uso: si tiene clases, asignaciones o disponibilidad, el sistema te lo dirá y te dirá desde dónde quitarlo primero.',
      },
    ],
  },
  {
    id: 'turno-horario',
    titulo: 'Bloques del turno (Turno-Horario)',
    resumen: 'Qué días y a qué horas hay clase, y dónde van los descansos.',
    ruta: '/catalogo/turnos/horarios',
    bloques: [
      {
        tipo: 'parrafo',
        texto:
          'Aquí se define la rejilla del turno: cada renglón es un bloque con su día, su hora de inicio y su hora de fin. Los descansos se marcan como tales para que ningún grupo tenga clase en ese hueco.',
      },
      {
        tipo: 'lista',
        items: [
          'El orden de los bloques es el que verás en todas las vistas y reportes.',
          'Un bloque puede marcarse como descanso: ahí no se coloca ninguna clase.',
          'Si el turno ya existe en otro semestre, se puede importar la rejilla completa.',
        ],
      },
    ],
  },
  {
    id: 'disponibilidad',
    titulo: 'Disponibilidad de maestros y de grupos',
    resumen: 'Quién puede dar clase y cuándo, y qué grupos están disponibles.',
    ruta: '/horarios/disponibilidad-maestro',
    bloques: [
      {
        tipo: 'parrafo',
        texto:
          'Es una rejilla de días por horas. Cada casilla se marca como disponible o no disponible, y el sistema la respeta al generar: nunca coloca a un maestro (ni a un grupo) en una casilla que no marcaste.',
      },
      {
        tipo: 'lista',
        items: [
          'En la rejilla de maestros, si una casilla ya tiene clase en el horario vigente, se pinta en naranja: gana sobre el verde.',
          'El botón "Traer disponibilidad de otro semestre" copia lo que falte y te reporta qué se saltó y por qué.',
          'La disponibilidad es por turno: el mismo maestro puede tener horarios distintos en cada turno.',
        ],
      },
      {
        tipo: 'aviso',
        tono: 'ojo',
        texto:
          'Si un maestro no tiene disponibilidad capturada, el sistema lo toma como no disponible en TODAS las casillas. Es la causa más común de que queden horas pendientes.',
      },
    ],
  },
  {
    id: 'asignacion',
    titulo: 'Asignación: materia, grupo, maestro y aula',
    resumen: 'Qué lleva cada grupo, con quién, dónde y repartido en qué días.',
    ruta: '/horarios/asignacion',
    bloques: [
      {
        tipo: 'parrafo',
        texto:
          'Cada asignación dice: este grupo lleva esta materia, con este maestro, en esta aula, estas horas por semana y con esta distribución por días. Es la entrada principal de todo lo que hace el motor.',
      },
      {
        tipo: 'lista',
        items: [
          'Horas: cuántas horas por semana lleva el grupo en esa materia.',
          'Distribución: cómo se reparten esas horas en la semana. "1,1" son dos sesiones de una hora en dos días; "2,2" son dos bloques dobles en dos días.',
          'Aula: en la que se supone que se da. La casilla "taller" del aula marca si es un aula normal o un taller de práctica.',
        ],
      },
      {
        tipo: 'aviso',
        tono: 'ojo',
        texto:
          'AULA y TALLER no son lo mismo. En el generador hay una bandera para "elegir el taller": si la enciendes, el motor puede mover las horas de TALLER entre los talleres de esa materia, pero las horas de un aula normal se quedan en su aula (nunca se van a un taller).',
      },
    ],
  },
  {
    id: 'generador-ia',
    titulo: 'Generador de horario (IA)',
    resumen: 'Armar el horario automáticamente y comparar corridas.',
    ruta: '/horarios/generador/ia',
    bloques: [
      {
        tipo: 'pasos',
        items: [
          'Elige el turno y revisa la validación previa: te dice si hay maestros en déficit, grupos imposibles o bloques sin maestro.',
          'Elige el modo: motor propio (heurística) o motor propio + asesor IA.',
          'Decide las dos banderas: repartir maestros desde el stock y elegir el taller de cada sesión entre los talleres de la materia. Son independientes.',
          'Ajusta cuántos intentos y cuánto tiempo por intento. Cada intento parte de cero y se queda el mejor.',
          'Al terminar, revisa las métricas de cada intento: horas colocadas, pendientes, huecos, arranques tarde, adyacencias y MEDIUM (más alto es mejor).',
          'Registra la corrida que te guste: queda guardada y la puedes comparar o aplicar después.',
        ],
      },
      {
        tipo: 'lista',
        items: [
          'Aplicar una corrida reemplaza el horario vigente. Se puede volver a aplicar otra en cualquier momento.',
          'La lista marca cuál está "en el horario" ahora mismo y cuáles se pueden aplicar.',
          'Borrar una corrida no toca el horario: solo la quita de la lista.',
        ],
      },
      {
        tipo: 'aviso',
        tono: 'tip',
        texto:
          'Si tienes prisa, lanza pocos intentos y tiempo corto para ver por dónde va, y después una corrida larga para la versión final. El motor aprovecha todo el tiempo que le des.',
      },
    ],
  },
  {
    id: 'tablero-manual',
    titulo: 'Tablero manual',
    resumen: 'Acomodar el horario a mano, arrastrando pines.',
    ruta: '/horarios/generador/manual',
    bloques: [
      {
        tipo: 'parrafo',
        texto:
          'Es la rejilla del horario con las clases como pines. Cada pin es una hora de clase: se arrastra de la caja a un hueco, se mueve de casilla a casilla o se devuelve a la caja. Hay dos vistas: "Maestros × Semana" (un renglón por maestro) y "Grupos × Horas" (el horario completo de un grupo).',
      },
      {
        tipo: 'lista',
        items: [
          'Las casillas con fondo verde claro son las horas en las que ese maestro marcó disponibilidad.',
          'El recuadro de estadísticas se recalcula al guardar: cobertura, materias completas, problemas, huecos, arranques tarde, adyacencias y MEDIUM.',
          '"Guardar" aplica los movimientos al horario vigente. Nada se guarda hasta que lo pulsas: mientras tanto todo está en borrador.',
          '"Guardar corrida" hace una foto del horario tal como está, como respaldo, sin tocar nada más.',
        ],
      },
      {
        tipo: 'aviso',
        tono: 'ojo',
        texto:
          'Los movimientos manuales viven solo en el horario. Si alguien aplica otra corrida, se pierden. Por eso, antes de aplicar algo distinto, usa "Guardar corrida": después puedes volver a ese estado desde la lista de corridas del generador.',
      },
    ],
  },
  {
    id: 'reglas',
    titulo: 'Las reglas que respeta el motor',
    resumen: 'Por qué el horario sale como sale.',
    bloques: [
      {
        tipo: 'lista',
        items: [
          'Nadie choca: un grupo, un maestro y un aula no pueden estar en dos clases a la misma hora.',
          'Nadie da clase en un descanso, ni fuera de su disponibilidad.',
          'Se respetan las horas por semana de cada asignación y su distribución por días.',
          'Un maestro no puede estar en dos grupos a la vez, ni dos veces en el mismo grupo el mismo día en materias distintas.',
          'Se castigan los huecos (horas libres entre clases), las clases que arrancan tarde y las sesiones demasiado largas.',
          'Se premian las adyacencias: dos horas seguidas del mismo maestro en el mismo grupo con materias distintas.',
        ],
      },
      {
        tipo: 'aviso',
        tono: 'info',
        texto:
          'El asesor IA solo aconseja el orden en que se intentan las materias difíciles. Todas las reglas las comprueba el motor por su cuenta, así que el asesor no puede romper nada.',
      },
    ],
  },
  {
    id: 'reportes',
    titulo: 'Reportes',
    resumen: 'Lo que se imprime y se entrega.',
    ruta: '/reportes/horario-grupos',
    bloques: [
      {
        tipo: 'lista',
        items: [
          'Horario por Grupo: el horario semanal de un grupo, con el resumen de materias y maestros asignados al final. Si una asignación quedó repartida en varias aulas, ahí se ve el desglose con sus horas.',
          'Reporte Cerebro: por especialidad y grado, una tabla de materias contra los grupos de ese grado, con el maestro que la imparte y las horas. Ideal para ver de un vistazo quién da qué y detectar materias sin cubrir.',
          'Reporte Asignación: el detalle de las asignaciones tal como están capturadas.',
        ],
      },
      {
        tipo: 'parrafo',
        texto:
          'Todos se pueden guardar en PDF (con corte de hoja por sección) o exportar a Excel. En los reportes de impresión, los controles no se imprimen.',
      },
    ],
  },
  {
    id: 'vistas',
    titulo: 'Vistas de consulta',
    resumen: 'Ver el horario vigente por grupo, por maestro o por aula.',
    ruta: '/vistas/grupo',
    bloques: [
      {
        tipo: 'parrafo',
        texto:
          'Son consultas del horario que está aplicado, sin posibilidad de cambiarlo. Sirven para responder rápido: "¿a qué hora tiene clase este maestro?", "¿está libre esta aula a las 10?" o "¿cómo quedó el grupo 3°B?".',
      },
    ],
  },
  {
    id: 'problemas',
    titulo: 'Problemas típicos y qué hacer',
    resumen: 'Las dudas que salen siempre.',
    bloques: [
      {
        tipo: 'lista',
        items: [
          'Quedaron horas pendientes: casi siempre es disponibilidad sin capturar, o demasiadas materias peleando por las mismas horas. Revisa el detalle de pendientes de la corrida.',
          'No me deja aplicar una corrida: el horario cambió desde que se generó, o la corrida no cubre todo el alcance. La lista te dice el motivo.',
          'Una clase apareció en un aula que no es la de la asignación: revisa si generaste con la bandera de "elegir el taller" encendida. Eso permite mover las horas de taller entre talleres.',
          'Se me perdieron los movimientos manuales: vuelve a la corrida que guardaste con "Guardar corrida" y aplícala.',
          'No veo una pantalla en el menú: tu rol no tiene permiso para ella. Pídele al administrador que revise Rol-Menú.',
        ],
      },
      {
        tipo: 'aviso',
        tono: 'tip',
        texto:
          'Antes de reclamar un horario raro, compara: la lista de corridas guarda cada versión con sus métricas, así que se puede ver qué cambió entre una y otra.',
      },
    ],
  },

  // ===================== Secciones solo para el administrador =====================
  {
    id: 'escuelas',
    titulo: 'Escuelas',
    resumen: 'Los datos de la escuela y su identidad visual.',
    ruta: '/administracion/escuelas',
    soloAdmin: true,
    bloques: [
      {
        tipo: 'lista',
        items: [
          'Nombre corto y nombre largo: el largo es el que sale en los encabezados de los reportes.',
          'Logo y colores: se usan en la interfaz y en los documentos.',
          'Una escuela puede tener varios semestres y usuarios; cada usuario puede tener distinto rol en cada escuela.',
        ],
      },
    ],
  },
  {
    id: 'usuarios',
    titulo: 'Usuarios y roles',
    resumen: 'Quién entra, con qué rol y a qué escuela.',
    ruta: '/administracion/usuarios',
    soloAdmin: true,
    bloques: [
      {
        tipo: 'lista',
        items: [
          'ADMIN: todo, incluida la administración (usuarios, roles, menús, escuelas).',
          'DIRECTOR: consulta y operación de la escuela, sin la administración del sistema.',
          'COORDINADOR: el trabajo académico completo: catálogos, disponibilidad, asignación, generación y reportes.',
          'MAESTRO: consulta de su propio horario.',
          'VIEWER: solo lectura.',
        ],
      },
      {
        tipo: 'pasos',
        items: [
          'Crea el usuario con su correo y contraseña temporal.',
          'Asígnale escuela y rol. Los roles se otorgan por escuela: un mismo usuario puede ser coordinador en una y maestro en otra.',
          'Si el usuario deja de laborar, desactívalo en lugar de borrarlo: así no se pierde el historial.',
        ],
      },
      {
        tipo: 'aviso',
        tono: 'ojo',
        texto:
          'El correo del usuario es con el que recupera su contraseña. Verifica que esté bien escrito: si no, no le llegará el enlace.',
      },
    ],
  },
  {
    id: 'menus',
    titulo: 'Menús y Rol-Menú',
    resumen: 'Qué ve cada rol en la barra lateral.',
    ruta: '/administracion/menus',
    soloAdmin: true,
    bloques: [
      {
        tipo: 'parrafo',
        texto:
          'El menú es un árbol: los grupos de primer nivel (Administración, Catálogos, Horarios, Vistas, Reportes) y, dentro, cada pantalla con su ruta y su icono. Se edita desde la pantalla de Menús.',
      },
      {
        tipo: 'lista',
        items: [
          'En Rol-Menú se decide qué entradas ve cada rol. Si una pantalla no aparece, es aquí donde se habilita.',
          'El icono se elige de un catálogo de nombres; si un nombre no existe en el catálogo, la barra lateral muestra las dos primeras letras como respaldo.',
          'Una entrada se puede desactivar sin borrarla: deja de mostrarse y se conserva el histórico.',
        ],
      },
    ],
  },
  {
    id: 'correo',
    titulo: 'Recuperación de contraseñas y correo',
    resumen: 'Cómo funciona el enlace de recuperación y qué revisar si no llega.',
    soloAdmin: true,
    bloques: [
      {
        tipo: 'pasos',
        items: [
          'El usuario pide la recuperación desde la pantalla de acceso.',
          'El sistema crea un enlace de un solo uso, válido 30 minutos, y lo envía por correo.',
          'Al usarlo, el usuario define su nueva contraseña y el enlace queda invalidado.',
        ],
      },
      {
        tipo: 'lista',
        items: [
          'Por seguridad, la pantalla responde igual exista o no el correo: un "listo" no garantiza que el mensaje haya salido.',
          'Hay un límite de solicitudes por usuario y por hora; si se pasa, se ignora en silencio.',
          'Si el correo no llega, lo primero es mirar los registros del servidor: ahí aparece "email enviado" o el motivo exacto del fallo.',
        ],
      },
      {
        tipo: 'aviso',
        tono: 'ojo',
        texto:
          'El correo se configura con las credenciales SMTP en el archivo de entorno del servidor. Con una contraseña de aplicación de Gmail hay que copiarla SIN los espacios que muestra Google (son 16 letras).',
      },
    ],
  },
  {
    id: 'operacion',
    titulo: 'Operación: versiones, respaldos y QA',
    resumen: 'Lo que conviene revisar de vez en cuando.',
    soloAdmin: true,
    bloques: [
      {
        tipo: 'lista',
        items: [
          'La versión desplegada y su commit se ven en /api/version; la barra lateral también muestra la versión.',
          'Las corridas guardadas son el respaldo natural del horario: antes de un cambio grande, guarda el estado actual como corrida.',
          'El proyecto incluye un QA del esquema que solo lee la base y devuelve un veredicto: revisa tablas, índices de choque, columnas críticas e integridad de los datos. Se corre después de cargar datos.',
          'Las publicaciones se hacen por etiquetas de versión: el pipeline compila, corre las pruebas y despliega.',
        ],
      },
      {
        tipo: 'aviso',
        tono: 'info',
        texto:
          'Si algo se ve raro después de un despliegue, lo primero es confirmar la versión: un contenedor viejo explica muchos comportamientos inesperados.',
      },
    ],
  },
];

export type VersionManual = 'administrador' | 'coordinador';

/** El administrador ve la versión completa; cualquier otro rol, la de coordinador. */
export const versionParaRol = (roles: string[]): VersionManual =>
  (roles ?? []).includes('ADMIN') ? 'administrador' : 'coordinador';

/** Secciones que le tocan a un usuario según sus roles. */
export const seccionesParaRol = (roles: string[]): SeccionManual[] => {
  const esAdmin = versionParaRol(roles) === 'administrador';
  return MANUAL.filter(s => esAdmin || !s.soloAdmin);
};

/** Título que se muestra en la pantalla y en la tarjeta del dashboard. */
export const tituloManual = (version: VersionManual): string =>
  version === 'administrador' ? 'Manual del administrador' : 'Manual del coordinador';
