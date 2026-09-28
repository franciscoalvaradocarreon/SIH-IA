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

    /**
     * Trae al semestre de destino las aulas de OTRO SEMESTRE, pero SOLO las del turno seleccionado:
     * se copian las del turno del origen que se llama igual. Como cada turno lo trabaja gente
     * distinta, traer el semestre entero haria aparecer datos que nadie pidio.
     *
     * SOLO las aulas. Las que ya existan (mismo nombre en ese turno) NO se tocan: vuelven en
     * `omitidos`.
     */
    importar: (semestreOrigenId: number, semestreDestinoId: number, turnoId: number) => {
        return api.post<{
            aulasCopiadas: number;
            omitidos: string[];
            mensaje: string;
        }>('/aulas/importar', { semestreOrigenId, semestreDestinoId, turnoId });
    },

    eliminar: (id: number) =>
        api.delete(`/aulas/${id}`)
};