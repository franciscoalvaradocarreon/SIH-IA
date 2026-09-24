import React, { useState, useEffect, useMemo, useRef, useCallback } from 'react';
import { horarioService } from '../api/horarioService';
import { aulaService } from '../api/aulaService';
import { turnoService } from '../api/turnoService';
import { turnoHorarioService } from '../api/turnoHorarioService';
import { useAuth } from '../context/AuthContext';
import type { Horario, Aula, Turno, TurnoHorario } from '../types';
import {
  MdRefresh, MdMeetingRoom, MdSchedule,
  MdChevronLeft, MdChevronRight, MdWarning,
} from 'react-icons/md';
import FilaBloqueHorario from '../components/FilaBloqueHorario';
import {
  DIAS_SEMANA,
  construirBloquesFilas,
  extraerLista,
  indexarHorarios,
} from '../utils/horarioUtils';

const HorarioAula: React.FC = () => {
  const { semestreActivo } = useAuth();

  // ── Estado ──
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

  // Nonce para forzar recarga
  const [reloadNonce, setReloadNonce] = useState(0);

  // ── Derivados ──
  const bloquesFilas = useMemo(
    () => construirBloquesFilas(bloquesTurno),
    [bloquesTurno]
  );

  const horarioIndex = useMemo(() => indexarHorarios(horarios), [horarios]);

  /**
   * 🔥 Solapamientos del aula: 2 grupos distintos ocupando la misma aula
   * en el mismo bloque. Con las constraints activas, esto debería ser siempre 0.
   */
  const solapamientos = useMemo(() => {
    let bloquesAfectados = 0;
    let clasesInvolucradas = 0;
    for (const arr of horarioIndex.values()) {
      if (arr.length > 1) {
        bloquesAfectados++;
        clasesInvolucradas += arr.length;
      }
    }
    return { bloquesAfectados, clasesInvolucradas };
  }, [horarioIndex]);

  const indiceActual = useMemo(
    () => aulas.findIndex((a) => a.id === aulaSeleccionada),
    [aulas, aulaSeleccionada]
  );
  const tieneAnterior = indiceActual > 0;
  const tieneSiguiente =
    indiceActual >= 0 && indiceActual < aulas.length - 1;

  const aulaActual = useMemo(
    () => aulas.find((a) => a.id === aulaSeleccionada) ?? null,
    [aulas, aulaSeleccionada]
  );

  const turnoActual = useMemo(
    () => turnos.find((t) => t.id === turnoSeleccionado) ?? null,
    [turnos, turnoSeleccionado]
  );

  // ── Carga de turnos ──
  const cargarTurnos = useCallback(async () => {
    if (!semestreActivo?.id) {
      setTurnos([]);
      setLoading(false);
      return;
    }
    setLoading(true);
    try {
      const res = await turnoService.listar(0, 100, '', semestreActivo.id);
      const activos = res.data.content.filter((t: Turno) => t.activo === true);
      setTurnos(activos);

      if (activos.length > 0) {
        setTurnoSeleccionado((prev) =>
          activos.some((t) => t.id === prev) ? prev : activos[0].id
        );
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
  }, [semestreActivo?.id]);

  useEffect(() => {
    void cargarTurnos();
  }, [cargarTurnos]);

  // ── Carga de aulas filtradas por turno ──
  const cargarAulas = useCallback(async (turnoId: number) => {
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
      setAulaSeleccionada((prev) =>
        activas.some((a) => a.id === prev) ? prev : activas[0]?.id ?? 0
      );
    } catch (err) {
      console.error('Error al cargar aulas:', err);
      setAulas([]);
      setAulaSeleccionada(0);
    } finally {
      setCargandoAulas(false);
    }
  }, [semestreActivo?.id]);

  useEffect(() => {
    if (!loading) {
      void cargarAulas(turnoSeleccionado);
    }
  }, [turnoSeleccionado, loading, cargarAulas]);

  // ── Carga de bloques del turno ──
  useEffect(() => {
    if (turnoSeleccionado === 0 || !semestreActivo?.id) {
      setBloquesTurno([]);
      return;
    }

    let cancelled = false;
    (async () => {
      try {
        const res = await turnoHorarioService.listar(
          turnoSeleccionado,
          semestreActivo.id
        );
        if (cancelled) return;
        const data = extraerLista<TurnoHorario>(res);
        setBloquesTurno(data);
      } catch (err) {
        if (cancelled) return;
        console.error('Error al cargar bloques del turno:', err);
        setBloquesTurno([]);
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [turnoSeleccionado, semestreActivo?.id, reloadNonce]);

  // ── Carga de horario del aula ──
  useEffect(() => {
    if (aulaSeleccionada === 0 || !semestreActivo?.id) {
      setHorarios([]);
      return;
    }

    setCargandoHorario(true);
    setError('');

    let cancelled = false;
    (async () => {
      try {
        const res = await horarioService.obtenerPorAula(
          aulaSeleccionada,
          semestreActivo.id
        );
        if (cancelled) return;
        setHorarios(res.data);
      } catch (err) {
        if (cancelled) return;
        console.error('Error al cargar horario del aula:', err);
        setHorarios([]);
        setError('Error al cargar el horario del aula');
      } finally {
        if (!cancelled) setCargandoHorario(false);
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [aulaSeleccionada, semestreActivo?.id, reloadNonce]);

  // ── Navegación entre aulas ──
  const irAnterior = useCallback(() => {
    if (!tieneAnterior) return;
    setAulaSeleccionada(aulas[indiceActual - 1].id);
  }, [tieneAnterior, aulas, indiceActual]);

  const irSiguiente = useCallback(() => {
    if (!tieneSiguiente) return;
    setAulaSeleccionada(aulas[indiceActual + 1].id);
  }, [tieneSiguiente, aulas, indiceActual]);

  const irAnteriorRef = useRef(irAnterior);
  const irSiguienteRef = useRef(irSiguiente);
  irAnteriorRef.current = irAnterior;
  irSiguienteRef.current = irSiguiente;

  useEffect(() => {
    const handleKey = (e: KeyboardEvent) => {
      const t = e.target;
      if (t instanceof HTMLInputElement || t instanceof HTMLSelectElement) return;
      if (e.key === 'ArrowLeft') irAnteriorRef.current();
      if (e.key === 'ArrowRight') irSiguienteRef.current();
    };
    window.addEventListener('keydown', handleKey);
    return () => window.removeEventListener('keydown', handleKey);
  }, []);

  // ── Handlers ──
  const handleTurnoChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    setTurnoSeleccionado(Number(e.target.value));
  };

  const handleRecargar = () => {
    setReloadNonce((n) => n + 1);
  };

  // ── Guards de render ──
  if (!semestreActivo) {
    return (
      <div className="p-6 text-center text-yellow-600 dark:text-yellow-400">
        <p className="text-lg font-semibold">⚠️ No hay semestre activo</p>
        <p className="text-sm">
          Selecciona un semestre en el menú para ver los horarios.
        </p>
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

  // ── Render ──
  return (
    <div className="p-6 max-w-7xl mx-auto">
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
        <div className="flex gap-3 flex-wrap">
          <button
            onClick={handleRecargar}
            disabled={aulaSeleccionada === 0 && turnoSeleccionado === 0}
            className="flex items-center gap-2 bg-gray-200 dark:bg-gray-700 hover:bg-gray-300 dark:hover:bg-gray-600 text-gray-700 dark:text-gray-300 px-4 py-2.5 rounded-lg shadow-md transition disabled:opacity-50"
          >
            <MdRefresh className="text-xl" />
            Recargar
          </button>
        </div>
      </div>

      {/* Banner de solapamientos (aula ocupada por 2 grupos a la vez) */}
      {solapamientos.bloquesAfectados > 0 && (
        <div className="bg-red-50 dark:bg-red-900/20 border-2 border-red-500 dark:border-red-700 rounded-xl p-4 mb-6">
          <div className="flex items-start gap-3">
            <MdWarning className="text-3xl text-red-600 dark:text-red-400 flex-shrink-0 mt-0.5" />
            <div className="flex-1">
              <h3 className="font-bold text-red-800 dark:text-red-200 text-lg">
                ⚠️ El aula tiene {solapamientos.bloquesAfectados} bloque(s) con solapamiento
              </h3>
              <p className="text-sm text-red-700 dark:text-red-300 mt-1">
                {solapamientos.clasesInvolucradas} clases de distintos grupos coinciden
                en el mismo bloque de esta aula. Esto <strong>no debería pasar</strong> con
                las constraints activas.
              </p>
              <p className="text-xs text-red-600 dark:text-red-400 mt-2 italic">
                Regenera el horario desde el Generador de Horarios para corregirlo.
              </p>
            </div>
          </div>
        </div>
      )}

      {/* Filtros */}
      <div className="bg-white dark:bg-gray-800 rounded-xl shadow-md p-6 mb-6 border border-gray-400 dark:border-gray-700">
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
              className="w-full px-4 py-2.5 border border-gray-400 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-blue-500"
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
              className="w-full px-4 py-2.5 border border-gray-400 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:opacity-50"
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

      {/* Matriz o mensajes vacíos */}
      {cargandoHorario ? (
        <div className="flex justify-center items-center h-48">
          <div className="animate-spin rounded-full h-10 w-10 border-4 border-blue-500 border-t-transparent"></div>
        </div>
      ) : bloquesFilas.length > 0 && aulaActual ? (
        <div className="bg-white dark:bg-gray-800 rounded-xl shadow-md border border-gray-400 dark:border-gray-700">
          {/* Header con info + navegación + total. Se queda PEGADO arriba mientras la tabla pasa
              por debajo (patrón de la caja de pines). Dos detalles que NO son opcionales:
              'overflow-hidden' fuera de la tarjeta —si no, el 'sticky' se pega a ella, que crece
              con el contenido, y no a la ventana— y fondo OPACO. */}
          <div className="sticky top-0 z-30 rounded-t-xl px-4 py-4 bg-gray-50 dark:bg-gray-700 border-b border-gray-400 dark:border-gray-700 flex flex-col md:flex-row items-center justify-between gap-4">
            {/* Info del aula */}
            <div className="flex items-center gap-3 md:flex-1 md:justify-start">
              <div className="w-12 h-12 rounded-full bg-indigo-100 dark:bg-indigo-900/40 flex items-center justify-center text-indigo-600 dark:text-indigo-400 font-bold text-lg">
                {aulaActual.nombre?.charAt(0) ?? 'A'}
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
            <div className="flex items-center gap-2 bg-white dark:bg-gray-800 rounded-xl border border-gray-400 dark:border-gray-600 shadow-md px-2 py-1">
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

              <span className="px-4 py-2 text-base font-bold text-gray-700 dark:text-gray-200 border-l border-r border-gray-400 dark:border-gray-600 whitespace-nowrap">
                {indiceActual >= 0 ? indiceActual + 1 : 0}{' '}
                <span className="text-gray-400 font-normal">de</span>{' '}
                {aulas.length}
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
              <span className="text-sm text-gray-600 dark:text-gray-400 whitespace-nowrap bg-white dark:bg-gray-800 px-4 py-2 rounded-lg border border-gray-400 dark:border-gray-600 shadow-sm">
                Total de clases:{' '}
                <span className="font-bold text-gray-800 dark:text-white text-base">
                  {horarios.length}
                </span>
              </span>
            </div>
          </div>

          {/* Matriz (misma estructura que HorarioView) */}
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
                {bloquesFilas.map((bloque) => (
                  <FilaBloqueHorario
                    key={bloque.id}
                    bloque={bloque}
                    horarioIndex={horarioIndex}
                    variante="aula"
                  />
                ))}
              </tbody>
            </table>
          </div>
        </div>
      ) : (
        <div className="bg-white dark:bg-gray-800 rounded-xl shadow-md p-12 text-center border border-gray-400 dark:border-gray-700">
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