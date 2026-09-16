import api from './axiosConfig';
import type { Horario, HorarioSolucion, HorarioSolucionMasiva } from '../types';

export const horarioService = {
  generar: (grupoId: number, semestreId?: number) => {
    const params = new URLSearchParams();
    if (semestreId) params.set('semestreId', String(semestreId));
    return api.post<HorarioSolucion>(
      `/horarios/generar/${grupoId}${params.toString() ? `?${params.toString()}` : ''}`
    );
  },
  generarTodos: (semestreId: number, turnoId?: number) => {
    const params = new URLSearchParams();
    params.set('semestreId', String(semestreId));
    if (turnoId) params.set('turnoId', String(turnoId));
    return api.post<HorarioSolucionMasiva>(
      `/horarios/generar-todos?${params.toString()}`
    );
  },

  validar: (semestreId: number, turnoId?: number) => {
    const params = new URLSearchParams();
    params.set('semestreId', String(semestreId));
    if (turnoId) params.set('turnoId', String(turnoId));
    return api.get<string[]>(`/horarios/validar?${params.toString()}`);
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
};