import api from './axiosConfig';
import type { Grupo, GrupoForm } from '../types';

export const grupoService = {
    listar: (
        page: number = 0,
        size: number = 10,
        busqueda?: string,
        especialidadId?: number,
        semestreId?: number,
        turnoId?: number
    ) => {
        const params = new URLSearchParams();
        params.set('page', String(page));
        params.set('size', String(size));
        if (busqueda) params.set('busqueda', busqueda);
        if (especialidadId) params.set('especialidadId', String(especialidadId));
        if (semestreId) params.set('semestreId', String(semestreId));
        if (turnoId) params.set('turnoId', String(turnoId));   // 🔥 NUEVO
        return api.get<{ content: Grupo[]; totalElements: number }>(
        `/grupos?${params.toString()}`
        );
    },
    
    obtener: (id: number) => 
        api.get<GrupoForm>(`/grupos/${id}`),

    crear: (grupo: GrupoForm) => 
        api.post('/grupos', grupo),

    actualizar: (id: number, grupo: GrupoForm) =>
        api.put(`/grupos/${id}`, grupo),

    cambiarEstado: (id: number, activo: boolean) =>
        api.patch(`/grupos/${id}/estado?activo=${activo}`),

    eliminar: (id: number) => 
        api.delete(`/grupos/${id}`),
};