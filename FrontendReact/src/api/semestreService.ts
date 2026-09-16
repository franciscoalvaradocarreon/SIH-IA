// src/api/semestreService.ts
import api from './axiosConfig';  // 🔥 Cambiar a 'axiosConfig'
import type { Semestre, SemestreForm } from '../types';

export const semestreService = {
  /**
   * Listar semestres con paginación
   */
  listar: (page: number = 0, size: number = 10, busqueda?: string) => {
    const params = new URLSearchParams();
    params.set('page', String(page));
    params.set('size', String(size));
    if (busqueda) params.set('busqueda', busqueda);
    return api.get<{ content: any[]; totalElements: number }>(
      `/semestres?${params.toString()}`
    ).then(response => {
      // 🔥 Mapear semestreId -> id
      const contentMapeado = (response.data.content || []).map((item: any) => ({
        id: item.semestreId || item.id,
        nombre: item.nombre || '',
        descripcion: item.descripcion || '',
        activo: item.activo !== undefined ? item.activo : true,
        creado: item.creado,
      }));
      
      return {
        ...response,
        data: {
          content: contentMapeado,
          totalElements: response.data.totalElements || 0,
        }
      };
    });
  },

  /**
   * Listar todos los semestres (sin paginación)
   */
  listarTodos: () => {
    return api.get<any[]>('/semestres/todos').then(response => {
      const dataMapeada = (response.data || []).map((item: any) => ({
        id: item.semestreId || item.id,
        nombre: item.nombre || '',
        descripcion: item.descripcion || '',
        activo: item.activo !== undefined ? item.activo : true,
        creado: item.creado,
      }));
      return {
        ...response,
        data: dataMapeada
      };
    });
  },

  /**
   * Listar semestres activos
   */
  listarActivos: () => {
    return api.get<any[]>('/semestres/activos').then(response => {
      const dataMapeada = (response.data || []).map((item: any) => ({
        id: item.semestreId || item.id,
        nombre: item.nombre || '',
        descripcion: item.descripcion || '',
        activo: item.activo !== undefined ? item.activo : true,
        creado: item.creado,
      }));
      return {
        ...response,
        data: dataMapeada
      };
    });
  },

  /**
   * Obtener semestre actual
   */
  obtenerActual: () => {
    return api.get<any>('/semestres/actual').then(response => {
      const item = response.data;
      return {
        ...response,
        data: {
          id: item.semestreId || item.id,
          nombre: item.nombre || '',
          descripcion: item.descripcion || '',
          activo: item.activo !== undefined ? item.activo : true,
          creado: item.creado,
        }
      };
    });
  },

  /**
   * Obtener semestre por ID
   */
  obtener: (id: number) => {
    return api.get<any>(`/semestres/${id}`).then(response => {
      const item = response.data;
      return {
        ...response,
        data: {
          id: item.semestreId || item.id,
          nombre: item.nombre || '',
          descripcion: item.descripcion || '',
          activo: item.activo !== undefined ? item.activo : true,
          creado: item.creado,
        }
      };
    });
  },

  /**
   * Crear semestre
   */
  crear: (data: SemestreForm) => {
    return api.post<Semestre>('/semestres', data);
  },

  /**
   * Actualizar semestre
   */
  actualizar: (id: number, data: SemestreForm) => {
    return api.put<Semestre>(`/semestres/${id}`, data);
  },

  /**
   * Cambiar estado
   */
  cambiarEstado: (id: number, activo: boolean) => {
    return api.patch<Semestre>(`/semestres/${id}/estado?activo=${activo}`);
  },

  /**
   * Eliminar semestre
   */
  eliminar: (id: number) => {
    return api.delete(`/semestres/${id}`);
  }
};