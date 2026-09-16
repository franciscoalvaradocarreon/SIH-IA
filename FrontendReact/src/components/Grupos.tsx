import React, { useState, useEffect, useRef } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { grupoService } from '../api/grupoService';
import { especialidadService } from '../api/especialidadService';
import { turnoService } from '../api/turnoService';
import type { Grupo, Especialidad, Turno } from '../types';
import { useAuth } from '../context/AuthContext';
import ErrorScreen from '../utils/ErrorScreen';
import {
  MdAdd, MdEdit, MdDelete, MdSearch, MdCheckCircle, MdCancel,
  MdGroup, MdSchool, MdSchedule, MdClass, MdCategory, MdRefresh,
  MdWarning
} from 'react-icons/md';

const Grupos: React.FC = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const { semestreActivo } = useAuth();

  const inputRef = useRef<HTMLInputElement>(null);

  const queryParams = new URLSearchParams(location.search);
  const especialidadInicial = parseInt(queryParams.get('especialidad') || '0');
  const turnoInicial = parseInt(queryParams.get('turno') || '0');
  const busquedaInicial = queryParams.get('busqueda') || '';
  const pageInicial = parseInt(queryParams.get('page') || '0');

  const [grupos, setGrupos] = useState<Grupo[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(pageInicial);
  const [size] = useState(12);
  const [busqueda, setBusqueda] = useState(busquedaInicial);
  const [especialidadId, setEspecialidadId] = useState<number>(especialidadInicial);
  const [turnoId, setTurnoId] = useState<number>(turnoInicial);
  const [especialidades, setEspecialidades] = useState<Especialidad[]>([]);
  const [turnos, setTurnos] = useState<Turno[]>([]);
  const [loading, setLoading] = useState(true);
  const [cargandoInicial, setCargandoInicial] = useState(true);   // 🔥 renombrado
  const [timeoutId, setTimeoutId] = useState<NodeJS.Timeout | null>(null);

  const [modalEliminar, setModalEliminar] = useState<{
    abierto: boolean;
    grupo: Grupo | null;
  }>({ abierto: false, grupo: null });

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

  const getGradoColor = (grado: number) => {
    const colores = [
      'bg-green-100 dark:bg-green-900/30 text-green-800 dark:text-green-400',
      'bg-blue-100 dark:bg-blue-900/30 text-blue-800 dark:text-blue-400',
      'bg-indigo-100 dark:bg-indigo-900/30 text-indigo-800 dark:text-indigo-400',
      'bg-purple-100 dark:bg-purple-900/30 text-purple-800 dark:text-purple-400',
      'bg-pink-100 dark:bg-pink-900/30 text-pink-800 dark:text-pink-400',
      'bg-red-100 dark:bg-red-900/30 text-red-800 dark:text-red-400',
    ];
    return colores[(grado - 1) % colores.length] || colores[0];
  };

  const getGradoText = (grado: number) => {
    const grados = ['1°', '2°', '3°', '4°', '5°', '6°'];
    return grados[(grado - 1) % grados.length] || `${grado}°`;
  };

  const truncarTexto = (texto: string | null | undefined, maxLength: number = 25): string => {
    if (!texto) return '';
    if (texto.length <= maxLength) return texto;
    return texto.substring(0, maxLength) + '...';
  };

  useEffect(() => {
    if (!loading && inputRef.current) {
      inputRef.current.focus();
      const length = inputRef.current.value.length;
      inputRef.current.setSelectionRange(length, length);
    }
  }, [loading, busqueda]);

  // 🔥 EFECTO 1: Carga inicial (turnos + especialidades sin filtro de turno)
  //    Se dispara solo cuando cambia el semestre.
  useEffect(() => {
    cargarCatalogosIniciales();
  }, [semestreActivo?.id]);

  // 🔥 EFECTO 2: Recargar especialidades cuando cambia el turno.
  //    Excluye la carga inicial para no duplicar la request.
  useEffect(() => {
    if (!cargandoInicial && semestreActivo?.id) {
      cargarEspecialidades(turnoId);
    }
  }, [turnoId]);

  // Cargar grupos cuando cambian filtros (excluye carga inicial de catálogos)
  useEffect(() => {
    if (!cargandoInicial) {
      cargarGrupos();
    }
  }, [page, especialidadId, turnoId, cargandoInicial, semestreActivo?.id]);

  // Debounce para búsqueda
  useEffect(() => {
    if (!cargandoInicial) {
      if (timeoutId) {
        clearTimeout(timeoutId);
      }

      const id = setTimeout(() => {
        if (busqueda !== busquedaInicial) {
          setPage(0);
          cargarGrupos();
        }
      }, 500);

      setTimeoutId(id);

      return () => {
        if (timeoutId) {
          clearTimeout(timeoutId);
        }
      };
    }
  }, [busqueda]);

  const actualizarURL = () => {
    const params = new URLSearchParams();
    if (especialidadId > 0) params.set('especialidad', String(especialidadId));
    if (turnoId > 0) params.set('turno', String(turnoId));
    if (busqueda) params.set('busqueda', busqueda);
    if (page > 0) params.set('page', String(page));

    const nuevaURL = `${location.pathname}${params.toString() ? `?${params.toString()}` : ''}`;
    navigate(nuevaURL, { replace: true });
  };

  // 🔥 Carga inicial: turnos + TODAS las especialidades del semestre
  const cargarCatalogosIniciales = async () => {
    if (!semestreActivo?.id) {
      setTurnos([]);
      setEspecialidades([]);
      setCargandoInicial(false);
      return;
    }

    setCargandoInicial(true);
    try {
      const [turnosRes, espRes] = await Promise.all([
        turnoService.listar(0, 100, '', semestreActivo.id),
        especialidadService.listar(0, 100, '', semestreActivo.id),
        // Sin turnoId: todas las especialidades del semestre
      ]);
      setTurnos(turnosRes.data.content.filter((t: Turno) => t.activo === true));
      setEspecialidades(espRes.data.content);
    } catch (error) {
      console.error('Error al cargar catálogos:', error);
    } finally {
      setCargandoInicial(false);
    }
  };

  // 🔥 Recarga especialidades filtradas por el turno actual.
  //    Si la especialidad seleccionada no pertenece al turno,
  //    se limpia el filtro para evitar combinaciones inválidas.
  const cargarEspecialidades = async (turnoActual: number) => {
    if (!semestreActivo?.id) {
      setEspecialidades([]);
      return;
    }
    try {
      const res = await especialidadService.listar(
        0,
        100,
        '',
        semestreActivo.id,
        turnoActual > 0 ? turnoActual : undefined
      );
      const lista = res.data.content;
      setEspecialidades(lista);

      // 🔥 Si la especialidad seleccionada ya no está en la lista, limpiarla
      setEspecialidadId((prev) => {
        if (prev > 0 && !lista.some((e: Especialidad) => e.id === prev)) {
          return 0;
        }
        return prev;
      });
    } catch (error) {
      console.error('Error al cargar especialidades:', error);
      setEspecialidades([]);
    }
  };

  const cargarGrupos = async () => {
    setLoading(true);
    try {
      const semestreId = semestreActivo?.id;
      const res = await grupoService.listar(
        page,
        size,
        busqueda,
        especialidadId,
        semestreId,
        turnoId > 0 ? turnoId : undefined
      );
      setGrupos(res.data.content);
      setTotal(res.data.totalElements);
      actualizarURL();
    } catch (error) {
      console.error('Error al cargar grupos:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleEspecialidadChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    setEspecialidadId(Number(e.target.value));
    setPage(0);
  };

  // 🔥 Al cambiar turno, limpiar especialidad y resetear paginación
  const handleTurnoChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    setTurnoId(Number(e.target.value));
    setEspecialidadId(0);   // limpiar (el useEffect recargará las válidas)
    setPage(0);
  };

  const handleBusquedaChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    setBusqueda(e.target.value);
  };

  const handleKeyPress = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter') {
      if (timeoutId) {
        clearTimeout(timeoutId);
        setTimeoutId(null);
      }
      setPage(0);
      cargarGrupos();
    }
  };

  const handleBuscar = () => {
    if (timeoutId) {
      clearTimeout(timeoutId);
      setTimeoutId(null);
    }
    setPage(0);
    cargarGrupos();
  };

  const handleRecargar = async () => {
    if (timeoutId) {
      clearTimeout(timeoutId);
      setTimeoutId(null);
    }
    setPage(0);
    // Recargar turnos + especialidades (con el turno actual) + grupos
    await cargarCatalogosIniciales();
    if (turnoId > 0) {
      await cargarEspecialidades(turnoId);
    }
    cargarGrupos();
  };

  const irANuevo = () => {
    const params = new URLSearchParams();
    if (especialidadId > 0) params.set('especialidad', String(especialidadId));
    if (turnoId > 0) params.set('turno', String(turnoId));
    if (busqueda) params.set('busqueda', busqueda);
    navigate(`/catalogo/grupos/new${params.toString() ? `?${params.toString()}` : ''}`);
  };

  const irAEditar = (id: number) => {
    const params = new URLSearchParams();
    if (especialidadId > 0) params.set('especialidad', String(especialidadId));
    if (turnoId > 0) params.set('turno', String(turnoId));
    if (busqueda) params.set('busqueda', busqueda);
    navigate(`/catalogo/grupos/edit/${id}${params.toString() ? `?${params.toString()}` : ''}`);
  };

  const handleEliminar = (grupo: Grupo) => {
    setModalEliminar({ abierto: true, grupo });
  };

  const confirmarEliminar = async () => {
    if (!modalEliminar.grupo) return;

    setEliminando(true);
    try {
      await grupoService.eliminar(modalEliminar.grupo.id);
      setModalEliminar({ abierto: false, grupo: null });
      cargarGrupos();
    } catch (error: any) {
      console.error('Error al eliminar:', error);
      const msg = error.response?.data?.message || 'Error al eliminar el grupo';
      const codigo = error.response?.data?.error;
      setModalEliminar({ abierto: false, grupo: null });

      const esDependencia =
        codigo === 'GRUPO_EN_USO' ||
        msg.includes('DEPENDENCIAS') ||
        msg.includes('está siendo usado') ||
        msg.includes('asociad') ||
        msg.includes('dependencias') ||
        msg.includes('foreign key') ||
        msg.includes('viola la llave') ||
        msg.includes('no se puede');

      if (esDependencia) {
        let sugerencia = 'Elimina primero los registros asociados o desactiva el grupo en lugar de eliminarlo.';
        if (msg.includes('asignación') || msg.includes('asignaciones')) {
          sugerencia = 'Este grupo tiene asignaciones activas. Elimínalas primero desde la pantalla de Asignaciones o desactiva el grupo.';
        } else if (msg.includes('disponibilidad')) {
          sugerencia = 'Este grupo tiene disponibilidad configurada. Elimínala desde la pantalla de Disponibilidad de Grupos.';
        } else if (msg.includes('horario') || msg.includes('horarios')) {
          sugerencia = 'Este grupo tiene horarios generados. Elimina primero los horarios asociados.';
        }

        setErrorPantalla({
          titulo: 'No se puede eliminar el grupo',
          mensaje: msg,
          sugerencia,
          tipo: 'error',
          acciones: [
            {
              label: 'Aceptar',
              onClick: () => {
                setErrorPantalla(null);
                cargarGrupos();
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
                cargarGrupos();
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
      await grupoService.cambiarEstado(id, activo);
      cargarGrupos();
    } catch (error) {
      console.error('Error al cambiar estado:', error);
      alert('Error al cambiar el estado del grupo');
    }
  };

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

  if (cargandoInicial) {
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
          <h1 className="text-3xl font-bold text-gray-800 dark:text-white">Grupos</h1>
          <div className="flex flex-wrap items-center gap-3 mt-1">
            <p className="text-sm text-gray-500 dark:text-gray-400">
              Gestiona los grupos de la institución
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
            Nuevo Grupo
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
              Selecciona un semestre en el menú lateral para ver y crear grupos.
            </p>
          </div>
        </div>
      )}

      {/* Filtros: 3 columnas */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-6">
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
              placeholder="Buscar por nombre, grado o turno..."
              value={busqueda}
              onChange={handleBusquedaChange}
              onKeyPress={handleKeyPress}
              className="w-full pl-10 pr-24 py-2.5 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-800 text-gray-900 dark:text-white focus:ring-2 focus:ring-blue-500 focus:border-transparent transition"
            />
            <button
              onClick={handleBuscar}
              className="absolute right-2 top-1/2 transform -translate-y-1/2 bg-blue-600 hover:bg-blue-700 text-white px-3 py-1.5 rounded-lg text-sm transition"
            >
              Buscar
            </button>
          </div>
          <p className="text-xs text-gray-400 mt-1">
            Escribe y espera 500ms o presiona Enter
          </p>
        </div>

        {/* Turno */}
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
              No hay turnos activos
            </p>
          )}
        </div>

        {/* Especialidad */}
        <div>
          <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
            <MdCategory className="inline mr-1" />
            Filtrar por Especialidad
          </label>
          <select
            value={especialidadId}
            onChange={handleEspecialidadChange}
            disabled={especialidades.length === 0}
            className="w-full px-4 py-2.5 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:ring-2 focus:ring-blue-500 focus:border-transparent transition disabled:opacity-50"
          >
            <option value={0}>
              {turnoId > 0 ? 'Todas las del turno' : 'Todas las especialidades'}
            </option>
            {especialidades.map((esp) => (
              <option key={esp.id} value={esp.id}>
                {esp.nombre}
              </option>
            ))}
          </select>
          {especialidades.length === 0 && semestreActivo && (
            <p className="text-xs text-yellow-600 dark:text-yellow-400 mt-1">
              {turnoId > 0
                ? 'Este turno no tiene especialidades'
                : 'No hay especialidades en este semestre'}
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
                        <MdGroup className="text-sm" />
                        Nombre
                      </div>
                    </th>
                    <th className="px-3 py-2 text-center text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider">
                      <div className="flex items-center gap-1.5">
                        <MdSchool className="text-sm" />
                        Grado
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
                        <MdClass className="text-sm" />
                        Especialidad
                      </div>
                    </th>
                    <th className="px-3 py-2 text-center text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider">
                      Capacidad
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
                  {grupos.length === 0 ? (
                    <tr>
                      <td colSpan={7} className="px-4 py-6 text-center text-gray-500 dark:text-gray-400">
                        <div className="flex flex-col items-center gap-2">
                          <MdGroup className="text-4xl text-gray-300 dark:text-gray-600" />
                          <p>
                            {semestreActivo
                              ? `No hay grupos en el semestre ${semestreActivo.nombre}`
                              : 'No hay grupos disponibles'}
                            {turnoId > 0 && turnos.find(t => t.id === turnoId) && (
                              <span className="ml-1">
                                (Turno: {turnos.find(t => t.id === turnoId)?.nombre})
                              </span>
                            )}
                            {especialidadId > 0 && especialidades.find(e => e.id === especialidadId) && (
                              <span className="ml-1">
                                (Especialidad: {especialidades.find(e => e.id === especialidadId)?.nombre})
                              </span>
                            )}
                          </p>
                          <button
                            onClick={irANuevo}
                            className="text-blue-600 dark:text-blue-400 hover:underline text-sm font-medium"
                          >
                            Crear primer grupo
                          </button>
                        </div>
                      </td>
                    </tr>
                  ) : (
                    grupos.map((grupo) => (
                      <tr
                        key={grupo.id}
                        className="hover:bg-gray-50 dark:hover:bg-gray-700/50 transition-colors duration-150"
                      >
                        <td className="px-3 py-2 whitespace-nowrap">
                          <div className="flex items-center gap-2">
                            <div className="w-7 h-7 rounded-lg bg-blue-100 dark:bg-blue-900/40 flex items-center justify-center text-blue-600 dark:text-blue-400 flex-shrink-0">
                              <MdGroup className="text-base" />
                            </div>
                            <span className="text-lg font-medium text-gray-900 dark:text-white">
                              {grupo.nombre}
                            </span>
                          </div>
                        </td>
                        <td className="px-3 py-1.5 whitespace-nowrap text-center">
                          <span className={`text-xs font-medium px-2 py-0.5 rounded-full ${getGradoColor(grupo.grado)}`}>
                            {getGradoText(grupo.grado)}
                          </span>
                        </td>
                        <td className="px-3 py-1.5 whitespace-nowrap">
                          <span className="text-sm text-gray-700 dark:text-gray-300">
                            {grupo.turno || '-'}
                          </span>
                        </td>
                        <td className="px-3 py-1.5 whitespace-nowrap">
                          <span
                            className="text-sm text-gray-700 dark:text-gray-300"
                            title={grupo.especialidad || ''}
                          >
                            {truncarTexto(grupo.especialidad, 20)}
                          </span>
                        </td>
                        <td className="px-3 py-1.5 whitespace-nowrap text-center">
                          <span className="text-sm text-gray-700 dark:text-gray-300">
                            {grupo.capacidad || 0}
                          </span>
                        </td>
                        <td className="px-3 py-1.5 whitespace-nowrap text-center">
                          <span
                            className={`px-2 py-0.5 text-base font-medium rounded-full flex items-center gap-1 inline-flex ${
                              grupo.activo
                                ? 'bg-green-100 dark:bg-green-900/30 text-green-800 dark:text-green-400'
                                : 'bg-red-100 dark:bg-red-900/30 text-red-800 dark:text-red-400'
                            }`}
                          >
                            {grupo.activo ? (
                              <MdCheckCircle className="text-base" />
                            ) : (
                              <MdCancel className="text-base" />
                            )}
                            {grupo.activo ? 'Activo' : 'Inactivo'}
                          </span>
                        </td>
                        <td className="px-3 py-1.5 whitespace-nowrap text-center">
                          <div className="flex items-center justify-center gap-0.5">
                            <button
                              onClick={() => irAEditar(grupo.id)}
                              className="p-1 text-blue-600 dark:text-blue-400 hover:text-blue-800 dark:hover:text-blue-300 hover:bg-blue-50 dark:hover:bg-blue-900/20 rounded transition"
                              title="Editar"
                            >
                              <MdEdit className="text-xl" />
                            </button>
                            <button
                              onClick={() => handleCambiarEstado(grupo.id, !grupo.activo)}
                              className={`p-1 rounded transition ${
                                grupo.activo
                                  ? 'text-yellow-600 dark:text-yellow-400 hover:text-yellow-800 dark:hover:text-yellow-300 hover:bg-yellow-50 dark:hover:bg-yellow-900/20'
                                  : 'text-green-600 dark:text-green-400 hover:text-green-800 dark:hover:text-green-300 hover:bg-green-50 dark:hover:bg-green-900/20'
                              }`}
                              title={grupo.activo ? 'Desactivar' : 'Activar'}
                            >
                              {grupo.activo ? <MdCancel className="text-xl" /> : <MdCheckCircle className="text-xl" />}
                            </button>
                            <button
                              onClick={() => handleEliminar(grupo)}
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
              Mostrando <span className="font-medium">{grupos.length}</span> de{' '}
              <span className="font-medium">{total}</span> grupos
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
              {especialidadId > 0 && (
                <span className="ml-2 text-purple-600 dark:text-purple-400">
                  · Especialidad: {especialidades.find(e => e.id === especialidadId)?.nombre}
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
                disabled={grupos.length < size}
                className="px-4 py-2 border border-gray-300 dark:border-gray-600 rounded-lg text-sm font-medium text-gray-700 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700 disabled:opacity-50 disabled:cursor-not-allowed transition"
              >
                Siguiente
              </button>
            </div>
          </div>
        </>
      )}

      {/* Modal de eliminación */}
      {modalEliminar.abierto && modalEliminar.grupo && (
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
              ¿Estás seguro de que quieres eliminar este grupo?
            </p>

            <div className="bg-gray-50 dark:bg-gray-700/50 rounded-lg p-3 mb-4 text-sm">
              <div className="flex items-center gap-3 mb-2">
                <div className="w-10 h-10 rounded-lg bg-blue-100 dark:bg-blue-900/40 flex items-center justify-center text-blue-600 dark:text-blue-400">
                  <MdGroup className="text-lg" />
                </div>
                <div>
                  <div className="font-medium text-gray-800 dark:text-white">
                    {modalEliminar.grupo.nombre}
                  </div>
                  <div className="text-xs text-gray-500 dark:text-gray-400">
                    {getGradoText(modalEliminar.grupo.grado)} · {modalEliminar.grupo.turno}
                  </div>
                </div>
              </div>
              {modalEliminar.grupo.especialidad && (
                <div className="flex justify-between mt-1">
                  <span className="text-gray-500 dark:text-gray-400">Especialidad:</span>
                  <span className="font-medium text-gray-800 dark:text-white truncate max-w-[200px]">
                    {modalEliminar.grupo.especialidad}
                  </span>
                </div>
              )}
              {modalEliminar.grupo.capacidad > 0 && (
                <div className="flex justify-between mt-1">
                  <span className="text-gray-500 dark:text-gray-400">Capacidad:</span>
                  <span className="font-medium text-gray-800 dark:text-white">
                    {modalEliminar.grupo.capacidad} alumnos
                  </span>
                </div>
              )}
            </div>

            <p className="text-xs text-red-600 dark:text-red-400 mb-4">
              ⚠️ Esta acción no se puede deshacer.
            </p>

            <div className="flex gap-3">
              <button
                onClick={() => setModalEliminar({ abierto: false, grupo: null })}
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

export default Grupos;