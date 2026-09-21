import api from './axiosConfig';
import type { Escuela, EscuelaForm } from '../types';

export const escuelaService = {
  listar: (page = 0, size = 10, busqueda = '') =>
    api.get<{ content: Escuela[]; totalElements: number }>(
      `/escuelas?page=${page}&size=${size}&busqueda=${busqueda}`
    ),

  obtener: (id: number) =>
    api.get<EscuelaForm>(`/escuelas/${id}`),

  crear: (data: EscuelaForm) => {
    const formData = construirFormData(data);
    return api.post<Escuela>('/escuelas', formData);
  },

  actualizar: (id: number, data: EscuelaForm) => {
    const formData = construirFormData(data);
    return api.put<Escuela>(`/escuelas/${id}`, formData);
  },

  cambiarEstado: (id: number, activo: boolean) =>
    api.patch(`/escuelas/${id}/estado?activo=${activo}`),

  eliminar: (id: number) =>
    api.delete(`/escuelas/${id}`),
};

/**
 * Construye el FormData con la estructura que espera el backend:
 *  - "datos": Blob JSON con el DTO.
 *  - "logoArchivo": Blob binario opcional.
 */
function construirFormData(data: EscuelaForm): FormData {
  const fd = new FormData();

  const { logoArchivo, ...datos } = data;
  const jsonBlob = new Blob([JSON.stringify(datos)], { type: 'application/json' });

  // El filename fuerza al navegador a preservar el Content-Type del Blob.
  fd.append('datos', jsonBlob, 'datos.json');

  if (logoArchivo instanceof File) {
    fd.append('logoArchivo', logoArchivo);
  }

  return fd;
}