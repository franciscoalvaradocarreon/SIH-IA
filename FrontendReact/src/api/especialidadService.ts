import api from './axiosConfig';
import type { Especialidad, EspecialidadForm } from '../types';

export const especialidadService = {
  listar: (
    page: number = 0,
    size: number = 10,
    busqueda?: string,
    semestreId?: number,
    turnoId?: number
  ) => {
    const params = new URLSearchParams();
    params.set('page', String(page));
    params.set('size', String(size));
    if (busqueda) params.set('busqueda', busqueda);
    if (semestreId) params.set('semestreId', String(semestreId));
    if (turnoId) params.set('turnoId', String(turnoId));
    return api.get<{ content: Especialidad[]; totalElements: number }>(
      `/especialidades?${params.toString()}`
    );
  },

  obtener: (id: number) =>
    api.get<EspecialidadForm>(`/especialidades/${id}`),

  crear: (data: EspecialidadForm) =>
    api.post<Especialidad>('/especialidades', data),

  actualizar: (id: number, data: EspecialidadForm) =>
    api.put<Especialidad>(`/especialidades/${id}`, data),

  eliminar: (id: number) =>
    api.delete(`/especialidades/${id}`),
};