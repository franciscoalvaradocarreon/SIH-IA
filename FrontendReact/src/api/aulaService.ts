import api from './axiosConfig';
import type { Aula, AulaForm } from '../types';

export const aulaService = {
    listar: (
        page: number = 0,
        size: number = 20,
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
        return api.get<{ content: Aula[]; totalElements: number }>(
            `/aulas?${params.toString()}`
        );
    },

    obtener: (id: number) =>
        api.get<AulaForm>(`/aulas/${id}`),

    crear: (data: AulaForm) =>
        api.post<Aula>('/aulas', data),

    actualizar: (id: number, data: AulaForm) =>
        api.put<Aula>(`/aulas/${id}`, data),

    cambiarEstado: (id: number, activo: boolean) =>
        api.patch(`/aulas/${id}/estado?activo=${activo}`),

    eliminar: (id: number) =>
        api.delete(`/aulas/${id}`)
};