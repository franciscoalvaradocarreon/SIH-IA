import React, { useState, useEffect, useRef } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { disponibilidadService } from '../api/disponibilidadService';
import { maestroService } from '../api/maestroService';
import { turnoHorarioService } from '../api/turnoHorarioService';
import { turnoService } from '../api/turnoService';
import { useAuth } from '../context/AuthContext';
import type { Maestro, Turno, TurnoHorario } from '../types';
import {
  MdAdd, MdEdit, MdRefresh, MdPerson, MdSchedule,
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

const DisponibilidadMaestro: React.FC = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const { semestreActivo } = useAuth();

  const queryParams = new URLSearchParams(location.search);
  const maestroIdFromUrl = parseInt(queryParams.get('maestroId') || '0');
  const turnoIdFromUrl = parseInt(queryParams.get('turnoId') || '0');

  const aplicarTurnoInicial = useRef(turnoIdFromUrl > 0);
  const aplicarMaestroInicial = useRef(maestroIdFromUrl > 0);

  const [maestros, setMaestros] = useState<Maestro[]>([]);
  const [turnos, setTurnos] = useState<Turno[]>([]);
  const [maestroSeleccionado, setMaestroSeleccionado] = useState<number>(0);
  const [turnoSeleccionado, setTurnoSeleccionado] = useState<number>(0);
  const [horarios, setHorarios] = useState<TurnoHorario[]>([]);
  const [disponibilidades, setDisponibilidades] = useState<Map<number, boolean>>(new Map());
  const [loading, setLoading] = useState(true);
  const [cargandoMaestros, setCargandoMaestros] = useState(false);
  const [error, setError] = useState('');

  const extraerHorarios = (respuesta: any): TurnoHorario[] => {
    if (respuesta?.data) {
      if (Array.isArray(respuesta.data)) return respuesta.data;
      if (respuesta.data.data && Array.isArray(respuesta.data.data)) return respuesta.data.data;
    }
    if (Array.isArray(respuesta)) return respuesta;
    return [];
  };

  // 🔥 EFECTO 1: Cargar turnos cuando cambia el semestre
  useEffect(() => {
    cargarTurnos();
  }, [semestreActivo?.id]);

  // 🔥 EFECTO 2: Cargar maestros cuando cambia el turno
  useEffect(() => {
    if (!loading) {
      cargarMaestros(turnoSeleccionado);
    }
  }, [turnoSeleccionado]);

  // 🔥 EFECTO 3: Cargar bloques del turno
  useEffect(() => {
    if (turnoSeleccionado > 0 && semestreActivo?.id) {
      cargarHorariosTurno(turnoSeleccionado);
    } else {
      setHorarios([]);
    }
  }, [turnoSeleccionado, semestreActivo]);

  // 🔥 EFECTO 4: Cargar disponibilidades del maestro
  useEffect(() => {
    if (maestroSeleccionado > 0 && horarios.length > 0) {
      cargarDisponibilidadesMaestro(maestroSeleccionado);
    } else {
      setDisponibilidades(new Map());
    }
  }, [maestroSeleccionado, horarios, semestreActivo]);

  // 🔥 EFECTO 5: Sincronizar URL con el estado actual
  useEffect(() => {
    if (loading) return;

    const currentParams = new URLSearchParams(location.search);
    const currentTurno = parseInt(currentParams.get('turnoId') || '0');
    const currentMaestro = parseInt(currentParams.get('maestroId') || '0');

    // GUARD 1: la URL pide un maestro que aún no se ha aplicado
    if (currentMaestro > 0 && maestroSeleccionado === 0) {
      return;
    }

    // GUARD 2: si estamos cargando maestros, no tocar la URL
    if (cargandoMaestros) {
      return;
    }

    if (currentTurno === turnoSeleccionado && currentMaestro === maestroSeleccionado) {
      return;
    }

    const newParams = new URLSearchParams();
    if (turnoSeleccionado > 0) newParams.set('turnoId', String(turnoSeleccionado));
    if (maestroSeleccionado > 0) newParams.set('maestroId', String(maestroSeleccionado));

    const query = newParams.toString();
    navigate(`${location.pathname}${query ? `?${query}` : ''}`, { replace: true });
  }, [turnoSeleccionado, maestroSeleccionado, loading, cargandoMaestros, location.search]);

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
      setMaestros([]);
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
        setMaestros([]);
        setMaestroSeleccionado(0);
      }
    } catch (error) {
      console.error('Error al cargar turnos:', error);
      setTurnos([]);
    } finally {
      setLoading(false);
    }
  };

  // 🔥 Cargar maestros filtrados por turno (con preselección desde URL)
  const cargarMaestros = async (turnoId: number) => {
    if (!semestreActivo?.id) return;
    setCargandoMaestros(true);
    try {
      const res = await maestroService.listar(
        0,
        100,
        '',
        semestreActivo.id,
        turnoId > 0 ? turnoId : undefined
      );
      const activos = res.data.content.filter((m: Maestro) => m.activo === true);
      setMaestros(activos);

      // 🔥 CRÍTICO: mutar el ref FUERA del updater.
      // React 18 StrictMode invoca el updater dos veces. Si la mutación
      // está dentro, la segunda invocación pierde el valor de la URL.
      const aplicarDesdeUrl = aplicarMaestroInicial.current && maestroIdFromUrl > 0;
      if (aplicarDesdeUrl) {
        aplicarMaestroInicial.current = false;
      }

      const maestroDesdeUrl =
        aplicarDesdeUrl && activos.some((m: Maestro) => m.id === maestroIdFromUrl)
          ? maestroIdFromUrl
          : null;

      setMaestroSeleccionado((prev) => {
        // Updater PURO: no muta refs, no llama a otras funciones.
        if (maestroDesdeUrl !== null) return maestroDesdeUrl;
        if (prev > 0 && activos.some((m: Maestro) => m.id === prev)) return prev;
        return activos.length > 0 ? activos[0].id : 0;
      });
    } catch (error) {
      console.error('Error al cargar maestros:', error);
      setMaestros([]);
      setMaestroSeleccionado(0);
    } finally {
      setCargandoMaestros(false);
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
      const horariosData = extraerHorarios(res);
      const clases = horariosData.filter((h: TurnoHorario) => !h.descanso);
      setHorarios(clases);
    } catch (error) {
      console.error('Error al cargar horarios:', error);
      setError('Error al cargar los horarios del turno');
    }
  };

  const cargarDisponibilidadesMaestro = async (maestroId: number) => {
    try {
      const semestreId = semestreActivo?.id;
      if (!semestreId) {
        setDisponibilidades(new Map());
        return;
      }
      const res = await disponibilidadService.obtenerPorMaestro(maestroId);
      const mapa = new Map<number, boolean>();
      const disponibilidadesData = Array.isArray(res.data) ? res.data : [];
      disponibilidadesData.forEach((disp) => {
        mapa.set(disp.turnoHorarioId, disp.disponible);
      });
      setDisponibilidades(mapa);
    } catch (error) {
      console.error('Error al cargar disponibilidades:', error);
      setDisponibilidades(new Map());
    }
  };

  const handleMaestroChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    setMaestroSeleccionado(Number(e.target.value));
  };

  const handleTurnoChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    setTurnoSeleccionado(Number(e.target.value));
  };

  const getHorarioPorDiaYOrden = (dia: number, orden: number) => {
    return horarios.find(h => h.diaSemana === dia && h.orden === orden);
  };

  const getOrdenesUnicos = () => {
    const ordenes = horarios.map(h => h.orden || 0);
    return [...new Set(ordenes)].sort((a, b) => a - b);
  };

  const formatearHora = (hora: string) => {
    return hora.substring(0, 5);
  };

  const indiceActual = maestros.findIndex(m => m.id === maestroSeleccionado);
  const tieneAnterior = indiceActual > 0;
  const tieneSiguiente = indiceActual >= 0 && indiceActual < maestros.length - 1;

  function irAnterior() {
    if (tieneAnterior) {
      setMaestroSeleccionado(maestros[indiceActual - 1].id);
    }
  }

  function irSiguiente() {
    if (tieneSiguiente) {
      setMaestroSeleccionado(maestros[indiceActual + 1].id);
    }
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
      <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4 mb-6">
        <div>
          <h1 className="text-3xl font-bold text-gray-800 dark:text-white">
            Disponibilidad de Maestros
          </h1>
          <p className="text-sm text-gray-500 dark:text-gray-400 mt-1">
            Visualiza la disponibilidad de cada maestro por turno y día
            <span className="ml-2 text-blue-600 dark:text-blue-400 font-medium">
              (Semestre: {semestreActivo?.nombre})
            </span>
          </p>
        </div>
      </div>

      {/* FILTROS: Turno + Maestro */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4 mb-6">
        <div>
          <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
            <MdSchedule className="inline mr-1" />
            Turno
          </label>
          <select
            value={turnoSeleccionado}
            onChange={handleTurnoChange}
            className="w-full px-4 py-2.5 border border-gray-400 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-blue-500"
          >
            <option value={0}>Seleccionar turno...</option>
            {turnos.map((turno) => (
              <option key={turno.id} value={turno.id}>
                {turno.nombre}
              </option>
            ))}
          </select>
          {turnos.length === 0 && semestreActivo && (
            <p className="text-xs text-yellow-600 dark:text-yellow-400 mt-1">
              No hay turnos activos en este semestre
            </p>
          )}
        </div>

        <div>
          <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
            <MdPerson className="inline mr-1" />
            Maestro
          </label>
          <select
            value={maestroSeleccionado}
            onChange={handleMaestroChange}
            disabled={cargandoMaestros || maestros.length === 0}
            className="w-full px-4 py-2.5 border border-gray-400 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:opacity-50"
          >
            <option value={0}>
              {cargandoMaestros
                ? 'Cargando maestros...'
                : maestros.length === 0
                ? 'Sin maestros en este turno'
                : 'Seleccionar maestro...'}
            </option>
            {maestros.map((maestro) => (
              <option key={maestro.id} value={maestro.id}>
                {maestro.nombreCompleto}
              </option>
            ))}
          </select>
          {turnoSeleccionado > 0 && !cargandoMaestros && maestros.length === 0 && (
            <p className="text-xs text-yellow-600 dark:text-yellow-400 mt-1">
              Este turno no tiene maestros asignados
            </p>
          )}
        </div>
      </div>

      {error && (
        <div className="bg-red-50 dark:bg-red-900/30 text-red-700 dark:text-red-300 p-4 rounded-lg border border-red-200 dark:border-red-800 mb-6">
          {error}
        </div>
      )}

      {maestroSeleccionado > 0 && horarios.length > 0 && (
        <div className="bg-white dark:bg-gray-800 rounded-xl shadow-md overflow-hidden border border-gray-400 dark:border-gray-700">
          <div className="px-4 py-4 bg-gray-50 dark:bg-gray-700/50 border-b border-gray-400 dark:border-gray-700 flex flex-col md:flex-row items-center justify-between gap-4">
            <div className="flex items-center gap-3 md:flex-1 md:justify-start">
              <div className="w-12 h-12 rounded-full bg-blue-100 dark:bg-blue-900/40 flex items-center justify-center text-blue-600 dark:text-blue-400 font-bold text-lg">
                {maestros.find(m => m.id === maestroSeleccionado)?.nombreCompleto?.charAt(0) || 'M'}
              </div>
              <div>
                <h3 className="font-semibold text-lg text-gray-800 dark:text-white">
                  {maestros.find(m => m.id === maestroSeleccionado)?.nombreCompleto}
                </h3>
                <p className="text-sm text-gray-500 dark:text-gray-400">
                  {turnos.find(t => t.id === turnoSeleccionado)?.nombre || 'Sin turno'}
                </p>
              </div>
            </div>

            <div className="flex items-center gap-2 bg-white dark:bg-gray-800 rounded-xl border border-gray-400 dark:border-gray-600 shadow-md px-2 py-1">
              <button
                onClick={irAnterior}
                disabled={!tieneAnterior}
                className={`flex items-center gap-1 px-4 py-2.5 rounded-l-lg text-base font-semibold transition ${
                  tieneAnterior
                    ? 'text-gray-700 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-700'
                    : 'text-gray-300 dark:text-gray-600 cursor-not-allowed'
                }`}
                title="Maestro anterior (←)"
              >
                <MdChevronLeft className="text-2xl" />
                <span>Anterior</span>
              </button>

              <span className="px-4 py-2 text-base font-bold text-gray-700 dark:text-gray-200 border-l border-r border-gray-400 dark:border-gray-600 whitespace-nowrap">
                {indiceActual >= 0 ? indiceActual + 1 : 0} <span className="text-gray-400 font-normal">de</span> {maestros.length}
              </span>

              <button
                onClick={irSiguiente}
                disabled={!tieneSiguiente}
                className={`flex items-center gap-1 px-4 py-2.5 rounded-r-lg text-base font-semibold transition ${
                  tieneSiguiente
                    ? 'text-gray-700 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-700'
                    : 'text-gray-300 dark:text-gray-600 cursor-not-allowed'
                }`}
                title="Maestro siguiente (→)"
              >
                <span>Siguiente</span>
                <MdChevronRight className="text-2xl" />
              </button>
            </div>

            <div className="flex items-center gap-2 md:flex-1 md:justify-end">
              <button
                onClick={() => navigate(`/horarios/disponibilidad-maestro/edit/${maestroSeleccionado}?turnoId=${turnoSeleccionado}`)}
                className="flex items-center gap-1.5 bg-indigo-600 hover:bg-indigo-700 text-white px-3 py-2 rounded-lg text-sm font-medium transition"
                title="Editar disponibilidad"
              >
                <MdEdit className="text-base" />
                Editar
              </button>
              <button
                onClick={() => {
                  if (maestroSeleccionado > 0) {
                    cargarDisponibilidadesMaestro(maestroSeleccionado);
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
            <table className="min-w-full divide-y divide-gray-400 dark:divide-gray-700">
              <thead className="bg-gray-50 dark:bg-gray-700/50">
                <tr>
                  <th className="px-3 py-2 text-left text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider">
                    Hora
                  </th>
                  {DIAS_SEMANA.map((dia) => (
                    <th
                      key={dia.value}
                      className="px-3 py-2 text-center text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider"
                    >
                      {dia.label}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody className="bg-white dark:bg-gray-800 divide-y divide-gray-400 dark:divide-gray-700">
                {getOrdenesUnicos().map((orden) => {
                  const horarioReferencia = horarios.find(h => h.orden === orden);
                  if (!horarioReferencia) return null;

                  return (
                    <tr key={orden} className="hover:bg-gray-50 dark:hover:bg-gray-700/50 transition-colors">
                      <td className="px-3 py-2 whitespace-nowrap text-sm text-gray-700 dark:text-gray-300">
                        {formatearHora(horarioReferencia.horaInicio)} - {formatearHora(horarioReferencia.horaFin)}
                      </td>
                      {DIAS_SEMANA.map((dia) => {
                        const horarioDia = getHorarioPorDiaYOrden(dia.value, orden);
                        const disponible = horarioDia
                          ? disponibilidades.get(horarioDia.id) || false
                          : false;
                        const existe = horarioDia !== undefined;

                        return (
                          <td key={dia.value} className="px-3 py-2 text-center">
                            {existe ? (
                              <div
                                className={`w-9 h-9 rounded-lg flex items-center justify-center mx-auto transition-all duration-200 ${
                                  disponible
                                    ? 'bg-green-500 text-white shadow-md shadow-green-200 dark:shadow-none'
                                    : 'bg-gray-200 dark:bg-gray-700 text-gray-500 dark:text-gray-400'
                                }`}
                                title={disponible ? 'Disponible' : 'No disponible'}
                              >
                                {disponible ? (
                                  <MdCheck className="text-lg" />
                                ) : (
                                  <MdClose className="text-lg" />
                                )}
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

          <div className="px-4 py-3 bg-gray-50 dark:bg-gray-700/30 border-t border-gray-400 dark:border-gray-700 text-sm text-gray-500 dark:text-gray-400 flex flex-wrap items-center gap-4">
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
              Última actualización: {new Date().toLocaleString()}
            </span>
          </div>

          <div className="px-4 py-3 bg-gray-50 dark:bg-gray-700/30 border-t border-gray-400 dark:border-gray-700">
            <div className="flex flex-wrap items-center justify-between gap-2 text-sm">
              <div>
                <span className="text-gray-600 dark:text-gray-400">
                  Bloques disponibles:{' '}
                  <span className="font-medium text-green-600 dark:text-green-400">
                    {Array.from(disponibilidades.values()).filter(v => v === true).length}
                  </span>
                </span>
                <span className="text-gray-600 dark:text-gray-400 ml-4">
                  No disponibles:{' '}
                  <span className="font-medium text-red-600 dark:text-red-400">
                    {Array.from(disponibilidades.values()).filter(v => v === false).length}
                  </span>
                </span>
                <span className="text-gray-600 dark:text-gray-400 ml-4">
                  Total:{' '}
                  <span className="font-medium text-gray-800 dark:text-white">
                    {disponibilidades.size}
                  </span>
                </span>
              </div>
            </div>
          </div>
        </div>
      )}

      {maestroSeleccionado === 0 && turnoSeleccionado > 0 && maestros.length === 0 && !cargandoMaestros && (
        <div className="bg-yellow-50 dark:bg-yellow-900/20 border border-yellow-200 dark:border-yellow-800 rounded-lg p-8 text-center">
          <MdWarning className="text-5xl text-yellow-600 dark:text-yellow-400 mx-auto mb-3" />
          <h3 className="text-lg font-semibold text-yellow-800 dark:text-yellow-200">
            Sin maestros en este turno
          </h3>
          <p className="text-sm text-yellow-700 dark:text-yellow-300 mt-1">
            El turno seleccionado no tiene maestros asignados. Cambia de turno
            o asigna maestros desde la pantalla de Maestros.
          </p>
        </div>
      )}

      {maestroSeleccionado === 0 && turnoSeleccionado === 0 && (
        <div className="text-center py-12 text-gray-500 dark:text-gray-400">
          <MdPerson className="text-4xl mx-auto mb-2 text-gray-300 dark:text-gray-600" />
          <p>Selecciona un turno para ver sus maestros</p>
        </div>
      )}
    </div>
  );
};

export default DisponibilidadMaestro;