import api from './axiosConfig';
import type { RolMenu, RolMenuCrear, RolMenuAsignacion } from '../types';

export const rolMenuService = {
    listar: (page = 0, size = 10) =>
        api.get<{ content: RolMenu[]; totalElements: number }>(
            `/rol-menu?page=${page}&size=${size}`
        ),

    listarPorRol: (rolId: number) =>
        api.get<RolMenu[]>(`/rol-menu/rol/${rolId}`),

    asignar: (data: RolMenuCrear) =>
        api.post<RolMenu>('/rol-menu', data),

    asignarMultiples: (data: RolMenuAsignacion) =>
        api.post('/rol-menu/asignar-multiples', data),

    desasignar: (rolId: number, menuId: number) =>
        api.delete(`/rol-menu/${rolId}/${menuId}`),

    eliminarPorRol: (rolId: number) =>
        api.delete(`/rol-menu/rol/${rolId}`)
};