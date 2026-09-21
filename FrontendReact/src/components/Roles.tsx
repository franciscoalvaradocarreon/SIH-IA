import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { rolService } from '../api/rolService';
import type { Rol } from '../types';
import ErrorScreen from '../utils/ErrorScreen';
import {
  MdAdd, MdEdit, MdDelete, MdSearch, MdSecurity, MdWarning
} from 'react-icons/md';

const Roles: React.FC = () => {
  const [roles, setRoles] = useState<Rol[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [size] = useState(10);
  const [busqueda, setBusqueda] = useState('');
  const [loading, setLoading] = useState(true);
  const navigate = useNavigate();

  // Estados para modal de confirmación y pantalla de error
  const [modalEliminar, setModalEliminar] = useState<{
    abierto: boolean;
    rol: Rol | null;
  }>({ abierto: false, rol: null });

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
    cargarRoles();
  }, [page, busqueda]);

  const cargarRoles = async () => {
    setLoading(true);
    try {
      const res = await rolService.listar(page, size, busqueda);
      setRoles(res.data.content);
      setTotal(res.data.totalElements);
    } catch (error) {
      console.error('Error al cargar roles:', error);
    } finally {
      setLoading(false);
    }
  };

  // 🔥 Abre el modal de confirmación
  const handleEliminar = (rol: Rol) => {
    setModalEliminar({ abierto: true, rol });
  };

  const confirmarEliminar = async () => {
    if (!modalEliminar.rol) return;

    setEliminando(true);
    try {
      await rolService.eliminar(modalEliminar.rol.id);
      setModalEliminar({ abierto: false, rol: null });
      cargarRoles();
    } catch (error: any) {
      console.error('Error al eliminar:', error);
      const msg = error.response?.data?.message || 'Error al eliminar el rol';
      setModalEliminar({ abierto: false, rol: null });

      // 🔥 Detectar si es error de dependencias
      const esDependencia = msg.includes('usuarios asignados') ||
                            msg.includes('asociad') ||
                            msg.includes('dependencias') ||
                            msg.includes('foreign key') ||
                            msg.includes('viola la llave') ||
                            msg.includes('no se puede');

      if (esDependencia) {
        setErrorPantalla({
          titulo: 'No se puede eliminar el rol',
          mensaje: msg,
          sugerencia: 'Desvincula primero los usuarios o permisos asignados a este rol, o modifícalo en lugar de eliminarlo.',
          tipo: 'error',
          acciones: [
            {
              label: 'Aceptar',
              onClick: () => {
                setErrorPantalla(null);
                cargarRoles();
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
                cargarRoles();
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
      <div className="flex justify-between items-center mb-6">
        <h1 className="text-3xl font-bold text-gray-800 dark:text-white">Roles</h1>
        <button
          onClick={() => navigate('/administracion/roles/new')}
          className="flex items-center gap-2 bg-blue-600 hover:bg-blue-700 text-white px-4 py-2.5 rounded-lg shadow-md transition duration-200"
        >
          <MdAdd className="text-xl" />
          Nuevo Rol
        </button>
      </div>

      <div className="mb-6">
        <div className="relative max-w-md">
          <MdSearch className="absolute left-3 top-1/2 transform -translate-y-1/2 text-gray-400 text-xl" />
          <input
            type="text"
            placeholder="Buscar por nombre..."
            value={busqueda}
            onChange={(e) => setBusqueda(e.target.value)}
            className="w-full pl-10 pr-4 py-2.5 border border-gray-400 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-800 text-gray-900 dark:text-white focus:ring-2 focus:ring-blue-500 focus:border-transparent transition"
          />
        </div>
      </div>

      {loading ? (
        <div className="flex justify-center py-12">
          <div className="animate-spin rounded-full h-12 w-12 border-4 border-blue-500 border-t-transparent"></div>
        </div>
      ) : (
        <>
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6">
            {roles.map((rol) => (
              <div
                key={rol.id}
                className="bg-white dark:bg-gray-800 rounded-xl shadow-md overflow-hidden hover:shadow-lg transition-shadow duration-300 border border-gray-400 dark:border-gray-700"
              >
                <div className="p-5">
                  <div className="flex items-center justify-between mb-3">
                    <div className="flex items-center gap-3">
                      <div className="w-10 h-10 rounded-lg bg-purple-100 dark:bg-purple-900/40 flex items-center justify-center text-purple-600 dark:text-purple-400">
                        <MdSecurity className="text-xl" />
                      </div>
                      <div>
                        <h3 className="text-lg font-semibold text-gray-800 dark:text-white">
                          {rol.nombre}
                        </h3>
                      </div>
                    </div>
                  </div>

                  {rol.descripcion && (
                    <p className="mt-2 text-sm text-gray-500 dark:text-gray-400 line-clamp-2">
                      {rol.descripcion}
                    </p>
                  )}

                  <div className="mt-4 flex items-center justify-end space-x-3 border-t border-gray-400 dark:border-gray-700 pt-3">
                    <button
                      onClick={() => navigate(`/administracion/roles/edit/${rol.id}`)}
                      className="flex items-center gap-1 text-blue-600 dark:text-blue-400 hover:text-blue-800 dark:hover:text-blue-300 text-sm font-medium transition"
                    >
                      <MdEdit className="text-base" />
                      Editar
                    </button>
                    <button
                      onClick={() => handleEliminar(rol)}
                      className="flex items-center gap-1 text-red-600 dark:text-red-400 hover:text-red-800 dark:hover:text-red-300 text-sm font-medium transition"
                    >
                      <MdDelete className="text-base" />
                      Eliminar
                    </button>
                  </div>
                </div>
              </div>
            ))}
          </div>

          <div className="flex justify-between items-center mt-8 bg-white dark:bg-gray-800 px-4 py-3 rounded-lg shadow-sm border border-gray-400 dark:border-gray-700">
            <div className="text-sm text-gray-600 dark:text-gray-400">
              Mostrando <span className="font-medium">{roles.length}</span> de{' '}
              <span className="font-medium">{total}</span> roles
            </div>
            <div className="space-x-3">
              <button
                onClick={() => setPage(Math.max(0, page - 1))}
                disabled={page === 0}
                className="px-4 py-2 border border-gray-400 dark:border-gray-600 rounded-lg text-sm font-medium text-gray-700 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700 disabled:opacity-50 disabled:cursor-not-allowed transition"
              >
                Anterior
              </button>
              <button
                onClick={() => setPage(page + 1)}
                disabled={roles.length < size}
                className="px-4 py-2 border border-gray-400 dark:border-gray-600 rounded-lg text-sm font-medium text-gray-700 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700 disabled:opacity-50 disabled:cursor-not-allowed transition"
              >
                Siguiente
              </button>
            </div>
          </div>
        </>
      )}

      {/* 🔥 Modal de confirmación de eliminación */}
      {modalEliminar.abierto && modalEliminar.rol && (
        <div className="fixed inset-0 z-50 flex items-start justify-center bg-black/50 backdrop-blur-sm p-4 pt-24">
          <div className="bg-white dark:bg-gray-800 rounded-xl shadow-2xl max-w-md w-full p-6 border border-gray-400 dark:border-gray-700">
            <div className="flex items-center gap-3 mb-4">
              <div className="p-2 bg-red-100 dark:bg-red-900/40 rounded-lg text-red-600 dark:text-red-400">
                <MdWarning className="text-2xl" />
              </div>
              <h3 className="text-lg font-bold text-gray-800 dark:text-white">
                Confirmar eliminación
              </h3>
            </div>

            <p className="text-sm text-gray-600 dark:text-gray-400 mb-4">
              ¿Estás seguro de que quieres eliminar este rol?
            </p>

            <div className="bg-gray-50 dark:bg-gray-700/50 rounded-lg p-3 mb-4 text-sm">
              <div className="flex items-center gap-3 mb-2">
                <div className="w-10 h-10 rounded-lg bg-purple-100 dark:bg-purple-900/40 flex items-center justify-center text-purple-600 dark:text-purple-400">
                  <MdSecurity className="text-xl" />
                </div>
                <div>
                  <div className="font-medium text-gray-800 dark:text-white">
                    {modalEliminar.rol.nombre}
                  </div>
                  <div className="text-xs text-gray-500 dark:text-gray-400">
                    ID: {modalEliminar.rol.id}
                  </div>
                </div>
              </div>
              {modalEliminar.rol.descripcion && (
                <div className="flex justify-between mt-1">
                  <span className="text-gray-500 dark:text-gray-400">Descripción:</span>
                  <span className="font-medium text-gray-800 dark:text-white truncate max-w-[200px]">
                    {modalEliminar.rol.descripcion}
                  </span>
                </div>
              )}
            </div>

            <p className="text-xs text-red-600 dark:text-red-400 mb-4">
              ⚠️ Esta acción no se puede deshacer.
            </p>

            <div className="flex gap-3">
              <button
                onClick={() => setModalEliminar({ abierto: false, rol: null })}
                disabled={eliminando}
                className="flex-1 px-4 py-2.5 border border-gray-400 dark:border-gray-600 rounded-lg text-gray-700 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700 transition disabled:opacity-50"
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

export default Roles;