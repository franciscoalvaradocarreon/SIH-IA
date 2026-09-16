import React, { useState, useEffect, useRef } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { especialidadService } from '../api/especialidadService';
import { turnoService } from '../api/turnoService';
import type { Especialidad, Turno } from '../types';
import { useAuth } from '../context/AuthContext';
import ErrorScreen from '../utils/ErrorScreen';
import {
  MdAdd, MdEdit, MdDelete, MdSearch, MdCategory,
  MdRefresh, MdWarning, MdSchedule
} from 'react-icons/md';

const Especialidades: React.FC = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const { semestreActivo } = useAuth();

  // REFERENCIA PARA EL INPUT DE BÚSQUEDA
  const inputRef = useRef<HTMLInputElement>(null);

  // Leer filtros de la URL
  const queryParams = new URLSearchParams(location.search);
  const busquedaInicial = queryParams.get('busqueda') || '';
  const turnoInicial = parseInt(queryParams.get('turno') || '0');
  const pageInicial = parseInt(queryParams.get('page') || '0');

  const [especialidades, setEspecialidades] = useState<Especialidad[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(pageInicial);
  const [size] = useState(12);
  const [busqueda, setBusqueda] = useState(busquedaInicial);
  const [turnoId, setTurnoId] = useState<number>(turnoInicial);  // 🔥 NUEVO
  const [turnos, setTurnos] = useState<Turno[]>([]);              // 🔥 NUEVO
  const [loading, setLoading] = useState(true);
  const [timeoutId, setTimeoutId] = useState<NodeJS.Timeout | null>(null);

  // Estados para modal de confirmación y pantalla de error
  const [modalEliminar, setModalEliminar] = useState<{
    abierto: boolean;
    especialidad: Especialidad | null;
  }>({ abierto: false, especialidad: null });

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

  // EFECTO PARA MANTENER EL FOCO EN EL INPUT DE BÚSQUEDA
  useEffect(() => {
    if (!loading && inputRef.current) {
      inputRef.current.focus();
      const length = inputRef.current.value.length;
      inputRef.current.setSelectionRange(length, length);
    }
  }, [loading, busqueda]);

  // 🔥 Cargar turnos cuando cambia el semestre activo
  useEffect(() => {
    cargarTurnos();
  }, [semestreActivo?.id]);

  // 🔥 Cargar especialidades cuando cambia cualquier filtro
  useEffect(() => {
    cargarEspecialidades();
  }, [page, busqueda, turnoId, semestreActivo?.id]);

  // Debounce para búsqueda
  useEffect(() => {
    if (timeoutId) {
      clearTimeout(timeoutId);
    }

    const id = setTimeout(() => {
      if (busqueda !== busquedaInicial) {
        setPage(0);
        cargarEspecialidades();
      }
    }, 500);

    setTimeoutId(id);

    return () => {
      if (timeoutId) {
        clearTimeout(timeoutId);
      }
    };
  }, [busqueda]);

  // 🔥 Cargar turnos activos del semestre
  const cargarTurnos = async () => {
    if (!semestreActivo?.id) return;
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

  const cargarEspecialidades = async () => {
    setLoading(true);
    try {
      const semestreId = semestreActivo?.id;
      const res = await especialidadService.listar(
        page,
        size,
        busqueda,
        semestreId,
        turnoId > 0 ? turnoId : undefined   // 🔥 NUEVO: solo enviar si > 0
      );
      setEspecialidades(res.data.content);
      setTotal(res.data.totalElements);
      actualizarURL();
    } catch (error) {
      console.error('Error al cargar especialidades:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleBusquedaChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const value = e.target.value;
    setBusqueda(value);
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
      cargarEspecialidades();
    }
  };

  const handleRecargar = () => {
    if (timeoutId) {
      clearTimeout(timeoutId);
      setTimeoutId(null);
    }
    setPage(0);
    cargarTurnos();          // 🔥 NUEVO: refrescar turnos por si cambiaron
    cargarEspecialidades();
  };

  const irANuevo = () => {
    const params = new URLSearchParams();
    if (busqueda) params.set('busqueda', busqueda);
    if (turnoId > 0) params.set('turno', String(turnoId));   // 🔥 NUEVO
    navigate(`/catalogo/especialidades/new${params.toString() ? `?${params.toString()}` : ''}`);
  };

  const irAEditar = (id: number) => {
    const params = new URLSearchParams();
    if (busqueda) params.set('busqueda', busqueda);
    if (turnoId > 0) params.set('turno', String(turnoId));   // 🔥 NUEVO
    navigate(`/catalogo/especialidades/edit/${id}${params.toString() ? `?${params.toString()}` : ''}`);
  };

  // 🔥 Abre el modal de confirmación
  const handleEliminar = (especialidad: Especialidad) => {
    setModalEliminar({ abierto: true, especialidad });
  };

  const confirmarEliminar = async () => {
    if (!modalEliminar.especialidad) return;

    setEliminando(true);
    try {
      await especialidadService.eliminar(modalEliminar.especialidad.id);
      setModalEliminar({ abierto: false, especialidad: null });
      cargarEspecialidades();
    } catch (error: any) {
      console.error('Error al eliminar:', error);
      const msg = error.response?.data?.message || 'Error al eliminar la especialidad';
      setModalEliminar({ abierto: false, especialidad: null });

      // Detectar si es error de dependencias
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

        let sugerencia = 'Elimina primero los registros asociados o modifica la especialidad en lugar de eliminarla.';
        if (tabla === 'grupo' || tabla === 'grupos') {
          sugerencia = 'Esta especialidad tiene grupos asociados. Elimina primero los grupos o desvincúlalos.';
        } else if (tabla === 'asignacion' || tabla === 'asignaciones') {
          sugerencia = 'Esta especialidad tiene asignaciones asociadas. Elimina primero las asignaciones.';
        }

        setErrorPantalla({
          titulo: 'No se puede eliminar la especialidad',
          mensaje: tabla
            ? `Esta especialidad está siendo usada en la tabla "${tabla}".`
            : msg,
          sugerencia,
          tipo: 'error',
          acciones: [
            {
              label: 'Aceptar',
              onClick: () => {
                setErrorPantalla(null);
                cargarEspecialidades();
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
                cargarEspecialidades();
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

  // Pantalla de error especial
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

  if (loading && especialidades.length === 0) {
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
          <h1 className="text-3xl font-bold text-gray-800 dark:text-white">Especialidades</h1>
          <p className="text-sm text-gray-500 dark:text-gray-400 mt-1">
            Gestiona las especialidades de la institución
          </p>
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
            Nueva Especialidad
          </button>
        </div>
      </div>

      {/* Filtros */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4 mb-6">
        {/* Búsqueda */}
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
              placeholder="Buscar por nombre..."
              value={busqueda}
              onChange={handleBusquedaChange}
              onKeyPress={handleKeyPress}
              className="w-full pl-10 pr-4 py-2.5 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-800 text-gray-900 dark:text-white focus:ring-2 focus:ring-blue-500 focus:border-transparent transition"
            />
          </div>
          <p className="text-xs text-gray-400 mt-1">
            Escribe y espera 500ms o presiona Enter para buscar
          </p>
        </div>

        {/* 🔥 Filtro por turno */}
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
                    <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider">
                      <div className="flex items-center gap-2">
                        <MdCategory className="text-sm" />
                        Nombre
                      </div>
                    </th>
                    <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider">
                      <div className="flex items-center gap-2">
                        <MdSchedule className="text-sm" />
                        Turno
                      </div>
                    </th>
                    <th className="px-4 py-3 text-center text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider">
                      Acciones
                    </th>
                  </tr>
                </thead>
                <tbody className="bg-white dark:bg-gray-800 divide-y divide-gray-200 dark:divide-gray-700">
                  {especialidades.length === 0 ? (
                    <tr>
                      <td colSpan={3} className="px-4 py-8 text-center text-gray-500 dark:text-gray-400">
                        <div className="flex flex-col items-center gap-2">
                          <MdCategory className="text-4xl text-gray-300 dark:text-gray-600" />
                          <p>No hay especialidades disponibles</p>
                          <button
                            onClick={irANuevo}
                            className="text-blue-600 dark:text-blue-400 hover:underline text-sm font-medium"
                          >
                            Crear primera especialidad
                          </button>
                        </div>
                      </td>
                    </tr>
                  ) : (
                    especialidades.map((especialidad) => (
                      <tr
                        key={especialidad.id}
                        className="hover:bg-gray-50 dark:hover:bg-gray-700/50 transition-colors duration-150"
                      >
                        <td className="px-4 py-3 whitespace-nowrap">
                          <div className="flex items-center gap-3">
                            <div className="w-8 h-8 rounded-full bg-blue-100 dark:bg-blue-900/40 flex items-center justify-center text-blue-600 dark:text-blue-400">
                              <MdCategory className="text-sm" />
                            </div>
                            <span className="text-xl font-medium text-gray-900 dark:text-white">
                              {especialidad.nombre}
                            </span>
                          </div>
                        </td>
                        {/* 🔥 Columna de turno */}
                        <td className="px-4 py-3 whitespace-nowrap">
                          {especialidad.turnoNombre ? (
                            <span className="inline-flex items-center gap-1 px-2.5 py-0.5 text-xs font-medium rounded-full bg-indigo-100 dark:bg-indigo-900/30 text-indigo-800 dark:text-indigo-400">
                              <MdSchedule className="text-sm" />
                              {especialidad.turnoNombre}
                            </span>
                          ) : (
                            <span className="text-xs text-gray-400 dark:text-gray-500 italic">
                              Sin turno
                            </span>
                          )}
                        </td>
                        <td className="px-4 py-3 whitespace-nowrap text-center">
                          <div className="flex items-center justify-center gap-1">
                            <button
                              onClick={() => irAEditar(especialidad.id)}
                              className="p-1.5 text-blue-600 dark:text-blue-400 hover:text-blue-800 dark:hover:text-blue-300 hover:bg-blue-50 dark:hover:bg-blue-900/20 rounded-lg transition"
                              title="Editar"
                            >
                              <MdEdit className="text-xl" />
                            </button>
                            <button
                              onClick={() => handleEliminar(especialidad)}
                              className="p-1.5 text-red-600 dark:text-red-400 hover:text-red-800 dark:hover:text-red-300 hover:bg-red-50 dark:hover:bg-red-900/20 rounded-lg transition"
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
              Mostrando <span className="font-medium">{especialidades.length}</span> de{' '}
              <span className="font-medium">{total}</span> especialidades
              {turnoId > 0 && (
                <span className="ml-2 text-indigo-600 dark:text-indigo-400">
                  (Turno: {turnos.find(t => t.id === turnoId)?.nombre})
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
                disabled={especialidades.length < size}
                className="px-4 py-2 border border-gray-300 dark:border-gray-600 rounded-lg text-sm font-medium text-gray-700 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700 disabled:opacity-50 disabled:cursor-not-allowed transition"
              >
                Siguiente
              </button>
            </div>
          </div>
        </>
      )}

      {/* Modal de confirmación de eliminación */}
      {modalEliminar.abierto && modalEliminar.especialidad && (
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
              ¿Estás seguro de que quieres eliminar esta especialidad?
            </p>

            <div className="bg-gray-50 dark:bg-gray-700/50 rounded-lg p-3 mb-4 text-sm">
              <div className="flex items-center gap-3 mb-2">
                <div className="w-10 h-10 rounded-lg bg-blue-100 dark:bg-blue-900/40 flex items-center justify-center text-blue-600 dark:text-blue-400">
                  <MdCategory className="text-lg" />
                </div>
                <div>
                  <div className="font-medium text-gray-800 dark:text-white">
                    {modalEliminar.especialidad.nombre}
                  </div>
                  <div className="text-xs text-gray-500 dark:text-gray-400">
                    ID: {modalEliminar.especialidad.id}
                  </div>
                </div>
              </div>
              {modalEliminar.especialidad.turnoNombre && (
                <div className="flex justify-between mt-2">
                  <span className="text-gray-500 dark:text-gray-400">Turno:</span>
                  <span className="font-medium text-gray-800 dark:text-white">
                    {modalEliminar.especialidad.turnoNombre}
                  </span>
                </div>
              )}
            </div>

            <p className="text-xs text-red-600 dark:text-red-400 mb-4">
              ⚠️ Esta acción no se puede deshacer.
            </p>

            <div className="flex gap-3">
              <button
                onClick={() => setModalEliminar({ abierto: false, especialidad: null })}
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

export default Especialidades;