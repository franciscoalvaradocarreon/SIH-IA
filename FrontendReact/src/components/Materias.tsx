// src/components/Materias.tsx
import React, { useState, useEffect, useRef } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { materiaService } from '../api/materiaService';
import { turnoService } from '../api/turnoService';
import { useAuth } from '../context/AuthContext';
import type { Materia, Turno } from '../types';
import ErrorScreen from '../utils/ErrorScreen';
import {
  MdAdd, MdEdit, MdDelete, MdSearch, MdCheckCircle, MdCancel,
  MdRefresh, MdBook, MdAccessTime, MdGrade, MdSchedule, MdWarning
} from 'react-icons/md';

const Materias: React.FC = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const { semestreActivo } = useAuth();

  const inputRef = useRef<HTMLInputElement>(null);

  // Leer filtros de la URL
  const queryParams = new URLSearchParams(location.search);
  const busquedaInicial = queryParams.get('busqueda') || '';
  const turnoInicial = parseInt(queryParams.get('turno') || '0');
  const pageInicial = parseInt(queryParams.get('page') || '0');

  const [materias, setMaterias] = useState<Materia[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(pageInicial);
  const [size] = useState(12);
  const [busqueda, setBusqueda] = useState(busquedaInicial);
  const [turnoId, setTurnoId] = useState<number>(turnoInicial);
  const [turnos, setTurnos] = useState<Turno[]>([]);
  const [loading, setLoading] = useState(true);
  const [timeoutId, setTimeoutId] = useState<NodeJS.Timeout | null>(null);

  // 🔥 Estados para modal de confirmación y pantalla de error
  const [modalEliminar, setModalEliminar] = useState<{
    abierto: boolean;
    materia: Materia | null;
  }>({ abierto: false, materia: null });

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

  // Mantener foco en input
  useEffect(() => {
    if (!loading && inputRef.current) {
      inputRef.current.focus();
      const length = inputRef.current.value.length;
      inputRef.current.setSelectionRange(length, length);
    }
  }, [loading, busqueda]);

  // Cargar turnos cuando cambia el semestre
  useEffect(() => {
    cargarTurnos();
  }, [semestreActivo?.id]);

  // Cargar materias cuando cambian filtros
  useEffect(() => {
    cargarMaterias();
  }, [page, busqueda, turnoId, semestreActivo?.id]);

  // Debounce para búsqueda
  useEffect(() => {
    if (timeoutId) {
      clearTimeout(timeoutId);
    }

    const id = setTimeout(() => {
      if (busqueda !== busquedaInicial) {
        setPage(0);
        cargarMaterias();
      }
    }, 500);

    setTimeoutId(id);

    return () => {
      if (timeoutId) {
        clearTimeout(timeoutId);
      }
    };
  }, [busqueda]);

  const cargarTurnos = async () => {
    if (!semestreActivo?.id) {
      setTurnos([]);
      return;
    }
    try {
      const res = await turnoService.listar(0, 100, '', semestreActivo.id);
      const activos = res.data.content.filter((t: Turno) => t.activo === true);
      setTurnos(activos);
    } catch (error) {
      console.error('Error al cargar turnos:', error);
      setTurnos([]);
    }
  };

  const actualizarURL = () => {
    const params = new URLSearchParams();
    if (busqueda) params.set('busqueda', busqueda);
    if (turnoId > 0) params.set('turno', String(turnoId));
    if (page > 0) params.set('page', String(page));

    const nuevaURL = `${location.pathname}${params.toString() ? `?${params.toString()}` : ''}`;
    navigate(nuevaURL, { replace: true });
  };

  const cargarMaterias = async () => {
    setLoading(true);
    try {
      const semestreId = semestreActivo?.id;
      const res = await materiaService.listar(
        page,
        size,
        busqueda,
        semestreId,
        turnoId > 0 ? turnoId : undefined
      );
      setMaterias(res.data.content);
      setTotal(res.data.totalElements);
      actualizarURL();
    } catch (error) {
      console.error('Error al cargar materias:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleBusquedaChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    setBusqueda(e.target.value);
  };

  const handleTurnoChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    setTurnoId(Number(e.target.value));
    setPage(0);
  };

  const handleKeyPress = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter') {
      if (timeoutId) {
        clearTimeout(timeoutId);
        setTimeoutId(null);
      }
      setPage(0);
      cargarMaterias();
    }
  };

  const handleRecargar = () => {
    if (timeoutId) {
      clearTimeout(timeoutId);
      setTimeoutId(null);
    }
    setPage(0);
    cargarTurnos();
    cargarMaterias();
  };

  const irANuevo = () => {
    const params = new URLSearchParams();
    if (busqueda) params.set('busqueda', busqueda);
    if (turnoId > 0) params.set('turno', String(turnoId));
    navigate(`/catalogo/materias/new${params.toString() ? `?${params.toString()}` : ''}`);
  };

  const irAEditar = (id: number) => {
    const params = new URLSearchParams();
    if (busqueda) params.set('busqueda', busqueda);
    if (turnoId > 0) params.set('turno', String(turnoId));
    navigate(`/catalogo/materias/edit/${id}${params.toString() ? `?${params.toString()}` : ''}`);
  };

  // 🔥 Abre el modal de confirmación
  const handleEliminar = (materia: Materia) => {
    setModalEliminar({ abierto: true, materia });
  };

  const confirmarEliminar = async () => {
    if (!modalEliminar.materia) return;

    setEliminando(true);
    try {
      await materiaService.eliminar(modalEliminar.materia.id);
      setModalEliminar({ abierto: false, materia: null });
      cargarMaterias();
    } catch (error: any) {
      console.error('Error al eliminar:', error);
      const msg = error.response?.data?.message || 'Error al eliminar la materia';
      setModalEliminar({ abierto: false, materia: null });

      // 🔥 Detectar si es error de dependencias
      const esDependencia = msg.includes('DEPENDENCIAS') ||
                            msg.includes('está siendo usado') ||
                            msg.includes('referida desde la tabla') ||
                            msg.includes('asociad') ||
                            msg.includes('dependencias') ||
                            msg.includes('foreign key') ||
                            msg.includes('viola la llave') ||
                            msg.includes('no se puede');

      if (esDependencia) {
        // Extraer nombre de la tabla
        const tablaMatch = msg.match(/tabla:\s*(\w+)/) || msg.match(/tabla «(\w+)»/);
        const tabla = tablaMatch ? tablaMatch[1] : null;

        let sugerencia = 'Elimina primero los registros asociados o desactiva la materia en lugar de eliminarla.';
        if (tabla === 'asignacion' || tabla === 'asignaciones') {
          sugerencia = 'Esta materia tiene asignaciones activas. Elimina primero las asignaciones o desactiva la materia.';
        } else if (tabla === 'horario' || tabla === 'horarios') {
          sugerencia = 'Esta materia tiene horarios generados. Elimina primero los horarios asociados.';
        }

        setErrorPantalla({
          titulo: 'No se puede eliminar la materia',
          mensaje: tabla
            ? `Esta materia está siendo usada en la tabla "${tabla}".`
            : msg,
          sugerencia,
          tipo: 'error',
          acciones: [
            {
              label: 'Aceptar',
              onClick: () => {
                setErrorPantalla(null);
                cargarMaterias();
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
                cargarMaterias();
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
      await materiaService.cambiarEstado(id, activo);
      cargarMaterias();
    } catch (error) {
      console.error('Error al cambiar estado:', error);
    }
  };

  const truncarTexto = (texto: string, maxLength: number = 25): string => {
    if (!texto) return '';
    if (texto.length <= maxLength) return texto;
    return texto.substring(0, maxLength) + '...';
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

  if (loading && materias.length === 0) {
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
          <h1 className="text-3xl font-bold text-gray-800 dark:text-white">Materias</h1>
          <div className="flex flex-wrap items-center gap-3 mt-1">
            <p className="text-sm text-gray-500 dark:text-gray-400">
              Gestiona las materias de la institución
            </p>
            {semestreActivo && (
              <div className="inline-flex items-center gap-2 px-3 py-1 bg-blue-50 dark:bg-blue-900/30 rounded-lg text-xs text-blue-600 dark:text-blue-400 border border-blue-200 dark:border-blue-800">
                <MdBook className="text-sm" />
                Semestre: <strong>{semestreActivo.nombre}</strong>
              </div>
            )}
          </div>
        </div>
        <div className="flex gap-3">
          <button
            onClick={handleRecargar}
            className="flex items-center gap-2 bg-gray-200 dark:bg-gray-700 hover:bg-gray-300 dark:hover:bg-gray-600 text-gray-700 dark:text-gray-300 px-4 py-2.5 rounded-lg shadow-md transition duration-200"
            title="Recargar datos"
          >
            <MdRefresh className="text-xl" />
            Recargar
          </button>
          <button
            onClick={irANuevo}
            className="flex items-center gap-2 bg-blue-600 hover:bg-blue-700 text-white px-4 py-2.5 rounded-lg shadow-md transition duration-200"
          >
            <MdAdd className="text-xl" />
            Nueva Materia
          </button>
        </div>
      </div>

      {/* Warning si no hay semestre */}
      {!semestreActivo && (
        <div className="mb-4 bg-yellow-50 dark:bg-yellow-900/30 border border-yellow-200 dark:border-yellow-800 rounded-lg p-4 flex items-start gap-3">
          <MdWarning className="text-xl text-yellow-600 dark:text-yellow-400 mt-0.5" />
          <div>
            <p className="text-sm font-medium text-yellow-800 dark:text-yellow-200">
              No hay semestre activo
            </p>
            <p className="text-sm text-yellow-700 dark:text-yellow-300">
              Selecciona un semestre en el menú lateral para ver y crear materias.
            </p>
          </div>
        </div>
      )}

      {/* Filtros: búsqueda + turno */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4 mb-6">
        <div>
          <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
            <MdSearch className="inline mr-1" />
            Buscar
          </label>
          <div className="relative">
            <MdSearch className="absolute left-3 top-1/2 transform -translate-y-1/2 text-gray-400 text-xl" />
            <input
              ref={inputRef}
              type="text"
              placeholder="Buscar por nombre o clave..."
              value={busqueda}
              onChange={handleBusquedaChange}
              onKeyPress={handleKeyPress}
              className="w-full pl-10 pr-24 py-2.5 border border-gray-400 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-800 text-gray-900 dark:text-white focus:ring-2 focus:ring-blue-500 focus:border-transparent transition"
            />
            <button
              onClick={() => {
                if (timeoutId) {
                  clearTimeout(timeoutId);
                  setTimeoutId(null);
                }
                setPage(0);
                cargarMaterias();
              }}
              className="absolute right-2 top-1/2 transform -translate-y-1/2 bg-blue-600 hover:bg-blue-700 text-white px-3 py-1.5 rounded-lg text-sm transition"
            >
              Buscar
            </button>
          </div>
        </div>

        <div>
          <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
            <MdSchedule className="inline mr-1" />
            Filtrar por Turno
          </label>
          <select
            value={turnoId}
            onChange={handleTurnoChange}
            className="w-full px-4 py-2.5 border border-gray-400 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:ring-2 focus:ring-blue-500 focus:border-transparent transition"
          >
            <option value={0}>Todos los turnos</option>
            {turnos.map((t) => (
              <option key={t.id} value={t.id}>
                {t.nombre}
              </option>
            ))}
          </select>
          {turnos.length === 0 && semestreActivo && (
            <p className="text-xs text-yellow-600 dark:text-yellow-400 mt-1">
              No hay turnos activos en este semestre
            </p>
          )}
        </div>
      </div>

      {loading ? (
        <div className="flex justify-center py-12">
          <div className="animate-spin rounded-full h-12 w-12 border-4 border-blue-500 border-t-transparent"></div>
        </div>
      ) : (
        <>
          <div className="bg-white dark:bg-gray-800 rounded-xl shadow-md overflow-hidden border border-gray-400 dark:border-gray-700">
            <div className="overflow-x-auto">
              <table className="min-w-full divide-y divide-gray-400 dark:divide-gray-700">
                <thead className="bg-gray-50 dark:bg-gray-700/50">
                  <tr>
                    <th className="px-3 py-2 text-left text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider">
                      <div className="flex items-center gap-1.5">
                        <MdBook className="text-sm" />
                        Clave
                      </div>
                    </th>
                    <th className="px-3 py-2 text-left text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider">
                      <div className="flex items-center gap-1.5">
                        <MdBook className="text-sm" />
                        Nombre
                      </div>
                    </th>
                    <th className="px-3 py-2 text-left text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider">
                      <div className="flex items-center gap-1.5">
                        <MdSchedule className="text-sm" />
                        Turno
                      </div>
                    </th>
                    <th className="px-3 py-2 text-center text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider">
                      <div className="flex items-center gap-1.5">
                        <MdGrade className="text-sm" />
                        Créditos
                      </div>
                    </th>
                    <th className="px-3 py-2 text-center text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider">
                      <div className="flex items-center gap-1.5">
                        <MdAccessTime className="text-sm" />
                        Hrs/Sem
                      </div>
                    </th>
                    <th className="px-3 py-2 text-center text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider">
                      Color
                    </th>
                    <th className="px-3 py-2 text-center text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider">
                      Estado
                    </th>
                    <th className="px-3 py-2 text-center text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider">
                      Acciones
                    </th>
                  </tr>
                </thead>
                <tbody className="bg-white dark:bg-gray-800 divide-y divide-gray-400 dark:divide-gray-700">
                  {materias.length === 0 ? (
                    <tr>
                      <td colSpan={8} className="px-4 py-6 text-center text-gray-500 dark:text-gray-400">
                        <div className="flex flex-col items-center gap-2">
                          <MdBook className="text-4xl text-gray-300 dark:text-gray-600" />
                          <p>
                            {semestreActivo
                              ? `No hay materias en el semestre ${semestreActivo.nombre}`
                              : 'No hay materias disponibles'}
                            {turnoId > 0 && turnos.find(t => t.id === turnoId) && (
                              <span className="ml-1">
                                (Turno: {turnos.find(t => t.id === turnoId)?.nombre})
                              </span>
                            )}
                          </p>
                          <button
                            onClick={irANuevo}
                            className="text-blue-600 dark:text-blue-400 hover:underline text-sm font-medium"
                          >
                            Crear primera materia
                          </button>
                        </div>
                      </td>
                    </tr>
                  ) : (
                    materias.map((materia) => (
                      <tr
                        key={materia.id}
                        className="hover:bg-gray-50 dark:hover:bg-gray-700/50 transition-colors duration-150"
                      >
                        <td className="px-3 py-1.5 whitespace-nowrap">
                          <div className="flex items-center gap-2">
                            <div
                              className="w-2.5 h-2.5 rounded-full flex-shrink-0"
                              style={{ backgroundColor: materia.colorHex || '#3B82F6' }}
                            />
                            <span className="text-lg font-medium text-gray-900 dark:text-white">
                              {materia.clave}
                            </span>
                          </div>
                        </td>
                        <td className="px-3 py-1.5 whitespace-nowrap">
                          <span
                            className="text-sm text-gray-700 dark:text-gray-300"
                            title={materia.nombre}
                          >
                            {truncarTexto(materia.nombre, 50)}
                          </span>
                        </td>
                        <td className="px-3 py-1.5 whitespace-nowrap">
                          {materia.turnoNombre ? (
                            <span className="inline-flex items-center gap-1 px-2.5 py-0.5 text-xs font-medium rounded-full bg-indigo-100 dark:bg-indigo-900/30 text-indigo-800 dark:text-indigo-400">
                              <MdSchedule className="text-sm" />
                              {materia.turnoNombre}
                            </span>
                          ) : (
                            <span className="text-xs text-gray-400 dark:text-gray-500 italic">
                              Sin turno
                            </span>
                          )}
                        </td>
                        <td className="px-3 py-1.5 whitespace-nowrap text-center">
                          <span className="text-sm text-gray-700 dark:text-gray-300">
                            {materia.creditos || 0}
                          </span>
                        </td>
                        <td className="px-3 py-1.5 whitespace-nowrap text-center">
                          <span className="text-sm text-gray-700 dark:text-gray-300">
                            {materia.horasSemana || 0}
                          </span>
                        </td>
                        <td className="px-3 py-1.5 whitespace-nowrap text-center">
                          {materia.colorHex ? (
                            <div
                              className="w-5 h-5 rounded-full border border-gray-400 dark:border-gray-600 mx-auto"
                              style={{ backgroundColor: materia.colorHex }}
                              title={materia.colorHex}
                            />
                          ) : (
                            <span className="text-xs text-gray-400">-</span>
                          )}
                        </td>
                        <td className="px-3 py-1.5 whitespace-nowrap text-center">
                          <span
                            className={`px-2 py-0.5 text-base font-medium rounded-full flex items-center gap-1 inline-flex ${
                              materia.activo
                                ? 'bg-green-100 dark:bg-green-900/30 text-green-800 dark:text-green-400'
                                : 'bg-red-100 dark:bg-red-900/30 text-red-800 dark:text-red-400'
                            }`}
                          >
                            {materia.activo ? (
                              <MdCheckCircle className="text-lg" />
                            ) : (
                              <MdCancel className="text-lg" />
                            )}
                            {materia.activo ? 'Activa' : 'Inactiva'}
                          </span>
                        </td>
                        <td className="px-3 py-1.5 whitespace-nowrap text-center">
                          <div className="flex items-center justify-center gap-0.5">
                            <button
                              onClick={() => irAEditar(materia.id)}
                              className="p-2 text-blue-600 dark:text-blue-400 hover:text-blue-800 dark:hover:text-blue-300 hover:bg-blue-50 dark:hover:bg-blue-900/20 rounded transition"
                              title="Editar"
                            >
                              <MdEdit className="text-xl" />
                            </button>
                            <button
                              onClick={() => handleCambiarEstado(materia.id, !materia.activo)}
                              className={`p-2 rounded transition ${
                                materia.activo
                                  ? 'text-yellow-600 dark:text-yellow-400 hover:text-yellow-800 dark:hover:text-yellow-300 hover:bg-yellow-50 dark:hover:bg-yellow-900/20'
                                  : 'text-green-600 dark:text-green-400 hover:text-green-800 dark:hover:text-green-300 hover:bg-green-50 dark:hover:bg-green-900/20'
                              }`}
                              title={materia.activo ? 'Desactivar' : 'Activar'}
                            >
                              {materia.activo ? <MdCancel className="text-xl" /> : <MdCheckCircle className="text-xl" />}
                            </button>
                            <button
                              onClick={() => handleEliminar(materia)}
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
              Mostrando <span className="font-medium">{materias.length}</span> de{' '}
              <span className="font-medium">{total}</span> materias
              {semestreActivo && (
                <span className="ml-2 text-blue-600 dark:text-blue-400">
                  (Semestre: {semestreActivo.nombre})
                </span>
              )}
              {turnoId > 0 && (
                <span className="ml-2 text-indigo-600 dark:text-indigo-400">
                  · Turno: {turnos.find(t => t.id === turnoId)?.nombre}
                </span>
              )}
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
                disabled={materias.length < size}
                className="px-4 py-2 border border-gray-400 dark:border-gray-600 rounded-lg text-sm font-medium text-gray-700 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700 disabled:opacity-50 disabled:cursor-not-allowed transition"
              >
                Siguiente
              </button>
            </div>
          </div>
        </>
      )}

      {/* 🔥 Modal de confirmación de eliminación */}
      {modalEliminar.abierto && modalEliminar.materia && (
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
              ¿Estás seguro de que quieres eliminar esta materia?
            </p>

            <div className="bg-gray-50 dark:bg-gray-700/50 rounded-lg p-3 mb-4 text-sm">
              <div className="flex items-center gap-3 mb-2">
                <div
                  className="w-10 h-10 rounded-lg flex items-center justify-center flex-shrink-0"
                  style={{ backgroundColor: `${modalEliminar.materia.colorHex}20` }}
                >
                  <div
                    className="w-5 h-5 rounded-full"
                    style={{ backgroundColor: modalEliminar.materia.colorHex || '#808080' }}
                  />
                </div>
                <div>
                  <div className="font-medium text-gray-800 dark:text-white">
                    {modalEliminar.materia.nombre}
                  </div>
                  <div className="text-xs text-gray-500 dark:text-gray-400 font-mono">
                    {modalEliminar.materia.clave}
                  </div>
                </div>
              </div>
              {modalEliminar.materia.turnoNombre && (
                <div className="flex justify-between mt-1">
                  <span className="text-gray-500 dark:text-gray-400">Turno:</span>
                  <span className="font-medium text-gray-800 dark:text-white">
                    {modalEliminar.materia.turnoNombre}
                  </span>
                </div>
              )}
              {modalEliminar.materia.semestreNombre && (
                <div className="flex justify-between mt-1">
                  <span className="text-gray-500 dark:text-gray-400">Semestre:</span>
                  <span className="font-medium text-gray-800 dark:text-white">
                    {modalEliminar.materia.semestreNombre}
                  </span>
                </div>
              )}
            </div>

            <p className="text-xs text-red-600 dark:text-red-400 mb-4">
              ⚠️ Esta acción no se puede deshacer.
            </p>

            <div className="flex gap-3">
              <button
                onClick={() => setModalEliminar({ abierto: false, materia: null })}
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

export default Materias;