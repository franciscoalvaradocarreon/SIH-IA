import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { isAxiosError } from 'axios';
import { horarioService } from '../api/horarioService';
import { grupoService } from '../api/grupoService';
import { turnoService } from '../api/turnoService';
import { turnoHorarioService } from '../api/turnoHorarioService';
import { disponibilidadGrupoService } from '../api/disponibilidadGrupoService';
import { useAuth } from '../context/AuthContext';
import type { Horario, Grupo, Turno, ClaseNoAsignada, TurnoHorario } from '../types';
import {
  MdRefresh, MdSchedule, MdClass, MdWarning,
  MdChevronLeft, MdChevronRight,
} from 'react-icons/md';
import FilaBloqueHorario from '../components/FilaBloqueHorario';
import {
  DIAS_SEMANA,
  construirBloquesFilas,
  extraerLista,
  indexarHorarios,
} from '../utils/horarioUtils';

const HorarioView: React.FC = () => {
  const navigate = useNavigate();
  const { semestreActivo } = useAuth();

  // ── Estado ──
  const [grupos, setGrupos] = useState<Grupo[]>([]);
  const [turnos, setTurnos] = useState<Turno[]>([]);
  const [turnoSeleccionado, setTurnoSeleccionado] = useState<number>(0);
  const [grupoSeleccionado, setGrupoSeleccionado] = useState<number>(0);
  const [horarios, setHorarios] = useState<Horario[]>([]);
  const [bloquesTurno, setBloquesTurno] = useState<TurnoHorario[]>([]);
  const [loading, setLoading] = useState(true);
  const [generando, setGenerando] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [bloquesConfigurados, setBloquesConfigurados] = useState<number | null>(null);
  const [validandoGrupo, setValidandoGrupo] = useState(false);
  const [noAsignadas, setNoAsignadas] = useState<ClaseNoAsignada[]>([]);

  // Nonce para forzar recarga sin duplicar la lógica de carga
  const [reloadNonce, setReloadNonce] = useState(0);

  // ── Derivados ──
  const gruposFiltrados = useMemo(
    () =>
      turnoSeleccionado > 0
        ? grupos.filter((g) => g.turnoId === turnoSeleccionado)
        : grupos,
    [grupos, turnoSeleccionado]
  );

  const grupoActual = useMemo(
    () => grupos.find((g) => g.id === grupoSeleccionado) ?? null,
    [grupos, grupoSeleccionado]
  );

  const turnoIdDelGrupo = grupoActual?.turnoId ?? null;

  const bloquesFilas = useMemo(
    () => construirBloquesFilas(bloquesTurno),
    [bloquesTurno]
  );

  const horarioIndex = useMemo(() => indexarHorarios(horarios), [horarios]);

  /**
   * 🔥 NUEVO: detecta solapamientos en el horario actual.
   *
   * Recorre el índice y cuenta cuántos bloques tienen más de 1 horario.
   * Devuelve:
   *  - `bloquesAfectados`: número de celdas (día+hora) con solapamiento
   *  - `clasesInvolucradas`: total de horarios implicados (suma de length > 1)
   *
   * Con las constraints activas del backend, esto debería ser SIEMPRE 0.
   * Si aparece > 0, es señal de que algo se coló (dato corrupto, backup
   * restaurado, bug del solver, etc.) y la UI debe avisar.
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
    () => gruposFiltrados.findIndex((g) => g.id === grupoSeleccionado),
    [gruposFiltrados, grupoSeleccionado]
  );
  const tieneAnterior = indiceActual > 0;
  const tieneSiguiente =
    indiceActual >= 0 && indiceActual < gruposFiltrados.length - 1;

  const turnoActual = useMemo(
    () => turnos.find((t) => t.id === turnoSeleccionado),
    [turnos, turnoSeleccionado]
  );

  // ── Carga de catálogos ──
  const cargarCatalogos = useCallback(
    async (semestreId: number, turnoFiltro: number) => {
      setLoading(true);
      try {
        const [turnosRes, gruposRes] = await Promise.all([
          turnoService.listar(0, 100, '', semestreId),
          grupoService.listar(0, 100, '', 0, semestreId),
        ]);

        const turnosActivos = (turnosRes.data.content as Turno[]).filter(
          (t) => t.activo === true
        );
        const gruposActivos = (gruposRes.data.content as Grupo[]).filter(
          (g) => g.activo === true
        );

        setTurnos(turnosActivos);
        setGrupos(gruposActivos);

        const filtrados =
          turnoFiltro > 0
            ? gruposActivos.filter((g) => g.turnoId === turnoFiltro)
            : gruposActivos;

        setGrupoSeleccionado((prev) =>
          filtrados.some((g) => g.id === prev) ? prev : filtrados[0]?.id ?? 0
        );
      } catch (err) {
        console.error('Error al cargar catálogos:', err);
        setError('Error al cargar los datos');
      } finally {
        setLoading(false);
      }
    },
    []
  );

  useEffect(() => {
    if (!semestreActivo?.id) return;
    void cargarCatalogos(semestreActivo.id, turnoSeleccionado);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [semestreActivo?.id, cargarCatalogos]);

  // ── Carga de horario + validación con AbortController ──
  useEffect(() => {
    if (!grupoSeleccionado || !semestreActivo?.id || turnoIdDelGrupo === null) {
      setHorarios([]);
      setBloquesTurno([]);
      setBloquesConfigurados(null);
      setNoAsignadas([]);
      return;
    }

    const ctrl = new AbortController();
    const { signal } = ctrl;
    const semestreId = semestreActivo.id;
    const grupoId = grupoSeleccionado;

    const ejecutar = async () => {
      setLoading(true);
      setError('');
      setValidandoGrupo(true);
      setNoAsignadas([]);

      const validacionPromise = disponibilidadGrupoService
        .contarDisponibles(grupoId, semestreId)
        .then((res) => {
          if (!signal.aborted) setBloquesConfigurados(res.data);
        })
        .catch((err) => {
          if (signal.aborted) return;
          console.error('Error al validar grupo:', err);
          setBloquesConfigurados(null);
        })
        .finally(() => {
          if (!signal.aborted) setValidandoGrupo(false);
        });

      const bloquesPromise = turnoHorarioService
        .listar(turnoIdDelGrupo, semestreId)
        .then((res) => {
          if (!signal.aborted) setBloquesTurno(extraerLista<TurnoHorario>(res));
        })
        .catch(() => {
          if (!signal.aborted) setBloquesTurno([]);
        });

      const horariosPromise = horarioService
        .obtenerPorGrupo(grupoId, semestreId)
        .then((res) => {
          if (signal.aborted) return;
          setHorarios(res.data);
          if (res.data.length === 0) {
            setError('Este grupo aún no tiene horario generado.');
          }
        })
        .catch((err) => {
          if (signal.aborted) return;
          console.error('Error al cargar horario:', err);
          setHorarios([]);
        });

      try {
        await Promise.all([validacionPromise, bloquesPromise, horariosPromise]);
      } finally {
        if (!signal.aborted) setLoading(false);
      }
    };

    void ejecutar();

    return () => ctrl.abort();
  }, [grupoSeleccionado, semestreActivo?.id, turnoIdDelGrupo, reloadNonce]);

  // ── Navegación entre grupos ──
  const irAnterior = useCallback(() => {
    if (!tieneAnterior) return;
    setGrupoSeleccionado(gruposFiltrados[indiceActual - 1].id);
  }, [tieneAnterior, gruposFiltrados, indiceActual]);

  const irSiguiente = useCallback(() => {
    if (!tieneSiguiente) return;
    setGrupoSeleccionado(gruposFiltrados[indiceActual + 1].id);
  }, [tieneSiguiente, gruposFiltrados, indiceActual]);

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
    const nuevoTurno = Number(e.target.value);
    setTurnoSeleccionado(nuevoTurno);

    const filtrados =
      nuevoTurno > 0 ? grupos.filter((g) => g.turnoId === nuevoTurno) : grupos;
    if (!filtrados.some((g) => g.id === grupoSeleccionado)) {
      setGrupoSeleccionado(filtrados[0]?.id ?? 0);
    }
  };

  const handleRecargar = () => {
    setNoAsignadas([]);
    setReloadNonce((n) => n + 1);
  };

  const handleGenerarHorario = async () => {
    if (grupoSeleccionado === 0) {
      setError('Selecciona un grupo');
      return;
    }
    if (bloquesConfigurados === null || bloquesConfigurados === 0) {
      setError(
        `El grupo "${grupoActual?.nombre ?? ''}" no tiene bloques configurados ` +
          `en este semestre. Configura su disponibilidad antes de generar el horario.`
      );
      return;
    }

    setGenerando(true);
    setError('');
    setSuccess('');
    setNoAsignadas([]);

    try {
      const res = await horarioService.generar(grupoSeleccionado, semestreActivo!.id);
      const data = res.data;

      setSuccess(
        `✅ Horario generado. Clases asignadas: ${data.totalClasesAsignadas}. ` +
          (data.totalClasesNoAsignadas && data.totalClasesNoAsignadas > 0
            ? `⚠️ ${data.totalClasesNoAsignadas} sin acomodar.`
            : '') +
          ` Score: ${data.score?.hardScore ?? 0} hard / ${data.score?.mediumScore ?? 0} medium / ${data.score?.softScore ?? 0} soft`
      );

      if (data.clasesNoAsignadas) setNoAsignadas(data.clasesNoAsignadas);

      setReloadNonce((n) => n + 1);
      setTimeout(() => setSuccess(''), 5000);
    } catch (err: unknown) {
      console.error('Error al generar horario:', err);
      const msg = isAxiosError(err)
        ? (err.response?.data as { message?: string } | undefined)?.message
        : undefined;
      setError(msg ?? 'Error al generar el horario');
    } finally {
      setGenerando(false);
    }
  };

  // ── Guards de render ──
  if (!semestreActivo) {
    return (
      <div className="p-6 text-center text-yellow-600 dark:text-yellow-400">
        <p className="text-lg font-semibold">⚠️ No hay semestre activo</p>
        <p className="text-sm">
          Selecciona un semestre en el menú para gestionar horarios.
        </p>
      </div>
    );
  }

  const generarDeshabilitado =
    generando || grupoSeleccionado === 0 || bloquesConfigurados === 0;

  // ── Render ──
  return (
    <div className="p-6 max-w-7xl mx-auto">
      {/* Encabezado */}
      <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4 mb-6">
        <div>
          <h1 className="text-3xl font-bold text-gray-800 dark:text-white">
            Generador de Horarios
          </h1>
          <p className="text-sm text-gray-500 dark:text-gray-400 mt-1">
            Selecciona un grupo para generar o visualizar su horario
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
            disabled={grupoSeleccionado === 0}
            className="flex items-center gap-2 bg-gray-200 dark:bg-gray-700 hover:bg-gray-300 dark:hover:bg-gray-600 text-gray-700 dark:text-gray-300 px-4 py-2.5 rounded-lg shadow-md transition disabled:opacity-50"
          >
            <MdRefresh className="text-xl" />
            Recargar
          </button>
          <button
            onClick={handleGenerarHorario}
            disabled={generarDeshabilitado}
            className={`flex items-center gap-2 px-4 py-2.5 rounded-lg shadow-md transition ${
              generarDeshabilitado
                ? 'bg-gray-400 cursor-not-allowed text-white'
                : 'bg-green-600 hover:bg-green-700 text-white'
            }`}
          >
            <MdSchedule className="text-xl" />
            {generando ? 'Generando...' : 'Generar Horario'}
          </button>
        </div>
      </div>

      {/* 🔥 Banner de solapamientos (defensivo) */}
      {solapamientos.bloquesAfectados > 0 && (
        <div className="bg-red-50 dark:bg-red-900/20 border-2 border-red-500 dark:border-red-700 rounded-xl p-4 mb-6">
          <div className="flex items-start gap-3">
            <MdWarning className="text-3xl text-red-600 dark:text-red-400 flex-shrink-0 mt-0.5" />
            <div className="flex-1">
              <h3 className="font-bold text-red-800 dark:text-red-200 text-lg">
                ⚠️ Se detectaron {solapamientos.bloquesAfectados} bloque(s) con solapamiento
              </h3>
              <p className="text-sm text-red-700 dark:text-red-300 mt-1">
                {solapamientos.clasesInvolucradas} clases comparten bloques que deberían ser únicos.
                Esto <strong>no debería pasar</strong> con las constraints activas en la base de datos.
              </p>
              <p className="text-xs text-red-600 dark:text-red-400 mt-2 italic">
                Causas posibles: restauración desde un backup antiguo, INSERT manual en BD, o un bug del solver.
                Revisa los logs del backend y regenera el horario del grupo afectado.
              </p>
            </div>
          </div>
        </div>
      )}

      {/* Filtros */}
      <div className="bg-white dark:bg-gray-800 rounded-xl shadow-md p-6 mb-6 border border-gray-400 dark:border-gray-700">
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4 mb-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
              <MdSchedule className="inline mr-1" />
              Filtrar por Turno
            </label>
            <select
              value={turnoSeleccionado}
              onChange={handleTurnoChange}
              className="w-full px-4 py-2.5 border border-gray-400 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-blue-500"
            >
              <option value={0}>Todos los turnos ({grupos.length} grupos)</option>
              {turnos.map((t) => {
                const cuenta = grupos.filter((g) => g.turnoId === t.id).length;
                return (
                  <option key={t.id} value={t.id}>
                    {t.nombre} ({cuenta} grupos)
                  </option>
                );
              })}
            </select>
            {turnos.length === 0 && (
              <p className="text-xs text-yellow-600 dark:text-yellow-400 mt-1">
                No hay turnos activos en este semestre
              </p>
            )}
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
              <MdClass className="inline mr-1" />
              Grupo
            </label>
            <select
              value={grupoSeleccionado}
              onChange={(e) => setGrupoSeleccionado(Number(e.target.value))}
              disabled={gruposFiltrados.length === 0}
              className="w-full px-4 py-2.5 border border-gray-400 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:opacity-50"
            >
              <option value={0}>
                {gruposFiltrados.length === 0
                  ? 'Sin grupos en este turno'
                  : 'Seleccionar grupo'}
              </option>
              {gruposFiltrados.map((grupo) => {
                let especialidadTexto = '';
                if (grupo.especialidad) {
                  if (typeof grupo.especialidad === 'string') {
                    especialidadTexto = grupo.especialidad;
                  } else if (
                    typeof grupo.especialidad === 'object' &&
                    grupo.especialidad.nombre
                  ) {
                    especialidadTexto = grupo.especialidad.nombre;
                  }
                }
                return (
                  <option key={grupo.id} value={grupo.id}>
                    {grupo.nombre} - {grupo.grado}° {grupo.turno}
                    {especialidadTexto ? ` (${especialidadTexto})` : ''}
                  </option>
                );
              })}
            </select>
          </div>
        </div>

        {/* Clases no asignadas */}
        {noAsignadas.length > 0 && (
          <div className="mt-4 bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-xl p-6">
            <div className="flex items-center justify-between mb-4">
              <h3 className="text-lg font-semibold text-red-800 dark:text-red-200 flex items-center gap-2">
                <MdWarning className="text-2xl" />
                Clases no acomodadas ({noAsignadas.length})
              </h3>
            </div>
            <p className="text-sm text-red-700 dark:text-red-300 mb-4">
              Estas clases no encontraron un bloque compatible. Revisa la
              disponibilidad del grupo y del maestro, o ajusta las asignaciones.
            </p>
            <div className="overflow-x-auto">
              <table className="min-w-full divide-y divide-red-200 dark:divide-red-800">
                <thead className="bg-red-100 dark:bg-red-900/30">
                  <tr>
                    {['Materia', 'Maestro', 'Aula', 'Motivo'].map((h) => (
                      <th
                        key={h}
                        className="px-3 py-2 text-left text-xs font-medium text-red-700 dark:text-red-300 uppercase"
                      >
                        {h}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody className="divide-y divide-red-200 dark:divide-red-800">
                  {noAsignadas.map((n, idx) => (
                    <tr
                      key={`${n.materiaClave}-${idx}`}
                      className="hover:bg-red-100/50 dark:hover:bg-red-900/20"
                    >
                      <td className="px-3 py-2 whitespace-nowrap">
                        <div className="flex items-center gap-2">
                          <div
                            className="w-3 h-3 rounded-full"
                            style={{ backgroundColor: '#dc2626' }}
                          />
                          <div>
                            <div className="text-sm font-semibold text-gray-900 dark:text-white">
                              {n.materiaClave}
                            </div>
                            <div className="text-xs text-gray-500 dark:text-gray-400">
                              {n.materiaNombre}
                            </div>
                          </div>
                        </div>
                      </td>
                      <td className="px-3 py-2 whitespace-nowrap text-sm text-gray-700 dark:text-gray-300">
                        {n.maestroNombre}
                      </td>
                      <td className="px-3 py-2 whitespace-nowrap text-sm text-gray-700 dark:text-gray-300">
                        {n.aulaNombre}
                      </td>
                      <td className="px-3 py-2 text-sm text-red-700 dark:text-red-300">
                        {n.motivo}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}

        {validandoGrupo && (
          <p className="mt-3 text-sm text-gray-500 dark:text-gray-400">
            Verificando disponibilidad del grupo...
          </p>
        )}

        {!validandoGrupo && bloquesConfigurados !== null && grupoSeleccionado > 0 && (
          <div
            className={`mt-3 p-3 rounded-lg text-sm flex items-center justify-between ${
              bloquesConfigurados > 0
                ? 'bg-green-50 dark:bg-green-900/20 text-green-700 dark:text-green-300 border border-green-200 dark:border-green-800'
                : 'bg-yellow-50 dark:bg-yellow-900/20 text-yellow-700 dark:text-yellow-300 border border-yellow-200 dark:border-yellow-800'
            }`}
          >
            <span>
              {bloquesConfigurados > 0
                ? `✅ ${bloquesConfigurados} bloques configurados para este grupo`
                : `⚠️ Este grupo no tiene bloques configurados`}
            </span>
            {bloquesConfigurados === 0 && (
              <button
                onClick={() =>
                  navigate(
                    `/horarios/disponibilidad-grupo/edit/${grupoSeleccionado}?semestreId=${semestreActivo.id}&turnoId=${turnoSeleccionado}`
                  )
                }
                className="ml-2 text-blue-600 dark:text-blue-400 hover:underline font-medium whitespace-nowrap"
              >
                Configurar ahora
              </button>
            )}
          </div>
        )}

        {error && (
          <div
            className={`mt-4 p-3 rounded-lg text-sm ${
              error.includes('aún no tiene') || error.includes('no tiene bloques')
                ? 'bg-yellow-50 dark:bg-yellow-900/30 text-yellow-700 dark:text-yellow-300 border border-yellow-200 dark:border-yellow-800'
                : 'bg-red-50 dark:bg-red-900/30 text-red-700 dark:text-red-300 border border-red-200 dark:border-red-800'
            }`}
          >
            {error}
          </div>
        )}

        {success && (
          <div className="mt-4 bg-green-50 dark:bg-green-900/30 text-green-700 dark:text-green-300 p-3 rounded-lg border border-green-200 dark:border-green-800">
            {success}
          </div>
        )}
      </div>

      {/* Matriz */}
      {horarios.length > 0 && bloquesFilas.length > 0 ? (
        <div className="bg-white dark:bg-gray-800 rounded-xl shadow-md overflow-hidden border border-gray-400 dark:border-gray-700">
          <div className="px-4 py-4 bg-gray-50 dark:bg-gray-700/50 border-b border-gray-400 dark:border-gray-700 flex flex-col md:flex-row items-center justify-between gap-4">
            <div className="flex items-center gap-3 md:flex-1 md:justify-start">
              <div className="w-12 h-12 rounded-full bg-blue-100 dark:bg-blue-900/40 flex items-center justify-center text-blue-600 dark:text-blue-400 font-bold text-lg">
                {grupoActual?.nombre?.charAt(0) ?? 'G'}
              </div>
              <div>
                <h3 className="font-semibold text-lg text-gray-800 dark:text-white">
                  {grupoActual?.nombre}
                </h3>
                <p className="text-sm text-gray-500 dark:text-gray-400">
                  {grupoActual?.grado}° - {grupoActual?.turno}
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
                title="Grupo anterior (←)"
              >
                <MdChevronLeft className="text-2xl" />
                <span>Anterior</span>
              </button>

              <span className="px-4 py-2 text-base font-bold text-gray-700 dark:text-gray-200 border-l border-r border-gray-400 dark:border-gray-600 whitespace-nowrap">
                {indiceActual >= 0 ? indiceActual + 1 : 0}{' '}
                <span className="text-gray-400 font-normal">de</span>{' '}
                {gruposFiltrados.length}
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

            <div className="md:flex-1 md:flex md:justify-end">
              <span className="text-sm text-gray-600 dark:text-gray-400 whitespace-nowrap bg-white dark:bg-gray-800 px-4 py-2 rounded-lg border border-gray-400 dark:border-gray-600 shadow-sm">
                Total de clases:{' '}
                <span className="font-bold text-gray-800 dark:text-white text-base">
                  {horarios.length}
                </span>
              </span>
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
                {bloquesFilas.map((bloque) => (
                  <FilaBloqueHorario
                    key={bloque.id}
                    bloque={bloque}
                    horarioIndex={horarioIndex}
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
            No hay horario generado
          </h3>
          <p className="text-gray-500 dark:text-gray-400 mb-4">
            {turnoSeleccionado > 0
              ? `Este grupo del turno ${turnoActual?.nombre} no tiene horario generado aún.`
              : 'Selecciona un grupo y presiona "Generar Horario" para crear su horario automáticamente.'}
          </p>
          {bloquesConfigurados === 0 && grupoSeleccionado > 0 && (
            <div className="inline-flex items-center gap-2 bg-yellow-50 dark:bg-yellow-900/20 text-yellow-700 dark:text-yellow-300 px-4 py-2 rounded-lg border border-yellow-200 dark:border-yellow-800">
              <MdWarning />
              <span className="text-sm">
                Configura la disponibilidad del grupo primero
              </span>
            </div>
          )}
        </div>
      )}
    </div>
  );
};

export default HorarioView;