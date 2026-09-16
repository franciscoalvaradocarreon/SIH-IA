import api from './axiosConfig';
import type { Maestro, MaestroForm } from '../types';

  export const maestroService = {
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
      if (turnoId) params.set('turnoId', String(turnoId));   // 🔥 NUEVO
      return api.get<{ content: Maestro[]; totalElements: number }>(
        `/maestros?${params.toString()}`
      );
  },

  obtener: (id: number) =>
    api.get<MaestroForm>(`/maestros/${id}`),

  // Cambiar a FormData para subir archivos
  crear: (data: FormData) =>
    api.post<Maestro>('/maestros', data, {
      headers: { 'Content-Type': 'multipart/form-data' }
    }),

  actualizar: (id: number, data: FormData) =>
    api.put<Maestro>(`/maestros/${id}`, data, {
      headers: { 'Content-Type': 'multipart/form-data' }
    }),

  cambiarEstado: (id: number, activo: boolean) =>
    api.patch(`/maestros/${id}/estado?activo=${activo}`),

  eliminar: (id: number) =>
    api.delete(`/maestros/${id}`)
};