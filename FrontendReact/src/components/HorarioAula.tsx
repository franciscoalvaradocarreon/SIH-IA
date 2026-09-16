// src/components/HorarioAula.tsx
import React, { useState, useEffect } from 'react';
import { horarioService } from '../api/horarioService';
import { aulaService } from '../api/aulaService';
import { turnoService } from '../api/turnoService';
import { turnoHorarioService } from '../api/turnoHorarioService';
import { useAuth } from '../context/AuthContext';
import type { Horario, Aula, Turno, TurnoHorario } from '../types';
import {
  MdRefresh, MdMeetingRoom, MdAccessTime, MdSchedule,
  MdClass, MdPerson, MdChevronLeft, MdChevronRight
} from 'react-icons/md';

const DIAS_SEMANA = [
  { value: 1, label: 'Lunes' },
  { value: 2, label: 'Martes' },
  { value: 3, label: 'Miércoles' },
  { value: 4, label: 'Jueves' },
  { value: 5, label: 'Viernes' },
];

const HorarioAula: React.FC = () => {
  const { semestreActivo } = useAuth();

  const [aulas, setAulas] = useState<Aula[]>([]);
  const [turnos, setTurnos] = useState<Turno[]>([]);
  const [turnoSeleccionado, setTurnoSeleccionado] = useState<number>(0);
  const [aulaSeleccionada, setAulaSeleccionada] = useState<number>(0);
  const [horarios, setHorarios] = useState<Horario[]>([]);
  const [bloquesTurno, setBloquesTurno] = useState<TurnoHorario[]>([]);
  const [loading, setLoading] = useState(true);
  const [cargandoAulas, setCargandoAulas] = useState(false);
  const [cargandoHorario, setCargandoHorario] = useState(false);
  const [error, setError] = useState('');

  const normalizarHora = (hora: string | undefined): string => {
    if (!hora) return '';
    return hora.substring(0, 5);
  };

  const extraerTurnoHorarios = (respuesta: any): TurnoHorario[] => {
    if (respuesta?.data) {
      if (Array.isArray(respuesta.data)) return respuesta.data;
      if (respuesta.data.data && Array.isArray(respuesta.data.data)) return respuesta.data.data;
    }
    if (Array.isArray(respuesta)) return respuesta;
    return [];
  };

  // 🔥 EFECTO 1: Cargar turnos al cambiar semestre
  useEffect(() => {
    if (semestreActivo?.id) {
      cargarTurnos();
    }
  }, [semestreActivo]);

  // 🔥 EFECTO 2: Cargar aulas cuando cambia el turno
  useEffect(() => {
    if (!loading) {
      cargarAulas(turnoSeleccionado);
    }
  }, [turnoSeleccionado]);

  // 🔥 EFECTO 3: Cargar bloques del turno
  useEffect(() => {
    if (turnoSeleccionado > 0 && semestreActivo?.id) {
      cargarBloquesTurno(turnoSeleccionado);
    } else {
      setBloquesTurno([]);
    }
  }, [turnoSeleccionado, semestreActivo]);

  // 🔥 EFECTO 4: Cargar horario del aula
  useEffect(() => {
    if (aulaSeleccionada > 0 && semestreActivo?.id) {
      cargarHorarioAula(aulaSeleccionada);
    } else {
      setHorarios([]);
    }
  }, [aulaSeleccionada, semestreActivo]);

  // 🔥 Cuando cambia la lista de aulas (por cambio de turno), ajustar selección
  useEffect(() => {
    if (!cargandoAulas && aulas.length >= 0 && turnoSeleccionado >= 0) {
      const aulaActualValida = aulas.some(a => a.id === aulaSeleccionada);
      if (!aulaActualValida && aulas.length > 0) {
        setAulaSeleccionada(aulas[0].id);
      } else if (aulas.length === 0) {
        setAulaSeleccionada(0);
      }
    }
  }, [aulas]);

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

  // 🔥 Cargar turnos
  const cargarTurnos = async () => {
    setLoading(true);
    try {
      const semestreId = semestreActivo?.id;
      if (!semestreId) {
        setTurnos([]);
        setLoading(false);
        return;
      }
      const res = await turnoService.listar(0, 100, '', semestreId);
      const activos = res.data.content.filter((t: Turno) => t.activo === true);
      setTurnos(activos);

      if (activos.length > 0) {
        setTurnoSeleccionado(activos[0].id);
      } else {
        setTurnoSeleccionado(0);
        setAulas([]);
        setAulaSeleccionada(0);
      }
    } catch (err) {
      console.error('Error al cargar turnos:', err);
      setTurnos([]);
    } finally {
      setLoading(false);
    }
  };

  // 🔥 Cargar aulas filtradas por turno
  const cargarAulas = async (turnoId: number) => {
    if (!semestreActivo?.id) return;
    setCargandoAulas(true);
    try {
      const res = await aulaService.listar(
        0,
        100,
        '',
        semestreActivo.id,
        turnoId > 0 ? turnoId : undefined
      );
      const activas = res.data.content.filter((a: Aula) => a.activo === true);
      setAulas(activas);

      // Si el aula actual ya no pertenece al turno, seleccionar la primera
      const aulaValida = activas.some((a: Aula) => a.id === aulaSeleccionada);
      if (!aulaValida && activas.length > 0) {
        setAulaSeleccionada(activas[0].id);
      } else if (activas.length === 0) {
        setAulaSeleccionada(0);
      }
    } catch (err) {
      console.error('Error al cargar aulas:', err);
      setAulas([]);
      setAulaSeleccionada(0);
    } finally {
      setCargandoAulas(false);
    }
  };

  // Cargar horario del aula (con semestreId)
  const cargarHorarioAula = async (aulaId: number) => {
    setCargandoHorario(true);
    setError('');
    try {
      const res = await horarioService.obtenerPorAula(aulaId, semestreActivo?.id);
      setHorarios(res.data);
    } catch (err) {
      console.error('Error al cargar horario del aula:', err);
      setHorarios([]);
      setError('Error al cargar el horario del aula');
    } finally {
      setCargandoHorario(false);
    }
  };

  // Cargar bloques del turno (para la matriz)
  const cargarBloquesTurno = async (turnoId: number) => {
    try {
      const semestreId = semestreActivo?.id;
      if (!semestreId) {
        setBloquesTurno([]);
        return;
      }
      const res = await turnoHorarioService.listar(turnoId, semestreId);
      const data = extraerTurnoHorarios(res);
      setBloquesTurno(data.filter((h: TurnoHorario) => !h.descanso));
    } catch (err) {
      console.error('Error al cargar bloques del turno:', err);
      setBloquesTurno([]);
    }
  };

  const handleTurnoChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    setTurnoSeleccionado(Number(e.target.value));
  };

  const handleRecargar = () => {
    if (aulaSeleccionada > 0) {
      cargarHorarioAula(aulaSeleccionada);
    }
    if (turnoSeleccionado > 0) {
      cargarBloquesTurno(turnoSeleccionado);
    }
  };

  const getOrdenesUnicos = (): number[] => {
    const ordenes = bloquesTurno.map(h => h.orden || 0);
    return [...new Set(ordenes)].sort((a, b) => a - b);
  };

  const getBloqueTurno = (dia: number, orden: number): TurnoHorario | null => {
    return bloquesTurno.find(h => h.diaSemana === dia && h.orden === orden) || null;
  };

  const getClaseAula = (dia: number, horaInicio: string): Horario | null => {
    const horaNorm = normalizarHora(horaInicio);
    return horarios.find(h =>
      h.diaSemana === dia && normalizarHora(h.horaInicio) === horaNorm
    ) || null;
  };

  // 🔥 Navegación entre aulas (solo las del turno)
  const indiceActual = aulas.findIndex(a => a.id === aulaSeleccionada);
  const tieneAnterior = indiceActual > 0;
  const tieneSiguiente = indiceActual >= 0 && indiceActual < aulas.length - 1;

  function irAnterior() {
    if (tieneAnterior) {
      setAulaSeleccionada(aulas[indiceActual - 1].id);
    }
  }

  function irSiguiente() {
    if (tieneSiguiente) {
      setAulaSeleccionada(aulas[indiceActual + 1].id);
    }
  }

  const aulaActual = aulas.find(a => a.id === aulaSeleccionada);
  const turnoActual = turnos.find(t => t.id === turnoSeleccionado);

  if (!semestreActivo) {
    return (
      <div className="p-6 text-center text-yellow-600 dark:text-yellow-400">
        <p className="text-lg font-semibold">⚠️ No hay semestre activo</p>
        <p className="text-sm">Selecciona un semestre en el menú para ver los horarios.</p>
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
    <div className="p-4 max-w-7xl mx-auto">
      {/* Encabezado */}
      <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4 mb-6">
        <div>
          <h1 className="text-3xl font-bold text-gray-800 dark:text-white">
            Horario de Aulas
          </h1>
          <p className="text-sm text-gray-500 dark:text-gray-400 mt-1">
            Consulta la ocupación semanal de cada aula
            {turnoActual && (
              <span className="ml-2 text-indigo-600 dark:text-indigo-400 font-medium">
                · Turno: {turnoActual.nombre}
              </span>
            )}
            <span className="ml-2 text-blue-600 dark:text-blue-400 font-medium">
              (Semestre: {semestreActivo.nombre})
            </span>
          </p>
        </div>
        <button
          onClick={handleRecargar}
          disabled={aulaSeleccionada === 0 && turnoSeleccionado === 0}
          className="flex items-center gap-2 bg-gray-200 dark:bg-gray-700 hover:bg-gray-300 dark:hover:bg-gray-600 text-gray-700 dark:text-gray-300 px-4 py-2.5 rounded-lg shadow-md transition disabled:opacity-50"
        >
          <MdRefresh className="text-xl" />
          Recargar
        </button>
      </div>

      {/* 🔥 Filtros: Turno + Aula */}
      <div className="bg-white dark:bg-gray-800 rounded-xl shadow-md p-6 mb-6 border border-gray-100 dark:border-gray-700">
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
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
              {turnos.map((t) => (
                <option key={t.id} value={t.id}>
                  {t.nombre}
                </option>
              ))}
            </select>
            {turnos.length === 0 && (
              <p className="text-xs text-yellow-600 dark:text-yellow-400 mt-1">
                No hay turnos activos en este semestre
              </p>
            )}
          </div>

          {/* Aula (filtrada por turno) */}
          <div>
            <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
              <MdMeetingRoom className="inline mr-1" />
              Aula
            </label>
            <select
              value={aulaSeleccionada}
              onChange={(e) => setAulaSeleccionada(Number(e.target.value))}
              disabled={cargandoAulas || aulas.length === 0}
              className="w-full px-4 py-2.5 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:opacity-50"
            >
              <option value={0}>
                {cargandoAulas
                  ? 'Cargando aulas...'
                  : aulas.length === 0
                  ? 'Sin aulas en este turno'
                  : 'Seleccionar aula'}
              </option>
              {aulas.map((a) => (
                <option key={a.id} value={a.id}>
                  {a.nombre}
                  {a.edificio ? ` - ${a.edificio}` : ''}
                  {a.piso ? ` (${a.piso})` : ''}
                </option>
              ))}
            </select>
            {turnoSeleccionado > 0 && !cargandoAulas && aulas.length === 0 && (
              <p className="text-xs text-yellow-600 dark:text-yellow-400 mt-1">
                Este turno no tiene aulas asignadas
              </p>
            )}
          </div>
        </div>

        {error && (
          <div className="mt-4 bg-red-50 dark:bg-red-900/30 text-red-700 dark:text-red-300 p-3 rounded-lg border border-red-200 dark:border-red-800 text-sm">
            {error}
          </div>
        )}
      </div>

      {/* Matriz */}
      {cargandoHorario ? (
        <div className="flex justify-center items-center h-48">
          <div className="animate-spin rounded-full h-10 w-10 border-4 border-blue-500 border-t-transparent"></div>
        </div>
      ) : bloquesTurno.length > 0 && aulaActual ? (
        <div className="bg-white dark:bg-gray-800 rounded-xl shadow-md overflow-hidden border border-gray-100 dark:border-gray-700">
          {/* Header con navegación */}
          <div className="px-4 py-4 bg-gray-50 dark:bg-gray-700/50 border-b border-gray-200 dark:border-gray-700 flex flex-col md:flex-row items-center justify-between gap-4">
            {/* Info del aula */}
            <div className="flex items-center gap-3 md:flex-1 md:justify-start">
              <div className="w-12 h-12 rounded-full bg-indigo-100 dark:bg-indigo-900/40 flex items-center justify-center text-indigo-600 dark:text-indigo-400 font-bold text-lg">
                {aulaActual.nombre?.charAt(0) || 'A'}
              </div>
              <div>
                <h3 className="font-semibold text-lg text-gray-800 dark:text-white">
                  {aulaActual.nombre}
                </h3>
                <p className="text-sm text-gray-500 dark:text-gray-400">
                  {aulaActual.edificio || 'Sin edificio'}
                  {aulaActual.piso ? ` · ${aulaActual.piso}` : ''}
                  {' · '}
                  {turnoActual?.nombre || 'Sin turno'}
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
                title="Aula anterior (←)"
              >
                <MdChevronLeft className="text-2xl" />
                <span>Anterior</span>
              </button>

              <span className="px-4 py-2 text-base font-bold text-gray-700 dark:text-gray-200 border-l border-r border-gray-200 dark:border-gray-600 whitespace-nowrap">
                {indiceActual >= 0 ? indiceActual + 1 : 0} <span className="text-gray-400 font-normal">de</span> {aulas.length}
              </span>

              <button
                onClick={irSiguiente}
                disabled={!tieneSiguiente}
                className={`flex items-center gap-1 px-4 py-2.5 rounded-r-lg text-base font-semibold transition ${
                  tieneSiguiente
                    ? 'text-gray-700 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-700'
                    : 'text-gray-300 dark:text-gray-600 cursor-not-allowed'
                }`}
                title="Aula siguiente (→)"
              >
                <span>Siguiente</span>
                <MdChevronRight className="text-2xl" />
              </button>
            </div>

            {/* Total de clases */}
            <div className="md:flex-1 md:flex md:justify-end">
              <span className="text-sm text-gray-600 dark:text-gray-400 whitespace-nowrap bg-white dark:bg-gray-800 px-4 py-2 rounded-lg border border-gray-200 dark:border-gray-600 shadow-sm">
                Total de clases: <span className="font-bold text-gray-800 dark:text-white text-base">{horarios.length}</span>
              </span>
            </div>
          </div>

          <div className="overflow-x-auto">
            <table className="min-w-full divide-y divide-gray-200 dark:divide-gray-700">
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
              <tbody className="bg-white dark:bg-gray-800 divide-y divide-gray-200 dark:divide-gray-700">
                {getOrdenesUnicos().map((orden) => {
                  const ref = bloquesTurno.find(h => h.orden === orden);
                  if (!ref) return null;

                  return (
                    <tr key={orden} className="hover:bg-gray-50 dark:hover:bg-gray-700/50 transition-colors">
                      <td className="px-3 py-2 whitespace-nowrap text-sm font-medium text-gray-700 dark:text-gray-300">
                        <div className="flex flex-col items-center leading-tight">
                          <span>{normalizarHora(ref.horaInicio)}</span>
                          <span className="text-xs text-gray-400">-</span>
                          <span className="text-xs text-gray-500">{normalizarHora(ref.horaFin)}</span>
                        </div>
                      </td>
                      {DIAS_SEMANA.map((dia) => {
                        const bloque = getBloqueTurno(dia.value, orden);

                        if (!bloque) {
                          return (
                            <td key={dia.value} className="px-2 py-2 text-center">
                              <span className="text-gray-300 dark:text-gray-600 text-xs">-</span>
                            </td>
                          );
                        }

                        const clase = getClaseAula(dia.value, ref.horaInicio);

                        if (clase) {
                          return (
                            <td key={dia.value} className="px-2 py-2 text-center align-top">
                              <div
                                className="rounded-lg p-1 text-xs border-4 transition hover:shadow-md"
                                style={{
                                  borderColor: clase.colorHex || '#e5e7eb',
                                  backgroundColor: `${clase.colorHex || '#808080'}15`,
                                }}
                              >
                                <div
                                  className="font-bold text-lg truncate"
                                  style={{ color: clase.colorHex || '#374151' }}
                                >
                                  {clase.materiaClave || clase.materiaNombre}
                                </div>
                                <div className="flex items-center justify-center gap-1">
                                  <span className="text-lg truncate max-w-[80px]">{clase.grupoNombre}</span>
                                </div>
                                <div className="flex items-center justify-center gap-1">
                                  <span className="text-base truncate max-w-[180px]">{clase.maestroNombre}</span>
                                </div>
                              </div>
                            </td>
                          );
                        }

                        return (
                          <td key={dia.value} className="px-2 py-2 text-center align-center">
                            <div className="rounded-lg p-2 border-2 border-dashed border-gray-300 dark:border-gray-600 bg-gray-50/50 dark:bg-gray-800/50">
                              <span className="text-gray-400 text-[20px]">Libre</span>
                            </div>
                          </td>
                        );
                      })}
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>

          <div className="px-4 py-3 bg-gray-50 dark:bg-gray-700/30 border-t border-gray-200 dark:border-gray-700 flex flex-wrap items-center gap-4 text-sm">
            <span className="inline-flex items-center gap-2 text-gray-600 dark:text-gray-400">
              <span className="w-4 h-4 bg-indigo-100 dark:bg-indigo-900/40 border border-gray-300 dark:border-gray-600 rounded"></span>
              Clase asignada
            </span>
            <span className="inline-flex items-center gap-2 text-gray-600 dark:text-gray-400">
              <span className="w-4 h-4 border-2 border-dashed border-gray-300 dark:border-gray-600 bg-gray-50 dark:bg-gray-800 rounded"></span>
              Libre
            </span>
            <span className="inline-flex items-center gap-2 text-gray-600 dark:text-gray-400">
              <span className="text-gray-300 dark:text-gray-600">-</span>
              Sin horario en el turno
            </span>
          </div>
        </div>
      ) : (
        <div className="bg-white dark:bg-gray-800 rounded-xl shadow-md p-12 text-center border border-gray-100 dark:border-gray-700">
          <MdSchedule className="text-6xl text-gray-300 dark:text-gray-600 mx-auto mb-4" />
          <h3 className="text-xl font-semibold text-gray-700 dark:text-gray-300 mb-2">
            No hay bloques para mostrar
          </h3>
          <p className="text-gray-500 dark:text-gray-400">
            {turnoSeleccionado === 0
              ? 'Selecciona un turno para ver la matriz.'
              : aulaSeleccionada === 0
              ? 'Este turno no tiene aulas asignadas.'
              : 'Este turno no tiene bloques configurados en el semestre actual.'}
          </p>
        </div>
      )}
    </div>
  );
};

export default HorarioAula;