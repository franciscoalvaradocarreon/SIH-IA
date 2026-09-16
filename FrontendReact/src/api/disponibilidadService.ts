// src/api/disponibilidadService.ts
import api from './axiosConfig';
import type { DisponibilidadMaestro, DisponibilidadMaestroCrear } from '../types';

export const disponibilidadService = {
  // Listar disponibilidades con paginación
  listar: (page = 0, size = 10, busqueda = '', semestreId?: number) => {
    const params = new URLSearchParams();
    params.set('page', String(page));
    params.set('size', String(size));
    if (busqueda) params.set('busqueda', busqueda);
    if (semestreId) params.set('semestreId', String(semestreId));
    return api.get<{ content: DisponibilidadMaestro[]; totalElements: number }>(
      `/disponibilidad-maestro?${params.toString()}`
    );
  },

  // Obtener disponibilidad por ID
  obtener: (id: number) =>
    api.get<DisponibilidadMaestro>(`/disponibilidad-maestro/${id}`),

  // Obtener disponibilidades por maestro
  obtenerPorMaestro: (maestroId: number) =>
    api.get<DisponibilidadMaestro[]>(`/disponibilidad-maestro/maestro/${maestroId}`),

  // Crear o actualizar disponibilidad
  guardar: (data: DisponibilidadMaestroCrear) =>
    api.post<DisponibilidadMaestro>('/disponibilidad-maestro', data),

  // Eliminar disponibilidad
  eliminar: (id: number) =>
    api.delete(`/disponibilidad-maestro/${id}`),

  // Eliminar todas las disponibilidades de un maestro
  eliminarPorMaestro: (maestroId: number) =>
    api.delete(`/disponibilidad-maestro/maestro/${maestroId}`),

  contarDisponibles: (grupoId: number, semestreId?: number) => {
    const params = new URLSearchParams();
    if (semestreId) params.set('semestreId', String(semestreId));
    return api.get<number>(
      `/disponibilidad-grupo/grupo/${grupoId}/count${params.toString() ? `?${params.toString()}` : ''}`
    );
  },
};