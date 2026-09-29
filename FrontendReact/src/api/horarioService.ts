import api from './axiosConfig';
import type { AnalisisCuelloBotella, Horario, ResultadoValidacion } from '../types';

/**
 * Estadísticas de un horario, calculadas con las MISMAS fórmulas y pesos que el motor IA: sirven
 * para saber si una edición manual mejoró o empeoró lo que había dejado una corrida.
 */
export interface EstadisticasHorario {
  turnoId: number;
  turnoNombre: string;
  semestreId: number;
  semestreNombre: string;
  grupos: number;
  horasColocadas: number;
  horasDemandadas: number;
  coberturaPorcentaje: number;
  materiasCompletas: number;
  materiasTotales: number;
  horasPendientes: number;
  huecos: number;
  castigoHuecos: number;
  arranquesTarde: number;
  adyacencias: number;
  desvioDistribucion: number;
  sesionesLargasPendientes: number;
  sesionesLargas: number;
  /** Score MEDIUM (negativo): más cerca de 0 = mejor. */
  medium: number;
  choquesGrupo: number;
  choquesMaestro: number;
  choquesAula: number;
  materiasRepetidasDia: number;
  clasesEnDescanso: number;
  excedeHoras: number;
  problemas: number;
  pendientes: Array<{
    asignacionId: number;
    grupo: string;
    materia: string;
    maestro: string;
    colocadas: number;
    contratadas: number;
    faltan: number;
  }>;
  /** Desglose de las adyacencias: qué maestro las tiene, cuántas y en qué grupos. */
  adyacenciasPorMaestro: Array<{
    maestroId: number;
    maestro: string;
    /** Apodo con el que se le conoce; vacío si no tiene. */
    apodo: string;
    pares: number;
    grupos: string;
  }>;
  mensaje: string;
}

export const horarioService = {
  validar: (semestreId: number, turnoId?: number) => {
    const params = new URLSearchParams();
    params.set('semestreId', String(semestreId));
    if (turnoId) params.set('turnoId', String(turnoId));
    return api.get<ResultadoValidacion>(`/horarios/validar?${params.toString()}`);
  },

  // 🔥 obtenerPorGrupo con semestreId
  obtenerPorGrupo: (grupoId: number, semestreId?: number) => {
    const params = new URLSearchParams();
    if (semestreId) params.set('semestreId', String(semestreId));
    return api.get<Horario[]>(
      `/horarios/grupo/${grupoId}${params.toString() ? `?${params.toString()}` : ''}`
    );
  },

  // 🔥 obtenerPorMaestro con semestreId
  obtenerPorMaestro: (maestroId: number, semestreId?: number) => {
    const params = new URLSearchParams();
    if (semestreId) params.set('semestreId', String(semestreId));
    return api.get<Horario[]>(
      `/horarios/maestro/${maestroId}${params.toString() ? `?${params.toString()}` : ''}`
    );
  },

  // 🔥 obtenerPorAula con semestreId
  obtenerPorAula: (aulaId: number, semestreId?: number) => {
    const params = new URLSearchParams();
    if (semestreId) params.set('semestreId', String(semestreId));
    return api.get<Horario[]>(
      `/horarios/aula/${aulaId}${params.toString() ? `?${params.toString()}` : ''}`
    );
  },

  // 🔥 obtenerTodos con semestreId
  obtenerTodos: (semestreId?: number) => {
    const params = new URLSearchParams();
    if (semestreId) params.set('semestreId', String(semestreId));
    return api.get<Horario[]>(
      `/horarios/todos${params.toString() ? `?${params.toString()}` : ''}`
    );
  },

  analizarCuellos: (semestreId: number, turnoId?: number) => {
    const params = new URLSearchParams();
    params.set('semestreId', String(semestreId));
    if (turnoId) params.set('turnoId', String(turnoId));
    return api.get<AnalisisCuelloBotella>(`/horarios/analisis-cuellos?${params.toString()}`);
  },

  /** Estadísticas del horario de un turno (el recuadro del tablero manual). */
  estadisticas: (semestreId: number, turnoId: number) => {
    const params = new URLSearchParams();
    params.set('semestreId', String(semestreId));
    params.set('turnoId', String(turnoId));
    return api.get<EstadisticasHorario>(`/horarios/estadisticas?${params.toString()}`);
  },

  /**
   * Aplica (o solo valida) una tanda de cambios del tablero manual de pines.
   *
   * El backend la valida como conjunto: si algun cambio choca (grupo, maestro, aula,
   * disponibilidad, bloque de otro turno o horas de mas) no aplica ninguno y devuelve la
   * lista de problemas en `errores`. Con validarSolo no escribe nada en la base.
   */
  aplicarCambiosManuales: (solicitud: {
    semestreId: number;
    /** Turno que se está editando: sirve para devolver las estadísticas en la misma respuesta. */
    turnoId?: number;
    validarSolo?: boolean;
    cambios: Array<{
      tipo: 'COLOCAR' | 'MOVER' | 'QUITAR';
      asignacionId?: number;
      horarioId?: number;
      turnoHorarioId?: number;
    }>;
  }) =>
    api.post<{
      aplicado: boolean;
      soloValidacion?: boolean;
      colocados?: number;
      movidos?: number;
      quitados?: number;
      mensaje?: string;
      errores?: string[];
      /** Estadísticas del horario ya con los cambios aplicados (para el recuadro). */
      estadisticas?: EstadisticasHorario;
    }>('/horarios/manual', solicitud),
};
