import api from './axiosConfig';
import type { Usuario, UsuarioDetalle, UsuarioForm } from '../types';

export const usuarioService = {
  listar: (page = 0, size = 10, busqueda = '') =>
    api.get<{ content: Usuario[]; totalElements: number }>(
      `/usuarios?page=${page}&size=${size}&busqueda=${busqueda}`
    ),

  obtener: (id: number) =>
    api.get<UsuarioDetalle>(`/usuarios/${id}`),

  crear: (data: UsuarioForm) => {
    const formData = construirFormData(data);
    return api.post<Usuario>('/usuarios', formData);
  },

  actualizar: (id: number, data: UsuarioForm) => {
    const formData = construirFormData(data);
    return api.put<Usuario>(`/usuarios/${id}`, formData);
  },

  cambiarEstado: (id: number, activo: boolean) =>
    api.patch(`/usuarios/${id}/estado?activo=${activo}`),

  eliminar: (id: number) =>
    api.delete(`/usuarios/${id}`),
};

/**
 * Construye el FormData con la estructura que espera el backend:
 *  - "datos": Blob JSON con las propiedades del DTO.
 *  - "fotoArchivo": Blob binario opcional.
 */
function construirFormData(data: UsuarioForm): FormData {
  const fd = new FormData();

  const { fotoArchivo, ...datos } = data;
  const jsonBlob = new Blob([JSON.stringify(datos)], { type: 'application/json' });

  fd.append('datos', jsonBlob, 'datos.json');

  if (fotoArchivo instanceof File) {
    fd.append('fotoArchivo', fotoArchivo);
  }

  return fd;
}