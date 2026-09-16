import api from './axiosConfig';
import type { Materia, MateriaForm } from '../types';

export const materiaService = {
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
    return api.get<{ content: Materia[]; totalElements: number }>(
      `/materias?${params.toString()}`
    );
  },

  // Obtener una materia por ID
  obtener: (id: number) =>
    api.get<MateriaForm>(`/materias/${id}`),

  // Crear nueva materia
  crear: (data: MateriaForm) => {
    // 🔥 Verificar que semestreId no sea undefined o null
    if (data.semestreId === undefined || data.semestreId === null) {
      console.error('❌ semestreId es undefined o null!');
    }  
    return api.post<Materia>('/materias', data);
  },

  // Actualizar materia existente
  actualizar: (id: number, data: MateriaForm) =>
    api.put<Materia>(`/materias/${id}`, data),

  // Cambiar estado (activar/desactivar)
  cambiarEstado: (id: number, activo: boolean) =>
    api.patch(`/materias/${id}/estado?activo=${activo}`),

  // Eliminar (baja lógica)
  eliminar: (id: number) =>
    api.delete(`/materias/${id}`)
};