import api from './axiosConfig';
import type { DisponibilidadGrupo, DisponibilidadGrupoCrear } from '../types';

export const disponibilidadGrupoService = {
  /**
   * Listar disponibilidad de un grupo (con semestre opcional)
   */
  obtenerPorGrupo: (grupoId: number, semestreId?: number) => {
    const params = new URLSearchParams();
    if (semestreId) params.set('semestreId', String(semestreId));
    return api.get<DisponibilidadGrupo[]>(
      `/disponibilidad-grupo/grupo/${grupoId}${params.toString() ? `?${params.toString()}` : ''}`
    );
  },

  obtener: (id: number) =>
    api.get<DisponibilidadGrupo>(`/disponibilidad-grupo/${id}`),

  guardar: (data: DisponibilidadGrupoCrear) =>
    api.post<DisponibilidadGrupo>('/disponibilidad-grupo', data),

  eliminar: (id: number) =>
    api.delete(`/disponibilidad-grupo/${id}`),

  eliminarPorGrupo: (grupoId: number, semestreId: number) =>
    api.delete(`/disponibilidad-grupo/grupo/${grupoId}?semestreId=${semestreId}`),

  contarDisponibles: (grupoId: number, semestreId?: number) => {
    const params = new URLSearchParams();
    if (semestreId) params.set('semestreId', String(semestreId));
    return api.get<number>(
        `/disponibilidad-grupo/grupo/${grupoId}/count${params.toString() ? `?${params.toString()}` : ''}`
    );
},

};