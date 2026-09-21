import React, { useState, useEffect } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { semestreService } from '../api/semestreService';
import type { Semestre } from '../types';
import ErrorScreen from '../utils/ErrorScreen';
import {
  MdAdd, MdEdit, MdDelete, MdSearch, MdCheckCircle, MdCancel,
  MdRefresh, MdClass, MdSchool, MdWarning
} from 'react-icons/md';

const SemestreList: React.FC = () => {
  const navigate = useNavigate();
  const location = useLocation();

  const queryParams = new URLSearchParams(location.search);
  const busquedaInicial = queryParams.get('busqueda') || '';
  const pageInicial = parseInt(queryParams.get('page') || '0');

  const [semestres, setSemestres] = useState<Semestre[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(pageInicial);
  const [size] = useState(10);
  const [busqueda, setBusqueda] = useState(busquedaInicial);
  const [loading, setLoading] = useState(true);

  // 🔥 Estados para modal de confirmación y pantalla de error
  const [modalEliminar, setModalEliminar] = useState<{
    abierto: boolean;
    semestre: Semestre | null;
  }>({ abierto: false, semestre: null });

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
    cargarSemestres();
  }, [page, busqueda]);

  const cargarSemestres = async () => {
    setLoading(true);
    try {
      const res = await semestreService.listar(page, size, busqueda);
      setSemestres(res.data.content);
      setTotal(res.data.totalElements);
    } catch (error) {
      console.error('Error al cargar semestres:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleBusquedaChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const value = e.target.value;
    setBusqueda(value);
    setPage(0);
  };

  const handleRecargar = () => {
    cargarSemestres();
  };

  const irANuevo = () => {
    navigate('/catalogo/semestres/new');
  };

  const irAEditar = (id: number) => {
    if (!id || isNaN(id)) {
      console.error('❌ ID inválido:', id);
      return;
    }
    navigate(`/catalogo/semestres/edit/${id}`);
  };

  const handleCambiarEstado = async (id: number, activo: boolean) => {
    try {
      await semestreService.cambiarEstado(id, activo);
      cargarSemestres();
    } catch (error) {
      console.error('Error al cambiar estado:', error);
    }
  };

  // 🔥 Abre el modal de confirmación
  const handleEliminar = (semestre: Semestre) => {
    setModalEliminar({ abierto: true, semestre });
  };

    const confirmarEliminar = async () => {
        if (!modalEliminar.semestre) return;

        setEliminando(true);
        try {
            await semestreService.eliminar(modalEliminar.semestre.id);
            setModalEliminar({ abierto: false, semestre: null });
            cargarSemestres();
        } catch (error: any) {
            console.error('Error al eliminar:', error);
            const msg = error.response?.data?.message || 'Error al eliminar el semestre';
            setModalEliminar({ abierto: false, semestre: null });

            // 🔥 Detectar si es error de dependencias
            const esDependencia = msg.includes('DEPENDENCIAS') ||
                                  msg.includes('está siendo usado') ||
                                  msg.includes('referida desde la tabla') ||
                                  msg.includes('viola la llave') ||
                                  msg.includes('foreign key') ||
                                  msg.includes('no se puede');

            if (esDependencia) {
                // 🔥 Extraer el nombre de la tabla del mensaje
                const tablaMatch = msg.match(/tabla:\s*(\w+)/) ||
                                  msg.match(/tabla «(\w+)»/);
                const tabla = tablaMatch ? tablaMatch[1] : null;

                // Sugerencia contextual según la tabla
                let sugerencia = 'Elimina primero los registros asociados o desactiva el semestre en lugar de eliminarlo.';
                if (tabla === 'turno') {
                    sugerencia = 'Este semestre tiene turnos asociados. Elimina primero los turnos o desactívalos.';
                } else if (tabla === 'grupo' || tabla === 'grupos') {
                    sugerencia = 'Este semestre tiene grupos asociados. Elimina primero los grupos.';
                } else if (tabla === 'asignacion' || tabla === 'asignaciones') {
                    sugerencia = 'Este semestre tiene asignaciones asociadas. Elimina primero las asignaciones.';
                } else if (tabla === 'horario' || tabla === 'horarios') {
                    sugerencia = 'Este semestre tiene horarios generados. Elimina primero los horarios.';
                } else if (tabla === 'aula' || tabla === 'aulas') {
                    sugerencia = 'Este semestre tiene aulas asociadas. Elimina primero las aulas.';
                } else if (tabla === 'maestro' || tabla === 'maestros') {
                    sugerencia = 'Este semestre tiene maestros asociados. Elimina primero los maestros.';
                } else if (tabla === 'materia' || tabla === 'materias') {
                    sugerencia = 'Este semestre tiene materias asociadas. Elimina primero las materias.';
                }

                setErrorPantalla({
                    titulo: 'No se puede eliminar el semestre',
                    mensaje: tabla
                        ? `Este semestre está siendo usado en la tabla "${tabla}".`
                        : msg,
                    sugerencia,
                    tipo: 'error',
                    acciones: [
                        {
                            label: 'Aceptar',
                            onClick: () => {
                                setErrorPantalla(null);
                                cargarSemestres();
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
                                cargarSemestres();
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

  if (loading && semestres.length === 0) {
    return (
      <div className="flex justify-center items-center h-64">
        <div className="animate-spin rounded-full h-12 w-12 border-4 border-blue-500 border-t-transparent"></div>
      </div>
    );
  }

  return (
    <div className="p-6">
      {/* Encabezado */}
      <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4 mb-6">
        <div>
          <h1 className="text-3xl font-bold text-gray-800 dark:text-white">
            📅 Gestión de Semestres
          </h1>
          <p className="text-sm text-gray-500 dark:text-gray-400 mt-1">
            Administra los períodos académicos (semestres)
          </p>
        </div>
        <div className="flex gap-3">
          <button
            onClick={handleRecargar}
            className="flex items-center gap-2 bg-gray-200 dark:bg-gray-700 hover:bg-gray-300 dark:hover:bg-gray-600 text-gray-700 dark:text-gray-300 px-4 py-2.5 rounded-lg shadow-md transition"
          >
            <MdRefresh className="text-xl" />
            Recargar
          </button>
          <button
            onClick={irANuevo}
            className="flex items-center gap-2 bg-blue-600 hover:bg-blue-700 text-white px-4 py-2.5 rounded-lg shadow-md transition"
          >
            <MdAdd className="text-xl" />
            Nuevo Semestre
          </button>
        </div>
      </div>

      {/* Búsqueda */}
      <div className="mb-6">
        <div className="relative max-w-md">
          <MdSearch className="absolute left-3 top-1/2 transform -translate-y-1/2 text-gray-400 text-xl" />
          <input
            type="text"
            placeholder="Buscar semestre..."
            value={busqueda}
            onChange={handleBusquedaChange}
            className="w-full pl-10 pr-4 py-2.5 border border-gray-400 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-800 text-gray-900 dark:text-white focus:ring-2 focus:ring-blue-500 focus:border-transparent transition"
          />
        </div>
      </div>

      {/* Tabla */}
      <div className="bg-white dark:bg-gray-800 rounded-xl shadow-md overflow-hidden border border-gray-400 dark:border-gray-700">
        <div className="overflow-x-auto">
          <table className="min-w-full divide-y divide-gray-400 dark:divide-gray-700">
            <thead className="bg-gray-50 dark:bg-gray-700/50">
              <tr>
                <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider">
                  Semestre
                </th>
                <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider">
                  Descripción
                </th>
                <th className="px-4 py-3 text-center text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider">
                  Estado
                </th>
                <th className="px-4 py-3 text-center text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider">
                  Acciones
                </th>
              </tr>
            </thead>
            <tbody className="bg-white dark:bg-gray-800 divide-y divide-gray-400 dark:divide-gray-700">
              {semestres.length === 0 ? (
                <tr>
                  <td colSpan={4} className="px-4 py-8 text-center text-gray-500 dark:text-gray-400">
                    <div className="flex flex-col items-center gap-2">
                      <MdSchool className="text-4xl text-gray-300 dark:text-gray-600" />
                      <p>No hay semestres registrados</p>
                      <button
                        onClick={irANuevo}
                        className="text-blue-600 dark:text-blue-400 hover:underline text-sm font-medium"
                      >
                        Crear primer semestre
                      </button>
                    </div>
                  </td>
                </tr>
              ) : (
                semestres.map((semestre) => (
                  <tr key={semestre.id} className="hover:bg-gray-50 dark:hover:bg-gray-700/50 transition-colors">
                    <td className="px-4 py-3 whitespace-nowrap">
                      <div className="flex items-center gap-2">
                        <div className="w-8 h-8 rounded-full bg-blue-100 dark:bg-blue-900/40 flex items-center justify-center text-blue-600 dark:text-blue-400 text-xs font-bold">
                          {semestre.nombre.charAt(0)}
                        </div>
                        <span className="text-xl font-medium text-gray-900 dark:text-white">
                          {semestre.nombre}
                        </span>
                      </div>
                    </td>
                    <td className="px-4 py-3">
                      <span className="text-base text-gray-700 dark:text-gray-300 line-clamp-1">
                        {semestre.descripcion || '-'}
                      </span>
                    </td>
                    <td className="px-4 py-3 whitespace-nowrap text-center">
                      <span
                        className={`px-2 py-0.5 text-base font-medium rounded-full flex items-center gap-1 inline-flex ${
                          semestre.activo
                            ? 'bg-green-100 dark:bg-green-900/30 text-green-800 dark:text-green-400'
                            : 'bg-gray-100 dark:bg-gray-700 text-gray-600 dark:text-gray-400'
                        }`}
                      >
                        {semestre.activo ? (
                          <MdCheckCircle className="text-xl" />
                        ) : (
                          <MdCancel className="text-xl" />
                        )}
                        {semestre.activo ? 'Activo' : 'Inactivo'}
                      </span>
                    </td>
                    <td className="px-4 py-3 whitespace-nowrap text-center">
                      <div className="flex items-center justify-center gap-1">
                        <button
                          onClick={() => irAEditar(semestre.id)}
                          className="p-2 text-blue-600 dark:text-blue-400 hover:text-blue-800 dark:hover:text-blue-300 hover:bg-blue-50 dark:hover:bg-blue-900/20 rounded transition"
                          title="Editar"
                        >
                          <MdEdit className="text-xl" />
                        </button>
                        <button
                          onClick={() => handleCambiarEstado(semestre.id, !semestre.activo)}
                          className={`p-2 rounded transition ${
                            semestre.activo
                              ? 'text-yellow-600 dark:text-yellow-400 hover:text-yellow-800 dark:hover:text-yellow-300 hover:bg-yellow-50 dark:hover:bg-yellow-900/20'
                              : 'text-green-600 dark:text-green-400 hover:text-green-800 dark:hover:text-green-300 hover:bg-green-50 dark:hover:bg-green-900/20'
                          }`}
                          title={semestre.activo ? 'Desactivar' : 'Activar'}
                        >
                          {semestre.activo ? <MdCancel className="text-xl" /> : <MdCheckCircle className="text-xl" />}
                        </button>
                        <button
                          onClick={() => handleEliminar(semestre)}
                          className="p-2 text-red-600 dark:text-red-400 hover:text-red-800 dark:hover:text-red-300 hover:bg-red-50 dark:hover:bg-red-900/20 rounded transition"
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
      <div className="flex flex-col sm:flex-row justify-between items-center gap-4 mt-6 bg-white dark:bg-gray-800 px-4 py-3 rounded-lg shadow-sm border border-gray-400 dark:border-gray-700">
        <div className="text-sm text-gray-600 dark:text-gray-400">
          Mostrando <span className="font-medium">{semestres.length}</span> de{' '}
          <span className="font-medium">{total}</span> semestres
        </div>
        <div className="flex gap-2">
          <button
            onClick={() => setPage(Math.max(0, page - 1))}
            disabled={page === 0}
            className="px-4 py-2 border border-gray-400 dark:border-gray-600 rounded-lg text-sm font-medium text-gray-700 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700 disabled:opacity-50 disabled:cursor-not-allowed transition"
          >
            Anterior
          </button>
          <button
            onClick={() => setPage(page + 1)}
            disabled={semestres.length < size}
            className="px-4 py-2 border border-gray-400 dark:border-gray-600 rounded-lg text-sm font-medium text-gray-700 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700 disabled:opacity-50 disabled:cursor-not-allowed transition"
          >
            Siguiente
          </button>
        </div>
      </div>

      {/* 🔥 Modal de confirmación de eliminación */}
      {modalEliminar.abierto && modalEliminar.semestre && (
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
              ¿Estás seguro de que quieres eliminar este semestre?
            </p>

            <div className="bg-gray-50 dark:bg-gray-700/50 rounded-lg p-3 mb-4 text-sm">
              <div className="flex items-center gap-3 mb-2">
                <div className="w-10 h-10 rounded-full bg-blue-100 dark:bg-blue-900/40 flex items-center justify-center text-blue-600 dark:text-blue-400 font-bold">
                  {modalEliminar.semestre.nombre.charAt(0)}
                </div>
                <div>
                  <div className="font-medium text-gray-800 dark:text-white">
                    {modalEliminar.semestre.nombre}
                  </div>
                  <div className="text-xs text-gray-500 dark:text-gray-400">
                    ID: {modalEliminar.semestre.id}
                  </div>
                </div>
              </div>
              {modalEliminar.semestre.descripcion && (
                <div className="flex justify-between mt-1">
                  <span className="text-gray-500 dark:text-gray-400">Descripción:</span>
                  <span className="font-medium text-gray-800 dark:text-white truncate max-w-[200px]">
                    {modalEliminar.semestre.descripcion}
                  </span>
                </div>
              )}
              <div className="flex justify-between mt-1">
                <span className="text-gray-500 dark:text-gray-400">Estado:</span>
                <span className={`font-medium ${modalEliminar.semestre.activo ? 'text-green-600 dark:text-green-400' : 'text-gray-600 dark:text-gray-400'}`}>
                  {modalEliminar.semestre.activo ? 'Activo' : 'Inactivo'}
                </span>
              </div>
            </div>

            <p className="text-xs text-red-600 dark:text-red-400 mb-4">
              ⚠️ Esta acción no se puede deshacer.
            </p>

            <div className="flex gap-3">
              <button
                onClick={() => setModalEliminar({ abierto: false, semestre: null })}
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

export default SemestreList;