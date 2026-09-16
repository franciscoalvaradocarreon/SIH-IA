// src/components/Asignacion.tsx
import React, { useState, useEffect, useRef } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { asignacionService } from '../api/asignacionService';
import { especialidadService } from '../api/especialidadService';
import { turnoService } from '../api/turnoService';
import { grupoService } from '../api/grupoService';
import type { Asignacion, Especialidad, Turno, Grupo } from '../types';
import { useAuth } from '../context/AuthContext';
import ErrorScreen from '../utils/ErrorScreen';
import {
  MdAdd, MdEdit, MdDelete, MdCheckCircle, MdCancel,
  MdRefresh, MdClass, MdPerson, MdBook, MdMeetingRoom, MdCategory,
  MdSchedule, MdWarning, MdClear, MdChevronLeft, MdChevronRight
} from 'react-icons/md';

const Asignaciones: React.FC = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const { semestreActivo } = useAuth();

  const queryParams = new URLSearchParams(location.search);
  const grupoInicial = parseInt(queryParams.get('grupo') || '0');
  const especialidadInicial = parseInt(queryParams.get('especialidad') || '0');
  const turnoInicial = parseInt(queryParams.get('turno') || '0');
  const pageInicial = parseInt(queryParams.get('page') || '0');

  const autoSeleccionarPrimerGrupo = useRef(grupoInicial === 0);

  const [asignaciones, setAsignaciones] = useState<Asignacion[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(pageInicial);
  const [size] = useState(12);
  const [grupoId, setGrupoId] = useState<number>(grupoInicial);
  const [especialidadId, setEspecialidadId] = useState<number>(especialidadInicial);
  const [turnoId, setTurnoId] = useState<number>(turnoInicial);

  const [grupos, setGrupos] = useState<Grupo[]>([]);
  const [especialidades, setEspecialidades] = useState<Especialidad[]>([]);
  const [turnos, setTurnos] = useState<Turno[]>([]);
  const [loading, setLoading] = useState(true);
  const [cargandoCatalogos, setCargandoCatalogos] = useState(true);

  const [modalEliminar, setModalEliminar] = useState<{
    abierto: boolean;
    asignacion: Asignacion | null;
  }>({ abierto: false, asignacion: null });

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
    cargarCatalogos();
  }, [semestreActivo?.id]);

  useEffect(() => {
    if (!cargandoCatalogos && semestreActivo?.id) {
      cargarEspecialidades(turnoId);
    }
  }, [turnoId]);

  useEffect(() => {
    if (!cargandoCatalogos && semestreActivo?.id) {
      cargarGrupos(turnoId);
    }
  }, [turnoId]);

  useEffect(() => {
    if (!cargandoCatalogos && semestreActivo?.id) {
      cargarAsignaciones();
      actualizarURL();
    }
  }, [page, grupoId, especialidadId, turnoId, cargandoCatalogos, semestreActivo]);

  const actualizarURL = () => {
    const params = new URLSearchParams();
    if (grupoId > 0) params.set('grupo', String(grupoId));
    if (especialidadId > 0) params.set('especialidad', String(especialidadId));
    if (turnoId > 0) params.set('turno', String(turnoId));
    if (page > 0) params.set('page', String(page));

    const nuevaURL = `${location.pathname}${params.toString() ? `?${params.toString()}` : ''}`;
    navigate(nuevaURL, { replace: true });
  };

  const cargarCatalogos = async () => {
    setCargandoCatalogos(true);
    try {
      const semestreId = semestreActivo?.id;
      if (semestreId) {
        const [espRes, turnosRes, gruposRes] = await Promise.all([
          especialidadService.listar(0, 100, '', semestreId),
          turnoService.listar(0, 100, '', semestreId),
          grupoService.listar(0, 100, '', 0, semestreId),
        ]);
        setEspecialidades(espRes.data.content);
        setTurnos(turnosRes.data.content.filter((t: Turno) => t.activo === true));
        setGrupos(gruposRes.data.content.filter((g: Grupo) => g.activo));
      } else {
        const espRes = await especialidadService.listar(0, 100);
        setEspecialidades(espRes.data.content);
        setTurnos([]);
        setGrupos([]);
      }
    } catch (error) {
      console.error('Error al cargar catálogos:', error);
    } finally {
      setCargandoCatalogos(false);
    }
  };

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

      setEspecialidadId((prev) => {
        if (prev > 0 && !lista.some((e: Especialidad) => e.id === prev)) return 0;
        return prev;
      });
    } catch (error) {
      console.error('Error al cargar especialidades:', error);
      setEspecialidades([]);
    }
  };

  const cargarGrupos = async (turnoActual: number) => {
    if (!semestreActivo?.id) {
      setGrupos([]);
      return;
    }
    try {
      const res = await grupoService.listar(
        0,
        100,
        '',
        0,
        semestreActivo.id,
        turnoActual > 0 ? turnoActual : undefined
      );
      const lista = res.data.content.filter((g: Grupo) => g.activo);
      setGrupos(lista);

      const auto = autoSeleccionarPrimerGrupo.current;
      const hayGrupoValido = lista.some((g: Grupo) => g.id === grupoId);

      if (auto && lista.length > 0 && !hayGrupoValido) {
        setGrupoId(lista[0].id);
        autoSeleccionarPrimerGrupo.current = false;
      } else if (lista.length === 0) {
        setGrupoId(0);
      } else if (!hayGrupoValido && lista.length > 0) {
        setGrupoId(lista[0].id);
      }
    } catch (error) {
      console.error('Error al cargar grupos:', error);
      setGrupos([]);
    }
  };

  const cargarAsignaciones = async () => {
    setLoading(true);
    try {
      const semestreId = semestreActivo?.id;
      const res = await asignacionService.listar(
        page,
        size,
        '',
        grupoId,
        especialidadId,
        semestreId,
        turnoId > 0 ? turnoId : undefined
      );
      setAsignaciones(res.data.content);
      setTotal(res.data.totalElements);
    } catch (error) {
      console.error('Error al cargar asignaciones:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleGrupoChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    setGrupoId(Number(e.target.value));
    autoSeleccionarPrimerGrupo.current = false;
    setPage(0);
  };

  const handleEspecialidadChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    setEspecialidadId(Number(e.target.value));
    setPage(0);
  };

  const handleTurnoChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    setTurnoId(Number(e.target.value));
    setGrupoId(0);
    setEspecialidadId(0);
    autoSeleccionarPrimerGrupo.current = true;
    setPage(0);
  };

  // Navegación entre grupos
  const indiceGrupoActual = grupos.findIndex(g => g.id === grupoId);
  const tieneGrupoAnterior = indiceGrupoActual > 0;
  const tieneGrupoSiguiente = indiceGrupoActual >= 0 && indiceGrupoActual < grupos.length - 1;

  const irGrupoAnterior = () => {
    if (tieneGrupoAnterior) {
      autoSeleccionarPrimerGrupo.current = false;
      setGrupoId(grupos[indiceGrupoActual - 1].id);
      setPage(0);
    }
  };

  const irGrupoSiguiente = () => {
    if (tieneGrupoSiguiente) {
      autoSeleccionarPrimerGrupo.current = false;
      setGrupoId(grupos[indiceGrupoActual + 1].id);
      setPage(0);
    }
  };

  const limpiarFiltros = () => {
    setGrupoId(0);
    setEspecialidadId(0);
    setTurnoId(0);
    autoSeleccionarPrimerGrupo.current = false;
    setPage(0);
  };

  const hayFiltrosActivos = grupoId > 0 || especialidadId > 0 || turnoId > 0;

  const irANuevo = () => {
    const params = new URLSearchParams();
    if (grupoId > 0) params.set('grupo', String(grupoId));
    if (especialidadId > 0) params.set('especialidad', String(especialidadId));
    if (turnoId > 0) params.set('turno', String(turnoId));
    navigate(`/horarios/asignacion/new${params.toString() ? `?${params.toString()}` : ''}`);
  };

  const irAEditar = (id: number) => {
    const params = new URLSearchParams();
    if (grupoId > 0) params.set('grupo', String(grupoId));
    if (especialidadId > 0) params.set('especialidad', String(especialidadId));
    if (turnoId > 0) params.set('turno', String(turnoId));
    navigate(`/horarios/asignacion/edit/${id}${params.toString() ? `?${params.toString()}` : ''}`);
  };

  const handleEliminar = (asignacion: Asignacion) => {
    setModalEliminar({ abierto: true, asignacion });
  };

  const confirmarEliminar = async () => {
    if (!modalEliminar.asignacion) return;

    setEliminando(true);
    try {
      await asignacionService.eliminar(modalEliminar.asignacion.id);
      setModalEliminar({ abierto: false, asignacion: null });
      cargarAsignaciones();
    } catch (error: any) {
      console.error('Error al eliminar:', error);
      const msg = error.response?.data?.message || 'Error al eliminar la asignación';
      const codigo = error.response?.data?.error;
      setModalEliminar({ abierto: false, asignacion: null });

      const esDependencia =
        codigo === 'ASIGNACION_EN_USO' ||
        msg.includes('DEPENDENCIAS') ||
        msg.includes('está siendo usado') ||
        msg.includes('asociad') ||
        msg.includes('dependencias') ||
        msg.includes('foreign key') ||
        msg.includes('viola la llave') ||
        msg.includes('no se puede');

      if (esDependencia) {
        let sugerencia = 'Elimina primero los registros asociados o desactiva la asignación en lugar de eliminarla.';
        if (msg.includes('horario') || msg.includes('horarios')) {
          sugerencia = 'Esta asignación ya tiene horarios generados. Regenera o elimina los horarios desde el generador de horarios antes de borrarla.';
        }

        setErrorPantalla({
          titulo: 'No se puede eliminar la asignación',
          mensaje: msg,
          sugerencia,
          tipo: 'error',
          acciones: [
            {
              label: 'Aceptar',
              onClick: () => {
                setErrorPantalla(null);
                cargarAsignaciones();
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
                cargarAsignaciones();
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
      await asignacionService.cambiarEstado(id, activo);
      cargarAsignaciones();
    } catch (error) {
      console.error('Error al cambiar estado:', error);
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

  if (cargandoCatalogos) {
    return (
      <div className="flex justify-center items-center h-64">
        <div className="animate-spin rounded-full h-12 w-12 border-4 border-blue-500 border-t-transparent"></div>
      </div>
    );
  }

  const grupoActual = grupos.find(g => g.id === grupoId);

  // 🔥 Total de horas de las asignaciones visibles (página actual)
  const totalHoras = asignaciones.reduce((sum, a) => sum + (a.horas || 0), 0);

  return (
    <div className="p-6">
      {/* Encabezado */}
      <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4 mb-6">
        <div>
          <h1 className="text-3xl font-bold text-gray-800 dark:text-white">
            Asignaciones Grupo-Materia-Maestro
          </h1>
          <p className="text-sm text-gray-500 dark:text-gray-400 mt-1">
            Asigna materias a grupos con su respectivo maestro y aula
          </p>
        </div>
        <div className="flex gap-3">
          <button
            onClick={() => {
              setPage(0);
              cargarCatalogos();
              cargarAsignaciones();
            }}
            className="flex items-center gap-2 bg-gray-200 dark:bg-gray-700 hover:bg-gray-300 dark:hover:bg-gray-600 text-gray-700 dark:text-gray-300 px-4 py-2.5 rounded-lg shadow-md transition"
            title="Recargar datos"
          >
            <MdRefresh className="text-xl" />
            Recargar
          </button>
          <button
            onClick={irANuevo}
            className="flex items-center gap-2 bg-blue-600 hover:bg-blue-700 text-white px-4 py-2.5 rounded-lg shadow-md transition"
          >
            <MdAdd className="text-xl" />
            Nueva Asignación
          </button>
        </div>
      </div>

      {/* Filtros: 3 columnas */}
      <div className="bg-white dark:bg-gray-800 rounded-xl shadow-md p-4 mb-6 border border-gray-100 dark:border-gray-700">
        <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
          {/* Turno */}
          <div>
            <label className="block text-xs font-medium text-gray-700 dark:text-gray-300 mb-1">
              <MdSchedule className="inline mr-1 text-sm" />
              Turno
            </label>
            <select
              value={turnoId}
              onChange={handleTurnoChange}
              className="w-full px-3 py-2 text-sm border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:ring-2 focus:ring-blue-500 focus:border-transparent transition"
            >
              <option value={0}>Todos los turnos</option>
              {turnos.map((t) => (
                <option key={t.id} value={t.id}>
                  {t.nombre}
                </option>
              ))}
            </select>
          </div>

          {/* Especialidad */}
          <div>
            <label className="block text-xs font-medium text-gray-700 dark:text-gray-300 mb-1">
              <MdCategory className="inline mr-1 text-sm" />
              Especialidad
            </label>
            <select
              value={especialidadId}
              onChange={handleEspecialidadChange}
              disabled={especialidades.length === 0}
              className="w-full px-3 py-2 text-sm border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:ring-2 focus:ring-blue-500 focus:border-transparent transition disabled:opacity-50"
            >
              <option value={0}>Todas las especialidades</option>
              {especialidades.map((esp) => (
                <option key={esp.id} value={esp.id}>
                  {esp.nombre}
                </option>
              ))}
            </select>
          </div>

          {/* Grupo */}
          <div>
            <label className="block text-xs font-medium text-gray-700 dark:text-gray-300 mb-1">
              <MdClass className="inline mr-1 text-sm" />
              Grupo
            </label>
            <select
              value={grupoId}
              onChange={handleGrupoChange}
              disabled={grupos.length === 0}
              className="w-full px-3 py-2 text-sm border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:ring-2 focus:ring-blue-500 focus:border-transparent transition disabled:opacity-50"
            >
              <option value={0}>Todos los grupos</option>
              {grupos.map((g) => (
                <option key={g.id} value={g.id}>
                  {g.nombre} - {g.grado}°
                </option>
              ))}
            </select>
          </div>
        </div>

        {hayFiltrosActivos && (
          <div className="flex flex-wrap items-center gap-2 mt-3 pt-3 border-t border-gray-200 dark:border-gray-700">
            <span className="text-xs text-gray-500 dark:text-gray-400">
              Filtros activos:
            </span>
            {turnoId > 0 && turnos.find(t => t.id === turnoId) && (
              <span className="inline-flex items-center gap-1 text-xs bg-indigo-50 dark:bg-indigo-900/30 text-indigo-700 dark:text-indigo-300 px-2 py-0.5 rounded-full">
                Turno: {turnos.find(t => t.id === turnoId)?.nombre}
              </span>
            )}
            {especialidadId > 0 && especialidades.find(e => e.id === especialidadId) && (
              <span className="inline-flex items-center gap-1 text-xs bg-purple-50 dark:bg-purple-900/30 text-purple-700 dark:text-purple-300 px-2 py-0.5 rounded-full">
                Especialidad: {especialidades.find(e => e.id === especialidadId)?.nombre}
              </span>
            )}
            {grupoId > 0 && grupos.find(g => g.id === grupoId) && (
              <span className="inline-flex items-center gap-1 text-xs bg-green-50 dark:bg-green-900/30 text-green-700 dark:text-green-300 px-2 py-0.5 rounded-full">
                Grupo: {grupos.find(g => g.id === grupoId)?.nombre}
              </span>
            )}
            <button
              onClick={limpiarFiltros}
              className="ml-auto inline-flex items-center gap-1 text-xs text-red-600 dark:text-red-400 hover:text-red-800 dark:hover:text-red-300 font-medium transition"
            >
              <MdClear className="text-base" />
              Limpiar
            </button>
          </div>
        )}
      </div>

      {loading ? (
        <div className="flex justify-center py-12">
          <div className="animate-spin rounded-full h-12 w-12 border-4 border-blue-500 border-t-transparent"></div>
        </div>
      ) : (
        <>
          <div className="bg-white dark:bg-gray-800 rounded-xl shadow-md overflow-hidden border border-gray-100 dark:border-gray-700">
            {/* Header de navegación entre grupos */}
            <div className="px-4 py-4 bg-gray-50 dark:bg-gray-700/50 border-b border-gray-200 dark:border-gray-700 flex flex-col md:flex-row items-center justify-between gap-4">
              {/* Info del grupo (izquierda) */}
              <div className="flex items-center gap-3 md:flex-1 md:justify-start">
                {grupoActual ? (
                  <>
                    <div className="w-12 h-12 rounded-full bg-blue-100 dark:bg-blue-900/40 flex items-center justify-center text-blue-600 dark:text-blue-400 font-bold text-lg">
                      {grupoActual.nombre?.charAt(0) || 'G'}
                    </div>
                    <div>
                      <h3 className="font-semibold text-lg text-gray-800 dark:text-white">
                        {grupoActual.nombre} - {grupoActual.grado}°
                      </h3>
                      <p className="text-sm text-gray-500 dark:text-gray-400">
                        {grupoActual.turno || 'Sin turno'}
                        {grupoActual.especialidad && (
                          <span className="ml-1">
                            · {typeof grupoActual.especialidad === 'string'
                              ? grupoActual.especialidad
                              : grupoActual.especialidad.nombre}
                          </span>
                        )}
                      </p>
                    </div>
                  </>
                ) : (
                  <>
                    <div className="w-12 h-12 rounded-full bg-gray-100 dark:bg-gray-700 flex items-center justify-center text-gray-500 dark:text-gray-400">
                      <MdClass className="text-xl" />
                    </div>
                    <div>
                      <h3 className="font-semibold text-lg text-gray-800 dark:text-white">
                        Todos los grupos
                      </h3>
                      <p className="text-sm text-gray-500 dark:text-gray-400">
                        Mostrando asignaciones de todos los grupos
                      </p>
                    </div>
                  </>
                )}
              </div>

              {/* Navegación (centro) */}
              <div className="flex items-center gap-2 bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-600 shadow-md px-2 py-1">
                <button
                  onClick={irGrupoAnterior}
                  disabled={!tieneGrupoAnterior}
                  className={`flex items-center gap-1 px-4 py-2.5 rounded-l-lg text-base font-semibold transition ${
                    tieneGrupoAnterior
                      ? 'text-gray-700 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-700'
                      : 'text-gray-300 dark:text-gray-600 cursor-not-allowed'
                  }`}
                  title="Grupo anterior"
                >
                  <MdChevronLeft className="text-2xl" />
                  <span>Anterior</span>
                </button>

                <span className="px-4 py-2 text-base font-bold text-gray-700 dark:text-gray-200 border-l border-r border-gray-200 dark:border-gray-600 whitespace-nowrap">
                  {indiceGrupoActual >= 0 ? indiceGrupoActual + 1 : 0}{' '}
                  <span className="text-gray-400 font-normal">de</span>{' '}
                  {grupos.length}
                </span>

                <button
                  onClick={irGrupoSiguiente}
                  disabled={!tieneGrupoSiguiente}
                  className={`flex items-center gap-1 px-4 py-2.5 rounded-r-lg text-base font-semibold transition ${
                    tieneGrupoSiguiente
                      ? 'text-gray-700 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-700'
                      : 'text-gray-300 dark:text-gray-600 cursor-not-allowed'
                  }`}
                  title="Grupo siguiente"
                >
                  <span>Siguiente</span>
                  <MdChevronRight className="text-2xl" />
                </button>
              </div>

              {/* Total de horas (derecha) — solo si hay grupo seleccionado */}
              <div className="md:flex-1 md:flex md:justify-end">
                {grupoId > 0 ? (
                  <span
                    className="text-sm text-gray-600 dark:text-gray-400 whitespace-nowrap bg-white dark:bg-gray-800 px-4 py-2 rounded-lg border border-gray-200 dark:border-gray-600 shadow-sm"
                    title={
                      asignaciones.length < total
                        ? 'Solo se cuentan las horas de esta página'
                        : 'Total de horas mostradas'
                    }
                  >
                    Total:{' '}
                    <span className="font-bold text-gray-800 dark:text-white text-base">
                      {totalHoras}
                    </span>{' '}
                    horas
                    {asignaciones.length < total && (
                      <span className="ml-2 text-xs text-gray-400 italic">
                        (página actual)
                      </span>
                    )}
                  </span>
                ) : (
                  <span className="text-sm text-gray-500 dark:text-gray-400 whitespace-nowrap bg-white dark:bg-gray-800 px-4 py-2 rounded-lg border border-gray-200 dark:border-gray-600 shadow-sm">
                    Mostrando{' '}
                    <span className="font-bold text-gray-800 dark:text-white text-base">
                      {asignaciones.length}
                    </span>{' '}
                    de{' '}
                    <span className="font-bold text-gray-800 dark:text-white text-base">
                      {total}
                    </span>{' '}
                    asignaciones
                  </span>
                )}
              </div>
            </div>

            {/* Tabla */}
            <div className="overflow-x-auto">
              <table className="min-w-full divide-y divide-gray-200 dark:divide-gray-700">
                <thead className="bg-gray-50 dark:bg-gray-700/50">
                  <tr>
                    <th className="px-3 py-2 text-left text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider">
                      <div className="flex items-center gap-1.5">
                        <MdClass className="text-sm" />
                        Grupo
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
                        <MdBook className="text-sm" />
                        Materia
                      </div>
                    </th>
                    <th className="px-3 py-2 text-left text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider">
                      <div className="flex items-center gap-1.5">
                        <MdPerson className="text-sm" />
                        Maestro
                      </div>
                    </th>
                    <th className="px-3 py-2 text-left text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider">
                      <div className="flex items-center gap-1.5">
                        <MdMeetingRoom className="text-sm" />
                        Aula
                      </div>
                    </th>
                    <th className="px-3 py-2 text-left text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider">
                      Distribución
                    </th>
                    <th className="px-3 py-2 text-center text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider">
                      Horas
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
                  {asignaciones.length === 0 ? (
                    <tr>
                      <td colSpan={9} className="px-4 py-8 text-center text-gray-500 dark:text-gray-400">
                        <div className="flex flex-col items-center gap-2">
                          <MdClass className="text-4xl text-gray-300 dark:text-gray-600" />
                          <p>
                            No hay asignaciones
                            {hayFiltrosActivos && (
                              <span className="ml-1">que coincidan con los filtros</span>
                            )}
                          </p>
                          <button
                            onClick={irANuevo}
                            className="text-blue-600 dark:text-blue-400 hover:underline text-sm font-medium"
                          >
                            Crear primera asignación
                          </button>
                        </div>
                      </td>
                    </tr>
                  ) : (
                    asignaciones.map((asignacion) => (
                      <tr
                        key={asignacion.id}
                        className="hover:bg-gray-50 dark:hover:bg-gray-700/50 transition-colors duration-150"
                      >
                        <td className="px-3 py-2 whitespace-nowrap">
                          <div className="flex items-center gap-2">
                            <div className="w-7 h-7 rounded-full bg-blue-100 dark:bg-blue-900/40 flex items-center justify-center text-blue-600 dark:text-blue-400 text-xs font-bold">
                              {asignacion.grupoNombre?.charAt(0) || 'G'}
                            </div>
                            <span className="text-xl font-medium text-gray-900 dark:text-white">
                              {asignacion.grupoNombre}
                            </span>
                          </div>
                        </td>
                        <td className="px-3 py-2 whitespace-nowrap">
                          {asignacion.turnoNombre ? (
                            <span className="inline-flex items-center gap-1 px-2.5 py-0.5 text-xs font-medium rounded-full bg-indigo-100 dark:bg-indigo-900/30 text-indigo-800 dark:text-indigo-400">
                              <MdSchedule className="text-sm" />
                              {asignacion.turnoNombre}
                            </span>
                          ) : (
                            <span className="text-xs text-gray-400 italic">-</span>
                          )}
                        </td>
                        <td className="px-3 py-2 whitespace-nowrap">
                          <div className="flex items-center gap-2">
                            <div
                              className="w-3 h-3 rounded-full flex-shrink-0"
                              style={{ backgroundColor: asignacion.colorHex || '#808080' }}
                            />
                            <div className="max-w-[400px] truncate">
                              <span className="text-xl text-gray-700 dark:text-gray-300">
                                {asignacion.materiaClave}
                              </span>
                              <span className="text-base text-gray-400 dark:text-gray-500 font-mono ml-1">
                                ({asignacion.materiaNombre})
                              </span>
                            </div>
                          </div>
                        </td>
                        <td className="px-3 py-2 whitespace-nowrap">
                          <div className="flex items-center gap-2">
                            <div className="w-6 h-6 rounded-full bg-gray-100 dark:bg-gray-700 flex items-center justify-center text-gray-600 dark:text-gray-400 text-xs font-medium">
                              {asignacion.maestroNombre?.charAt(0) || 'M'}
                            </div>
                            <span className="text-lg text-gray-700 dark:text-gray-300 truncate max-w-[250px]">
                              {asignacion.maestroNombre}
                            </span>
                          </div>
                        </td>
                        <td className="px-3 py-2 whitespace-nowrap">
                          <div className="flex items-center gap-2">
                            <MdMeetingRoom className="text-gray-400 text-sm" />
                            <span className="text-base text-gray-700 dark:text-gray-300">
                              {asignacion.aulaNombre || 'Sin aula'}
                            </span>
                          </div>
                        </td>
                        <td className="px-3 py-2 whitespace-nowrap">
                          <span className="text-base text-gray-700 dark:text-gray-300">
                            {asignacion.distribucion}
                          </span>
                        </td>
                        <td className="px-3 py-2 whitespace-nowrap text-center">
                          <span className="inline-flex items-center px-2 py-0.5 rounded-full text-lg font-medium bg-indigo-100 dark:bg-indigo-900/30 text-indigo-700 dark:text-indigo-300">
                            {asignacion.horas}
                          </span>
                        </td>
                        <td className="px-3 py-2 whitespace-nowrap text-center">
                          <span
                            className={`px-2 py-0.5 text-base font-medium rounded-full flex items-center gap-1 inline-flex ${
                              asignacion.activo
                                ? 'bg-green-100 dark:bg-green-900/30 text-green-800 dark:text-green-400'
                                : 'bg-red-100 dark:bg-red-900/30 text-red-800 dark:text-red-400'
                            }`}
                          >
                            {asignacion.activo ? (
                              <MdCheckCircle className="text-xl" />
                            ) : (
                              <MdCancel className="text-xl" />
                            )}
                            {asignacion.activo ? 'Activo' : 'Inactivo'}
                          </span>
                        </td>
                        <td className="px-3 py-2 whitespace-nowrap text-center">
                          <div className="flex items-center justify-center gap-0.5">
                            <button
                              onClick={() => irAEditar(asignacion.id)}
                              className="p-2 text-blue-600 dark:text-blue-400 hover:text-blue-800 dark:hover:text-blue-300 hover:bg-blue-50 dark:hover:bg-blue-900/20 rounded transition"
                              title="Editar"
                            >
                              <MdEdit className="text-xl" />
                            </button>
                            <button
                              onClick={() => handleCambiarEstado(asignacion.id, !asignacion.activo)}
                              className={`p-2 rounded transition ${
                                asignacion.activo
                                  ? 'text-yellow-600 dark:text-yellow-400 hover:text-yellow-800 dark:hover:text-yellow-300 hover:bg-yellow-50 dark:hover:bg-yellow-900/20'
                                  : 'text-green-600 dark:text-green-400 hover:text-green-800 dark:hover:text-green-300 hover:bg-green-50 dark:hover:bg-green-900/20'
                              }`}
                              title={asignacion.activo ? 'Desactivar' : 'Activar'}
                            >
                              {asignacion.activo ? <MdCancel className="text-xl" /> : <MdCheckCircle className="text-xl" />}
                            </button>
                            <button
                              onClick={() => handleEliminar(asignacion)}
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
          <div className="flex flex-col sm:flex-row justify-between items-center gap-4 mt-6 bg-white dark:bg-gray-800 px-4 py-3 rounded-lg shadow-sm border border-gray-100 dark:border-gray-700">
            <div className="text-sm text-gray-600 dark:text-gray-400">
              Mostrando <span className="font-medium">{asignaciones.length}</span> de{' '}
              <span className="font-medium">{total}</span> asignaciones
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
                disabled={asignaciones.length < size}
                className="px-4 py-2 border border-gray-300 dark:border-gray-600 rounded-lg text-sm font-medium text-gray-700 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700 disabled:opacity-50 disabled:cursor-not-allowed transition"
              >
                Siguiente
              </button>
            </div>
          </div>
        </>
      )}

      {/* Modal de eliminación */}
      {modalEliminar.abierto && modalEliminar.asignacion && (
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
              ¿Estás seguro de que quieres eliminar esta asignación?
            </p>

            <div className="bg-gray-50 dark:bg-gray-700/50 rounded-lg p-3 mb-4 text-sm">
              <div className="flex items-center gap-3 mb-2">
                <div
                  className="w-10 h-10 rounded-lg flex items-center justify-center flex-shrink-0"
                  style={{ backgroundColor: `${modalEliminar.asignacion.colorHex}20` }}
                >
                  <MdBook
                    className="text-lg"
                    style={{ color: modalEliminar.asignacion.colorHex || '#808080' }}
                  />
                </div>
                <div>
                  <div className="font-medium text-gray-800 dark:text-white">
                    {modalEliminar.asignacion.materiaNombre}
                  </div>
                  <div className="text-xs text-gray-500 dark:text-gray-400 font-mono">
                    {modalEliminar.asignacion.materiaClave}
                  </div>
                </div>
              </div>
              <div className="flex justify-between mt-1">
                <span className="text-gray-500 dark:text-gray-400">Grupo:</span>
                <span className="font-medium text-gray-800 dark:text-white">
                  {modalEliminar.asignacion.grupoNombre}
                </span>
              </div>
              <div className="flex justify-between mt-1">
                <span className="text-gray-500 dark:text-gray-400">Maestro:</span>
                <span className="font-medium text-gray-800 dark:text-white truncate max-w-[200px]">
                  {modalEliminar.asignacion.maestroNombre}
                </span>
              </div>
              <div className="flex justify-between mt-1">
                <span className="text-gray-500 dark:text-gray-400">Aula:</span>
                <span className="font-medium text-gray-800 dark:text-white">
                  {modalEliminar.asignacion.aulaNombre}
                </span>
              </div>
              <div className="flex justify-between mt-1">
                <span className="text-gray-500 dark:text-gray-400">Horas:</span>
                <span className="font-medium text-gray-800 dark:text-white">
                  {modalEliminar.asignacion.horas}
                </span>
              </div>
              {modalEliminar.asignacion.turnoNombre && (
                <div className="flex justify-between mt-1">
                  <span className="text-gray-500 dark:text-gray-400">Turno:</span>
                  <span className="font-medium text-gray-800 dark:text-white">
                    {modalEliminar.asignacion.turnoNombre}
                  </span>
                </div>
              )}
            </div>

            <p className="text-xs text-red-600 dark:text-red-400 mb-4">
              ⚠️ Esta acción no se puede deshacer.
            </p>

            <div className="flex gap-3">
              <button
                onClick={() => setModalEliminar({ abierto: false, asignacion: null })}
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

export default Asignaciones;