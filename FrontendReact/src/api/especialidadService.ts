import api from './axiosConfig';
import type { Especialidad, EspecialidadForm } from '../types';

export const especialidadService = {
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
    if (turnoId) params.set('turnoId', String(turnoId));
    return api.get<{ content: Especialidad[]; totalElements: number }>(
      `/especialidades?${params.toString()}`
    );
  },

  obtener: (id: number) =>
    api.get<EspecialidadForm>(`/especialidades/${id}`),

  crear: (data: EspecialidadForm) =>
    api.post<Especialidad>('/especialidades', data),

  /**
   * Trae al semestre de destino las especialidades de OTRO SEMESTRE, pero SOLO las del turno
   * seleccionado: se copian las del turno del origen que se llama igual. Como cada turno lo trabaja
   * gente distinta, traer el semestre entero haria aparecer datos que nadie pidio.
   *
   * SOLO las especialidades. Las que ya existan en ese turno NO se tocan: vuelven en `omitidos` con
   * el motivo.
   */
  importar: (semestreOrigenId: number, semestreDestinoId: number, turnoId: number) => {
    return api.post<{
      especialidadesCopiadas: number;
      omitidos: string[];
      mensaje: string;
    }>('/especialidades/importar', { semestreOrigenId, semestreDestinoId, turnoId });
  },

  actualizar: (id: number, data: EspecialidadForm) =>
    api.put<Especialidad>(`/especialidades/${id}`, data),

  eliminar: (id: number) =>
    api.delete(`/especialidades/${id}`),
};