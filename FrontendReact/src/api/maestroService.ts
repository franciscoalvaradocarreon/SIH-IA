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

  /**
   * Trae al semestre de destino los maestros de OTRO SEMESTRE, pero SOLO los del turno
   * seleccionado: se copian los del turno del origen que se llama igual. Como cada turno lo trabaja
   * gente distinta, traer el semestre entero haria aparecer datos que nadie pidio.
   *
   * SOLO los maestros (las fotos se copian en el servidor). Los que ya existan en ese turno NO se
   * tocan: vuelven en `omitidos` con el motivo.
   */
  importar: (semestreOrigenId: number, semestreDestinoId: number, turnoId: number) => {
    return api.post<{
      maestrosCopiados: number;
      omitidos: string[];
      mensaje: string;
    }>('/maestros/importar', { semestreOrigenId, semestreDestinoId, turnoId });
  },

  eliminar: (id: number) =>
    api.delete(`/maestros/${id}`)
};