import api from './axiosConfig';
import type { TipoEspacio, TipoEspacioForm } from '../types';

/**
 * Catalogo de laboratorios y talleres de un TURNO.
 *
 * Es por escuela, semestre y turno, como aulas, grupos o materias: cada turno tiene su propia
 * lista y puede tener tipos distintos al otro turno del mismo semestre. Por eso todas las
 * llamadas llevan `turnoId`.
 */
export const tiposEspacioService = {
  /** Lista los tipos del turno. `soloActivos=false` incluye los dados de baja. */
  listar: (turnoId: number, soloActivos: boolean = true) => {
    const params = new URLSearchParams();
    params.set('turnoId', String(turnoId));
    params.set('soloActivos', String(soloActivos));
    return api.get<TipoEspacio[]>(`/tipos-espacio?${params.toString()}`);
  },

  obtener: (id: number, turnoId: number) =>
    api.get<TipoEspacio>(`/tipos-espacio/${id}?turnoId=${turnoId}`),

  crear: (data: TipoEspacioForm) =>
    api.post<TipoEspacio>('/tipos-espacio', data),

  actualizar: (id: number, data: TipoEspacioForm) =>
    api.put<TipoEspacio>(`/tipos-espacio/${id}`, data),

  /** Baja logica: deja de ofrecerse, pero las aulas que lo usan no se tocan. */
  desactivar: (id: number, turnoId: number) =>
    api.delete<TipoEspacio>(`/tipos-espacio/${id}?turnoId=${turnoId}`),
};
