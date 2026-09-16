import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { escuelaService } from '../api/escuelaService';
import type { Escuela } from '../types';
import ErrorScreen from '../utils/ErrorScreen';
import {
  MdAdd, MdEdit, MdDelete, MdSearch, MdSchool,
  MdCheckCircle, MdCancel, MdWarning
} from 'react-icons/md';

const Escuelas: React.FC = () => {
  const [escuelas, setEscuelas] = useState<Escuela[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [size] = useState(10);
  const [busqueda, setBusqueda] = useState('');
  const [loading, setLoading] = useState(true);
  const navigate = useNavigate();

  // 🔥 Estados para modal de confirmación y pantalla de error
  const [modalEliminar, setModalEliminar] = useState<{
    abierto: boolean;
    escuela: Escuela | null;
  }>({ abierto: false, escuela: null });

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
    cargarEscuelas();
  }, [page, busqueda]);

  const cargarEscuelas = async () => {
    setLoading(true);
    try {
      const res = await escuelaService.listar(page, size, busqueda);
      setEscuelas(res.data.content);
      setTotal(res.data.totalElements);
    } catch (error) {
      console.error('Error al cargar escuelas:', error);
    } finally {
      setLoading(false);
    }
  };

  // 🔥 Abre el modal de confirmación
  const handleEliminar = (escuela: Escuela) => {
    setModalEliminar({ abierto: true, escuela });
  };

  const confirmarEliminar = async () => {
    if (!modalEliminar.escuela) return;

    setEliminando(true);
    try {
      await escuelaService.eliminar(modalEliminar.escuela.id);
      setModalEliminar({ abierto: false, escuela: null });
      cargarEscuelas();
    } catch (error: any) {
      console.error('Error al eliminar:', error);
      const msg = error.response?.data?.message || 'Error al eliminar la escuela';
      setModalEliminar({ abierto: false, escuela: null });

      // 🔥 Detectar si es error de dependencias
      const esDependencia = msg.includes('asociad') ||
                            msg.includes('dependencias') ||
                            msg.includes('foreign key') ||
                            msg.includes('viola la llave') ||
                            msg.includes('no se puede') ||
                            msg.includes('semestres') ||
                            msg.includes('usuarios');

      if (esDependencia) {
        setErrorPantalla({
          titulo: 'No se puede eliminar la escuela',
          mensaje: msg,
          sugerencia: 'Desvincula primero los semestres, usuarios o dependencias asociadas a esta escuela, o desactívala en lugar de eliminarla.',
          tipo: 'error',
          acciones: [
            {
              label: 'Aceptar',
              onClick: () => {
                setErrorPantalla(null);
                cargarEscuelas();
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
                cargarEscuelas();
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
      await escuelaService.cambiarEstado(id, activo);
      cargarEscuelas();
    } catch (error) {
      console.error('Error al cambiar estado:', error);
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
        <h1 className="text-3xl font-bold text-gray-800 dark:text-white">Escuelas</h1>
        <button
          onClick={() => navigate('/administracion/escuelas/new')}
          className="flex items-center gap-2 bg-blue-600 hover:bg-blue-700 text-white px-4 py-2.5 rounded-lg shadow-md transition duration-200"
        >
          <MdAdd className="text-xl" />
          Nueva Escuela
        </button>
      </div>

      <div className="mb-6">
        <div className="relative max-w-md">
          <MdSearch className="absolute left-3 top-1/2 transform -translate-y-1/2 text-gray-400 text-xl" />
          <input
            type="text"
            placeholder="Buscar por nombre o clave..."
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
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6">
            {escuelas.map((escuela) => (
              <div
                key={escuela.id}
                className="bg-white dark:bg-gray-800 rounded-xl shadow-md overflow-hidden hover:shadow-lg transition-shadow duration-300 border border-gray-100 dark:border-gray-700"
              >
                <div className="p-5">
                  <div className="flex items-center justify-between mb-3">
                    <div className="flex items-center gap-3">
                      <div className="w-10 h-10 rounded-lg bg-blue-100 dark:bg-blue-900/40 flex items-center justify-center text-blue-600 dark:text-blue-400">
                        <MdSchool className="text-xl" />
                      </div>
                      <div>
                        <h3 className="text-lg font-semibold text-gray-800 dark:text-white truncate max-w-[150px]">
                          {escuela.nombre}
                        </h3>
                        <p className="text-sm text-gray-500 dark:text-gray-400 font-mono">
                          {escuela.clave}
                        </p>
                      </div>
                    </div>
                    <span
                      className={`px-2.5 py-1 text-xs font-medium rounded-full flex items-center gap-1 ${
                        escuela.activo
                          ? 'bg-green-100 dark:bg-green-900/30 text-green-800 dark:text-green-400'
                          : 'bg-red-100 dark:bg-red-900/30 text-red-800 dark:text-red-400'
                      }`}
                    >
                      {escuela.activo ? (
                        <MdCheckCircle className="text-sm" />
                      ) : (
                        <MdCancel className="text-sm" />
                      )}
                      {escuela.activo ? 'Activa' : 'Inactiva'}
                    </span>
                  </div>

                  <div className="mt-3 space-y-1 text-sm text-gray-600 dark:text-gray-400">
                    {escuela.direccion && <p className="truncate">📍 {escuela.direccion}</p>}
                    {escuela.telefono && <p>📞 {escuela.telefono}</p>}
                  </div>

                  <div className="mt-4 flex items-center justify-end space-x-3 border-t border-gray-100 dark:border-gray-700 pt-3">
                    <button
                      onClick={() => navigate(`/administracion/escuelas/edit/${escuela.id}`)}
                      className="flex items-center gap-1 text-blue-600 dark:text-blue-400 hover:text-blue-800 dark:hover:text-blue-300 text-sm font-medium transition"
                    >
                      <MdEdit className="text-base" />
                      Editar
                    </button>
                    <button
                      onClick={() => handleCambiarEstado(escuela.id, !escuela.activo)}
                      className={`flex items-center gap-1 text-sm font-medium transition ${
                        escuela.activo
                          ? 'text-yellow-600 dark:text-yellow-400 hover:text-yellow-800 dark:hover:text-yellow-300'
                          : 'text-green-600 dark:text-green-400 hover:text-green-800 dark:hover:text-green-300'
                      }`}
                    >
                      {escuela.activo ? 'Desactivar' : 'Activar'}
                    </button>
                    <button
                      onClick={() => handleEliminar(escuela)}
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

          <div className="flex justify-between items-center mt-8 bg-white dark:bg-gray-800 px-4 py-3 rounded-lg shadow-sm border border-gray-100 dark:border-gray-700">
            <div className="text-sm text-gray-600 dark:text-gray-400">
              Mostrando <span className="font-medium">{escuelas.length}</span> de{' '}
              <span className="font-medium">{total}</span> escuelas
            </div>
            <div className="space-x-3">
              <button
                onClick={() => setPage(Math.max(0, page - 1))}
                disabled={page === 0}
                className="px-4 py-2 border border-gray-300 dark:border-gray-600 rounded-lg text-sm font-medium text-gray-700 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700 disabled:opacity-50 disabled:cursor-not-allowed transition"
              >
                Anterior
              </button>
              <button
                onClick={() => setPage(page + 1)}
                disabled={escuelas.length < size}
                className="px-4 py-2 border border-gray-300 dark:border-gray-600 rounded-lg text-sm font-medium text-gray-700 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700 disabled:opacity-50 disabled:cursor-not-allowed transition"
              >
                Siguiente
              </button>
            </div>
          </div>
        </>
      )}

      {/* 🔥 Modal de confirmación de eliminación */}
      {modalEliminar.abierto && modalEliminar.escuela && (
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
              ¿Estás seguro de que quieres eliminar esta escuela?
            </p>

            <div className="bg-gray-50 dark:bg-gray-700/50 rounded-lg p-3 mb-4 text-sm">
              <div className="flex items-center gap-3 mb-2">
                <div className="w-10 h-10 rounded-lg bg-blue-100 dark:bg-blue-900/40 flex items-center justify-center text-blue-600 dark:text-blue-400">
                  <MdSchool className="text-lg" />
                </div>
                <div>
                  <div className="font-medium text-gray-800 dark:text-white">
                    {modalEliminar.escuela.nombre}
                  </div>
                  <div className="text-xs text-gray-500 dark:text-gray-400 font-mono">
                    {modalEliminar.escuela.clave}
                  </div>
                </div>
              </div>
              {modalEliminar.escuela.direccion && (
                <div className="flex justify-between mt-1">
                  <span className="text-gray-500 dark:text-gray-400">Dirección:</span>
                  <span className="font-medium text-gray-800 dark:text-white truncate max-w-[200px]">
                    {modalEliminar.escuela.direccion}
                  </span>
                </div>
              )}
              {modalEliminar.escuela.telefono && (
                <div className="flex justify-between mt-1">
                  <span className="text-gray-500 dark:text-gray-400">Teléfono:</span>
                  <span className="font-medium text-gray-800 dark:text-white">
                    {modalEliminar.escuela.telefono}
                  </span>
                </div>
              )}
              <div className="flex justify-between mt-1">
                <span className="text-gray-500 dark:text-gray-400">Estado:</span>
                <span className={`font-medium ${modalEliminar.escuela.activo ? 'text-green-600 dark:text-green-400' : 'text-red-600 dark:text-red-400'}`}>
                  {modalEliminar.escuela.activo ? 'Activa' : 'Inactiva'}
                </span>
              </div>
            </div>

            <p className="text-xs text-red-600 dark:text-red-400 mb-4">
              ⚠️ Esta acción no se puede deshacer.
            </p>

            <div className="flex gap-3">
              <button
                onClick={() => setModalEliminar({ abierto: false, escuela: null })}
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

export default Escuelas;