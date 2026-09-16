import api from './axiosConfig';
import type { Usuario, UsuarioDetalle, UsuarioForm } from '../types';

export const usuarioService = {
  listar: (page = 0, size = 10, busqueda = '') =>
    api.get<{ content: Usuario[]; totalElements: number }>(
      `/usuarios?page=${page}&size=${size}&busqueda=${busqueda}`
    ),

  obtener: (id: number) =>
    api.get<UsuarioDetalle>(`/usuarios/${id}`),

  crear: (data: UsuarioForm) =>
    api.post<Usuario>('/usuarios', data),

  actualizar: (id: number, data: UsuarioForm) =>
    api.put<Usuario>(`/usuarios/${id}`, data),

  cambiarEstado: (id: number, activo: boolean) =>
    api.patch(`/usuarios/${id}/estado?activo=${activo}`),

  eliminar: (id: number) =>
    api.delete(`/usuarios/${id}`)
};