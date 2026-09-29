import api from './axiosConfig';
import type { TurnoHorario, TurnoHorarioCrear } from '../types';

export const turnoHorarioService = {
    /**
     * Listar horarios de un turno (con semestre opcional)
     */
    listar: (turnoId: number, semestreId?: number) => {
        const params = new URLSearchParams();
        if (semestreId) {
            params.set('semestreId', String(semestreId));
        }
        const url = `/turnos/${turnoId}/horarios${params.toString() ? `?${params.toString()}` : ''}`;
        return api.get<TurnoHorario[]>(url);
    },

    /**
     * Listar horarios de un turno por día (con semestre opcional)
     */
    listarPorDia: (turnoId: number, diaSemana: number, semestreId?: number) => {
        const params = new URLSearchParams();
        if (semestreId) {
            params.set('semestreId', String(semestreId));
        }
        const url = `/turnos/${turnoId}/horarios/dia/${diaSemana}${params.toString() ? `?${params.toString()}` : ''}`;
        return api.get<TurnoHorario[]>(url);
    },

    /**
     * Crear un nuevo bloque horario (con semestreId en el body)
     */
    crear: (turnoId: number, data: TurnoHorarioCrear) => {
        return api.post<TurnoHorario>(`/turnos/${turnoId}/horarios`, data);
    },

    /**
     * Trae los bloques de ESTE turno desde otro semestre.
     *
     * Se busca el turno que se llama igual en el semestre de origen y se copian sus bloques (días,
     * horas y descansos). Los que choquen con uno que ya exista aquí NO se tocan: vuelven en
     * `omitidos` con el motivo.
     */
    importar: (turnoId: number, semestreOrigenId: number, semestreDestinoId: number) => {
        return api.post<{
            bloquesCopiados: number;
            omitidos: string[];
            mensaje: string;
        }>(`/turnos/${turnoId}/horarios/importar`, { semestreOrigenId, semestreDestinoId });
    },

    /**
     * Actualizar un bloque horario
     */
    actualizar: (turnoId: number, id: number, data: TurnoHorarioCrear) =>
        api.put<TurnoHorario>(`/turnos/${turnoId}/horarios/${id}`, data),

    /**
     * Eliminar un bloque horario
     */
    eliminar: (turnoId: number, id: number) =>
        api.delete(`/turnos/${turnoId}/horarios/${id}`)
};