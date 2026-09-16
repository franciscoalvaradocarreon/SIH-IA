import api from './axiosConfig';
import type { Asignacion, AsignacionForm } from '../types';

export const asignacionService = {
  listar: (
    page = 0,
    size = 10,
    busqueda = '',
    grupoId = 0,
    especialidadId = 0,
    semestreId?: number,
    turnoId?: number
  ) => {
    const params = new URLSearchParams();
    params.set('page', String(page));
    params.set('size', String(size));
    if (busqueda) params.set('busqueda', busqueda);
    if (grupoId) params.set('grupoId', String(grupoId));
    if (especialidadId) params.set('especialidadId', String(especialidadId));
    if (semestreId) params.set('semestreId', String(semestreId));
    if (turnoId) params.set('turnoId', String(turnoId));
    return api.get<{ content: Asignacion[]; totalElements: number }>(
      `/asignaciones?${params.toString()}`
    );
  },

  obtener: (id: number) =>
    api.get<AsignacionForm>(`/asignaciones/${id}`),

  crear: (data: AsignacionForm) =>
    api.post<Asignacion>('/asignaciones', data),

  actualizar: (id: number, data: AsignacionForm) =>
    api.put<Asignacion>(`/asignaciones/${id}`, data),

  cambiarEstado: (id: number, activo: boolean) =>
    api.patch(`/asignaciones/${id}/estado?activo=${activo}`),

  eliminar: (id: number) =>
    api.delete(`/asignaciones/${id}`)
};