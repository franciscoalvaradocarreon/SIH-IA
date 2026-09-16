import React, { useState, useEffect, useRef } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { disponibilidadGrupoService } from '../api/disponibilidadGrupoService';
import { grupoService } from '../api/grupoService';
import { turnoHorarioService } from '../api/turnoHorarioService';
import { turnoService } from '../api/turnoService';
import { useAuth } from '../context/AuthContext';
import type { Grupo, Turno, TurnoHorario } from '../types';
import {
  MdAdd, MdEdit, MdRefresh, MdClass, MdSchedule,
  MdCheck, MdClose, MdChevronLeft, MdChevronRight,
  MdWarning
} from 'react-icons/md';

const DIAS_SEMANA = [
  { value: 1, label: 'Lunes' },
  { value: 2, label: 'Martes' },
  { value: 3, label: 'Miércoles' },
  { value: 4, label: 'Jueves' },
  { value: 5, label: 'Viernes' },
];

const DisponibilidadGrupo: React.FC = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const { semestreActivo } = useAuth();

  const queryParams = new URLSearchParams(location.search);
  const grupoIdFromUrl = parseInt(queryParams.get('grupoId') || '0');
  const turnoIdFromUrl = parseInt(queryParams.get('turnoId') || '0');

  const aplicarTurnoInicial = useRef(turnoIdFromUrl > 0);
  const aplicarGrupoInicial = useRef(grupoIdFromUrl > 0);

  const [grupos, setGrupos] = useState<Grupo[]>([]);
  const [turnos, setTurnos] = useState<Turno[]>([]);
  const [grupoSeleccionado, setGrupoSeleccionado] = useState<number>(0);
  const [turnoSeleccionado, setTurnoSeleccionado] = useState<number>(0);
  const [horarios, setHorarios] = useState<TurnoHorario[]>([]);
  const [disponibilidades, setDisponibilidades] = useState<Map<number, boolean>>(new Map());
  const [loading, setLoading] = useState(true);
  const [cargandoGrupos, setCargandoGrupos] = useState(false);
  const [error, setError] = useState('');

  const extraerHorarios = (respuesta: any): TurnoHorario[] => {
    if (respuesta?.data) {
      if (Array.isArray(respuesta.data)) return respuesta.data;
      if (respuesta.data.data && Array.isArray(respuesta.data.data)) return respuesta.data.data;
    }
    if (Array.isArray(respuesta)) return respuesta;
    return [];
  };

  // 🔥 EFECTO 1: Cargar turnos al cambiar semestre
  useEffect(() => {
    cargarTurnos();
  }, [semestreActivo?.id]);

  // 🔥 EFECTO 2: Cargar grupos al cambiar turno
  useEffect(() => {
    if (!loading) {
      cargarGrupos(turnoSeleccionado);
    }
  }, [turnoSeleccionado]);

  // 🔥 EFECTO 3: Cargar horarios del turno
  useEffect(() => {
    if (turnoSeleccionado > 0 && semestreActivo?.id) {
      cargarHorariosTurno(turnoSeleccionado);
    } else {
      setHorarios([]);
    }
  }, [turnoSeleccionado, semestreActivo]);

  // 🔥 EFECTO 4: Cargar disponibilidades del grupo
  useEffect(() => {
    if (grupoSeleccionado > 0 && horarios.length > 0) {
      cargarDisponibilidades(grupoSeleccionado);
    } else {
      setDisponibilidades(new Map());
    }
  }, [grupoSeleccionado, horarios, semestreActivo]);

  // 🔥 EFECTO 5: Sincronizar URL (con guards para no pisar la URL durante la carga)
  useEffect(() => {
    if (loading) return;

    const currentParams = new URLSearchParams(location.search);
    const currentTurno = parseInt(currentParams.get('turnoId') || '0');
    const currentGrupo = parseInt(currentParams.get('grupoId') || '0');

    if (currentGrupo > 0 && grupoSeleccionado === 0) return;
    if (cargandoGrupos) return;

    if (currentTurno === turnoSeleccionado && currentGrupo === grupoSeleccionado) {
      return;
    }

    const newParams = new URLSearchParams();
    if (turnoSeleccionado > 0) newParams.set('turnoId', String(turnoSeleccionado));
    if (grupoSeleccionado > 0) newParams.set('grupoId', String(grupoSeleccionado));

    const query = newParams.toString();
    navigate(`${location.pathname}${query ? `?${query}` : ''}`, { replace: true });
  }, [turnoSeleccionado, grupoSeleccionado, loading, cargandoGrupos, location.search]);

  // 🔥 Atajos de teclado
  useEffect(() => {
    const handleKey = (e: KeyboardEvent) => {
      if (e.target instanceof HTMLInputElement || e.target instanceof HTMLSelectElement) return;
      if (e.key === 'ArrowLeft') irAnterior();
      if (e.key === 'ArrowRight') irSiguiente();
    };
    window.addEventListener('keydown', handleKey);
    return () => window.removeEventListener('keydown', handleKey);
  });

  const cargarTurnos = async () => {
    if (!semestreActivo?.id) {
      setTurnos([]);
      setGrupos([]);
      setLoading(false);
      return;
    }
    setLoading(true);
    try {
      const res = await turnoService.listar(0, 100, '', semestreActivo.id);
      const activos = res.data.content.filter((t: Turno) => t.activo === true);
      setTurnos(activos);

      if (activos.length > 0) {
        if (aplicarTurnoInicial.current && turnoIdFromUrl > 0) {
          const existe = activos.some(t => t.id === turnoIdFromUrl);
          aplicarTurnoInicial.current = false;
          if (existe) {
            setTurnoSeleccionado(turnoIdFromUrl);
            return;
          }
        }
        setTurnoSeleccionado(activos[0].id);
      } else {
        setTurnoSeleccionado(0);
        setGrupos([]);
        setGrupoSeleccionado(0);
      }
    } catch (error) {
      console.error('Error al cargar turnos:', error);
      setTurnos([]);
    } finally {
      setLoading(false);
    }
  };

  // 🔥 Cargar grupos filtrados por turno (con preselección desde URL)
  const cargarGrupos = async (turnoId: number) => {
    if (!semestreActivo?.id) return;
    setCargandoGrupos(true);
    try {
      // 🔥 listar(page, size, busqueda, especialidadId, semestreId, turnoId)
      const res = await grupoService.listar(
        0,
        100,
        '',
        0,
        semestreActivo.id,
        turnoId > 0 ? turnoId : undefined
      );
      const activos = res.data.content.filter((g: Grupo) => g.activo === true);
      setGrupos(activos);

      // 🔥 Fix: mutar el ref FUERA del updater
      const aplicarDesdeUrl = aplicarGrupoInicial.current && grupoIdFromUrl > 0;
      if (aplicarDesdeUrl) {
        aplicarGrupoInicial.current = false;
      }

      const grupoDesdeUrl =
        aplicarDesdeUrl && activos.some((g: Grupo) => g.id === grupoIdFromUrl)
          ? grupoIdFromUrl
          : null;

      setGrupoSeleccionado((prev) => {
        if (grupoDesdeUrl !== null) return grupoDesdeUrl;
        if (prev > 0 && activos.some((g: Grupo) => g.id === prev)) return prev;
        return activos.length > 0 ? activos[0].id : 0;
      });
    } catch (error) {
      console.error('Error al cargar grupos:', error);
      setGrupos([]);
      setGrupoSeleccionado(0);
    } finally {
      setCargandoGrupos(false);
    }
  };

  const cargarHorariosTurno = async (turnoId: number) => {
    try {
      const semestreId = semestreActivo?.id;
      if (!semestreId) {
        setHorarios([]);
        return;
      }
      const res = await turnoHorarioService.listar(turnoId, semestreId);
      const data = extraerHorarios(res);
      const clases = data.filter((h: TurnoHorario) => !h.descanso);
      setHorarios(clases);
    } catch (error) {
      console.error('Error al cargar horarios:', error);
      setError('Error al cargar los horarios del turno');
    }
  };

  const cargarDisponibilidades = async (grupoId: number) => {
    try {
      const semestreId = semestreActivo?.id;
      if (!semestreId) {
        setDisponibilidades(new Map());
        return;
      }
      const res = await disponibilidadGrupoService.obtenerPorGrupo(grupoId, semestreId);
      const data = Array.isArray(res.data) ? res.data : [];
      const mapa = new Map<number, boolean>();
      data.forEach((d) => {
        mapa.set(d.turnoHorarioId, d.disponible);
      });
      setDisponibilidades(mapa);
    } catch (error) {
      console.error('Error al cargar disponibilidades:', error);
      setDisponibilidades(new Map());
    }
  };

  const handleGrupoChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    setGrupoSeleccionado(Number(e.target.value));
  };

  const handleTurnoChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    setTurnoSeleccionado(Number(e.target.value));
  };

  const getOrdenesUnicos = () => {
    const ordenes = horarios.map(h => h.orden || 0);
    return [...new Set(ordenes)].sort((a, b) => a - b);
  };

  const getHorarioPorDiaYOrden = (dia: number, orden: number) => {
    return horarios.find(h => h.diaSemana === dia && h.orden === orden);
  };

  const formatearHora = (hora: string) => hora.substring(0, 5);

  const indiceActual = grupos.findIndex(g => g.id === grupoSeleccionado);
  const tieneAnterior = indiceActual > 0;
  const tieneSiguiente = indiceActual >= 0 && indiceActual < grupos.length - 1;

  function irAnterior() {
    if (tieneAnterior) {
      setGrupoSeleccionado(grupos[indiceActual - 1].id);
    }
  }

  function irSiguiente() {
    if (tieneSiguiente) {
      setGrupoSeleccionado(grupos[indiceActual + 1].id);
    }
  }

  if (!semestreActivo) {
    return (
      <div className="p-6 text-center text-yellow-600 dark:text-yellow-400">
        <p className="text-lg font-semibold">⚠️ No hay semestre activo</p>
        <p className="text-sm">Selecciona un semestre en el menú para gestionar disponibilidad.</p>
      </div>
    );
  }

  if (loading) {
    return (
      <div className="flex justify-center items-center h-64">
        <div className="animate-spin rounded-full h-12 w-12 border-4 border-blue-500 border-t-transparent"></div>
      </div>
    );
  }

  return (
    <div className="p-6 max-w-7xl mx-auto">
      {/* Encabezado */}
      <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4 mb-6">
        <div>
          <h1 className="text-3xl font-bold text-gray-800 dark:text-white">
            Disponibilidad de Grupos
          </h1>
          <p className="text-sm text-gray-500 dark:text-gray-400 mt-1">
            Configura los bloques horarios disponibles para cada grupo
            <span className="ml-2 text-blue-600 dark:text-blue-400 font-medium">
              (Semestre: {semestreActivo.nombre})
            </span>
          </p>
        </div>
      </div>

      {/* 🔥 FILTROS: Turno + Grupo */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4 mb-6">
        {/* Turno */}
        <div>
          <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
            <MdSchedule className="inline mr-1" />
            Turno
          </label>
          <select
            value={turnoSeleccionado}
            onChange={handleTurnoChange}
            className="w-full px-4 py-2.5 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-blue-500"
          >
            <option value={0}>Seleccionar turno...</option>
            {turnos.map((turno) => (
              <option key={turno.id} value={turno.id}>
                {turno.nombre}
              </option>
            ))}
          </select>
          {turnos.length === 0 && (
            <p className="text-xs text-yellow-600 dark:text-yellow-400 mt-1">
              No hay turnos activos en este semestre
            </p>
          )}
        </div>

        {/* Grupo (filtrado por turno) */}
        <div>
          <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
            <MdClass className="inline mr-1" />
            Grupo
          </label>
          <select
            value={grupoSeleccionado}
            onChange={handleGrupoChange}
            disabled={cargandoGrupos || grupos.length === 0}
            className="w-full px-4 py-2.5 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:opacity-50"
          >
            <option value={0}>
              {cargandoGrupos
                ? 'Cargando grupos...'
                : grupos.length === 0
                ? 'Sin grupos en este turno'
                : 'Seleccionar grupo...'}
            </option>
            {grupos.map((g) => {
              let espTexto = '';
              if (g.especialidad) {
                espTexto = typeof g.especialidad === 'string'
                  ? g.especialidad
                  : g.especialidad.nombre || '';
              }
              return (
                <option key={g.id} value={g.id}>
                  {g.nombre} - {g.grado}°
                  {espTexto ? ` (${espTexto})` : ''}
                </option>
              );
            })}
          </select>
          {turnoSeleccionado > 0 && !cargandoGrupos && grupos.length === 0 && (
            <p className="text-xs text-yellow-600 dark:text-yellow-400 mt-1">
              Este turno no tiene grupos asignados
            </p>
          )}
        </div>
      </div>

      {error && (
        <div className="bg-red-50 dark:bg-red-900/30 text-red-700 dark:text-red-300 p-4 rounded-lg border border-red-200 dark:border-red-800 mb-6">
          {error}
        </div>
      )}

      {/* Matriz */}
      {grupoSeleccionado > 0 && horarios.length > 0 && (
        <div className="bg-white dark:bg-gray-800 rounded-xl shadow-md overflow-hidden border border-gray-100 dark:border-gray-700">
          <div className="px-4 py-4 bg-gray-50 dark:bg-gray-700/50 border-b border-gray-200 dark:border-gray-700 flex flex-col md:flex-row items-center justify-between gap-4">
            <div className="flex items-center gap-3 md:flex-1 md:justify-start">
              <div className="w-12 h-12 rounded-full bg-indigo-100 dark:bg-indigo-900/40 flex items-center justify-center text-indigo-600 dark:text-indigo-400 font-bold text-lg">
                {grupos.find(g => g.id === grupoSeleccionado)?.nombre?.charAt(0) || 'G'}
              </div>
              <div>
                <h3 className="font-semibold text-lg text-gray-800 dark:text-white">
                  {grupos.find(g => g.id === grupoSeleccionado)?.nombre} -{' '}
                  {grupos.find(g => g.id === grupoSeleccionado)?.grado}°
                </h3>
                <p className="text-sm text-gray-500 dark:text-gray-400">
                  {turnos.find(t => t.id === turnoSeleccionado)?.nombre || 'Sin turno'}
                </p>
              </div>
            </div>

            {/* Navegación */}
            <div className="flex items-center gap-2 bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-600 shadow-md px-2 py-1">
              <button
                onClick={irAnterior}
                disabled={!tieneAnterior}
                className={`flex items-center gap-1 px-4 py-2.5 rounded-l-lg text-base font-semibold transition ${
                  tieneAnterior
                    ? 'text-gray-700 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-700'
                    : 'text-gray-300 dark:text-gray-600 cursor-not-allowed'
                }`}
                title="Grupo anterior (←)"
              >
                <MdChevronLeft className="text-2xl" />
                <span>Anterior</span>
              </button>

              <span className="px-4 py-2 text-base font-bold text-gray-700 dark:text-gray-200 border-l border-r border-gray-200 dark:border-gray-600 whitespace-nowrap">
                {indiceActual >= 0 ? indiceActual + 1 : 0} <span className="text-gray-400 font-normal">de</span> {grupos.length}
              </span>

              <button
                onClick={irSiguiente}
                disabled={!tieneSiguiente}
                className={`flex items-center gap-1 px-4 py-2.5 rounded-r-lg text-base font-semibold transition ${
                  tieneSiguiente
                    ? 'text-gray-700 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-700'
                    : 'text-gray-300 dark:text-gray-600 cursor-not-allowed'
                }`}
                title="Grupo siguiente (→)"
              >
                <span>Siguiente</span>
                <MdChevronRight className="text-2xl" />
              </button>
            </div>

            {/* Botones de acción */}
            <div className="flex items-center gap-2 md:flex-1 md:justify-end">
              <button
                onClick={() => navigate(`/horarios/disponibilidad-grupo/edit/${grupoSeleccionado}?turnoId=${turnoSeleccionado}&semestreId=${semestreActivo.id}`)}
                className="flex items-center gap-1.5 bg-indigo-600 hover:bg-indigo-700 text-white px-3 py-2 rounded-lg text-sm font-medium transition"
                title="Editar disponibilidad"
              >
                <MdEdit className="text-base" />
                Editar
              </button>
              <button
                onClick={() => {
                  if (grupoSeleccionado > 0) {
                    cargarDisponibilidades(grupoSeleccionado);
                  }
                }}
                className="flex items-center gap-1.5 bg-blue-600 hover:bg-blue-700 text-white px-3 py-2 rounded-lg text-sm font-medium transition"
                title="Recargar disponibilidad"
              >
                <MdRefresh className="text-base" />
                Recargar
              </button>
            </div>
          </div>

          <div className="overflow-x-auto">
            <table className="min-w-full divide-y divide-gray-200 dark:divide-gray-700">
              <thead className="bg-gray-50 dark:bg-gray-700/50">
                <tr>
                  <th className="px-3 py-2 text-left text-xs font-medium text-gray-500 dark:text-gray-400 uppercase">
                    Hora
                  </th>
                  {DIAS_SEMANA.map((dia) => (
                    <th
                      key={dia.value}
                      className="px-3 py-2 text-center text-xs font-medium text-gray-500 dark:text-gray-400 uppercase"
                    >
                      {dia.label}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody className="bg-white dark:bg-gray-800 divide-y divide-gray-200 dark:divide-gray-700">
                {getOrdenesUnicos().map((orden) => {
                  const ref = horarios.find(h => h.orden === orden);
                  if (!ref) return null;

                  return (
                    <tr key={orden} className="hover:bg-gray-50 dark:hover:bg-gray-700/50 transition">
                      <td className="px-3 py-2 whitespace-nowrap text-sm text-gray-700 dark:text-gray-300">
                        {formatearHora(ref.horaInicio)} - {formatearHora(ref.horaFin)}
                      </td>
                      {DIAS_SEMANA.map((dia) => {
                        const h = getHorarioPorDiaYOrden(dia.value, orden);
                        const disponible = h ? disponibilidades.get(h.id) || false : false;
                        const existe = h !== undefined;

                        return (
                          <td key={dia.value} className="px-3 py-2 text-center">
                            {existe ? (
                              <div
                                className={`w-9 h-9 rounded-lg flex items-center justify-center mx-auto transition-all ${
                                  disponible
                                    ? 'bg-green-500 text-white shadow-md shadow-green-200 dark:shadow-none'
                                    : 'bg-gray-200 dark:bg-gray-700 text-gray-500 dark:text-gray-400'
                                }`}
                                title={disponible ? 'Disponible' : 'No disponible'}
                              >
                                {disponible ? <MdCheck className="text-lg" /> : <MdClose className="text-lg" />}
                              </div>
                            ) : (
                              <span className="text-gray-300 dark:text-gray-600">-</span>
                            )}
                          </td>
                        );
                      })}
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>

          <div className="px-4 py-3 bg-gray-50 dark:bg-gray-700/30 border-t border-gray-200 dark:border-gray-700 text-sm text-gray-500 dark:text-gray-400 flex flex-wrap items-center gap-4">
            <span className="inline-flex items-center gap-2">
              <span className="w-4 h-4 bg-green-500 rounded"></span>
              Disponible
            </span>
            <span className="inline-flex items-center gap-2">
              <span className="w-4 h-4 bg-gray-300 dark:bg-gray-600 rounded"></span>
              No disponible
            </span>
            <span className="inline-flex items-center gap-2">
              <span className="text-gray-300 dark:text-gray-600">-</span>
              Sin horario
            </span>
            <span className="text-xs text-gray-400 ml-auto">
              Total: <span className="font-medium text-gray-700 dark:text-gray-300">
                {Array.from(disponibilidades.values()).filter(v => v === true).length}
              </span> bloques disponibles
            </span>
          </div>
        </div>
      )}

      {grupoSeleccionado > 0 && horarios.length === 0 && !loading && !cargandoGrupos && (
        <div className="bg-yellow-50 dark:bg-yellow-900/20 border border-yellow-200 dark:border-yellow-800 rounded-lg p-8 text-center">
          <MdSchedule className="text-5xl text-yellow-600 dark:text-yellow-400 mx-auto mb-3" />
          <h3 className="text-lg font-semibold text-yellow-800 dark:text-yellow-200">
            Sin bloques horarios
          </h3>
          <p className="text-sm text-yellow-700 dark:text-yellow-300 mt-1">
            El turno no tiene bloques configurados en este semestre.
          </p>
        </div>
      )}

      {turnoSeleccionado > 0 && grupos.length === 0 && !cargandoGrupos && (
        <div className="bg-yellow-50 dark:bg-yellow-900/20 border border-yellow-200 dark:border-yellow-800 rounded-lg p-8 text-center">
          <MdWarning className="text-5xl text-yellow-600 dark:text-yellow-400 mx-auto mb-3" />
          <h3 className="text-lg font-semibold text-yellow-800 dark:text-yellow-200">
            Sin grupos en este turno
          </h3>
          <p className="text-sm text-yellow-700 dark:text-yellow-300 mt-1">
            El turno seleccionado no tiene grupos asignados. Cambia de turno o asigna grupos desde la pantalla de Grupos.
          </p>
        </div>
      )}

      {grupoSeleccionado === 0 && turnoSeleccionado === 0 && (
        <div className="text-center py-12 text-gray-500 dark:text-gray-400">
          <MdClass className="text-4xl mx-auto mb-2 text-gray-300 dark:text-gray-600" />
          <p>Selecciona un turno para ver sus grupos</p>
        </div>
      )}
    </div>
  );
};

export default DisponibilidadGrupo;