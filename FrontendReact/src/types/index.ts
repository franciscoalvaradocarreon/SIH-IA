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
  direccion: string;F
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
    titulo?: string;
    activo: boolean;
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
  score: {
    initScore: number;
    hardScore: number;
    softScore: number;
  };
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
  score?: {
    initScore: number;
    hardScore: number;
    softScore: number;
  };
  hardScore: number;
  softScore: number;
  totalGrupos: number;
  gruposConHorario: number;
  gruposSinAsignaciones: number;
  gruposSinDisponibilidad: number;
  totalClasesAsignadas: number;
  totalAsignaciones: number;
  tiempoMs: number;
  tiempoSegundos: number;
  detalles: DetalleGrupo[];
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