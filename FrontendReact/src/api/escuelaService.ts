import api from './axiosConfig';
import type { Escuela, EscuelaForm } from '../types';

export const escuelaService = {
  listar: (page = 0, size = 10, busqueda = '') =>
    api.get<{ content: Escuela[]; totalElements: number }>(
      `/escuelas?page=${page}&size=${size}&busqueda=${busqueda}`
    ),

  obtener: (id: number) =>
    api.get<EscuelaForm>(`/escuelas/${id}`),

  crear: (data: EscuelaForm) =>
    api.post<Escuela>('/escuelas', data),

  actualizar: (id: number, data: EscuelaForm) =>
    api.put<Escuela>(`/escuelas/${id}`, data),

  cambiarEstado: (id: number, activo: boolean) =>
    api.patch(`/escuelas/${id}/estado?activo=${activo}`),

  eliminar: (id: number) =>
    api.delete(`/escuelas/${id}`)
};