// src/components/MenuCrud.tsx
import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { menuCrudService } from '../api/menuCrudService';
import type { MenuLista } from '../types';
import ErrorScreen from '../utils/ErrorScreen';
import {
    MdAdd, MdEdit, MdDelete, MdSearch, MdCheckCircle, MdCancel,
    MdMenu, MdRefresh, MdCheck, MdWarning
} from 'react-icons/md';
import { IconRenderer } from '../utils/iconos';


const MenuCrud: React.FC = () => {
    const [menus, setMenus] = useState<MenuLista[]>([]);
    const [total, setTotal] = useState(0);
    const [page, setPage] = useState(0);
    const [size] = useState(15);
    const [busqueda, setBusqueda] = useState('');
    const [loading, setLoading] = useState(true);
    const navigate = useNavigate();

    // 🔥 Estados para modal de confirmación y pantalla de error
    const [modalEliminar, setModalEliminar] = useState<{
        abierto: boolean;
        menu: MenuLista | null;
    }>({ abierto: false, menu: null });

    const [eliminando, setEliminando] = useState(false);

    const [errorPantalla, setErrorPantalla] = useState<{
        titulo?: string;
        mensaje: string;
        detalles?: string;
        sugerencia?: string;
        tipo?: 'error' | 'warning' | 'info';
        acciones?: {
            label: string;
            onClick: () => void;
            tipo?: 'primary' | 'secondary';
            icono?: React.ReactNode;
        }[];
    } | null>(null);

    useEffect(() => {
        cargarMenus();
    }, [page, busqueda]);

    const cargarMenus = async () => {
        setLoading(true);
        try {
            const res = await menuCrudService.listar(page, size, busqueda);
            setMenus(res.data.content);
            setTotal(res.data.totalElements);
        } catch (error) {
            console.error('Error al cargar menús:', error);
        } finally {
            setLoading(false);
        }
    };

    // 🔥 Abre el modal de confirmación
    const handleEliminar = (menu: MenuLista) => {
        setModalEliminar({ abierto: true, menu });
    };

    const confirmarEliminar = async () => {
        if (!modalEliminar.menu) return;

        setEliminando(true);
        try {
            await menuCrudService.eliminar(modalEliminar.menu.menuId);
            setModalEliminar({ abierto: false, menu: null });
            cargarMenus();
        } catch (error: any) {
            console.error('Error al eliminar:', error);
            const msg = error.response?.data?.message || 'Error al eliminar el menú';
            setModalEliminar({ abierto: false, menu: null });

            // 🔥 Detectar si es error de dependencias (tiene submenús)
            const esDependencia = msg.includes('submenús') ||
                                  msg.includes('submenus') ||
                                  msg.includes('hijos') ||
                                  msg.includes('dependencias') ||
                                  msg.includes('foreign key') ||
                                  msg.includes('viola la llave');

            if (esDependencia) {
                setErrorPantalla({
                    titulo: 'No se puede eliminar el menú',
                    mensaje: msg,
                    sugerencia: 'Elimina primero los submenús asociados o desactívalo en lugar de eliminarlo.',
                    tipo: 'error',
                    acciones: [
                        {
                            label: 'Aceptar',
                            onClick: () => {
                                setErrorPantalla(null);
                                cargarMenus();
                            },
                            tipo: 'primary',
                        },
                    ],
                });
            } else {
                setErrorPantalla({
                    titulo: 'Error al eliminar',
                    mensaje: msg,
                    tipo: 'error',
                    acciones: [
                        {
                            label: 'Aceptar',
                            onClick: () => {
                                setErrorPantalla(null);
                                cargarMenus();
                            },
                            tipo: 'primary',
                        },
                    ],
                });
            }
        } finally {
            setEliminando(false);
        }
    };

    const handleCambiarEstado = async (id: number, activo: boolean) => {
        try {
            await menuCrudService.cambiarEstado(id, activo);
            cargarMenus();
        } catch (error) {
            console.error('Error al cambiar estado:', error);
        }
    };

    const getNivelText = (nivel: number) => {
        const niveles = ['', 'Principal', 'Nivel 2', 'Nivel 3'];
        return niveles[nivel] || `Nivel ${nivel}`;
    };

    const getNivelColor = (nivel: number) => {
        if (nivel === 1) return 'bg-blue-100 dark:bg-blue-900/30 text-blue-700 dark:text-blue-300';
        if (nivel === 2) return 'bg-indigo-100 dark:bg-indigo-900/30 text-indigo-700 dark:text-indigo-300';
        if (nivel === 3) return 'bg-purple-100 dark:bg-purple-900/30 text-purple-700 dark:text-purple-300';
        return 'bg-gray-100 dark:bg-gray-700/30 text-gray-700 dark:text-gray-300';
    };

    // 🔥 Pantalla de error especial
    if (errorPantalla) {
        return (
            <ErrorScreen
                titulo={errorPantalla.titulo}
                mensaje={errorPantalla.mensaje}
                detalles={errorPantalla.detalles}
                sugerencia={errorPantalla.sugerencia}
                tipo={errorPantalla.tipo || 'error'}
                acciones={errorPantalla.acciones}
                mostrarVolver={false}
                mostrarInicio={false}
            />
        );
    }

    return (
        <div className="p-6">
            {/* Encabezado */}
            <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4 mb-6">
                <h1 className="text-3xl font-bold text-gray-800 dark:text-white">
                    Gestión de Menús
                </h1>
                <div className="flex gap-3">
                    <button
                        onClick={cargarMenus}
                        className="flex items-center gap-2 bg-gray-200 dark:bg-gray-700 hover:bg-gray-300 dark:hover:bg-gray-600 text-gray-700 dark:text-gray-300 px-4 py-2.5 rounded-lg shadow-md transition duration-200"
                        title="Recargar datos"
                    >
                        <MdRefresh className="text-xl" />
                        Recargar
                    </button>
                    <button
                        onClick={() => navigate('/administracion/menus/new')}
                        className="flex items-center gap-2 bg-blue-600 hover:bg-blue-700 text-white px-4 py-2.5 rounded-lg shadow-md transition duration-200"
                    >
                        <MdAdd className="text-xl" />
                        Nuevo Menú
                    </button>
                </div>
            </div>

            {/* Barra de búsqueda */}
            <div className="mb-6">
                <div className="relative max-w-md">
                    <MdSearch className="absolute left-3 top-1/2 transform -translate-y-1/2 text-gray-400 text-xl" />
                    <input
                        type="text"
                        placeholder="Buscar por etiqueta o ruta..."
                        value={busqueda}
                        onChange={(e) => setBusqueda(e.target.value)}
                        className="w-full pl-10 pr-4 py-2.5 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-800 text-gray-900 dark:text-white focus:ring-2 focus:ring-blue-500 focus:border-transparent transition"
                    />
                </div>
            </div>

            {loading ? (
                <div className="flex justify-center py-12">
                    <div className="animate-spin rounded-full h-12 w-12 border-4 border-blue-500 border-t-transparent"></div>
                </div>
            ) : (
                <>
                    <div className="bg-white dark:bg-gray-800 rounded-xl shadow-md overflow-hidden border border-gray-100 dark:border-gray-700">
                        <div className="overflow-x-auto">
                            <table className="min-w-full divide-y divide-gray-200 dark:divide-gray-700">
                                <thead className="bg-gray-50 dark:bg-gray-700/50">
                                    <tr>
                                        <th className="px-4 py-2.5 text-left text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider">
                                            <div className="flex items-center gap-2">
                                                <MdMenu className="text-sm" />
                                                Menú
                                            </div>
                                        </th>
                                        <th className="px-4 py-2.5 text-left text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider">
                                            Ruta
                                        </th>
                                        <th className="px-4 py-2.5 text-left text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider">
                                            Padre
                                        </th>
                                        <th className="px-4 py-2.5 text-left text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider">
                                            Nivel
                                        </th>
                                        <th className="px-4 py-2.5 text-center text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider">
                                            Orden
                                        </th>
                                        <th className="px-4 py-2.5 text-center text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider">
                                            Estado
                                        </th>
                                        <th className="px-4 py-2.5 text-center text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider">
                                            Acciones
                                        </th>
                                    </tr>
                                </thead>
                                <tbody className="bg-white dark:bg-gray-800 divide-y divide-gray-200 dark:divide-gray-700">
                                    {menus.length === 0 ? (
                                        <tr>
                                            <td colSpan={7} className="px-4 py-6 text-center text-gray-500 dark:text-gray-400">
                                                No hay menús disponibles
                                            </td>
                                        </tr>
                                    ) : (
                                        menus.map((menu) => (
                                            <tr key={menu.menuId} className="hover:bg-gray-50 dark:hover:bg-gray-700/50 transition">
                                                <td className="px-4 py-2 whitespace-nowrap">
                                                    <div className="flex items-center gap-2.5">
                                                        <div className={`w-7 h-7 rounded-full flex items-center justify-center text-white text-xs flex-shrink-0 ${
                                                            menu.nivel === 1 ? 'bg-blue-500' :
                                                            menu.nivel === 2 ? 'bg-indigo-500' :
                                                            'bg-purple-500'
                                                        }`}>
                                                            <MdMenu className="text-sm" />
                                                        </div>
                                                        <span className="text-sm font-medium text-gray-900 dark:text-white">
                                                            {menu.label}
                                                        </span>
                                                        {menu.icono ? (
                                                            <span className="text-blue-500 dark:text-blue-400 ml-1">
                                                                <IconRenderer name={menu.icono} className="w-4 h-4" />
                                                            </span>
                                                        ) : null}
                                                    </div>
                                                </td>
                                                <td className="px-4 py-2 whitespace-nowrap">
                                                    <span className="text-xs text-gray-500 dark:text-gray-400 font-mono bg-gray-100 dark:bg-gray-700 px-2 py-0.5 rounded">
                                                        {menu.path || '-'}
                                                    </span>
                                                </td>
                                                <td className="px-4 py-2 whitespace-nowrap">
                                                    <span className="text-sm text-gray-700 dark:text-gray-300">
                                                        {menu.parienteLabel || <span className="text-gray-400 text-xs">Raíz</span>}
                                                    </span>
                                                </td>
                                                <td className="px-4 py-2 whitespace-nowrap">
                                                    <span className={`text-xs px-2.5 py-0.5 rounded-full ${getNivelColor(menu.nivel)}`}>
                                                        {getNivelText(menu.nivel)}
                                                    </span>
                                                </td>
                                                <td className="px-4 py-2 whitespace-nowrap text-center">
                                                    <span className="text-sm font-medium text-gray-700 dark:text-gray-300">
                                                        {menu.menuOrden}
                                                    </span>
                                                </td>
                                                <td className="px-4 py-2 whitespace-nowrap text-center">
                                                    <span
                                                        className={`px-2.5 py-0.5 text-base font-medium rounded-full flex items-center justify-center gap-1 inline-flex ${
                                                            menu.activo
                                                                ? 'bg-green-100 dark:bg-green-900/30 text-green-800 dark:text-green-400'
                                                                : 'bg-red-100 dark:bg-red-900/30 text-red-800 dark:text-red-400'
                                                        }`}
                                                    >
                                                        {menu.activo ? (
                                                            <MdCheckCircle className="text-base" />
                                                        ) : (
                                                            <MdCancel className="text-base" />
                                                        )}
                                                        {menu.activo ? 'Activo' : 'Inactivo'}
                                                    </span>
                                                </td>
                                                <td className="px-4 py-2 whitespace-nowrap text-center">
                                                    <div className="flex items-center justify-center gap-1.5">
                                                        <button
                                                            onClick={() => navigate(`/administracion/menus/edit/${menu.menuId}`)}
                                                            className="text-blue-600 dark:text-blue-400 hover:text-blue-800 dark:hover:text-blue-300 transition p-1 rounded-lg hover:bg-blue-50 dark:hover:bg-blue-900/20"
                                                            title="Editar"
                                                        >
                                                            <MdEdit className="text-xl" />
                                                        </button>
                                                        <button
                                                            onClick={() => handleCambiarEstado(menu.menuId, !menu.activo)}
                                                            className={`transition p-1 rounded-lg ${
                                                                menu.activo
                                                                    ? 'text-yellow-600 dark:text-yellow-400 hover:bg-yellow-50 dark:hover:bg-yellow-900/20'
                                                                    : 'text-green-600 dark:text-green-400 hover:bg-green-50 dark:hover:bg-green-900/20'
                                                            }`}
                                                            title={menu.activo ? 'Desactivar' : 'Activar'}
                                                        >
                                                            {menu.activo ? <MdCancel className="text-xl" /> : <MdCheck className="text-xl" />}
                                                        </button>
                                                        <button
                                                            onClick={() => handleEliminar(menu)}
                                                            className="text-red-600 dark:text-red-400 hover:text-red-800 dark:hover:text-red-300 transition p-1 rounded-lg hover:bg-red-50 dark:hover:bg-red-900/20"
                                                            title="Eliminar"
                                                        >
                                                            <MdDelete className="text-xl" />
                                                        </button>
                                                    </div>
                                                </td>
                                            </tr>
                                        ))
                                    )}
                                </tbody>
                            </table>
                        </div>
                    </div>

                    {/* Paginación */}
                    <div className="flex flex-col sm:flex-row justify-between items-center gap-4 mt-8 bg-white dark:bg-gray-800 px-4 py-3 rounded-lg shadow-sm border border-gray-100 dark:border-gray-700">
                        <div className="text-sm text-gray-600 dark:text-gray-400">
                            Mostrando <span className="font-medium">{menus.length}</span> de{' '}
                            <span className="font-medium">{total}</span> menús
                        </div>
                        <div className="flex gap-2">
                            <button
                                onClick={() => setPage(Math.max(0, page - 1))}
                                disabled={page === 0}
                                className="px-4 py-2 border border-gray-300 dark:border-gray-600 rounded-lg text-sm font-medium text-gray-700 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700 disabled:opacity-50 disabled:cursor-not-allowed transition"
                            >
                                Anterior
                            </button>
                            <button
                                onClick={() => setPage(page + 1)}
                                disabled={menus.length < size}
                                className="px-4 py-2 border border-gray-300 dark:border-gray-600 rounded-lg text-sm font-medium text-gray-700 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700 disabled:opacity-50 disabled:cursor-not-allowed transition"
                            >
                                Siguiente
                            </button>
                        </div>
                    </div>
                </>
            )}

            {/* 🔥 Modal de confirmación de eliminación */}
            {modalEliminar.abierto && modalEliminar.menu && (
                <div className="fixed inset-0 z-50 flex items-start justify-center bg-black/50 backdrop-blur-sm p-4 pt-24">
                    <div className="bg-white dark:bg-gray-800 rounded-xl shadow-2xl max-w-md w-full p-6 border border-gray-200 dark:border-gray-700">
                        <div className="flex items-center gap-3 mb-4">
                            <div className="p-2 bg-red-100 dark:bg-red-900/40 rounded-lg text-red-600 dark:text-red-400">
                                <MdWarning className="text-2xl" />
                            </div>
                            <h3 className="text-lg font-bold text-gray-800 dark:text-white">
                                Confirmar eliminación
                            </h3>
                        </div>

                        <p className="text-sm text-gray-600 dark:text-gray-400 mb-4">
                            ¿Estás seguro de que quieres eliminar este menú?
                        </p>

                        <div className="bg-gray-50 dark:bg-gray-700/50 rounded-lg p-3 mb-4 text-sm">
                            <div className="flex justify-between">
                                <span className="text-gray-500 dark:text-gray-400">Etiqueta:</span>
                                <span className="font-medium text-gray-800 dark:text-white truncate max-w-[200px]">
                                    {modalEliminar.menu.label}
                                </span>
                            </div>
                            {modalEliminar.menu.path && (
                                <div className="flex justify-between mt-1">
                                    <span className="text-gray-500 dark:text-gray-400">Ruta:</span>
                                    <span className="font-mono text-xs text-gray-800 dark:text-white bg-gray-100 dark:bg-gray-700 px-2 py-0.5 rounded">
                                        {modalEliminar.menu.path}
                                    </span>
                                </div>
                            )}
                            <div className="flex justify-between mt-1">
                                <span className="text-gray-500 dark:text-gray-400">Nivel:</span>
                                <span className={`text-xs px-2 py-0.5 rounded-full ${getNivelColor(modalEliminar.menu.nivel)}`}>
                                    {getNivelText(modalEliminar.menu.nivel)}
                                </span>
                            </div>
                            {modalEliminar.menu.parienteLabel && (
                                <div className="flex justify-between mt-1">
                                    <span className="text-gray-500 dark:text-gray-400">Padre:</span>
                                    <span className="font-medium text-gray-800 dark:text-white">
                                        {modalEliminar.menu.parienteLabel}
                                    </span>
                                </div>
                            )}
                        </div>

                        <p className="text-xs text-red-600 dark:text-red-400 mb-4">
                            ⚠️ Esta acción no se puede deshacer.
                        </p>

                        <div className="flex gap-3">
                            <button
                                onClick={() => setModalEliminar({ abierto: false, menu: null })}
                                disabled={eliminando}
                                className="flex-1 px-4 py-2.5 border border-gray-300 dark:border-gray-600 rounded-lg text-gray-700 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700 transition disabled:opacity-50"
                            >
                                Cancelar
                            </button>
                            <button
                                onClick={confirmarEliminar}
                                disabled={eliminando}
                                className="flex-1 px-4 py-2.5 bg-red-600 hover:bg-red-700 text-white rounded-lg font-medium transition disabled:opacity-50 flex items-center justify-center gap-2"
                            >
                                {eliminando ? (
                                    <>
                                        <div className="animate-spin rounded-full h-4 w-4 border-2 border-white border-t-transparent" />
                                        Eliminando...
                                    </>
                                ) : (
                                    <>
                                        <MdDelete className="text-lg" />
                                        Eliminar
                                    </>
                                )}
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
};

export default MenuCrud;