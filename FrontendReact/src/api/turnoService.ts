import api from './axiosConfig';
import type { Turno, TurnoForm } from '../types';

export const turnoService = {

  listar: (page: number = 0, size: number = 10, busqueda?: string, semestreId?: number) => {
    const params = new URLSearchParams();
    params.set('page', String(page));
    params.set('size', String(size));
    if (busqueda) params.set('busqueda', busqueda);
    if (semestreId) {
      params.set('semestreId', String(semestreId)); 
    }
    return api.get<{ content: Turno[]; totalElements: number }>(
      `/turnos?${params.toString()}`
    );
  },

  obtener: (id: number) => {
    return api.get<Turno>(`/turnos/${id}`);
  },
 
  crear: (data: TurnoForm) => {
    return api.post<Turno>('/turnos', data);
  },
 
  actualizar: (id: number, data: TurnoForm) => {
    return api.put<Turno>(`/turnos/${id}`, data);
  },
 
  cambiarEstado: (id: number, activo: boolean) => {
    return api.patch<Turno>(`/turnos/${id}/estado?activo=${activo}`);
  },
 
  eliminar: (id: number) => {
    return api.delete(`/turnos/${id}`);
  }

};