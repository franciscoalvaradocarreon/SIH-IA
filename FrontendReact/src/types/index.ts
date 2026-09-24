// Datos del usuario que recibimos del backend al hacer login
export interface LoginResponse {
  tokenJwt: string;
  nombreCompleto: string;
  correo: string;
  escuelaActivaId: number;
  roles?: string[];
}

// Menu
export interface MenuLista {
    menuId: number;
    label: string;
    path: string | null;
    icono: string | null;
    parienteId: number;
    parienteLabel: string | null;
    nivel: number;
    menuOrden: number;
    activo: boolean;
}

export interface MenuCrear {
    label: string;
    path?: string | null;
    icono?: string | null;
    parienteId?: number;
    nivel?: number;
    menuOrden?: number;
    activo?: boolean;
}

export interface MenuItem {
  id: number;
  label: string;
  path: string | null;      // null si es un nodo padre (que tiene hijos)
  icono: string | null;
  hijos: MenuItem[];        // Lista de hijos (recursividad)
}

// Escuela
export interface Escuela {
  id: number;
  nombre: string;
  nombreLargo?: string;
  direccion: string;
  telefono: string;
  logoUrl?: string | null;
  clave: string;
  activo?: boolean;
}

export interface EscuelaForm {
  nombre: string;
  nombreLargo?: string;
  direccion: string;
  telefono: string;
  clave: string;
  logoUrl?: string | null;
  logoArchivo?: File | null;
}

// Semestre
export interface Semestre {
  id: number;
  nombre: string;
  descripcion?: string;
  activo: boolean;
  creado?: string;
}

export interface SemestreForm {
  nombre: string;
  descripcion?: string;
  activo?: boolean;
}

export interface SemestreFiltros {
  busqueda?: string;
  activo?: boolean;
}

export interface Maestro {
    id: number;
    nombre: string;
    apellidos: string;
    nombreCompleto: string;
    email: string;
    telefono: string;
    fotoUrl: string | null;
    titulo?: string;
    apodo?: string;
    activo: boolean;
    semestreId?: number;
    semestreNombre?: string;
    turnoId?: number;
    turnoNombre?: string;
}

export interface MaestroForm {
    id?: number;
    nombre: string;
    apellidos: string;
    email: string;
    telefono: string;
    fotoUrl?: string;
    // El formulario guarda aqui el archivo elegido en el input de tipo file,
    // ademas del estado propio `fotoArchivo` que ya existia en el componente.
    fotoArchivo?: File | null;
    titulo?: string;
    apodo?: string;
    // Opcional a proposito: este formulario no edita el estado activo/inactivo.
    // Eso se cambia con maestroService.cambiarEstado(), un endpoint aparte, asi
    // que el objeto de estado del formulario nunca incluye este campo.
    activo?: boolean;
    semestreId: number;
    turnoId: number;
}

// Materia (para listados y detalle)
export interface Materia {
  id: number;
  nombre: string;
  clave: string;
  descripcion?: string;
  creditos: number;
  horasSemana: number;
  colorHex: string;
  activo: boolean;
  semestreId?: number;
  semestreNombre?: string;
  turnoId?: number;
  turnoNombre?: string;
}

// MateriaForm (para crear/editar)
export interface MateriaForm {
  nombre: string;
  clave: string;
  descripcion?: string;
  creditos: number;
  horasSemana: number;
  colorHex: string;
  activo?: boolean;
  semestreId: number;
  turnoId: number;
}

// Especialidad
export interface Especialidad {
  id: number;
  nombre: string;
  semestreId?: number;
  semestreNombre?: string;
  turnoId?: number;
  turnoNombre?: string;
}

// Especialidad (para crear y editar)
export interface EspecialidadForm {
  nombre: string;
  semestreId: number;
  turnoId: number;
}


// Usuario
export interface Usuario {
  id: number;
  usuario: string;
  nombreCompleto: string;
  email: string;
  fotoUrl?: string | null;
  activo: boolean;
  ultimoAcceso?: string;
  roles: string[];
  escuelas: string[];
}

export interface UsuarioForm {
  usuario: string;
  nombreCompleto: string;
  email: string;
  fotoUrl?: string | null;
  fotoArchivo?: File | null;
  password?: string;
  activo?: boolean;
  asignaciones?: AsignacionEscuelaRol[];
}

export interface UsuarioDetalle extends Usuario {
  asignaciones: UsuarioEscuelaRol[];
}

export interface UsuarioEscuelaRol {
  id: number;
  escuelaId: number;
  escuelaNombre: string;
  rolId: number;
  rolNombre: string;
  activo: boolean;
}

// Escuela - Rol
export interface AsignacionEscuelaRol {
  escuelaId: number;
  rolId: number;
  activo: boolean;
}

// Rol
export interface Rol {
  id: number;
  nombre: string;
  descripcion?: string;
}

export interface RolForm {
  nombre: string;
  descripcion?: string;
}

// Turno
export interface Turno {
  id: number;
  nombre: string;
  descripcion?: string;
  activo: boolean;
  semestreId?: number;
  semestreNombre?: string;
}

export interface TurnoForm {
  nombre: string;
  descripcion?: string;
  activo?: boolean;
  semestreId?: number;
}

// Grupo
export interface Grupo {
  id: number;
  nombre: string;
  grado: number;
  turnoId?: number;
  turno: string;
  especialidad?: {
    id: number;
    nombre: string;
    clave?: string;
  } | null; 
  capacidad: number;
  activo: boolean;
  semestreId?: number;
  semestreNombre?: string;
}

export interface GrupoForm {
  nombre: string;
  grado: number;
  turnoId: number;
  especialidadId?: number | null;
  capacidad: number;
  activo?: boolean;
  semestreId: number;  
}

export interface GrupoResultado {
  grupoId: number;
  grupoNombre: string;
  estado: 'pendiente' | 'ok' | 'sin-asignaciones' | 'sin-disponibilidad' | 'error';
  mensaje?: string;
  clases?: number;
  score?: string;
}

// Aula
export interface Aula {
    id: number;
    nombre: string;
    edificio: string;
    piso: string;
    descripcion?: string;
    activo: boolean;
    taller?: boolean;
    semestreId?: number;
    semestreNombre?: string;
    turnoId?: number;
    turnoNombre?: string;
}

export interface AulaForm {
    nombre: string;
    edificio?: string;
    piso?: string;
    descripcion?: string;
    activo?: boolean;
    taller?: boolean;
    semestreId?: number;
    turnoId: number;
}

// Rol-Menu
export interface RolMenu {
    rolId: number;
    rolNombre: string;
    menuId: number;
    menuLabel: string;
    menuPath: string;
}

export interface RolMenuCrear {
    rolId: number;
    menuId: number;
}

export interface RolMenuAsignacion {
    rolId: number;
    menuIds: number[];
}

// Turno Horario
export interface TurnoHorario {
    id: number;
    turnoId: number;
    turnoNombre: string;
    diaSemana: number;
    diaNombre: string;
    horaInicio: string;
    horaFin: string;
    descanso: boolean;
    orden: number;
    semestreId?: number;
    semestreNombre?: string;
}

export interface TurnoHorarioCrear {
    diaSemana: number;
    horaInicio: string;
    horaFin: string;
    descanso: boolean;
    orden: number;
    semestreId: number;
}

// Asignación
export interface Asignacion {
  id: number;
  grupoId: number;
  grupoNombre: string;
  materiaId: number;
  materiaNombre: string;
  materiaClave: string;
  maestroId: number;
  maestroNombre: string;
  maestroApellidos?: string;
  aulaId: number;
  aulaNombre: string;
  horas: number;
  colorHex: string;
  distribucion: string;
  activo: boolean;
  semestreId?: number;
  semestreNombre?: string;
  turnoId?: number;
  turnoNombre?: string;
}


export interface AsignacionForm {
  grupoId: number;
  materiaId: number;
  maestroId: number;
  aulaId: number;
  horas: number;
  colorHex?: string;
  distribucion: string;
  activo?: boolean;
  semestreId: number;
  turnoId?: number;
}

// DISPONIBILIDAD MAESTRO
export interface DisponibilidadMaestro {
  id: number;
  maestroId: number;
  maestroNombre: string;
  turnoHorarioId: number;
  diaSemana: number;
  horaInicio: string;
  horaFin: string;
  disponible: boolean;
  semestreId?: number;
  semestreNombre?: string;
}

export interface DisponibilidadMaestroCrear {
  maestroId: number;
  turnoHorarioId: number;
  disponible: boolean;
  semestreId: number; 
}

export interface DisponibilidadMaestroForm {
  maestroId: number;
  disponibilidades: {
    turnoHorarioId: number;
    disponible: boolean;
  }[];
}

// Para la matriz de horarios
export interface HorarioMatriz {
  turnoHorarioId: number;
  diaSemana: number;
  horaInicio: string;
  horaFin: string;
  disponible: boolean;
}


// HORARIO
export interface Horario {
  id: number;
  grupoId: number;
  grupoNombre: string;
  asignacionId: number;
  materiaNombre: string;
  materiaClave: string;
  maestroId: number;
  maestroNombre: string;
  turnoHorarioId: number;
  diaSemana: number;
  horaInicio: string;
  horaFin: string;
  aulaId: number;
  aulaNombre: string;
  colorHex?: string;
  version: number;
  semestreId?: number;
  semestreNombre?: string;
}

export interface HorarioSolucion {
  grupoId: number;
  grupoNombre?: string;
  score: ScoreHardMediumSoft;
  fechaGeneracion: string;
  totalClasesAsignadas: number;
  totalClasesNoAsignadas?: number;
  clasesNoAsignadas?: ClaseNoAsignada[];
  semestreId: number;
  semestreNombre: string;
}

export interface HorarioSolucionMasiva {
  semestreId: number;
  semestreNombre: string;
  fechaGeneracion: string;
  score?: ScoreHardMediumSoft;
  hardScore: number;
  mediumScore: number;
  softScore: number;
  totalGrupos: number;
  gruposConHorario: number;
  gruposSinAsignaciones: number;
  gruposSinDisponibilidad: number;
  totalClasesAsignadas: number;
  totalAsignaciones: number;
  tiempoMs: number;
  tiempoSegundos: number;
  turnoId?: number;
  turnoNombre?: string;
  detalles: DetalleGrupo[];
  factible: boolean;
  motivoInfactibilidad?: string | null;
  violacionesHard: ViolacionConstraint[];
  detalleAsignaciones: DetalleAsignacion[];
  conflictosDetectados: DetalleConflicto[];
}

/**
 * Trabajo de generación masiva de horarios (asíncrono).
 *
 * POST /api/horarios/generar-todos devuelve 202 con uno de estos trabajos; hay que
 * consultar su estado en GET /api/horarios/generar-todos/{id} hasta que pase a
 * COMPLETADO (el resultado viaja en `resultado`) o a ERROR (el motivo en `error`).
 */
export interface TrabajoGeneracion {
  id: string;
  estado: 'EN_COLA' | 'EN_PROCESO' | 'COMPLETADO' | 'ERROR';
  mensaje: string;
  encoladoEn?: string;
  iniciadoEn?: string | null;
  finalizadoEn?: string | null;
  segundosTranscurridos?: number;
  /** Límite configurado del solver masivo, en segundos (lo publica el backend). */
  limiteSegundos?: number;
  semestreId?: number;
  turnoId?: number | null;
  solicitadoPor?: string;
  error?: string | null;
  /** Solo viene cuando estado = 'COMPLETADO'. */
  resultado?: HorarioSolucionMasiva | null;
}

export interface ViolacionConstraint {
  constraint: string;
  detalle: string;
}

export interface DetalleConflicto {
  tipo: 'GRUPO' | 'MAESTRO' | 'AULA' | 'MAESTRO_NO_DISPONIBLE' | 'GRUPO_NO_DISPONIBLE' | 'BLOQUE_FUERA_TURNO';
  titulo: string;
  bloqueTexto: string;
  grupoNombre?: string | null;
  maestroNombre?: string | null;
  aulaNombre?: string | null;
  materias: string[];
  sugerencia?: string | null;
}

export interface DetalleGrupo {
  grupoId: number;
  grupoNombre: string;
  grado: number;
  turno?: string;
  clasesAsignadas: number;
  estado: 'OK' | 'SIN_ASIGNACIONES' | 'SIN_DISPONIBILIDAD';
  mensaje: string;
}

export interface DetalleAsignacion {
  asignacionId: number;
  grupoId: number;
  grupoNombre: string;
  grupoGrado: number;
  especialidadNombre?: string | null;
  materiaId: number;
  materiaClave: string;
  materiaNombre: string;
  maestroId: number;
  maestroNombre: string;
  aulaId: number;
  aulaNombre: string;
  turnoId: number;
  turnoNombre: string;
  horasEsperadas: number;
  clasesAsignadas: number;
  clasesSinAsignar: number;
  estado: 'OK' | 'PARCIAL' | 'SIN_ASIGNAR';
  motivo?: string | null;
}

// ============================================
// HORARIO DEL MAESTRO
// ============================================
export interface HorarioMaestro {
  id: number;
  maestroId: number;
  maestroNombre: string;
  maestroTitulo?: string;
  grupoId: number;
  grupoNombre: string;
  materiaId: number;
  materiaNombre: string;
  materiaClave: string;
  aulaId: number;
  aulaNombre: string;
  diaSemana: number;
  horaInicio: string;
  horaFin: string;
  colorHex: string;
}

// DISPONIBILIDAD GRUPO
export interface DisponibilidadGrupo {
  id: number;
  grupoId: number;
  grupoNombre: string;
  turnoHorarioId: number;
  diaSemana: number;
  horaInicio: string;
  horaFin: string;
  disponible: boolean;
  semestreId?: number;
  semestreNombre?: string;
}

export interface DisponibilidadGrupoCrear {
  grupoId: number;
  turnoHorarioId: number;
  disponible: boolean;
  semestreId: number;
}

export type ValidacionHorario = string[];

export interface ClaseNoAsignada {
  asignacionId: number;
  grupoId: number;
  grupoNombre: string;
  materiaId: number;
  materiaNombre: string;
  materiaClave: string;
  maestroId: number;
  maestroNombre: string;
  aulaId: number;
  aulaNombre: string;
  motivo: string;
}


export interface Validacion {
  codigo: string;
  titulo: string;
  estado: 'OK' | 'ADVERTENCIA' | 'ERROR';
  mensaje: string;
}

export interface ResultadoValidacion {
  validaciones: Validacion[];
  aptoParaGenerar: boolean;
  totalErrores: number;
  totalAdvertencias: number;
}

export interface ScoreHardMediumSoft {
  initScore: number;
  hardScore: number;
  mediumScore: number;
  softScore: number;
}

export interface ParGrupoBloqueCuello {
  grupoId: number;
  grupoNombre: string;
  grupoGrado: number;
  grupoTurno?: string | null;

  turnoHorarioId: number;
  diaSemana: number;
  diaNombre: string;
  horaInicio: string;
  horaFin: string;

  maestrosDisponibles: number;
  totalAsignacionesGrupo: number;
  maestrosNombres: string[];
  severidad: 'CRITICO' | 'ADVERTENCIA' | 'OK';
}

export interface AnalisisCuelloBotella {
  semestreId: number;
  semestreNombre: string;
  turnoId?: number;
  turnoNombre?: string;

  totalBloques: number;
  totalGrupos: number;
  totalParesGrupoBloque: number;
  paresCriticos: number;
  paresAdvertencia: number;
  paresOk: number;

  pares: ParGrupoBloqueCuello[];
}

