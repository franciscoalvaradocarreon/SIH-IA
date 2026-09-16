import React, { useState, useEffect } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { menuCrudService } from '../api/menuCrudService';
import type { MenuCrear } from '../types';
import { MdSave, MdCancel, MdMenu, MdLink, MdLabel, MdArrowRight, MdSearch } from 'react-icons/md';
import { ICONOS_DISPONIBLES, IconPreview } from '../utils/iconos'; 

const MenuCrudForm: React.FC = () => {
    const { id } = useParams<{ id: string }>();
    const navigate = useNavigate();
    const isEdit = Boolean(id);

    const [form, setForm] = useState<MenuCrear>({
        label: '',
        path: '',
        icono: '',
        parienteId: 0,
        nivel: 1,
        menuOrden: 0,
        activo: true,
    });
    const [menusPadre, setMenusPadre] = useState<any[]>([]);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');
    const [mostrarSelector, setMostrarSelector] = useState(false);
    const [filtroIconos, setFiltroIconos] = useState('');

    useEffect(() => {
        cargarMenusPadre();
        if (isEdit) {
            cargarMenu();
        }
    }, [id]);

    const cargarMenusPadre = async () => {
        try {
            const res = await menuCrudService.listar(0, 100);
            const menus = res.data.content.filter((m: any) => m.nivel < 3 && m.activo);
            setMenusPadre(menus);
        } catch (error) {
            console.error('Error al cargar menús padre:', error);
        }
    };

    const cargarMenu = async () => {
        setLoading(true);
        try {
            const res = await menuCrudService.obtener(Number(id));
            setForm(res.data);
        } catch (err) {
            setError('Error al cargar los datos del menú');
        } finally {
            setLoading(false);
        }
    };

    const handleChange = (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => {
        const { name, value, type } = e.target;
        setForm({
            ...form,
            [name]: type === 'number' ? Number(value) : value,
        });
    };

    const handleSelectIcono = (icono: string) => {
        setForm({ ...form, icono });
        setMostrarSelector(false);
        setFiltroIconos('');
    };

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        setError('');
        setLoading(true);

        try {
            if (isEdit) {
                await menuCrudService.actualizar(Number(id), form);
            } else {
                await menuCrudService.crear(form);
            }
            navigate('/administracion/menus');
        } catch (err: any) {
            if (err.response?.data?.message) {
                setError(err.response.data.message);
            } else {
                setError('Error al guardar el menú');
            }
        } finally {
            setLoading(false);
        }
    };

    const iconosFiltrados = ICONOS_DISPONIBLES.filter(icono =>
        icono.toLowerCase().includes(filtroIconos.toLowerCase())
    );

    if (loading && isEdit) {
        return (
            <div className="flex justify-center items-center h-64">
                <div className="animate-spin rounded-full h-12 w-12 border-4 border-blue-500 border-t-transparent"></div>
            </div>
        );
    }

    return (
        <div className="max-w-2xl mx-auto p-6">
            <div className="bg-white dark:bg-gray-800 rounded-xl shadow-md p-6 md:p-8 border border-gray-100 dark:border-gray-700">
                <h1 className="text-2xl font-bold text-gray-800 dark:text-white mb-6">
                    {isEdit ? 'Editar Menú' : 'Nuevo Menú'}
                </h1>

                {error && (
                    <div className="bg-red-50 dark:bg-red-900/30 text-red-700 dark:text-red-300 p-4 rounded-lg border border-red-200 dark:border-red-800 mb-6">
                        {error}
                    </div>
                )}

                <form onSubmit={handleSubmit} className="space-y-5">
                    {/* Etiqueta */}
                    <div>
                        <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                            Etiqueta *
                        </label>
                        <div className="relative">
                            <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                                <MdLabel className="text-gray-400 dark:text-gray-500 text-lg" />
                            </div>
                            <input
                                type="text"
                                name="label"
                                value={form.label}
                                onChange={handleChange}
                                required
                                className="w-full pl-10 pr-4 py-2.5 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition"
                                placeholder="Ej: Dashboard, Catálogos, Administración"
                            />
                        </div>
                    </div>

                    {/* Ruta */}
                    <div>
                        <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                            Ruta
                        </label>
                        <div className="relative">
                            <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                                <MdLink className="text-gray-400 dark:text-gray-500 text-lg" />
                            </div>
                            <input
                                type="text"
                                name="path"
                                value={form.path || ''}
                                onChange={handleChange}
                                className="w-full pl-10 pr-4 py-2.5 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition"
                                placeholder="Ej: /dashboard, /catalogo/maestros"
                            />
                        </div>
                    </div>

                    {/* Icono - Selector visual */}
                    <div>
                        <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                            Icono
                        </label>
                        
                        {/* Input con botón para abrir selector */}
                        <div className="flex items-center gap-3">
                            <div className="relative flex-1">
                                <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                                    <MdMenu className="text-gray-400 dark:text-gray-500 text-lg" />
                                </div>
                                <input
                                    type="text"
                                    value={form.icono || ''}
                                    readOnly
                                    className="w-full pl-10 pr-4 py-2.5 border border-gray-300 dark:border-gray-600 rounded-lg bg-gray-50 dark:bg-gray-700 text-gray-900 dark:text-white cursor-pointer"
                                    placeholder="Seleccionar icono"
                                    onClick={() => setMostrarSelector(!mostrarSelector)}
                                />
                            </div>
                            {form.icono && (
                                <div className="w-10 h-10 rounded-lg bg-blue-100 dark:bg-blue-900/40 flex items-center justify-center text-blue-600 dark:text-blue-400 flex-shrink-0 border border-blue-200 dark:border-blue-800">
                                    <IconPreview name={form.icono} className="w-6 h-6" />
                                </div>
                            )}
                            <button
                                type="button"
                                onClick={() => setMostrarSelector(!mostrarSelector)}
                                className="px-4 py-2.5 bg-gray-200 dark:bg-gray-600 text-gray-700 dark:text-gray-300 rounded-lg hover:bg-gray-300 dark:hover:bg-gray-500 transition"
                            >
                                {mostrarSelector ? 'Cerrar' : 'Seleccionar'}
                            </button>
                        </div>

                        {/* Selector visual de iconos */}
                        {mostrarSelector && (
                            <div className="mt-3 bg-white dark:bg-gray-800 border border-gray-300 dark:border-gray-600 rounded-lg shadow-lg p-4">
                                {/* Barra de búsqueda */}
                                <div className="relative mb-3">
                                    <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                                        <MdSearch className="text-gray-400 text-lg" />
                                    </div>
                                    <input
                                        type="text"
                                        placeholder="Buscar icono..."
                                        value={filtroIconos}
                                        onChange={(e) => setFiltroIconos(e.target.value)}
                                        className="w-full pl-10 pr-4 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition"
                                    />
                                </div>

                                {/* Grid de iconos */}
                                <div className="grid grid-cols-4 sm:grid-cols-6 md:grid-cols-8 gap-2 max-h-60 overflow-y-auto p-1">
                                    {iconosFiltrados.map((icono) => (
                                        <div
                                            key={icono}
                                            onClick={() => handleSelectIcono(icono)}
                                            className={`flex flex-col items-center justify-center p-2 rounded-lg cursor-pointer transition-all duration-200 hover:bg-blue-50 dark:hover:bg-blue-900/30 ${
                                                form.icono === icono
                                                    ? 'bg-blue-100 dark:bg-blue-900/50 border-2 border-blue-500'
                                                    : 'border-2 border-transparent hover:border-blue-300'
                                            }`}
                                        >
                                            <IconPreview name={icono} className="w-6 h-6 text-gray-700 dark:text-gray-300" />
                                            <span className="text-[10px] text-gray-500 dark:text-gray-400 mt-1 text-center truncate w-full">
                                                {icono.replace(/^Md|^Pi/, '')}
                                            </span>
                                        </div>
                                    ))}
                                </div>

                                {iconosFiltrados.length === 0 && (
                                    <div className="text-center py-4 text-gray-500 dark:text-gray-400">
                                        No se encontraron iconos
                                    </div>
                                )}
                            </div>
                        )}

                        <p className="text-xs text-gray-400 mt-1">
                            Haz clic en "Seleccionar" para elegir un icono visualmente
                        </p>
                    </div>

                    <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                        {/* Menú Padre */}
                        <div>
                            <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                                Menú Padre
                            </label>
                            <div className="relative">
                                <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                                    <MdArrowRight className="text-gray-400 dark:text-gray-500 text-lg" />
                                </div>
                                <select
                                    name="parienteId"
                                    value={form.parienteId || 0}
                                    onChange={handleChange}
                                    className="w-full pl-10 pr-4 py-2.5 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition"
                                >
                                    <option value={0}>Sin padre (Raíz)</option>
                                    {menusPadre
                                        .filter((m: any) => m.menuId !== Number(id))
                                        .map((menu: any) => (
                                            <option key={menu.menuId} value={menu.menuId}>
                                                {'—'.repeat(menu.nivel)} {menu.label}
                                            </option>
                                        ))}
                                </select>
                            </div>
                        </div>

                        {/* Orden */}
                        <div>
                            <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                                Orden
                            </label>
                            <input
                                type="number"
                                name="menuOrden"
                                value={form.menuOrden || 0}
                                onChange={handleChange}
                                min={0}
                                className="w-full px-4 py-2.5 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition"
                            />
                        </div>
                    </div>

                    {/* Checkbox Activo */}
                    <div className="flex items-center gap-2">
                        <input
                            type="checkbox"
                            name="activo"
                            checked={form.activo !== false}
                            onChange={(e) => setForm({ ...form, activo: e.target.checked })}
                            className="w-4 h-4 text-blue-600 border-gray-300 rounded focus:ring-blue-500"
                        />
                        <label className="text-sm font-medium text-gray-700 dark:text-gray-300">
                            Menú activo
                        </label>
                    </div>

                    {/* Botones */}
                    <div className="flex gap-3 pt-4">
                        <button
                            type="submit"
                            disabled={loading}
                            className="flex items-center gap-2 flex-1 bg-blue-600 hover:bg-blue-700 text-white px-6 py-2.5 rounded-lg font-medium transition disabled:opacity-50 disabled:cursor-not-allowed shadow-sm"
                        >
                            <MdSave className="text-xl" />
                            {loading ? 'Guardando...' : isEdit ? 'Actualizar' : 'Crear'}
                        </button>
                        <button
                            type="button"
                            onClick={() => navigate('/administracion/menus')}
                            className="flex items-center gap-2 px-6 py-2.5 border border-gray-300 dark:border-gray-600 rounded-lg text-gray-700 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700 transition"
                        >
                            <MdCancel className="text-xl" />
                            Cancelar
                        </button>
                    </div>
                </form>
            </div>
        </div>
    );
};

export default MenuCrudForm;