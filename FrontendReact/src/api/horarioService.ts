import api from './axiosConfig';
import type { AnalisisCuelloBotella, Horario, HorarioSolucion, ResultadoValidacion, TrabajoGeneracion } from '../types';

export const horarioService = {
  generar: (grupoId: number, semestreId?: number) => {
    const params = new URLSearchParams();
    if (semestreId) params.set('semestreId', String(semestreId));
    return api.post<HorarioSolucion>(
      `/horarios/generar/${grupoId}${params.toString() ? `?${params.toString()}` : ''}`
    );
  },

  /**
   * Encola la generación masiva de horarios.
   *
   * El backend responde 202 con un trabajo en estado EN_COLA: el solver tarda hasta
   * 300 s y antes esta petición se quedaba bloqueada todo ese tiempo (riesgo de
   * timeout en navegador, proxy o balanceador). Hay que consultar el avance con
   * consultarGeneracion() hasta que el estado sea COMPLETADO o ERROR.
   */
  generarTodos: (semestreId: number, turnoId?: number) => {
    const params = new URLSearchParams();
    params.set('semestreId', String(semestreId));
    if (turnoId) params.set('turnoId', String(turnoId));
    return api.post<TrabajoGeneracion>(`/horarios/generar-todos?${params.toString()}`);
  },

  /**
   * Estado del trabajo de generación.
   * Cuando estado = 'COMPLETADO' incluye el resultado completo en `resultado`.
   */
  consultarGeneracion: (trabajoId: string) =>
    api.get<TrabajoGeneracion>(`/horarios/generar-todos/${encodeURIComponent(trabajoId)}`),

  /**
   * Termina la generación en curso conservando la MEJOR solución ya encontrada.
   * El backend deja de lanzar intentos, guarda esa solución y el trabajo pasa a COMPLETADO.
   */
  terminarGeneracion: (trabajoId: string) =>
    api.post<TrabajoGeneracion>(
      `/horarios/generar-todos/${encodeURIComponent(trabajoId)}/terminar`
    ),

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

  /**
   * Aplica (o solo valida) una tanda de cambios del tablero manual de pines.
   *
   * El backend la valida como conjunto: si algun cambio choca (grupo, maestro, aula,
   * disponibilidad, bloque de otro turno o horas de mas) no aplica ninguno y devuelve la
   * lista de problemas en `errores`. Con validarSolo no escribe nada en la base.
   */
  aplicarCambiosManuales: (solicitud: {
    semestreId: number;
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
    }>('/horarios/manual', solicitud),
};
