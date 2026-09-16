import api from './axiosConfig';
import type { Rol, RolForm } from '../types';

export const rolService = {
  listar: (page = 0, size = 10, busqueda = '') =>
    api.get<{ content: Rol[]; totalElements: number }>(
      `/roles?page=${page}&size=${size}&busqueda=${busqueda}`
    ),

  obtener: (id: number) =>
    api.get<RolForm>(`/roles/${id}`),

  crear: (data: RolForm) =>
    api.post<Rol>('/roles', data),

  actualizar: (id: number, data: RolForm) =>
    api.put<Rol>(`/roles/${id}`, data),

  eliminar: (id: number) =>
    api.delete(`/roles/${id}`)
};