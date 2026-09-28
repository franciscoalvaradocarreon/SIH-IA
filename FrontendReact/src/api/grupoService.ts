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

    /**
     * Trae al semestre de destino los grupos de OTRO SEMESTRE, pero SOLO los del turno seleccionado:
     * se copian los del turno del origen que se llama igual. Como cada turno lo trabaja gente
     * distinta, traer el semestre entero haria aparecer datos que nadie pidio.
     *
     * SOLO los grupos (asignaciones, disponibilidad y horarios no se tocan). La especialidad de cada
     * grupo se busca por nombre DENTRO de ese turno. Los que ya existan, o cuya especialidad no
     * exista en el turno del destino, NO se tocan: vuelven en `omitidos` con el motivo.
     */
    importar: (semestreOrigenId: number, semestreDestinoId: number, turnoId: number) => {
        return api.post<{
            gruposCopiados: number;
            omitidos: string[];
            mensaje: string;
        }>('/grupos/importar', { semestreOrigenId, semestreDestinoId, turnoId });
    },

    eliminar: (id: number) => 
        api.delete(`/grupos/${id}`),
};