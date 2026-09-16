// src/components/Aulas.tsx
import React, { useState, useEffect, useRef } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { aulaService } from '../api/aulaService';
import { turnoService } from '../api/turnoService';
import type { Aula, Turno } from '../types';
import { useAuth } from '../context/AuthContext';
import ErrorScreen from '../utils/ErrorScreen';
import {
  MdAdd, MdEdit, MdDelete, MdSearch, MdCheckCircle, MdCancel,
  MdMeetingRoom, MdHome, MdElevator, MdRefresh, MdDescription,
  MdClass, MdSchedule, MdWarning
} from 'react-icons/md';

const Aulas: React.FC = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const { semestreActivo } = useAuth();

  const inputRef = useRef<HTMLInputElement>(null);

  const queryParams = new URLSearchParams(location.search);
  const busquedaInicial = queryParams.get('busqueda') || '';
  const turnoInicial = parseInt(queryParams.get('turno') || '0');   // 🔥 NUEVO
  const pageInicial = parseInt(queryParams.get('page') || '0');

  const [aulas, setAulas] = useState<Aula[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(pageInicial);
  const [size] = useState(20);
  const [busqueda, setBusqueda] = useState(busquedaInicial);
  const [turnoId, setTurnoId] = useState<number>(turnoInicial);     // 🔥 NUEVO
  const [turnos, setTurnos] = useState<Turno[]>([]);                // 🔥 NUEVO
  const [loading, setLoading] = useState(true);
  const [timeoutId, setTimeoutId] = useState<NodeJS.Timeout | null>(null);

  // 🔥 Estados para modal de confirmación y pantalla de error
  const [modalEliminar, setModalEliminar] = useState<{
    abierto: boolean;
    aula: Aula | null;
  }>({ abierto: false, aula: null });

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

  // 🔥 Cargar turnos cuando cambia el semestre
  useEffect(() => {
    cargarTurnos();
  }, [semestreActivo?.id]);

  // Cargar aulas cuando cambian filtros
  useEffect(() => {
    cargarAulas();
  }, [page, busqueda, turnoId, semestreActivo?.id]);   // 🔥 turnoId

  // Debounce para búsqueda
  useEffect(() => {
    if (timeoutId) {
      clearTimeout(timeoutId);
    }

    const id = setTimeout(() => {
      if (busqueda !== busquedaInicial) {
        setPage(0);
        cargarAulas();
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
    if (turnoId > 0) params.set('turno', String(turnoId));   // 🔥 NUEVO
    if (page > 0) params.set('page', String(page));

    const nuevaURL = `${location.pathname}${params.toString() ? `?${params.toString()}` : ''}`;
    navigate(nuevaURL, { replace: true });
  };

  const cargarAulas = async () => {
    setLoading(true);
    try {
      const semestreId = semestreActivo?.id;
      const res = await aulaService.listar(
        page,
        size,
        busqueda,
        semestreId,
        turnoId > 0 ? turnoId : undefined   // 🔥 NUEVO
      );
      setAulas(res.data.content);
      setTotal(res.data.totalElements);
      actualizarURL();
    } catch (error) {
      console.error('Error al cargar aulas:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleBusquedaChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    setBusqueda(e.target.value);
  };

  // 🔥 Cambio de turno → resetear paginación
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
      cargarAulas();
    }
  };

  const handleRecargar = () => {
    if (timeoutId) {
      clearTimeout(timeoutId);
      setTimeoutId(null);
    }
    setPage(0);
    cargarTurnos();
    cargarAulas();
  };

  const irANuevo = () => {
    const params = new URLSearchParams();
    if (busqueda) params.set('busqueda', busqueda);
    if (turnoId > 0) params.set('turno', String(turnoId));   // 🔥 NUEVO
    navigate(`/catalogo/aulas/new${params.toString() ? `?${params.toString()}` : ''}`);
  };

  const irAEditar = (id: number) => {
    const params = new URLSearchParams();
    if (busqueda) params.set('busqueda', busqueda);
    if (turnoId > 0) params.set('turno', String(turnoId));   // 🔥 NUEVO
    navigate(`/catalogo/aulas/edit/${id}${params.toString() ? `?${params.toString()}` : ''}`);
  };

  // 🔥 Abre el modal de confirmación
  const handleEliminar = (aula: Aula) => {
    setModalEliminar({ abierto: true, aula });
  };

  const confirmarEliminar = async () => {
    if (!modalEliminar.aula) return;

    setEliminando(true);
    try {
      await aulaService.eliminar(modalEliminar.aula.id);
      setModalEliminar({ abierto: false, aula: null });
      cargarAulas();
    } catch (error: any) {
      console.error('Error al eliminar:', error);
      const msg = error.response?.data?.message || 'Error al eliminar el aula';
      const codigo = error.response?.data?.error;
      setModalEliminar({ abierto: false, aula: null });

      const esDependencia =
        codigo === 'AULA_EN_USO' ||
        msg.includes('DEPENDENCIAS') ||
        msg.includes('está siendo usado') ||
        msg.includes('asociad') ||
        msg.includes('dependencias') ||
        msg.includes('foreign key') ||
        msg.includes('viola la llave') ||
        msg.includes('no se puede');

      if (esDependencia) {
        let sugerencia = 'Elimina primero los registros asociados o desactiva el aula en lugar de eliminarla.';
        if (msg.includes('asignación') || msg.includes('asignaciones')) {
          sugerencia = 'Esta aula tiene asignaciones activas. Elimínalas primero desde la pantalla de Asignaciones o desactiva el aula.';
        } else if (msg.includes('horario') || msg.includes('horarios')) {
          sugerencia = 'Esta aula tiene horarios generados. Elimina primero los horarios asociados.';
        }

        setErrorPantalla({
          titulo: 'No se puede eliminar el aula',
          mensaje: msg,
          sugerencia,
          tipo: 'error',
          acciones: [
            {
              label: 'Aceptar',
              onClick: () => {
                setErrorPantalla(null);
                cargarAulas();
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
                cargarAulas();
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
      await aulaService.cambiarEstado(id, activo);
      cargarAulas();
    } catch (error) {
      console.error('Error al cambiar estado:', error);
    }
  };

  const truncarTexto = (texto: string | null | undefined, maxLength: number = 25): string => {
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

  if (loading && aulas.length === 0) {
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
          <h1 className="text-3xl font-bold text-gray-800 dark:text-white">Aulas</h1>
          <div className="flex flex-wrap items-center gap-3 mt-1">
            <p className="text-sm text-gray-500 dark:text-gray-400">
              Gestiona las aulas de la institución
            </p>
            {semestreActivo && (
              <div className="inline-flex items-center gap-2 px-3 py-1 bg-blue-50 dark:bg-blue-900/30 rounded-lg text-xs text-blue-600 dark:text-blue-400 border border-blue-200 dark:border-blue-800">
                <MdClass className="text-sm" />
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
            Nueva Aula
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
              Selecciona un semestre en el menú lateral para ver y crear aulas.
            </p>
          </div>
        </div>
      )}

      {/* 🔥 Filtros: búsqueda + turno */}
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
              placeholder="Buscar por nombre, edificio, piso o descripción..."
              value={busqueda}
              onChange={handleBusquedaChange}
              onKeyPress={handleKeyPress}
              className="w-full pl-10 pr-24 py-2.5 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-800 text-gray-900 dark:text-white focus:ring-2 focus:ring-blue-500 focus:border-transparent transition"
            />
            <button
              onClick={() => {
                if (timeoutId) {
                  clearTimeout(timeoutId);
                  setTimeoutId(null);
                }
                setPage(0);
                cargarAulas();
              }}
              className="absolute right-2 top-1/2 transform -translate-y-1/2 bg-blue-600 hover:bg-blue-700 text-white px-3 py-1.5 rounded-lg text-sm transition"
            >
              Buscar
            </button>
          </div>
          <p className="text-xs text-gray-400 mt-1">
            Escribe y espera 500ms o presiona Enter para buscar
          </p>
        </div>

        <div>
          <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
            <MdSchedule className="inline mr-1" />
            Filtrar por Turno
          </label>
          <select
            value={turnoId}
            onChange={handleTurnoChange}
            className="w-full px-4 py-2.5 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:ring-2 focus:ring-blue-500 focus:border-transparent transition"
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
          <div className="bg-white dark:bg-gray-800 rounded-xl shadow-md overflow-hidden border border-gray-100 dark:border-gray-700">
            <div className="overflow-x-auto">
              <table className="min-w-full divide-y divide-gray-200 dark:divide-gray-700">
                <thead className="bg-gray-50 dark:bg-gray-700/50">
                  <tr>
                    <th className="px-3 py-2 text-left text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider">
                      <div className="flex items-center gap-1.5">
                        <MdMeetingRoom className="text-sm" />
                        Nombre
                      </div>
                    </th>
                    <th className="px-3 py-2 text-left text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider">
                      <div className="flex items-center gap-1.5">
                        <MdHome className="text-sm" />
                        Edificio
                      </div>
                    </th>
                    <th className="px-3 py-2 text-left text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider">
                      <div className="flex items-center gap-1.5">
                        <MdElevator className="text-sm" />
                        Piso
                      </div>
                    </th>
                    <th className="px-3 py-2 text-left text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider">
                      <div className="flex items-center gap-1.5">
                        <MdSchedule className="text-sm" />
                        Turno
                      </div>
                    </th>
                    <th className="px-3 py-2 text-left text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider">
                      <div className="flex items-center gap-1.5">
                        <MdDescription className="text-sm" />
                        Descripción
                      </div>
                    </th>
                    <th className="px-3 py-2 text-center text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider">
                      Estado
                    </th>
                    <th className="px-3 py-2 text-center text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider">
                      Acciones
                    </th>
                  </tr>
                </thead>
                <tbody className="bg-white dark:bg-gray-800 divide-y divide-gray-200 dark:divide-gray-700">
                  {aulas.length === 0 ? (
                    <tr>
                      <td colSpan={7} className="px-4 py-6 text-center text-gray-500 dark:text-gray-400">
                        <div className="flex flex-col items-center gap-2">
                          <MdMeetingRoom className="text-4xl text-gray-300 dark:text-gray-600" />
                          <p>
                            {semestreActivo
                              ? `No hay aulas en el semestre ${semestreActivo.nombre}`
                              : 'No hay aulas disponibles'}
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
                            Crear primera aula
                          </button>
                        </div>
                      </td>
                    </tr>
                  ) : (
                    aulas.map((aula) => (
                      <tr
                        key={aula.id}
                        className="hover:bg-gray-50 dark:hover:bg-gray-700/50 transition-colors duration-150"
                      >
                        <td className="px-3 py-3 whitespace-nowrap">
                          <div className="flex items-center gap-2">
                            <div className="w-7 h-7 rounded-lg bg-indigo-100 dark:bg-indigo-900/40 flex items-center justify-center text-indigo-600 dark:text-indigo-400 flex-shrink-0">
                              <MdMeetingRoom className="text-sm" />
                            </div>
                            <span className="text-xl font-medium text-gray-900 dark:text-white">
                              {aula.nombre}
                            </span>
                          </div>
                        </td>
                        <td className="px-3 py-2 whitespace-nowrap">
                          <span className="text-sm text-gray-700 dark:text-gray-300">
                            {truncarTexto(aula.edificio, 20)}
                          </span>
                        </td>
                        <td className="px-3 py-1.5 whitespace-nowrap">
                          <span className="text-sm text-gray-700 dark:text-gray-300">
                            {aula.piso || '-'}
                          </span>
                        </td>
                        {/* 🔥 Columna de turno */}
                        <td className="px-3 py-1.5 whitespace-nowrap">
                          {aula.turnoNombre ? (
                            <span className="inline-flex items-center gap-1 px-2.5 py-0.5 text-xs font-medium rounded-full bg-indigo-100 dark:bg-indigo-900/30 text-indigo-800 dark:text-indigo-400">
                              <MdSchedule className="text-sm" />
                              {aula.turnoNombre}
                            </span>
                          ) : (
                            <span className="text-xs text-gray-400 dark:text-gray-500 italic">
                              Sin turno
                            </span>
                          )}
                        </td>
                        <td className="px-3 py-1.5 whitespace-nowrap">
                          <span
                            className="text-sm text-gray-500 dark:text-gray-400"
                            title={aula.descripcion || ''}
                          >
                            {truncarTexto(aula.descripcion, 25)}
                          </span>
                        </td>
                        <td className="px-3 py-1.5 whitespace-nowrap text-center">
                          <span
                            className={`px-2 py-0.5 text-base font-medium rounded-full flex items-center gap-1 inline-flex ${
                              aula.activo
                                ? 'bg-green-100 dark:bg-green-900/30 text-green-800 dark:text-green-400'
                                : 'bg-red-100 dark:bg-red-900/30 text-red-800 dark:text-red-400'
                            }`}
                          >
                            {aula.activo ? (
                              <MdCheckCircle className="text-base" />
                            ) : (
                              <MdCancel className="text-base" />
                            )}
                            {aula.activo ? 'Activa' : 'Inactiva'}
                          </span>
                        </td>
                        <td className="px-3 py-1.5 whitespace-nowrap text-center">
                          <div className="flex items-center justify-center gap-0.5">
                            <button
                              onClick={() => irAEditar(aula.id)}
                              className="p-1 text-blue-600 dark:text-blue-400 hover:text-blue-800 dark:hover:text-blue-300 hover:bg-blue-50 dark:hover:bg-blue-900/20 rounded transition"
                              title="Editar"
                            >
                              <MdEdit className="text-xl" />
                            </button>
                            <button
                              onClick={() => handleCambiarEstado(aula.id, !aula.activo)}
                              className={`p-1 rounded transition ${
                                aula.activo
                                  ? 'text-yellow-600 dark:text-yellow-400 hover:text-yellow-800 dark:hover:text-yellow-300 hover:bg-yellow-50 dark:hover:bg-yellow-900/20'
                                  : 'text-green-600 dark:text-green-400 hover:text-green-800 dark:hover:text-green-300 hover:bg-green-50 dark:hover:bg-green-900/20'
                              }`}
                              title={aula.activo ? 'Desactivar' : 'Activar'}
                            >
                              {aula.activo ? <MdCancel className="text-xl" /> : <MdCheckCircle className="text-sm" />}
                            </button>
                            <button
                              onClick={() => handleEliminar(aula)}
                              className="p-1 text-red-600 dark:text-red-400 hover:text-red-800 dark:hover:text-red-300 hover:bg-red-50 dark:hover:bg-red-900/20 rounded transition"
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
          <div className="flex flex-col sm:flex-row justify-between items-center gap-4 mt-6 bg-white dark:bg-gray-800 px-4 py-3 rounded-lg shadow-sm border border-gray-100 dark:border-gray-700">
            <div className="text-sm text-gray-600 dark:text-gray-400">
              Mostrando <span className="font-medium">{aulas.length}</span> de{' '}
              <span className="font-medium">{total}</span> aulas
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
                className="px-4 py-2 border border-gray-300 dark:border-gray-600 rounded-lg text-sm font-medium text-gray-700 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700 disabled:opacity-50 disabled:cursor-not-allowed transition"
              >
                Anterior
              </button>
              <button
                onClick={() => setPage(page + 1)}
                disabled={aulas.length < size}
                className="px-4 py-2 border border-gray-300 dark:border-gray-600 rounded-lg text-sm font-medium text-gray-700 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700 disabled:opacity-50 disabled:cursor-not-allowed transition"
              >
                Siguiente
              </button>
            </div>
          </div>
        </>
      )}

      {/* 🔥 Modal de confirmación de eliminación */}
      {modalEliminar.abierto && modalEliminar.aula && (
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
              ¿Estás seguro de que quieres eliminar esta aula?
            </p>

            <div className="bg-gray-50 dark:bg-gray-700/50 rounded-lg p-3 mb-4 text-sm">
              <div className="flex items-center gap-3 mb-2">
                <div className="w-10 h-10 rounded-lg bg-indigo-100 dark:bg-indigo-900/40 flex items-center justify-center text-indigo-600 dark:text-indigo-400">
                  <MdMeetingRoom className="text-lg" />
                </div>
                <div>
                  <div className="font-medium text-gray-800 dark:text-white">
                    {modalEliminar.aula.nombre}
                  </div>
                  <div className="text-xs text-gray-500 dark:text-gray-400">
                    {modalEliminar.aula.edificio || 'Sin edificio'}
                    {modalEliminar.aula.piso ? ` · ${modalEliminar.aula.piso}` : ''}
                  </div>
                </div>
              </div>
              {modalEliminar.aula.turnoNombre && (
                <div className="flex justify-between mt-1">
                  <span className="text-gray-500 dark:text-gray-400">Turno:</span>
                  <span className="font-medium text-gray-800 dark:text-white">
                    {modalEliminar.aula.turnoNombre}
                  </span>
                </div>
              )}
              {modalEliminar.aula.semestreNombre && (
                <div className="flex justify-between mt-1">
                  <span className="text-gray-500 dark:text-gray-400">Semestre:</span>
                  <span className="font-medium text-gray-800 dark:text-white">
                    {modalEliminar.aula.semestreNombre}
                  </span>
                </div>
              )}
            </div>

            <p className="text-xs text-red-600 dark:text-red-400 mb-4">
              ⚠️ Esta acción no se puede deshacer.
            </p>

            <div className="flex gap-3">
              <button
                onClick={() => setModalEliminar({ abierto: false, aula: null })}
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

export default Aulas;