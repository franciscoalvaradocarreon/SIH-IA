import api from './axiosConfig';
import type { MenuLista, MenuCrear } from '../types';

export const menuCrudService = {
    listar: (page = 0, size = 15, busqueda = '') =>
        api.get<{ content: MenuLista[]; totalElements: number }>(
            `/admin/menu?page=${page}&size=${size}&busqueda=${busqueda}`
        ),

    obtener: (id: number) =>
        api.get<MenuCrear>(`/admin/menu/${id}`),

    crear: (data: MenuCrear) =>
        api.post<MenuLista>('/admin/menu', data),

    actualizar: (id: number, data: MenuCrear) =>
        api.put<MenuLista>(`/admin/menu/${id}`, data),

    cambiarEstado: (id: number, activo: boolean) =>
        api.patch(`/admin/menu/${id}/estado?activo=${activo}`),

    eliminar: (id: number) =>
        api.delete(`/admin/menu/${id}`)
};