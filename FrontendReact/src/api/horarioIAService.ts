import api from './axiosConfig';
import type { ResultadoValidacion } from '../types';

/**
 * GENERADOR DE HORARIOS IA (sin Timefold).
 *
 * El motor arma el horario con su propio algoritmo y hace varios intentos, cada uno desde cero.
 * Igual que la generación con solver, corre en segundo plano: `generar()` responde 202 con un
 * trabajo y hay que consultarlo hasta que termine.
 */

export type EstadoChequeoIA = 'OK' | 'ADVERTENCIA' | 'ERROR';

export interface ChequeoIA {
  nombre: string;
  estado: EstadoChequeoIA;
  detalle: string;
}

export interface MateriaImposible {
  asignacionId: number;
  grupo: string;
  materia: string;
  maestro: string;
  horas: number;
  ventanas: number;
  motivo: string;
}

export interface ValidacionIA {
  backend: ResultadoValidacion;
  chequeos: ChequeoIA[];
  imposibles: MateriaImposible[];
  aptoParaGenerar: boolean;
  grupos: number;
  asignaciones: number;
  sesiones: number;
  bloques: number;
  horasDemandadas: number;
  ventanasLegales: number;
  /** Diagnóstico del reparto: qué maestros/grupos no caben y cuáles van justos. */
  analisisViabilidad?: AnalisisViabilidadIA;
}

// ── análisis de viabilidad (diagnóstico, no bloquea la generación) ──

export type SeveridadViabilidad = 'IMPOSIBLE' | 'AJUSTADO' | 'HOLGADO';

/** Un maestro visto desde su capacidad real: horas asignadas contra huecos legales. */
export interface MaestroViabilidadIA {
  maestroId: number;
  maestro: string;
  horasAsignadas: number;
  ventanasLegales: number;
  /** Horas que no caben en ningún lado (0 si no hay problema). */
  deficit: number;
  materias: string[];
  grupos: number;
  severidad: SeveridadViabilidad;
}

/** Un grupo visto desde su capacidad real, con el índice que permite compararlo. */
export interface GrupoViabilidadIA {
  grupoId: number;
  grupo: string;
  horasNecesarias: number;
  bloquesDisponibles: number;
  /** Promedio de maestros disponibles por bloque disponible. */
  maestrosPromedio: number;
  maestros: number;
  /** Bloques donde NINGÚN maestro del grupo está disponible. */
  bloquesConCero: number;
  bloquesSinMaestro: string[];
  /** Menor = más justo: es el orden de la lista de desbalance. */
  indiceHolgura: number;
  severidad: SeveridadViabilidad;
}

export interface GrupoDesbalanceIA {
  grupoId: number;
  grupo: string;
  horasNecesarias: number;
  bloquesDisponibles: number;
  indiceHolgura: number;
  severidad: SeveridadViabilidad;
}

export interface ResumenViabilidadIA {
  maestrosEnDeficit: number;
  horasSinHueco: number;
  gruposImposibles: number;
  bloquesSinMaestro: number;
  gruposAjustados: number;
}

export interface AnalisisViabilidadIA {
  maestros: MaestroViabilidadIA[];
  /** Grupos ordenados de más a menos ajustado. */
  grupos: GrupoViabilidadIA[];
  desbalance: GrupoDesbalanceIA[];
  resumen: ResumenViabilidadIA;
  /** Las dos revisiones finas: cupo por par y bloques apretados. */
  revisiones?: RevisionesViabilidadIA;
}

// ── las dos revisiones finas (diagnóstico, nunca bloquea) ──

/**
 * REVISIÓN 1 · Cupo real de un par (maestro, grupo): las horas que ese maestro debe dar en ese grupo
 * contra los bloques en los que los dos están disponibles a la vez. Si debe más, es déficit real.
 */
export interface CupoMaestroGrupoIA {
  maestroId: number;
  maestro: string;
  grupoId: number;
  grupo: string;
  horasEnGrupo: number;
  bloquesComunes: number;
  /** Horas que no caben en ese grupo con ese maestro (0 si no hay problema). */
  deficit: number;
  materias: string[];
  severidad: SeveridadViabilidad;
}

/** REVISIÓN 2 · Un bloque con pocos maestros posibles dentro de un grupo. */
export interface BloquePocosMaestrosIA {
  bloque: string;
  maestrosPosibles: number;
  /** Nombre del único maestro posible (solo cuando `maestrosPosibles` es 1). */
  unicoMaestro?: string | null;
}

/** REVISIÓN 2 · Bloques donde el grupo solo tiene 1 o 2 maestros posibles. */
export interface GrupoBloquesApretadosIA {
  grupoId: number;
  grupo: string;
  bloquesDisponibles: number;
  /** Bloques con UN solo maestro posible: punto único de fallo. */
  bloquesConUno: number;
  bloquesConDos: number;
  maestrosMinimo: number;
  detalle: BloquePocosMaestrosIA[];
}

/** Totales de las dos revisiones finas. */
export interface ResumenRevisionesIA {
  paresConDeficit: number;
  horasDeficit: number;
  gruposConBloqueUnico: number;
  bloquesConUnMaestro: number;
  bloquesConDosMaestros: number;
}

export interface RevisionesViabilidadIA {
  cupo: CupoMaestroGrupoIA[];
  bloquesApretados: GrupoBloquesApretadosIA[];
  resumen: ResumenRevisionesIA;
}

/** Una sesión (trozo del patrón) que no se pudo colocar, con el motivo. */
export interface PendienteIA {
  asignacionId: number;
  grupoId: number;
  grupoNombre: string;
  materiaClave: string;
  materiaNombre: string;
  maestroId: number;
  maestroNombre: string;
  duracion: number;
  motivo: string;
  /** Ventanas legales con quién las ocupa ('LIBRE' si cabría). */
  ventanas: string[];
}

export interface IntentoIA {
  numero: number;
  asesor: string;
  generadoEn: string;
  milisegundos: number;
  horas: number;
  horasDemandadas: number;
  sesionesLargas: number;
  sesionesLargasPendientes: number;
  arranquesTarde: number;
  castigoHuecos: number;
  adyacencias: number;
  materiasCompletas: number;
  materiasTotales: number;
  /** Igual que el score medium del solver, para poder comparar los dos motores. */
  medium: number;
  pendientes: PendienteIA[];
  /** Violaciones duras detectadas por la comprobación independiente (vacío = válido). */
  problemas: string[];
}

export interface TrabajoIA {
  id: string;
  estado: 'EN_COLA' | 'EN_PROCESO' | 'COMPLETADO' | 'ERROR';
  mensaje: string;
  modo: string;
  encoladoEn: string;
  iniciadoEn?: string;
  finalizadoEn?: string;
  segundosTranscurridos: number;
  segundosPorIntento: number;
  semestreId?: number;
  turnoId?: number;
  solicitadoPor: string;
  intentosPlaneados: number;
  intentoActual: number;
  horasDemandadas: number;
  mejorNumero?: number;
  registrado?: number;
  registradoEn?: string;
  filasRegistradas: number;
  horasRegistradas: number;
  terminadoPorUsuario: boolean;
  error?: string;
  validacion?: ValidacionIA;
  intentos: IntentoIA[];
}

/**
 * Una corrida guardada del generador: una solucion completa con sus metricas.
 *
 * Guardar NO toca el horario real. Es lo que permite lanzar varias generaciones, apartar las que
 * interesen y aplicar despues la mejor.
 */
export interface CorridaIA {
  id: number;
  nombre: string;
  notas: string | null;
  /** Turno del alcance. Puede ser null si el turno se borro del catalogo. */
  turnoId: number | null;
  asesor: string;
  generadoEn: string;
  creado: string;
  creadoPor: string | null;

  /** Con qué banderas se generó. null = guardada antes de que se empezara a registrar. */
  asignarMaestros: boolean | null;
  asignarAulas: boolean | null;

  // ── metricas: es lo que se compara entre corridas ──
  milisegundos: number;
  horas: number;
  horasDemandadas: number;
  sesionesLargas: number;
  sesionesLargasPendientes: number;
  arranquesTarde: number;
  castigoHuecos: number;
  adyacencias: number;
  materiasCompletas: number;
  materiasTotales: number;
  medium: number;

  totalFilas: number;
  totalPendientes: number;
  totalProblemas: number;
  /** Bloques que hay guardados AHORA. Si no cuadra con totalFilas, la corrida esta incompleta. */
  filasGuardadas: number;
  /** true si el horario vigente de ese turno es exactamente esta corrida. */
  vigente: boolean;
  aplicable: boolean;
  /** Por que no se puede aplicar, listo para mostrar. null si si se puede. */
  motivoNoAplicable: string | null;
}

/** Lo que devuelve aplicar una corrida al horario vigente. */
export interface ResumenAplicadoIA {
  filas: number;
  horas: number;
  grupos: number;
  pendientes: number;
}

export interface ConfigIA {
  intentos: number;
  segundosPorIntento: number;
  maxPasos: number;
  /** true si el servidor tiene una clave configurada (app.ia.api-key). */
  llmConfigurado: boolean;
  /** Modelo configurado en el servidor, para proponerlo en el diálogo de la clave. */
  modeloPorDefecto: string;
  /**
   * Cuántos intentos calcula el servidor EN PARALELO (app.ia.hilos).
   *
   * <p>Lo necesita la interfaz para estimar el tiempo REAL: con N a la vez, los intentos van en
   * tandas de N, así que el trabajo dura ceil(intentos / hilos) tandas y no 'intentos' veces.
   * Sin esto, la barra de progreso se quedaría en un tercio al terminar.
   */
  hilos: number;
}

export interface SolicitudIA {
  semestreId: number;
  turnoId?: number;
  modo: string;
  intentos: number;
  segundosPorIntento: number;
  maxPasos?: number;
  /**
   * Clave de la API del asesor IA, SOLO para esta generación: el servidor la usa para crear el
   * asesor de este trabajo y la descarta al terminar (no se guarda ni se devuelve).
   */
  apiKey?: string;
  /** Modelo a usar en esta generación (opcional). */
  modelo?: string;
  /**
   * MODO "ASIGNAR MAESTROS DESDE EL STOCK": el motor elige el maestro de cada materia entre los que
   * ya la imparten en las asignaciones (por disponibilidad y carga, con la regla de Jóvenes).
   */
  asignarMaestros?: boolean;
  /**
   * MODO "ASIGNAR TALLERES DESDE EL STOCK": el motor elige el taller (aula) de cada sesión entre los
   * que YA USA ESA MATERIA -no entre todas las aulas del plantel-, en vez de quedarse siempre con el
   * de la asignación.
   *
   * OJO con el nombre del campo: se llama asignarAulas porque lo que acaba escribiendo es un
   * aula_id en la tabla horario, y Aula es el nombre de la entidad. En la pantalla se le dice
   * "talleres", que es como lo llama el propio motor por dentro (ver el stock de talleres en
   * GeneradorIA).
   *
   * Es INDEPENDIENTE de `asignarMaestros`: las dos banderas se combinan libremente.
   */
  asignarAulas?: boolean;
  /**
   * URL del endpoint `/chat/completions` (opcional). Se manda al elegir proveedor, porque la clave
   * del servidor puede ser de otro proveedor distinto del configurado.
   */
  url?: string;
}

/**
 * Proveedores que se ofrecen en el diálogo de la clave. Todos hablan el formato de OpenAI
 * (`/chat/completions`), que es lo que entiende el asesor, así que solo cambia la URL y el modelo.
 *
 * <p>Se puede además configurar en el servidor con `IA_URL` / `IA_MODELO` (y `IA_API_KEY`), y lo que
 * se elija aquí tiene prioridad SOLO para esa generación.
 */
export interface ProveedorIA {
  id: string;
  nombre: string;
  url: string;
  modeloPorDefecto: string;
  modelos: string[];
}

export const PROVEEDORES_IA: ProveedorIA[] = [
  {
    id: 'deepseek',
    nombre: 'DeepSeek',
    url: 'https://api.deepseek.com/chat/completions',
    modeloPorDefecto: 'deepseek-chat',
    modelos: ['deepseek-chat', 'deepseek-reasoner'],
  },
  {
    id: 'openai',
    nombre: 'OpenAI',
    url: 'https://api.openai.com/v1/chat/completions',
    modeloPorDefecto: 'gpt-4o-mini',
    modelos: ['gpt-4o-mini', 'gpt-4o', 'gpt-4.1-mini'],
  },
  {
    id: 'personalizado',
    nombre: 'Otro (URL propia)',
    url: '',
    modeloPorDefecto: '',
    modelos: [],
  },
];

export const horarioIAService = {
  /** Ajustes por defecto del motor (los mismos que usa el backend si no se manda nada). */
  config: () => api.get<ConfigIA>('/horario-ia/config'),

  /** Pre-validación: la del backend de siempre + los chequeos propios del motor IA. */
  validar: (semestreId: number, turnoId?: number) => {
    const params = new URLSearchParams();
    params.set('semestreId', String(semestreId));
    if (turnoId) params.set('turnoId', String(turnoId));
    return api.get<ValidacionIA>(`/horario-ia/validar?${params.toString()}`);
  },

  /** Lanza la generación (202 + trabajo). */
  generar: (solicitud: SolicitudIA) => api.post<TrabajoIA>('/horario-ia/generar', solicitud),

  consultar: (trabajoId: string) =>
    api.get<TrabajoIA>(`/horario-ia/trabajo/${encodeURIComponent(trabajoId)}`),

  /** Deja de lanzar intentos y conserva lo ya hecho. */
  terminar: (trabajoId: string) =>
    api.post<TrabajoIA>(`/horario-ia/trabajo/${encodeURIComponent(trabajoId)}/terminar`),

  /** Escribe ese intento en el horario real (reemplaza el de los grupos del alcance). */
  registrar: (trabajoId: string, numero: number) =>
    api.post<TrabajoIA>(
      `/horario-ia/trabajo/${encodeURIComponent(trabajoId)}/intento/${numero}/registrar`
    ),

  pendientes: (trabajoId: string, numero: number) =>
    api.get<PendienteIA[]>(
      `/horario-ia/trabajo/${encodeURIComponent(trabajoId)}/intento/${numero}/pendientes`
    ),

  mejor: (trabajoId: string) =>
    api.get<IntentoIA>(`/horario-ia/trabajo/${encodeURIComponent(trabajoId)}/mejor`),

  /**
   * Guarda ese intento como una opcion, SIN tocar el horario real.
   *
   * OJO con el nombre, que invita a confusion: el boton "Usar este" de la pantalla llama a
   * registrar(), que SI escribe en el horario. Esto solo aparta la corrida para compararla despues.
   */
  guardarCorrida: (trabajoId: string, numero: number, nombre: string, notas?: string) =>
    api.post<CorridaIA>(
      `/horario-ia/trabajo/${encodeURIComponent(trabajoId)}/intento/${numero}/guardar`,
      { nombre, notas }
    ),

  /** Corridas guardadas de un semestre, para comparar sus metricas. */
  corridas: (semestreId: number) =>
    api.get<CorridaIA[]>('/horario-ia/corridas', { params: { semestreId } }),

  /** Aplica una corrida guardada al horario VIGENTE: reemplaza el de los grupos del alcance. */
  aplicarCorrida: (id: number) =>
    api.post<ResumenAplicadoIA>(`/horario-ia/corridas/${id}/aplicar`),

  /**
   * Quita una corrida de la lista de opciones.
   *
   * NO toca el horario real: si esa corrida ya se aplico, el horario que quedo sigue igual.
   */
  borrarCorrida: (id: number) =>
    api.delete<void>(`/horario-ia/corridas/${id}`),
};
