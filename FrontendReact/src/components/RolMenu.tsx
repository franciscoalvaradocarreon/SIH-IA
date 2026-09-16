import React, { useState, useEffect, useMemo } from 'react';
import { rolMenuService } from '../api/rolMenuService';
import { rolService } from '../api/rolService';
import api from '../api/axiosConfig';
import type { MenuLista, RolMenu as RolMenuType, RolMenuAsignacion } from '../types';
import ErrorScreen from '../utils/ErrorScreen';
import { IconRenderer } from '../utils/iconos';
import {
    MdAdd,
    MdDelete,
    MdSearch,
    MdMenu,
    MdRefresh,
    MdSave,
    MdWarning,
    MdExpandMore,
    MdExpandLess,
} from 'react-icons/md';

// ============================================================
// ESTRUCTURA DE ÁRBOL
// ============================================================
interface MenuNodo {
    menu: MenuLista;
    hijos: MenuNodo[];
}

/**
 * Reconstruye el árbol a partir de la lista plana de menús.
 * Se apoya en `parienteId` (0 o null = raíz) y ordena por `menuOrden`.
 */
const construirArbol = (menus: MenuLista[]): MenuNodo[] => {
    const mapa = new Map<number, MenuNodo>();
    menus.forEach(m => mapa.set(m.menuId, { menu: m, hijos: [] }));

    const raices: MenuNodo[] = [];
    menus.forEach(m => {
        const nodo = mapa.get(m.menuId)!;
        const padreId = m.parienteId ?? 0;
        if (padreId > 0 && mapa.has(padreId)) {
            mapa.get(padreId)!.hijos.push(nodo);
        } else {
            raices.push(nodo);
        }
    });

    const ordenar = (nodos: MenuNodo[]) => {
        nodos.sort((a, b) => (a.menu.menuOrden ?? 0) - (b.menu.menuOrden ?? 0));
        nodos.forEach(n => ordenar(n.hijos));
    };
    ordenar(raices);

    return raices;
};

/** Recolecta todos los IDs de un nodo (incluyéndose a sí mismo). */
const recolectarIds = (nodo: MenuNodo): number[] => {
    const ids = [nodo.menu.menuId];
    nodo.hijos.forEach(h => ids.push(...recolectarIds(h)));
    return ids;
};


// ============================================================
// COMPONENTE RECURSIVO
// ============================================================
const MenuNodoItem: React.FC<{
    nodo: MenuNodo;
    selectedMenus: number[];
    onToggleSingle: (id: number) => void;
    onToggleRama: (nodo: MenuNodo, seleccionar: boolean) => void;
    nivel: number;
}> = ({ nodo, selectedMenus, onToggleSingle, onToggleRama, nivel }) => {
    const { menu, hijos } = nodo;
    const tieneHijos = hijos.length > 0;
    const isChecked = selectedMenus.includes(menu.menuId);
    const hijosSeleccionados = hijos.filter(h => selectedMenus.includes(h.menu.menuId)).length;
    const [expandido, setExpandido] = useState(true);

    const handleCheckbox = () => {
        // Si tiene hijos, marcar/desmarcar toda la rama
        if (tieneHijos) {
            onToggleRama(nodo, !isChecked);
        } else {
            onToggleSingle(menu.menuId);
        }
    };

    return (
        <div>
            <div
                className={`
                    flex items-center gap-2 py-2 pr-3 rounded-lg cursor-pointer
                    transition-colors
                    ${isChecked
                        ? 'bg-blue-50 dark:bg-blue-900/20'
                        : 'hover:bg-gray-50 dark:hover:bg-gray-700/40'}
                `}
                style={{ paddingLeft: `${8 + nivel * 20}px` }}
            >
                {/* Flechita para expandir/colapsar */}
                {tieneHijos ? (
                    <button
                        type="button"
                        onClick={(e) => {
                            e.stopPropagation();
                            setExpandido(!expandido);
                        }}
                        className="p-0.5 rounded hover:bg-gray-200 dark:hover:bg-gray-600 text-gray-500 dark:text-gray-400 flex-shrink-0"
                        title={expandido ? 'Colapsar' : 'Expandir'}
                    >
                        {expandido
                            ? <MdExpandMore className="text-lg" />
                            : <MdExpandLess className="text-lg" />}
                    </button>
                ) : (
                    <span className="w-6 flex-shrink-0" />
                )}

                {/* Checkbox */}
                <input
                    type="checkbox"
                    checked={isChecked}
                    onChange={handleCheckbox}
                    onClick={(e) => e.stopPropagation()}
                    className="w-4 h-4 text-blue-600 rounded focus:ring-blue-500 flex-shrink-0"
                />

                {/* Icono + label */}
                {menu.icono && (
                    <span className="text-blue-500 dark:text-blue-400 flex-shrink-0">
                        <IconRenderer name={menu.icono} className="w-4 h-4" />
                    </span>
                )}
                <span className="text-sm text-gray-700 dark:text-gray-300 truncate flex-1 min-w-0">
                    {menu.label}
                </span>

                {/* Contador de hijos seleccionados */}
                {tieneHijos && (
                    <span
                        className={`
                            text-[10px] font-medium px-1.5 py-0.5 rounded-full flex-shrink-0
                            ${hijosSeleccionados === hijos.length
                                ? 'bg-green-100 dark:bg-green-900/40 text-green-700 dark:text-green-300'
                                : hijosSeleccionados > 0
                                ? 'bg-yellow-100 dark:bg-yellow-900/40 text-yellow-700 dark:text-yellow-300'
                                : 'bg-gray-100 dark:bg-gray-700 text-gray-500 dark:text-gray-400'}
                        `}
                        title={`${hijosSeleccionados} de ${hijos.length} hijos seleccionados`}
                    >
                        {hijosSeleccionados}/{hijos.length}
                    </span>
                )}

                {/* Ruta (opcional, ayuda a distinguir menús homónimos) */}
                {menu.path && (
                    <span className="text-[10px] text-gray-400 dark:text-gray-500 font-mono truncate max-w-[160px] flex-shrink-0 hidden md:inline">
                        {menu.path}
                    </span>
                )}
            </div>

            {tieneHijos && expandido && (
                <div>
                    {hijos.map(hijo => (
                        <MenuNodoItem
                            key={hijo.menu.menuId}
                            nodo={hijo}
                            selectedMenus={selectedMenus}
                            onToggleSingle={onToggleSingle}
                            onToggleRama={onToggleRama}
                            nivel={nivel + 1}
                        />
                    ))}
                </div>
            )}
        </div>
    );
};


// ============================================================
// PANTALLA
// ============================================================
const RolMenu: React.FC = () => {
    const [asignaciones, setAsignaciones] = useState<RolMenuType[]>([]);
    const [total, setTotal] = useState(0);
    const [page] = useState(0);
    const [loading, setLoading] = useState(true);
    const [roles, setRoles] = useState<any[]>([]);
    const [menus, setMenus] = useState<MenuLista[]>([]);
    const [selectedRol, setSelectedRol] = useState<number>(0);
    const [selectedMenus, setSelectedMenus] = useState<number[]>([]);
    const [showForm, setShowForm] = useState(false);
    const [busqueda, setBusqueda] = useState('');

    const [modalDesasignar, setModalDesasignar] = useState<{
        abierto: boolean;
        asignacion: RolMenuType | null;
    }>({ abierto: false, asignacion: null });

    const [desasignando, setDesasignando] = useState(false);

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
        cargarRoles();
    }, []);

    useEffect(() => {
        if (selectedRol > 0) {
            cargarAsignaciones(selectedRol);
        } else {
            setAsignaciones([]);
            setTotal(0);
        }
    }, [selectedRol, page, busqueda]);

    const cargarRoles = async () => {
        setLoading(true);
        try {
            const rolesRes = await rolService.listar(0, 100);
            setRoles(rolesRes.data.content);

            try {
                const menusRes = await api.get('/menu/todos');
                setMenus(menusRes.data);
            } catch (error) {
                console.error('❌ Error al cargar menús:', error);
                setMenus([]);
            }
        } catch (error) {
            console.error('Error al cargar roles:', error);
        } finally {
            setLoading(false);
        }
    };

    const cargarAsignaciones = async (rolId: number) => {
        setLoading(true);
        try {
            const res = await rolMenuService.listarPorRol(rolId);
            let data = res.data;
            if (busqueda) {
                data = data.filter(a =>
                    a.menuLabel.toLowerCase().includes(busqueda.toLowerCase()) ||
                    (a.menuPath && a.menuPath.toLowerCase().includes(busqueda.toLowerCase()))
                );
            }
            setAsignaciones(data);
            setTotal(data.length);
        } catch (error) {
            console.error('Error al cargar asignaciones:', error);
        } finally {
            setLoading(false);
        }
    };

    const handleRolChange = async (rolId: number) => {
        setSelectedRol(rolId);
        setShowForm(false);
        setSelectedMenus([]);
        if (rolId > 0) {
            try {
                const res = await rolMenuService.listarPorRol(rolId);
                const menuIds = res.data.map(a => a.menuId);
                setSelectedMenus(menuIds);
            } catch (error) {
                console.error('Error al cargar menús del rol:', error);
                setSelectedMenus([]);
            }
        }
    };

    const handleAsignarMenus = async () => {
        if (selectedRol === 0) {
            setErrorPantalla({
                titulo: 'Rol no seleccionado',
                mensaje: 'Debes seleccionar un rol antes de asignar menús.',
                tipo: 'warning',
                acciones: [{ label: 'Aceptar', onClick: () => setErrorPantalla(null), tipo: 'primary' }],
            });
            return;
        }
        if (selectedMenus.length === 0) {
            setErrorPantalla({
                titulo: 'Sin menús seleccionados',
                mensaje: 'Debes seleccionar al menos un menú para asignar.',
                tipo: 'warning',
                acciones: [{ label: 'Aceptar', onClick: () => setErrorPantalla(null), tipo: 'primary' }],
            });
            return;
        }

        try {
            const data: RolMenuAsignacion = {
                rolId: selectedRol,
                menuIds: selectedMenus,
            };
            await rolMenuService.asignarMultiples(data);
            setShowForm(false);
            cargarAsignaciones(selectedRol);
            const res = await rolMenuService.listarPorRol(selectedRol);
            const menuIds = res.data.map(a => a.menuId);
            setSelectedMenus(menuIds);
        } catch (error: any) {
            console.error('Error al asignar menús:', error);
            const msg = error.response?.data?.message || 'Error al asignar menús';
            setErrorPantalla({
                titulo: 'Error al asignar menús',
                mensaje: msg,
                tipo: 'error',
                acciones: [{
                    label: 'Aceptar',
                    onClick: () => {
                        setErrorPantalla(null);
                        if (selectedRol > 0) cargarAsignaciones(selectedRol);
                    },
                    tipo: 'primary',
                }],
            });
        }
    };

    const handleDesasignar = (asignacion: RolMenuType) => {
        setModalDesasignar({ abierto: true, asignacion });
    };

    const confirmarDesasignar = async () => {
        if (!modalDesasignar.asignacion) return;

        setDesasignando(true);
        try {
            await rolMenuService.desasignar(selectedRol, modalDesasignar.asignacion.menuId);
            setModalDesasignar({ abierto: false, asignacion: null });
            cargarAsignaciones(selectedRol);
            setSelectedMenus(prev => prev.filter(id => id !== modalDesasignar.asignacion?.menuId));
        } catch (error: any) {
            console.error('Error al desasignar:', error);
            const msg = error.response?.data?.message || 'Error al desasignar el menú';
            setModalDesasignar({ abierto: false, asignacion: null });

            const esDependencia = msg.includes('dependencias') ||
                                  msg.includes('foreign key') ||
                                  msg.includes('viola la llave') ||
                                  msg.includes('no se puede');

            setErrorPantalla({
                titulo: esDependencia ? 'No se puede desasignar el menú' : 'Error al desasignar',
                mensaje: msg,
                sugerencia: esDependencia ? 'Revisa las dependencias del menú o intenta desasignarlo desde otra pantalla.' : undefined,
                tipo: 'error',
                acciones: [{
                    label: 'Aceptar',
                    onClick: () => {
                        setErrorPantalla(null);
                        if (selectedRol > 0) cargarAsignaciones(selectedRol);
                    },
                    tipo: 'primary',
                }],
            });
        } finally {
            setDesasignando(false);
        }
    };

    const handleToggleSingle = (menuId: number) => {
        setSelectedMenus(prev =>
            prev.includes(menuId)
                ? prev.filter(id => id !== menuId)
                : [...prev, menuId]
        );
    };

    const handleToggleRama = (nodo: MenuNodo, seleccionar: boolean) => {
        const ids = recolectarIds(nodo);
        setSelectedMenus(prev => {
            const set = new Set(prev);
            if (seleccionar) {
                ids.forEach(id => set.add(id));
            } else {
                ids.forEach(id => set.delete(id));
            }
            return Array.from(set);
        });
    };

    const getRolNombre = () => {
        const rol = roles.find(r => r.id === selectedRol);
        return rol ? rol.nombre : 'Selecciona un rol';
    };

    // 🔥 Construcción del árbol (memoizada)
    const arbolMenus = useMemo(() => construirArbol(menus), [menus]);

    // 🔥 Filtro por búsqueda: si hay texto, mostramos lista plana de coincidencias.
    // Si no, mostramos el árbol completo.
    const menusFiltrados = useMemo(() => {
        if (!busqueda.trim()) return null;
        const q = busqueda.toLowerCase();
        return menus.filter(m =>
            m.label.toLowerCase().includes(q) ||
            (m.path && m.path.toLowerCase().includes(q))
        );
    }, [menus, busqueda]);

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
                    Gestión de Permisos
                </h1>
                <div className="flex gap-3">
                    <button
                        onClick={() => {
                            cargarRoles();
                            if (selectedRol > 0) cargarAsignaciones(selectedRol);
                        }}
                        className="flex items-center gap-2 bg-gray-200 dark:bg-gray-700 hover:bg-gray-300 dark:hover:bg-gray-600 text-gray-700 dark:text-gray-300 px-4 py-2.5 rounded-lg shadow-md transition duration-200"
                        title="Recargar datos"
                    >
                        <MdRefresh className="text-xl" />
                        Recargar
                    </button>
                </div>
            </div>

            {/* Selector de Rol */}
            <div className="bg-white dark:bg-gray-800 rounded-xl shadow-md p-6 mb-6 border border-gray-100 dark:border-gray-700">
                <div className="flex flex-col sm:flex-row items-start sm:items-center gap-4">
                    <div className="flex-1">
                        <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                            Seleccionar Rol *
                        </label>
                        <select
                            value={selectedRol}
                            onChange={(e) => handleRolChange(Number(e.target.value))}
                            className="w-full max-w-md px-4 py-2.5 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-blue-500"
                        >
                            <option value={0}>Seleccionar rol...</option>
                            {roles.map((rol) => (
                                <option key={rol.id} value={rol.id}>
                                    {rol.nombre}
                                </option>
                            ))}
                        </select>
                    </div>
                    {selectedRol > 0 && (
                        <button
                            onClick={() => setShowForm(!showForm)}
                            className="flex items-center gap-2 bg-blue-600 hover:bg-blue-700 text-white px-4 py-2.5 rounded-lg shadow-md transition duration-200 whitespace-nowrap"
                        >
                            <MdAdd className="text-xl" />
                            {showForm ? 'Cancelar' : 'Asignar Menús'}
                        </button>
                    )}
                </div>
            </div>

            {/* Formulario de asignación (árbol) */}
            {showForm && selectedRol > 0 && (
                <div className="bg-white dark:bg-gray-800 rounded-xl shadow-md p-6 mb-6 border border-gray-100 dark:border-gray-700">
                    <div className="flex items-center justify-between mb-4">
                        <h2 className="text-xl font-bold text-gray-800 dark:text-white">
                            Asignar Menús a <span className="text-blue-600 dark:text-blue-400">{getRolNombre()}</span>
                        </h2>
                        <span className="text-sm text-gray-500 dark:text-gray-400">
                            {selectedMenus.length} menús seleccionados
                        </span>
                    </div>

                    {/* Búsqueda */}
                    <div className="mb-3">
                        <div className="relative max-w-md">
                            <MdSearch className="absolute left-3 top-1/2 transform -translate-y-1/2 text-gray-400 text-xl" />
                            <input
                                type="text"
                                placeholder="Buscar menú..."
                                value={busqueda}
                                onChange={(e) => setBusqueda(e.target.value)}
                                className="w-full pl-10 pr-4 py-2.5 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-800 text-gray-900 dark:text-white focus:ring-2 focus:ring-blue-500 focus:border-transparent transition"
                            />
                        </div>
                    </div>

                    {/* Árbol de menús o lista filtrada */}
                    <div className="border border-gray-200 dark:border-gray-700 rounded-lg max-h-[500px] overflow-y-auto p-2">
                        {menusFiltrados ? (
                            // Vista filtrada: lista plana
                            menusFiltrados.length === 0 ? (
                                <div className="text-center py-6 text-sm text-gray-500 dark:text-gray-400">
                                    Sin resultados para "{busqueda}"
                                </div>
                            ) : (
                                menusFiltrados.map((menu) => {
                                    const isChecked = selectedMenus.includes(menu.menuId);
                                    return (
                                        <label
                                            key={menu.menuId}
                                            className={`
                                                flex items-center gap-2 p-2 rounded-lg cursor-pointer
                                                ${isChecked
                                                    ? 'bg-blue-50 dark:bg-blue-900/20'
                                                    : 'hover:bg-gray-50 dark:hover:bg-gray-700/40'}
                                            `}
                                        >
                                            <input
                                                type="checkbox"
                                                checked={isChecked}
                                                onChange={() => handleToggleSingle(menu.menuId)}
                                                className="w-4 h-4 text-blue-600 rounded focus:ring-blue-500"
                                            />
                                            {menu.icono && (
                                                <span className="text-blue-500 dark:text-blue-400">
                                                    <IconRenderer name={menu.icono} className="w-4 h-4" />
                                                </span>
                                            )}
                                            <span className="text-sm text-gray-700 dark:text-gray-300 truncate flex-1">
                                                {menu.label}
                                            </span>
                                            {menu.path && (
                                                <span className="text-xs text-gray-400 font-mono truncate max-w-[200px]">
                                                    {menu.path}
                                                </span>
                                            )}
                                        </label>
                                    );
                                })
                            )
                        ) : arbolMenus.length === 0 ? (
                            <div className="text-center py-6 text-sm text-gray-500 dark:text-gray-400">
                                No hay menús configurados.
                            </div>
                        ) : (
                            // Vista árbol: jerárquica con indentación
                            arbolMenus.map((nodo) => (
                                <MenuNodoItem
                                    key={nodo.menu.menuId}
                                    nodo={nodo}
                                    selectedMenus={selectedMenus}
                                    onToggleSingle={handleToggleSingle}
                                    onToggleRama={handleToggleRama}
                                    nivel={0}
                                />
                            ))
                        )}
                    </div>

                    <div className="flex gap-3 mt-4">
                        <button
                            onClick={handleAsignarMenus}
                            disabled={selectedMenus.length === 0}
                            className={`bg-blue-600 hover:bg-blue-700 text-white px-6 py-2.5 rounded-lg font-medium transition ${
                                selectedMenus.length === 0 ? 'opacity-50 cursor-not-allowed' : ''
                            }`}
                        >
                            <MdSave className="inline mr-2" />
                            Guardar Asignaciones
                        </button>
                        <button
                            onClick={() => {
                                setShowForm(false);
                                if (selectedRol > 0) {
                                    rolMenuService.listarPorRol(selectedRol)
                                        .then(res => {
                                            const menuIds = res.data.map(a => a.menuId);
                                            setSelectedMenus(menuIds);
                                        });
                                }
                            }}
                            className="bg-gray-300 dark:bg-gray-600 text-gray-700 dark:text-gray-300 px-6 py-2.5 rounded-lg font-medium hover:bg-gray-400 dark:hover:bg-gray-500 transition"
                        >
                            Cancelar
                        </button>
                    </div>
                </div>
            )}

            {/* Tabla de asignaciones del rol seleccionado */}
            {selectedRol > 0 ? (
                <>
                    <div className="flex justify-between items-center mb-4">
                        <h2 className="text-xl font-semibold text-gray-800 dark:text-white">
                            Menús asignados a <span className="text-blue-600 dark:text-blue-400">{getRolNombre()}</span>
                        </h2>
                        <span className="text-sm text-gray-500 dark:text-gray-400">
                            {total} menús asignados
                        </span>
                    </div>

                    <div className="mb-4">
                        <div className="relative max-w-md">
                            <MdSearch className="absolute left-3 top-1/2 transform -translate-y-1/2 text-gray-400 text-xl" />
                            <input
                                type="text"
                                placeholder="Buscar por menú o ruta..."
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
                        <div className="bg-white dark:bg-gray-800 rounded-xl shadow-md overflow-hidden border border-gray-100 dark:border-gray-700">
                            <div className="overflow-x-auto">
                                <table className="min-w-full divide-y divide-gray-200 dark:divide-gray-700">
                                    <thead className="bg-gray-50 dark:bg-gray-700/50">
                                        <tr>
                                            <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider">
                                                <div className="flex items-center gap-2">
                                                    <MdMenu className="text-sm" />
                                                    Menú
                                                </div>
                                            </th>
                                            <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider">
                                                Ruta
                                            </th>
                                            <th className="px-6 py-3 text-center text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider">
                                                Acciones
                                            </th>
                                        </tr>
                                    </thead>
                                    <tbody className="bg-white dark:bg-gray-800 divide-y divide-gray-200 dark:divide-gray-700">
                                        {asignaciones.length === 0 ? (
                                            <tr>
                                                <td colSpan={3} className="px-6 py-8 text-center text-gray-500 dark:text-gray-400">
                                                    No hay menús asignados a este rol
                                                </td>
                                            </tr>
                                        ) : (
                                            asignaciones.map((asignacion) => (
                                                <tr key={asignacion.menuId} className="hover:bg-gray-50 dark:hover:bg-gray-700/50 transition">
                                                    <td className="px-6 py-1 whitespace-nowrap">
                                                        <div className="flex items-center gap-2">
                                                            <div className="w-8 h-8 rounded-full bg-blue-100 dark:bg-blue-900/40 flex items-center justify-center text-blue-600 dark:text-blue-400">
                                                                <MdMenu className="text-sm" />
                                                            </div>
                                                            <span className="text-sm font-medium text-gray-900 dark:text-white">
                                                                {asignacion.menuLabel}
                                                            </span>
                                                        </div>
                                                    </td>
                                                    <td className="px-6 py-1 whitespace-nowrap">
                                                        <span className="text-sm text-gray-500 dark:text-gray-400 font-mono bg-gray-100 dark:bg-gray-700 px-2 py-1 rounded">
                                                            {asignacion.menuPath || '-'}
                                                        </span>
                                                    </td>
                                                    <td className="px-6 py-1 whitespace-nowrap text-center">
                                                        <button
                                                            onClick={() => handleDesasignar(asignacion)}
                                                            className="text-red-600 dark:text-red-400 hover:text-red-800 dark:hover:text-red-300 transition p-1.5 rounded-lg hover:bg-red-50 dark:hover:bg-red-900/20"
                                                            title="Desasignar menú"
                                                        >
                                                            <MdDelete className="text-lg" />
                                                        </button>
                                                    </td>
                                                </tr>
                                            ))
                                        )}
                                    </tbody>
                                </table>
                            </div>
                        </div>
                    )}
                </>
            ) : (
                <div className="bg-white dark:bg-gray-800 rounded-xl shadow-md p-12 text-center border border-gray-100 dark:border-gray-700">
                    <div className="text-6xl mb-4">🔒</div>
                    <h3 className="text-xl font-semibold text-gray-700 dark:text-gray-300 mb-2">
                        Selecciona un rol para gestionar sus permisos
                    </h3>
                    <p className="text-gray-500 dark:text-gray-400">
                        Elige un rol del selector superior para ver y administrar sus menús asignados.
                    </p>
                </div>
            )}

            {/* Modal de confirmación de desasignación */}
            {modalDesasignar.abierto && modalDesasignar.asignacion && (
                <div className="fixed inset-0 z-50 flex items-start justify-center bg-black/50 backdrop-blur-sm p-4 pt-24">
                    <div className="bg-white dark:bg-gray-800 rounded-xl shadow-2xl max-w-md w-full p-6 border border-gray-200 dark:border-gray-700">
                        <div className="flex items-center gap-3 mb-4">
                            <div className="p-2 bg-red-100 dark:bg-red-900/40 rounded-lg text-red-600 dark:text-red-400">
                                <MdWarning className="text-2xl" />
                            </div>
                            <h3 className="text-lg font-bold text-gray-800 dark:text-white">
                                Confirmar desasignación
                            </h3>
                        </div>

                        <p className="text-sm text-gray-600 dark:text-gray-400 mb-4">
                            ¿Estás seguro de que quieres desasignar este menú del rol?
                        </p>

                        <div className="bg-gray-50 dark:bg-gray-700/50 rounded-lg p-3 mb-4 text-sm">
                            <div className="flex items-center gap-3 mb-2">
                                <div className="w-10 h-10 rounded-full bg-blue-100 dark:bg-blue-900/40 flex items-center justify-center text-blue-600 dark:text-blue-400">
                                    <MdMenu className="text-lg" />
                                </div>
                                <div>
                                    <div className="font-medium text-gray-800 dark:text-white">
                                        {modalDesasignar.asignacion.menuLabel}
                                    </div>
                                    <div className="text-xs text-gray-500 dark:text-gray-400 font-mono">
                                        {modalDesasignar.asignacion.menuPath || 'Sin ruta'}
                                    </div>
                                </div>
                            </div>
                            <div className="flex justify-between mt-2">
                                <span className="text-gray-500 dark:text-gray-400">Rol:</span>
                                <span className="font-medium text-gray-800 dark:text-white">
                                    {getRolNombre()}
                                </span>
                            </div>
                        </div>

                        <p className="text-xs text-red-600 dark:text-red-400 mb-4">
                            ⚠️ Esta acción no se puede deshacer.
                        </p>

                        <div className="flex gap-3">
                            <button
                                onClick={() => setModalDesasignar({ abierto: false, asignacion: null })}
                                disabled={desasignando}
                                className="flex-1 px-4 py-2.5 border border-gray-300 dark:border-gray-600 rounded-lg text-gray-700 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700 transition disabled:opacity-50"
                            >
                                Cancelar
                            </button>
                            <button
                                onClick={confirmarDesasignar}
                                disabled={desasignando}
                                className="flex-1 px-4 py-2.5 bg-red-600 hover:bg-red-700 text-white rounded-lg font-medium transition disabled:opacity-50 flex items-center justify-center gap-2"
                            >
                                {desasignando ? (
                                    <>
                                        <div className="animate-spin rounded-full h-4 w-4 border-2 border-white border-t-transparent" />
                                        Desasignando...
                                    </>
                                ) : (
                                    <>
                                        <MdDelete className="text-lg" />
                                        Desasignar
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

export default RolMenu;