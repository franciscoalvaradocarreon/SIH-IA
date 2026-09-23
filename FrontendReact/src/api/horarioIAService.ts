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
  /** Las cinco revisiones finas: cupo por par, bloques apretados, días, sesiones largas y horario vigente. */
  revisiones?: RevisionesViabilidadIA;
}

// ── las cinco revisiones finas (diagnóstico, nunca bloquea) ──

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

/** REVISIÓN 3 · Materia cuyas horas superan los días disponibles (regla de un día por materia). */
export interface MateriaPorDiasIA {
  grupoId: number;
  grupo: string;
  materia: string;
  maestro: string;
  horas: number;
  /** Días distintos con disponibilidad del grupo (tope de la regla). */
  diasGrupo: number;
  /** Días en que además el maestro está disponible (tope real de esta combinación). */
  diasComunes: number;
  faltan: number;
  motivo: string;
  /**
   * REVISIÓN 3 · el backend NO manda severidad en esta lista (las otras cuatro revisiones sí): llega
   * `undefined`. Por eso es opcional y quien la pinte tiene que aguantar que falte.
   */
  severidad?: SeveridadViabilidad;
}

/** REVISIÓN 4 · Materia con sesiones de 2+ h sin días suficientes con par de bloques contiguos. */
export interface SesionLargaSinParesIA {
  grupoId: number;
  grupo: string;
  materia: string;
  maestro: string;
  horasLargas: number;
  sesionesLargas: number;
  paresContiguos: number;
  diasConPar: number;
  faltan: number;
  motivo: string;
  severidad: SeveridadViabilidad;
}

/** REVISIÓN 5 · Desglose por grupo del horario vigente (versión 1). */
export interface GrupoHorarioVigenteIA {
  grupoId: number;
  grupo: string;
  clases: number;
  horas: number;
  bloquesDelTurno: number;
  diasConClase: number;
  /** Bloques desde el inicio del turno hasta la primera clase (sumado por día). */
  arranquesTarde: number;
  /** Bloques libres entre la primera y la última clase del grupo. */
  huecos: number;
  /** Pares de bloques consecutivos del mismo día con materias distintas del mismo maestro. */
  adyacencias: number;
  severidad: SeveridadViabilidad;
}

/** Totales de las cinco revisiones finas. */
export interface ResumenRevisionesIA {
  paresConDeficit: number;
  horasDeficit: number;
  gruposConBloqueUnico: number;
  bloquesConUnMaestro: number;
  bloquesConDosMaestros: number;
  materiasPorDias: number;
  sesionesLargasCortas: number;
  gruposConArranqueTarde: number;
  huecosHorarioVigente: number;
  adyacenciasHorarioVigente: number;
  sinHorarioVigente: number;
}

export interface RevisionesViabilidadIA {
  cupo: CupoMaestroGrupoIA[];
  bloquesApretados: GrupoBloquesApretadosIA[];
  materiasPorDias: MateriaPorDiasIA[];
  sesionesLargas: SesionLargaSinParesIA[];
  horarioVigente: GrupoHorarioVigenteIA[];
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

export interface ConfigIA {
  intentos: number;
  segundosPorIntento: number;
  maxPasos: number;
  /** true si el servidor tiene una clave configurada (app.ia.api-key). */
  llmConfigurado: boolean;
  /** Modelo configurado en el servidor, para proponerlo en el diálogo de la clave. */
  modeloPorDefecto: string;
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
};
