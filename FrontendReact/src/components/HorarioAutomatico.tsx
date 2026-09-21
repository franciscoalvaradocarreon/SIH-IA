import React, { useState, useEffect, useMemo } from 'react';
import { horarioService } from '../api/horarioService';
import { grupoService } from '../api/grupoService';
import { turnoService } from '../api/turnoService';
import { useAuth } from '../context/AuthContext';
import type {
  Grupo,
  Turno,
  HorarioSolucionMasiva,
  ResultadoValidacion,
  DetalleAsignacion,
  DetalleConflicto,
  AnalisisCuelloBotella,
} from '../types';
import {
  MdSchedule, MdCheckCircle, MdCancel,
  MdWarning, MdInfo, MdSchool, MdClass, MdTimer,
  MdRule, MdRefresh, MdBook, MdPerson, MdMeetingRoom,
  MdTrendingDown, MdVisibility, MdVisibilityOff,
  MdHourglassFull, MdHourglassTop, MdHourglassBottom, MdHourglassEmpty,
} from 'react-icons/md';

interface GrupoResultado {
  grupoId: number;
  grupoNombre: string;
  estado: 'pendiente' | 'ok' | 'sin-asignaciones' | 'sin-disponibilidad' | 'error';
  mensaje?: string;
  clases?: number;
}

type FiltroSeveridad = 'criticos' | 'todos';

/**
 * Reloj de arena animado para indicar que la generación está en curso.
 *
 * Ciclo de 4 fotogramas (lleno → arriba → abajo → vacío) cada 900 ms: se ve cómo la
 * arena se vacía y el reloj se voltea. Se hace alternando iconos de Material Design,
 * así no hace falta agregar keyframes de CSS ni tocar la configuración de Tailwind.
 */
const FOTOGRAMAS_RELOJ_ARENA = [
  MdHourglassFull,
  MdHourglassTop,
  MdHourglassBottom,
  MdHourglassEmpty,
];

const RelojArena: React.FC<{ className?: string }> = ({ className = 'text-xl' }) => {
  const [fotograma, setFotograma] = useState(0);

  useEffect(() => {
    const temporizador = setInterval(
      () => setFotograma(actual => (actual + 1) % FOTOGRAMAS_RELOJ_ARENA.length),
      900
    );
    return () => clearInterval(temporizador);
  }, []);

  const Icono = FOTOGRAMAS_RELOJ_ARENA[fotograma];
  return <Icono className={`${className} animate-pulse`} aria-hidden="true" />;
};

const HorarioAutomatico: React.FC = () => {
  const { semestreActivo } = useAuth();

  const [grupos, setGrupos] = useState<Grupo[]>([]);
  const [turnos, setTurnos] = useState<Turno[]>([]);
  const [turnoSeleccionado, setTurnoSeleccionado] = useState<number>(0);
  const [resultados, setResultados] = useState<GrupoResultado[]>([]);
  const [loading, setLoading] = useState(true);
  const [generando, setGenerando] = useState(false);
  const [progreso, setProgreso] = useState('');
  // Barra de progreso real: segundos transcurridos sobre el límite que publica el backend.
  const [segundosGeneracion, setSegundosGeneracion] = useState(0);
  const [limiteGeneracion, setLimiteGeneracion] = useState(300);
  // Trabajo en curso: lo necesita el botón "Terminar".
  const [trabajoIdActual, setTrabajoIdActual] = useState<string | null>(null);
  const [terminando, setTerminando] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [metricas, setMetricas] = useState<HorarioSolucionMasiva | null>(null);

  const [validando, setValidando] = useState(false);
  const [resultadoValidacion, setResultadoValidacion] = useState<ResultadoValidacion | null>(null);
  const [mostrarValidaciones, setMostrarValidaciones] = useState(false);
  // El panel de validaciones se COLAPSA (conserva su encabezado), no se cierra.
  const [validacionesColapsado, setValidacionesColapsado] = useState(false);

  // 🔥 Cuellos de botella: ya no tienen botón ni panel propios. Son un bloque más de lo que
  // revisa "Validar" (misma consulta, mismo panel y mismo colapso).
  const [analisisCuellos, setAnalisisCuellos] = useState<AnalisisCuelloBotella | null>(null);
  const [filtroSeveridad, setFiltroSeveridad] = useState<FiltroSeveridad>('criticos');

  // Filtro de la tabla de asignaciones

  // Estado por fila para regeneración individual
  const [regenerandoGrupo, setRegenerandoGrupo] = useState<number | null>(null);

  // 🔥 Derivados
  const gruposFiltrados = useMemo(
    () => turnoSeleccionado > 0
      ? grupos.filter(g => g.turnoId === turnoSeleccionado)
      : grupos,
    [grupos, turnoSeleccionado]
  );

  const gruposFiltradosIds = useMemo(
    () => new Set(gruposFiltrados.map(g => g.id)),
    [gruposFiltrados]
  );

  useEffect(() => {
    if (semestreActivo?.id) {
      cargarCatalogos();
    }
  }, [semestreActivo?.id]);

  const cargarCatalogos = async () => {
    setLoading(true);
    try {
      const semestreId = semestreActivo!.id;
      const [turnosRes, gruposRes] = await Promise.all([
        turnoService.listar(0, 100, '', semestreId),
        grupoService.listar(0, 100, '', 0, semestreId),
      ]);

      const turnosActivos = turnosRes.data.content.filter((t: Turno) => t.activo === true);
      setTurnos(turnosActivos);

      const gruposActivos = gruposRes.data.content.filter((g: Grupo) => g.activo);
      setGrupos(gruposActivos);

      reconstruirResultados(gruposActivos, turnoSeleccionado);
      setMetricas(null);
    } catch (error) {
      console.error('Error al cargar catálogos:', error);
      setError('Error al cargar los datos');
    } finally {
      setLoading(false);
    }
  };

  const reconstruirResultados = (gruposBase: Grupo[], turnoId: number) => {
    const filtrados = turnoId > 0
      ? gruposBase.filter(g => g.turnoId === turnoId)
      : gruposBase;

    setResultados(filtrados.map(g => ({
      grupoId: g.id,
      grupoNombre: `${g.nombre} - ${g.grado}° ${g.turno}`,
      estado: 'pendiente',
    })));
  };

  useEffect(() => {
    if (grupos.length > 0 || turnoSeleccionado === 0) {
      reconstruirResultados(grupos, turnoSeleccionado);
      setMetricas(null);
      setResultadoValidacion(null);
      setMostrarValidaciones(false);
      setAnalisisCuellos(null);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [turnoSeleccionado]);

  const handleTurnoChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    setTurnoSeleccionado(Number(e.target.value));
  };

  /**
   * "Validar" revisa las dos cosas de una vez: la factibilidad y los cuellos de botella
   * (grupo × bloque). Antes eran dos botones y dos paneles; ahora es un solo panel con los
   * cuellos como un bloque más.
   */
  const validarAntesDeGenerar = async () => {
    if (turnoSeleccionado === 0) return;

    setValidando(true);
    setError('');
    setSuccess('');
    setAnalisisCuellos(null);
    try {
      const [res, cuellos] = await Promise.all([
        horarioService.validar(semestreActivo!.id, turnoSeleccionado),
        // Si el análisis de cuellos falla, la validación sigue sirviendo: se muestra sin ese
        // bloque en vez de perder el resultado completo.
        horarioService
          .analizarCuellos(semestreActivo!.id, turnoSeleccionado)
          .catch((e: any) => {
            console.error('Error al analizar cuellos de botella:', e);
            return null;
          }),
      ]);

      setResultadoValidacion(res.data);
      setMostrarValidaciones(true);
      setValidacionesColapsado(false);

      if (cuellos) {
        setAnalisisCuellos(cuellos.data);
        // Por defecto mostrar críticos si los hay
        setFiltroSeveridad(cuellos.data.paresCriticos > 0 ? 'criticos' : 'todos');
      }

      if (res.data.totalErrores === 0 && res.data.totalAdvertencias === 0) {
        setSuccess('✅ Todo listo para generar. No se detectaron problemas.');
      } else if (res.data.totalErrores === 0) {
        setSuccess(`⚠️ ${res.data.totalAdvertencias} advertencia(s) detectada(s). Puedes continuar.`);
      } else {
        setError(`❌ ${res.data.totalErrores} error(es) crítico(s). Revisa el detalle abajo.`);
      }
    } catch (err: any) {
      console.error('Error al validar:', err);
      setError(err.response?.data?.message || 'Error al validar la factibilidad');
    } finally {
      setValidando(false);
    }
  };

  const generarTodos = async () => {
    if (turnoSeleccionado === 0) {
      setError('Selecciona un turno primero');
      return;
    }
    if (resultados.length === 0) {
      setError('No hay grupos para generar horarios en la selección actual');
      return;
    }

    if (resultadoValidacion && resultadoValidacion.totalErrores > 0) {
      setError('Hay errores críticos pendientes. Corrígelos antes de generar.');
      return;
    }

    setGenerando(true);
    setError('');
    setSuccess('');
    setSegundosGeneracion(0);
    setProgreso('Enviando la petición...');
    setResultados(prev => prev.map(r => ({ ...r, estado: 'pendiente', mensaje: 'En cola...' })));

    try {
      // 1) Encolar el trabajo: el backend responde 202 de inmediato.
      //    El solver tarda hasta 300 s y antes esta petición se quedaba bloqueada
      //    todo ese tiempo, con riesgo de timeout en navegador o proxy.
      const alta = await horarioService.generarTodos(semestreActivo!.id, turnoSeleccionado);
      const trabajoId = alta.data.id;
      setTrabajoIdActual(trabajoId);
      const inicio = Date.now();
      let data: HorarioSolucionMasiva | null = null;

      // 2) Consultar el estado hasta que el trabajo termine.
      while (true) {
        await new Promise(resolve => setTimeout(resolve, 3000));

        const estadoRes = await horarioService.consultarGeneracion(trabajoId);
        const trabajo = estadoRes.data;
        const segundos = Math.round((Date.now() - inicio) / 1000);
        setSegundosGeneracion(segundos);
        // El límite lo manda el backend (app.solver.masiva.seconds-spent-limit).
        if (trabajo.limiteSegundos) setLimiteGeneracion(trabajo.limiteSegundos);

        if (trabajo.estado === 'COMPLETADO') {
          data = trabajo.resultado ?? null;
          break;
        }
        if (trabajo.estado === 'ERROR') {
          throw new Error(trabajo.error || 'La generación falló en el servidor');
        }
        if (segundos > 1200) {
          throw new Error('La generación superó los 20 minutos sin responder');
        }

        setProgreso(
          `${trabajo.estado === 'EN_COLA' ? 'En cola' : 'Generando horario'} · ${segundos}s`
        );
      }

      if (!data) {
        throw new Error('La generación terminó sin devolver resultado');
      }

      setMetricas(data);

      const totalFallidas = (data.detalleAsignaciones ?? []).filter(d => d.estado !== 'OK').length;

      if (data.factible) {
        setSuccess(
          `✅ Generación completada en ${data.tiempoSegundos}s: ` +
          `${data.totalClasesAsignadas} clases asignadas · ` +
          `${data.gruposConHorario} grupos OK · ` +
          (totalFallidas > 0
            ? `⚠️ ${totalFallidas} asignación(es) con clases sin asignar`
            : `✅ 0 asignaciones fallidas`)
        );
        setError('');
      } else {
        setError(
          `🚫 No se pudo generar un horario factible. Score: ${data.hardScore} hard. ` +
          `Revisa los conflictos abajo y corrige disponibilidades.`
        );
        setSuccess('');
      }
    } catch (err: any) {
      console.error('❌ Error inesperado en generación masiva:', err);
      const msg = err.response?.data?.message || err.message || 'Error desconocido';
      setError(`Error al generar horarios: ${msg}`);
      setResultados(prev => prev.map(r => ({ ...r, estado: 'error', mensaje: `❌ ${msg}` })));
    } finally {
      setGenerando(false);
      setProgreso('');
      setTrabajoIdActual(null);
      setTerminando(false);
    }
  };

  /**
   * Corta la generación en curso.
   *
   * El backend deja de lanzar intentos y guarda la MEJOR solución encontrada hasta ese momento, así
   * que no se pierde nada: es la forma de quedarse con un resultado que ya sirve sin esperar a que
   * se agoten los intentos. Después el propio bucle de consulta ve el trabajo COMPLETADO y carga el
   * resultado como si hubiera terminado solo.
   */
  const terminarGeneracionActual = async () => {
    if (!trabajoIdActual) return;
    setTerminando(true);
    try {
      await horarioService.terminarGeneracion(trabajoIdActual);
      setProgreso('Terminando: se guardará la mejor solución encontrada...');
    } catch (err: any) {
      console.error('Error al terminar la generación:', err);
      setError(err.response?.data?.message || 'No se pudo terminar la generación');
      setTerminando(false);
    }
  };

  const regenerarGrupo = async (grupoId: number) => {
    setRegenerandoGrupo(grupoId);
    setError('');
    setSuccess('');

    try {
      await horarioService.generar(grupoId, semestreActivo!.id);

      // Refresca SOLO el avance del grupo regenerado, sin tocar el resto de la lista.
      //
      // Antes se llamaba a recargarMetricas(), que reconstruía el detalle con
      // construirDetalleDesdeHorarios()... y esa función devolvía [] (era un stub con `void`), así
      // que la tabla de asignaciones pendientes se vaciaba al terminar la primera regeneración: no
      // se podían regenerar los demás grupos sin volver a generar TODO.
      const res = await horarioService.obtenerTodos(semestreActivo!.id);
      const asignadasPorAsignacion = new Map<number, number>();
      for (const h of res.data) {
        if (!h.grupoId || !h.asignacionId) continue;
        if (!gruposFiltradosIds.has(h.grupoId)) continue;
        asignadasPorAsignacion.set(
          h.asignacionId,
          (asignadasPorAsignacion.get(h.asignacionId) ?? 0) + 1
        );
      }

      setMetricas((prev) => {
        if (!prev?.detalleAsignaciones) return prev;
        return {
          ...prev,
          detalleAsignaciones: prev.detalleAsignaciones.map((d) => {
            if (d.grupoId !== grupoId) return d;
            const asignadas = asignadasPorAsignacion.get(d.asignacionId) ?? 0;
            const esperadas = d.horasEsperadas;
            const estado: DetalleAsignacion['estado'] =
              asignadas === 0 ? 'SIN_ASIGNAR' : asignadas >= esperadas ? 'OK' : 'PARCIAL';
            const motivo =
              estado === 'OK'
                ? ''
                : estado === 'SIN_ASIGNAR'
                  ? 'No se pudo asignar ninguna hora'
                  : `Solo se asignaron ${asignadas} de ${esperadas} horas`;
            return {
              ...d,
              clasesAsignadas: asignadas,
              clasesSinAsignar: Math.max(0, esperadas - asignadas),
              estado,
              motivo,
            };
          }),
        };
      });

      setSuccess(`✅ Grupo regenerado correctamente.`);
      setTimeout(() => setSuccess(''), 4000);
    } catch (err: any) {
      console.error('Error al regenerar grupo:', err);
      setError(
        err.response?.data?.message ||
        'Error al regenerar el horario del grupo. Revisa la disponibilidad del maestro y del grupo.'
      );
    } finally {
      setRegenerandoGrupo(null);
    }
  };


  const getIconoEstado = (estado: string) => {
    switch (estado) {
      case 'pendiente': return <MdInfo className="text-gray-400" />;
      case 'ok': return <MdCheckCircle className="text-green-500 text-xl" />;
      case 'sin-asignaciones': return <MdWarning className="text-yellow-500 text-xl" />;
      case 'sin-disponibilidad': return <MdWarning className="text-orange-500 text-xl" />;
      case 'error': return <MdCancel className="text-red-500 text-xl" />;
      default: return null;
    }
  };

  // Solo interesan las asignaciones que NO quedaron completas: las OK no aportan nada al revisar
  // una generación, y son la mayoría de la tabla.
  const asignacionesFiltradas = useMemo(
    () => (metricas?.detalleAsignaciones ?? []).filter(d => d.estado !== 'OK'),
    [metricas]
  );

  // 🔥 Pares filtrados por severidad
  const paresCuellosFiltrados = useMemo(() => {
    if (!analisisCuellos?.pares) return [];
    if (filtroSeveridad === 'criticos') {
      return analisisCuellos.pares.filter(p => p.severidad === 'CRITICO');
    }
    // "todos" muestra críticos + advertencias
    return analisisCuellos.pares.filter(p => p.severidad !== 'OK');
  }, [analisisCuellos, filtroSeveridad]);

  const totalAsignaciones = metricas?.detalleAsignaciones?.length ?? 0;
  const totalFallidas = metricas?.detalleAsignaciones?.filter(
    d => d.estado !== 'OK'
  ).length ?? 0;

  if (!semestreActivo) {
    return (
      <div className="p-6 text-center text-yellow-600 dark:text-yellow-400">
        <p className="text-lg font-semibold">⚠️ No hay semestre activo</p>
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

  const turnoActual = turnos.find(t => t.id === turnoSeleccionado);

  return (
    <div className="p-6 max-w-7xl mx-auto">
      {/* Encabezado + botones */}
      <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4 mb-6">
        <div>
          <h1 className="text-3xl font-bold text-gray-800 dark:text-white">
            📋 Generación de Horarios
          </h1>
          <p className="text-sm text-gray-500 dark:text-gray-400 mt-1">
            Genera horarios para todos los grupos activos
            {turnoActual && (
              <span className="ml-2 text-indigo-600 dark:text-indigo-400 font-medium">
                del turno {turnoActual.nombre}
              </span>
            )}
            <span className="ml-2 text-blue-600 dark:text-blue-400 font-medium">
              (Semestre: {semestreActivo.nombre})
            </span>
          </p>
        </div>
        <div className="flex gap-3 flex-wrap">
          <button
            onClick={validarAntesDeGenerar}
            disabled={validando || generando || turnoSeleccionado === 0}
            title={
              turnoSeleccionado === 0
                ? 'Selecciona un turno primero'
                : 'Validar factibilidad y analizar cuellos de botella (grupo × bloque)'
            }
            className="flex items-center gap-2 bg-yellow-500 hover:bg-yellow-600 text-white px-4 py-2.5 rounded-lg shadow-md transition disabled:opacity-50 disabled:cursor-not-allowed"
          >
            <MdCheckCircle className="text-xl" />
            {validando ? 'Validando...' : 'Validar'}
          </button>
          <button
            onClick={generarTodos}
            disabled={
              generando ||
              turnoSeleccionado === 0 ||
              (resultadoValidacion?.totalErrores ?? 0) > 0
            }
            title={
              turnoSeleccionado === 0
                ? 'Selecciona un turno primero'
                : (resultadoValidacion?.totalErrores ?? 0) > 0
                ? 'Corrige los errores de validación primero'
                : 'Generar horarios para los grupos del turno seleccionado'
            }
            className="flex items-center gap-2 bg-blue-600 hover:bg-blue-700 text-white px-6 py-2.5 rounded-lg shadow-md transition disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {generando ? <RelojArena className="text-xl" /> : <MdSchedule className="text-xl" />}
            {generando ? 'Generando...' : 'Generar Todos'}
          </button>
        </div>
      </div>

      {/* Selector de turno */}
      <div className="bg-white dark:bg-gray-800 rounded-xl shadow-md p-4 mb-6 border border-gray-400 dark:border-gray-700">
        <div className="flex flex-col sm:flex-row items-start sm:items-center gap-3">
          <label className="text-sm font-medium text-gray-700 dark:text-gray-300 whitespace-nowrap">
            <MdSchedule className="inline mr-1" />
            Turno a generar:
          </label>
          <select
            value={turnoSeleccionado}
            onChange={handleTurnoChange}
            disabled={generando}
            className="w-full sm:max-w-md px-4 py-2.5 border border-gray-400 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:opacity-50"
          >
            <option value={0}>Selecciona un turno...</option>
            {turnos.map((t) => {
              const cuenta = grupos.filter(g => g.turnoId === t.id).length;
              return (
                <option key={t.id} value={t.id}>
                  {t.nombre} ({cuenta} grupos)
                </option>
              );
            })}
          </select>
          {turnoSeleccionado > 0 && (
            <span className="text-sm text-gray-500 dark:text-gray-400">
              {resultados.length} grupos se incluirán
            </span>
          )}
        </div>
        {turnos.length === 0 && (
          <p className="text-xs text-yellow-600 dark:text-yellow-400 mt-2">
            No hay turnos activos en este semestre. Activa uno antes de generar horarios.
          </p>
        )}
      </div>

      {/* Métricas */}
      {metricas && !generando && (
        <div className="bg-gradient-to-r from-blue-50 to-indigo-50 dark:from-blue-900/20 dark:to-indigo-900/20 rounded-xl shadow-md p-6 mb-6 border border-blue-200 dark:border-blue-800">
          <h3 className="text-lg font-bold text-gray-800 dark:text-white mb-4 flex items-center gap-2">
            <MdTimer className="text-2xl text-blue-600 dark:text-blue-400" />
            Métricas de la generación
          </h3>
          <div className="grid grid-cols-2 md:grid-cols-6 gap-4">
            <div>
              <p className="text-xs text-gray-600 dark:text-gray-400">Hard Score</p>
              <p className={`text-xl font-bold ${metricas.hardScore >= 0 ? 'text-green-600 dark:text-green-400' : 'text-red-600 dark:text-red-400'}`}>
                {metricas.hardScore}
              </p>
            </div>
            <div>
              <p className="text-xs text-gray-600 dark:text-gray-400">Medium</p>
              <p className="text-xl font-bold text-purple-600 dark:text-purple-400">{metricas.mediumScore}</p>
            </div>
            <div>
              <p className="text-xs text-gray-600 dark:text-gray-400">Soft</p>
              <p className="text-xl font-bold text-blue-600 dark:text-blue-400">{metricas.softScore}</p>
            </div>
            <div>
              <p className="text-xs text-gray-600 dark:text-gray-400">Clases</p>
              <p className="text-xl font-bold text-gray-800 dark:text-white">{metricas.totalClasesAsignadas}</p>
            </div>
            <div>
              <p className="text-xs text-gray-600 dark:text-gray-400">Tiempo</p>
              <p className="text-xl font-bold text-gray-800 dark:text-white">{metricas.tiempoSegundos}s</p>
            </div>
            <div>
              <p className="text-xs text-gray-600 dark:text-gray-400">Fallidas</p>
              <p className={`text-xl font-bold ${totalFallidas > 0 ? 'text-red-600 dark:text-red-400' : 'text-green-600 dark:text-green-400'}`}>
                {totalFallidas}
              </p>
            </div>
          </div>
        </div>
      )}

      {/* Barra de progreso */}
      {generando && (
        <div className="bg-white dark:bg-gray-800 rounded-xl shadow-md p-6 mb-6 border border-blue-200 dark:border-blue-800">
          <div className="flex items-center gap-4">
            <RelojArena className="text-5xl text-blue-600 dark:text-blue-400 shrink-0" />
            <div className="flex-1 min-w-0">
              <p className="text-base font-semibold text-gray-800 dark:text-white">
                Generando horarios
                {turnoActual && (
                  <span className="text-blue-600 dark:text-blue-400"> · turno {turnoActual.nombre}</span>
                )}
                <span className="ml-2 text-sm font-normal text-gray-500 dark:text-gray-400">
                  {resultados.length} grupo(s)
                </span>
              </p>
              <p className="text-sm text-gray-600 dark:text-gray-300 mt-1">
                {progreso || 'Enviando la petición al servidor...'}
              </p>
              <div className="w-full bg-gray-200 dark:bg-gray-700 rounded-full h-2.5 overflow-hidden mt-3">
                <div
                  className="bg-blue-600 h-2.5 rounded-full transition-all duration-700"
                  style={{
                    width: `${Math.min(100, Math.max(3, (segundosGeneracion * 100) / (limiteGeneracion || 300)))}%`,
                  }}
                />
              </div>
              <div className="flex justify-end mt-3">
                <button
                  onClick={terminarGeneracionActual}
                  disabled={terminando || !trabajoIdActual}
                  title="Deja de buscar y guarda la mejor solución encontrada hasta ahora"
                  className="bg-gray-600 hover:bg-gray-700 text-white text-sm font-medium px-3 py-1.5 rounded-lg transition disabled:opacity-50 disabled:cursor-not-allowed"
                >
                  {terminando ? 'Terminando...' : 'Terminar y quedarme con la mejor'}
                </button>
              </div>

            </div>
          </div>
        </div>
      )}

      {error && (
        <div className="bg-red-50 dark:bg-red-900/30 text-red-700 dark:text-red-300 p-4 rounded-lg border border-red-200 dark:border-red-800 mb-6 whitespace-pre-line">
          {error}
        </div>
      )}

      {success && (
        <div className="bg-green-50 dark:bg-green-900/30 text-green-700 dark:text-green-300 p-4 rounded-lg border border-green-200 dark:border-green-800 mb-6">
          {success}
        </div>
      )}


      {/* Panel de validaciones */}
      {mostrarValidaciones && resultadoValidacion && (
        <div className={`bg-white dark:bg-gray-800 rounded-xl shadow-md p-6 mb-6 border border-gray-400 dark:border-gray-700 ${
          validacionesColapsado ? '[&>*:not(:first-child)]:hidden [&>*:first-child]:mb-0' : ''
        }`}>
          <div className="flex items-center justify-between mb-4 flex-wrap gap-3">
            <h3 className="font-semibold text-gray-800 dark:text-white flex items-center gap-2">
              <MdRule className="text-2xl text-blue-600 dark:text-blue-400" />
              Validaciones previas ({resultadoValidacion.validaciones.length})
            </h3>
            <div className="flex items-center gap-2">
              {resultadoValidacion.totalErrores > 0 && (
                <span className="text-xs font-medium text-red-600 dark:text-red-400 bg-red-50 dark:bg-red-900/20 px-3 py-1 rounded-full">
                  {resultadoValidacion.totalErrores} error(es)
                </span>
              )}
              {resultadoValidacion.totalAdvertencias > 0 && (
                <span className="text-xs font-medium text-yellow-600 dark:text-yellow-400 bg-yellow-50 dark:bg-yellow-900/20 px-3 py-1 rounded-full">
                  {resultadoValidacion.totalAdvertencias} advertencia(s)
                </span>
              )}
              {resultadoValidacion.totalErrores === 0 && resultadoValidacion.totalAdvertencias === 0 && (
                <span className="text-xs font-medium text-green-600 dark:text-green-400 bg-green-50 dark:bg-green-900/20 px-3 py-1 rounded-full">
                  Todo OK
                </span>
              )}
              <button
                onClick={() => setValidacionesColapsado((v) => !v)}
                className="rounded border border-blue-300 px-2.5 py-1 text-xs font-medium text-blue-700 hover:bg-blue-50 dark:border-blue-700 dark:text-blue-300 dark:hover:bg-blue-900/30"
                title={validacionesColapsado ? 'Mostrar el detalle' : 'Ocultar el detalle sin cerrar el panel'}
              >
                {validacionesColapsado ? 'Expandir' : 'Colapsar'}
              </button>
            </div>
          </div>

          <ul className="space-y-2">
            {resultadoValidacion.validaciones.map((v) => {
              const estilo = {
                OK: {
                  icono: <MdCheckCircle className="text-xl text-green-500" />,
                  bg: 'bg-green-50 dark:bg-green-900/10 border-green-200 dark:border-green-800',
                  titulo: 'text-green-800 dark:text-green-200',
                  mensaje: 'text-green-700 dark:text-green-300',
                },
                ADVERTENCIA: {
                  icono: <MdWarning className="text-xl text-yellow-500" />,
                  bg: 'bg-yellow-50 dark:bg-yellow-900/10 border-yellow-200 dark:border-yellow-800',
                  titulo: 'text-yellow-800 dark:text-yellow-200',
                  mensaje: 'text-yellow-700 dark:text-yellow-300',
                },
                ERROR: {
                  icono: <MdCancel className="text-xl text-red-500" />,
                  bg: 'bg-red-50 dark:bg-red-900/10 border-red-200 dark:border-red-800',
                  titulo: 'text-red-800 dark:text-red-200',
                  mensaje: 'text-red-700 dark:text-red-300',
                },
              }[v.estado];

              return (
                <li key={v.codigo} className={`border rounded-lg p-3 ${estilo.bg}`}>
                  <div className="flex items-start gap-3">
                    <div className="flex-shrink-0 mt-0.5">{estilo.icono}</div>
                    <div className="flex-1 min-w-0">
                      <div className={`font-medium text-sm ${estilo.titulo}`}>{v.titulo}</div>
                      <div className={`text-xs mt-1 whitespace-pre-line ${estilo.mensaje}`}>
                        {v.mensaje}
                      </div>
                    </div>
                  </div>
                </li>
              );
            })}
          </ul>

          {resultadoValidacion.totalErrores > 0 && (
            <div className="mt-4 p-3 bg-red-50 dark:bg-red-900/20 rounded-lg text-xs text-red-700 dark:text-red-300 border border-red-200 dark:border-red-800">
              ⚠️ Los errores deben resolverse antes de generar horarios.
            </div>
          )}

          {/* 🔥 Cuellos de botella: un bloque más de lo que revisa "Validar" */}
          {analisisCuellos && (
            <div className="mt-6 pt-5 border-t border-gray-300 dark:border-gray-600">
              <h3 className="font-semibold text-gray-800 dark:text-white flex items-center gap-2 mb-3">
                <MdTrendingDown className="text-2xl text-purple-600 dark:text-purple-400" />
                Cuellos de botella · Grupos × Bloques
              </h3>

              <p className="text-base text-gray-500 dark:text-gray-400 mb-4">
                Muestra cuántos maestros del grupo pueden dar clase en cada bloque.
                Bloques con 0-1 opciones son <strong>críticos</strong>:
                si varios grupos compiten por el mismo maestro, habrá conflicto seguro.
              </p>

              {/* Resumen */}
              <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mb-4">
                <div className={`rounded-lg p-3 border ${
                  analisisCuellos.paresCriticos > 0
                    ? 'bg-red-50 dark:bg-red-900/20 border-red-200 dark:border-red-800'
                    : 'bg-gray-50 dark:bg-gray-700 border-gray-400 dark:border-gray-600'
                }`}>
                  <div className="text-xs text-red-700 dark:text-red-300">🔴 Críticos (0-1)</div>
                  <div className="text-2xl font-bold text-red-800 dark:text-red-200">
                    {analisisCuellos.paresCriticos}
                  </div>
                </div>
                <div className={`rounded-lg p-3 border ${
                  analisisCuellos.paresAdvertencia > 0
                    ? 'bg-yellow-50 dark:bg-yellow-900/20 border-yellow-200 dark:border-yellow-800'
                    : 'bg-gray-50 dark:bg-gray-700 border-gray-400 dark:border-gray-600'
                }`}>
                  <div className="text-xs text-yellow-700 dark:text-yellow-300">🟡 Advertencia (2)</div>
                  <div className="text-2xl font-bold text-yellow-800 dark:text-yellow-200">
                    {analisisCuellos.paresAdvertencia}
                  </div>
                </div>
                <div className="bg-green-50 dark:bg-green-900/20 rounded-lg p-3 border border-green-200 dark:border-green-800">
                  <div className="text-xs text-green-700 dark:text-green-300">🟢 OK (3+)</div>
                  <div className="text-2xl font-bold text-green-800 dark:text-green-200">
                    {analisisCuellos.paresOk}
                  </div>
                </div>
                <div className="bg-gray-50 dark:bg-gray-700 rounded-lg p-3 border border-gray-400 dark:border-gray-600">
                  <div className="text-xs text-gray-700 dark:text-gray-300">Total pares</div>
                  <div className="text-2xl font-bold text-gray-800 dark:text-white">
                    {analisisCuellos.totalParesGrupoBloque}
                  </div>
                </div>
              </div>

              {/* Filtro de severidad */}
              <div className="flex items-center gap-2 mb-4">
                <button
                  onClick={() => setFiltroSeveridad('criticos')}
                  className={`text-xs px-3 py-1 rounded-full font-medium transition ${
                    filtroSeveridad === 'criticos'
                      ? 'bg-red-600 text-white'
                      : 'bg-gray-200 dark:bg-gray-700 text-gray-700 dark:text-gray-300 hover:bg-gray-300 dark:hover:bg-gray-600'
                  }`}
                >
                  Solo críticos ({analisisCuellos.paresCriticos})
                </button>
                <button
                  onClick={() => setFiltroSeveridad('todos')}
                  className={`text-xs px-3 py-1 rounded-full font-medium transition ${
                    filtroSeveridad === 'todos'
                      ? 'bg-purple-600 text-white'
                      : 'bg-gray-200 dark:bg-gray-700 text-gray-700 dark:text-gray-300 hover:bg-gray-300 dark:hover:bg-gray-600'
                  }`}
                >
                  Críticos + advertencias ({analisisCuellos.paresCriticos + analisisCuellos.paresAdvertencia})
                </button>
              </div>

              {/* Tabla de cuellos */}
              {paresCuellosFiltrados.length === 0 ? (
                <div className="bg-green-50 dark:bg-green-900/20 rounded-lg p-6 text-center border border-green-200 dark:border-green-800">
                  <MdCheckCircle className="text-4xl mx-auto mb-2 text-green-500" />
                  <p className="text-sm font-medium text-green-700 dark:text-green-300">
                    🎉 No hay cuellos de botella en esta vista
                  </p>
                  <p className="text-xs text-green-600 dark:text-green-400 mt-1">
                    {filtroSeveridad === 'criticos'
                      ? 'Ningún (grupo, bloque) tiene 0 o 1 maestros disponibles.'
                      : 'Todos los bloques tienen 3+ maestros disponibles.'}
                  </p>
                </div>
              ) : (
                <div className="overflow-x-auto max-h-[500px] overflow-y-auto border border-gray-400 dark:border-gray-700 rounded-lg">
                  <table className="min-w-full divide-y divide-gray-400 dark:divide-gray-700">
                    <thead className="bg-gray-50 dark:bg-gray-700/50 sticky top-0">
                      <tr>
                        <th className="px-3 py-2 text-left text-xs font-medium text-gray-500 dark:text-gray-400 uppercase">
                          Severidad
                        </th>
                        <th className="px-3 py-2 text-left text-xs font-medium text-gray-500 dark:text-gray-400 uppercase">
                          Grupo
                        </th>
                        <th className="px-3 py-2 text-left text-xs font-medium text-gray-500 dark:text-gray-400 uppercase">
                          Día
                        </th>
                        <th className="px-3 py-2 text-left text-xs font-medium text-gray-500 dark:text-gray-400 uppercase">
                          Hora
                        </th>
                        <th className="px-3 py-2 text-center text-xs font-medium text-gray-500 dark:text-gray-400 uppercase">
                          Maestros
                        </th>
                        <th className="px-3 py-2 text-left text-xs font-medium text-gray-500 dark:text-gray-400 uppercase">
                          Disponibles
                        </th>
                      </tr>
                    </thead>
                    <tbody className="bg-white dark:bg-gray-800 divide-y divide-gray-400 dark:divide-gray-700">
                      {paresCuellosFiltrados.map((p, i) => (
                        <tr
                          key={`${p.grupoId}-${p.turnoHorarioId}-${i}`}
                          className={
                            p.severidad === 'CRITICO'
                              ? 'bg-red-50/50 dark:bg-red-900/10'
                              : 'bg-yellow-50/50 dark:bg-yellow-900/10'
                          }
                        >
                          <td className="px-3 py-2 whitespace-nowrap">
                            <span
                              className={`text-xs font-bold px-2 py-0.5 rounded-full ${
                                p.severidad === 'CRITICO'
                                  ? 'bg-red-100 text-red-700 dark:bg-red-900/40 dark:text-red-300'
                                  : 'bg-yellow-100 text-yellow-700 dark:bg-yellow-900/40 dark:text-yellow-300'
                              }`}
                            >
                              {p.severidad}
                            </span>
                          </td>
                          <td className="px-3 py-2 whitespace-nowrap">
                            <span className="text-sm font-medium text-gray-800 dark:text-white">
                              {p.grupoNombre} · {p.grupoGrado}°
                            </span>
                          </td>
                          <td className="px-3 py-2 whitespace-nowrap text-sm text-gray-700 dark:text-gray-300">
                            {p.diaNombre}
                          </td>
                          <td className="px-3 py-2 whitespace-nowrap text-sm font-mono text-gray-700 dark:text-gray-300">
                            {p.horaInicio.substring(0, 5)} - {p.horaFin.substring(0, 5)}
                          </td>
                          <td className="px-3 py-2 whitespace-nowrap text-center">
                            <span
                              className={`font-bold ${
                                p.maestrosDisponibles <= 1
                                  ? 'text-red-600 dark:text-red-400'
                                  : 'text-yellow-600 dark:text-yellow-400'
                              }`}
                            >
                              {p.maestrosDisponibles}
                            </span>
                          </td>
                          <td className="px-3 py-2 text-xs text-gray-500 dark:text-gray-400 max-w-md">
                            {p.maestrosNombres.join(', ') || '—'}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          )}
        </div>
      )}

      {/* Panel de infactibilidad + conflictos detallados */}
      {metricas && metricas.factible === false && (
        <div className="bg-red-50 dark:bg-red-900/20 border-2 border-red-500 dark:border-red-700 rounded-xl p-6 mb-6">
          <div className="flex items-start gap-3 mb-4">
            <MdCancel className="text-3xl text-red-600 dark:text-red-400 flex-shrink-0 mt-0.5" />
            <div>
              <h3 className="font-bold text-red-800 dark:text-red-200 text-lg">
                🚫 Horario infactible — {metricas.conflictosDetectados?.length ?? 0} conflicto(s) detectado(s)
              </h3>
              <p className="text-sm text-red-700 dark:text-red-300 mt-1">
                El solver no pudo encontrar una solución sin violar las reglas duras.
                Revisa cada conflicto y corrige la disponibilidad o las asignaciones.
              </p>
            </div>
          </div>

          {metricas.violacionesHard && metricas.violacionesHard.length > 0 && (
            <div className="mb-4 bg-white dark:bg-gray-800 border border-red-300 dark:border-red-800 rounded-lg p-3">
              <p className="text-xs font-semibold text-red-800 dark:text-red-200 uppercase mb-2">
                Resumen por constraint
              </p>
              <ul className="space-y-1">
                {metricas.violacionesHard.map((v, i) => (
                  <li key={i} className="text-xs text-red-700 dark:text-red-300">
                    <span className="font-mono">{v.constraint}</span>
                    <span className="mx-2">→</span>
                    <span className="font-mono">{v.detalle}</span>
                  </li>
                ))}
              </ul>
            </div>
          )}

          {metricas.conflictosDetectados && metricas.conflictosDetectados.length > 0 && (
            <div className="space-y-2">
              <p className="text-xs font-semibold text-red-800 dark:text-red-200 uppercase">
                Conflictos específicos
              </p>
              {metricas.conflictosDetectados.map((c: DetalleConflicto, i: number) => (
                <div
                  key={i}
                  className="bg-white dark:bg-gray-800 border-l-4 border-red-500 rounded-lg p-3"
                >
                  <div className="flex items-start justify-between gap-2 mb-2 flex-wrap">
                    <div className="flex items-center gap-2 flex-wrap">
                      <span className={`text-xs font-bold px-2 py-0.5 rounded-full uppercase ${
                        c.tipo === 'GRUPO' ? 'bg-blue-100 text-blue-700 dark:bg-blue-900/40 dark:text-blue-300'
                        : c.tipo === 'MAESTRO' ? 'bg-purple-100 text-purple-700 dark:bg-purple-900/40 dark:text-purple-300'
                        : c.tipo === 'AULA' ? 'bg-orange-100 text-orange-700 dark:bg-orange-900/40 dark:text-orange-300'
                        : 'bg-red-100 text-red-700 dark:bg-red-900/40 dark:text-red-300'
                      }`}>
                        {c.tipo}
                      </span>
                      <span className="text-sm font-semibold text-gray-800 dark:text-white">
                        {c.titulo}
                      </span>
                    </div>
                    <span className="text-xs font-mono text-gray-500 dark:text-gray-400 whitespace-nowrap">
                      {c.bloqueTexto}
                    </span>
                  </div>

                  <div className="grid grid-cols-1 md:grid-cols-2 gap-2 text-xs text-gray-600 dark:text-gray-400 mb-2">
                    {c.grupoNombre && (
                      <div>
                        <span className="font-semibold text-gray-700 dark:text-gray-300">Grupo:</span> {c.grupoNombre}
                      </div>
                    )}
                    {c.maestroNombre && (
                      <div>
                        <span className="font-semibold text-gray-700 dark:text-gray-300">Maestro:</span> {c.maestroNombre}
                      </div>
                    )}
                    {c.aulaNombre && (
                      <div>
                        <span className="font-semibold text-gray-700 dark:text-gray-300">Aula:</span> {c.aulaNombre}
                      </div>
                    )}
                  </div>

                  {c.materias && c.materias.length > 0 && (
                    <div className="mb-2">
                      <span className="text-xs font-semibold text-gray-700 dark:text-gray-300">
                        Materias en conflicto:
                      </span>
                      <ul className="list-disc list-inside text-xs text-red-700 dark:text-red-300 mt-1">
                        {c.materias.map((m, j) => (
                          <li key={j} className="font-mono">{m}</li>
                        ))}
                      </ul>
                    </div>
                  )}

                  {c.sugerencia && (
                    <div className="text-xs italic text-gray-500 dark:text-gray-400 border-t border-gray-400 dark:border-gray-700 pt-2 mt-2">
                      💡 {c.sugerencia}
                    </div>
                  )}
                </div>
              ))}
            </div>
          )}

          {(!metricas.conflictosDetectados || metricas.conflictosDetectados.length === 0) && (
            <div className="mt-2 p-3 bg-white dark:bg-gray-800 rounded-lg text-xs text-gray-600 dark:text-gray-400 border border-gray-400 dark:border-gray-700">
              ℹ️ No se pudieron extraer los conflictos específicos. Revisa el log del backend para más detalle.
            </div>
          )}
        </div>
      )}

      {/* Tabla de asignaciones */}
      <div className="bg-white dark:bg-gray-800 rounded-xl shadow-md overflow-hidden border border-gray-400 dark:border-gray-700">
        <div className="px-4 py-3 bg-gray-50 dark:bg-gray-700/50 border-b border-gray-400 dark:border-gray-700 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3">
          <div className="flex items-center gap-2">
            <MdClass className="text-xl text-blue-600 dark:text-blue-400" />
            <h3 className="font-semibold text-gray-800 dark:text-white">
              Asignaciones a colocar
              {turnoActual && (
                <span className="ml-2 text-sm font-normal text-indigo-600 dark:text-indigo-400">
                  (Turno: {turnoActual.nombre})
                </span>
              )}
            </h3>
          </div>
          {metricas && (
            <p className="text-xs text-gray-500 dark:text-gray-400">
              Solo se listan las que no quedaron completas:{' '}
              <b className={totalFallidas > 0 ? 'text-red-600 dark:text-red-400' : 'text-green-600 dark:text-green-400'}>
                {totalFallidas}
              </b>{' '}
              de {totalAsignaciones}
            </p>
          )}
        </div>

        <div className="overflow-x-auto">
          <table className="min-w-full divide-y divide-gray-400 dark:divide-gray-700">
            <thead className="bg-gray-50 dark:bg-gray-700/50">
              <tr>
                <th className="px-3 py-2 text-left text-xs font-medium text-gray-500 dark:text-gray-400 uppercase">Grupo</th>
                <th className="px-3 py-2 text-left text-xs font-medium text-gray-500 dark:text-gray-400 uppercase">Especialidad</th>
                <th className="px-3 py-2 text-left text-xs font-medium text-gray-500 dark:text-gray-400 uppercase">Materia</th>
                <th className="px-3 py-2 text-left text-xs font-medium text-gray-500 dark:text-gray-400 uppercase">Maestro</th>
                <th className="px-3 py-2 text-left text-xs font-medium text-gray-500 dark:text-gray-400 uppercase">Aula</th>
                <th className="px-3 py-2 text-center text-xs font-medium text-gray-500 dark:text-gray-400 uppercase">Hrs. esperadas</th>
                <th className="px-3 py-2 text-center text-xs font-medium text-gray-500 dark:text-gray-400 uppercase">Asignadas</th>
                <th className="px-3 py-2 text-center text-xs font-medium text-gray-500 dark:text-gray-400 uppercase">Estado</th>
                <th className="px-3 py-2 text-left text-xs font-medium text-gray-500 dark:text-gray-400 uppercase">Motivo</th>
                <th className="px-3 py-2 text-center text-xs font-medium text-gray-500 dark:text-gray-400 uppercase">Acciones</th>
              </tr>
            </thead>
            <tbody className="bg-white dark:bg-gray-800 divide-y divide-gray-400 dark:divide-gray-700">
              {!metricas ? (
                <tr>
                  <td colSpan={10} className="px-4 py-12 text-center text-gray-500 dark:text-gray-400">
                    <MdSchedule className="text-4xl mx-auto mb-2 text-gray-300 dark:text-gray-600" />
                    <p>Presiona "Generar Todos" para ver el detalle de asignaciones</p>
                  </td>
                </tr>
              ) : asignacionesFiltradas.length === 0 ? (
                <tr>
                  <td colSpan={10} className="px-4 py-12 text-center text-gray-500 dark:text-gray-400">
                    <MdCheckCircle className="text-4xl mx-auto mb-2 text-green-500" />
                    <p className="font-medium text-green-600 dark:text-green-400">
                      🎉 Todas las asignaciones se colocaron completas
                    </p>
                  </td>
                </tr>
              ) : (
                asignacionesFiltradas.map((a) => {
                  const esOk = a.estado === 'OK';
                  const esParcial = a.estado === 'PARCIAL';

                  return (
                    <tr
                      key={a.asignacionId}
                      className={`hover:bg-gray-50 dark:hover:bg-gray-700/50 transition-colors ${
                        !esOk ? 'bg-red-50/40 dark:bg-red-900/10' : ''
                      }`}
                    >
                      <td className="px-3 py-2 whitespace-nowrap">
                        <div className="flex items-center gap-2">
                          <div className="w-7 h-7 rounded-full bg-blue-100 dark:bg-blue-900/40 flex items-center justify-center text-blue-600 dark:text-blue-400 text-xs font-bold">
                            {a.grupoNombre?.charAt(0) || 'G'}
                          </div>
                          <div>
                            <div className="text-sm font-medium text-gray-900 dark:text-white">
                              {a.grupoNombre}
                            </div>
                            <div className="text-xs text-gray-500 dark:text-gray-400">
                              {a.grupoGrado}°
                            </div>
                          </div>
                        </div>
                      </td>
                      <td className="px-3 py-2 whitespace-nowrap">
                        {a.especialidadNombre ? (
                          <span className="text-sm text-gray-700 dark:text-gray-300">
                            {a.especialidadNombre}
                          </span>
                        ) : (
                          <span className="text-xs text-gray-400 italic">-</span>
                        )}
                      </td>
                      <td className="px-3 py-2 whitespace-nowrap">
                        <div>
                          <div className="text-sm font-medium text-gray-800 dark:text-white font-mono">
                            {a.materiaClave}
                          </div>
                          <div className="text-xs text-gray-500 dark:text-gray-400 truncate max-w-[200px]">
                            {a.materiaNombre}
                          </div>
                        </div>
                      </td>
                      <td className="px-3 py-2 whitespace-nowrap">
                        <div className="flex items-center gap-1.5">
                          <MdPerson className="text-gray-400 text-sm" />
                          <span className="text-sm text-gray-700 dark:text-gray-300 truncate max-w-[180px]">
                            {a.maestroNombre}
                          </span>
                        </div>
                      </td>
                      <td className="px-3 py-2 whitespace-nowrap">
                        <div className="flex items-center gap-1.5">
                          <MdMeetingRoom className="text-gray-400 text-sm" />
                          <span className="text-sm text-gray-700 dark:text-gray-300">
                            {a.aulaNombre}
                          </span>
                        </div>
                      </td>
                      <td className="px-3 py-2 whitespace-nowrap text-center">
                        <span className="inline-flex items-center px-2 py-0.5 rounded-full text-sm font-medium bg-gray-100 dark:bg-gray-700 text-gray-700 dark:text-gray-300">
                          {a.horasEsperadas}
                        </span>
                      </td>
                      <td className="px-3 py-2 whitespace-nowrap text-center">
                        <span
                          className={`inline-flex items-center px-2 py-0.5 rounded-full text-sm font-bold ${
                            a.clasesAsignadas === a.horasEsperadas
                              ? 'bg-green-100 dark:bg-green-900/30 text-green-700 dark:text-green-300'
                              : a.clasesAsignadas > 0
                              ? 'bg-yellow-100 dark:bg-yellow-900/30 text-yellow-700 dark:text-yellow-300'
                              : 'bg-red-100 dark:bg-red-900/30 text-red-700 dark:text-red-300'
                          }`}
                        >
                          {a.clasesAsignadas}
                        </span>
                      </td>
                      <td className="px-3 py-2 whitespace-nowrap text-center">
                        {esOk && (
                          <span className="inline-flex items-center gap-1 px-2 py-1 rounded-full text-xs font-bold bg-green-100 dark:bg-green-900/30 text-green-700 dark:text-green-300">
                            <MdCheckCircle className="text-sm" />
                            OK
                          </span>
                        )}
                        {esParcial && (
                          <span className="inline-flex items-center gap-1 px-2 py-1 rounded-full text-xs font-bold bg-yellow-100 dark:bg-yellow-900/30 text-yellow-700 dark:text-yellow-300">
                            <MdWarning className="text-sm" />
                            PARCIAL
                          </span>
                        )}
                        {!esOk && !esParcial && (
                          <span className="inline-flex items-center gap-1 px-2 py-1 rounded-full text-xs font-bold bg-red-100 dark:bg-red-900/30 text-red-700 dark:text-red-300">
                            <MdCancel className="text-sm" />
                            SIN ASIGNAR
                          </span>
                        )}
                      </td>
                      <td className="px-3 py-2 text-sm text-gray-600 dark:text-gray-400 max-w-md">
                        {a.motivo ? (
                          <span className="text-red-700 dark:text-red-300">{a.motivo}</span>
                        ) : (
                          <span className="text-gray-400 italic">-</span>
                        )}
                      </td>
                      <td className="px-3 py-2 whitespace-nowrap text-center">
                        {!esOk && (
                          <button
                            onClick={() => regenerarGrupo(a.grupoId)}
                            disabled={regenerandoGrupo === a.grupoId}
                            title="Regenerar solo este grupo"
                            className="inline-flex items-center gap-1 px-2 py-1 rounded text-xs font-medium bg-indigo-100 dark:bg-indigo-900/30 text-indigo-700 dark:text-indigo-300 hover:bg-indigo-200 dark:hover:bg-indigo-900/50 transition disabled:opacity-50 disabled:cursor-not-allowed"
                          >
                            <MdRefresh className="text-sm" />
                            {regenerandoGrupo === a.grupoId ? 'Regenerando...' : 'Regenerar'}
                          </button>
                        )}
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
};

// ============================================================
// Helper: reconstruye el detalle por asignación desde los
// horarios existentes. Se usa después de regenerar un grupo.
// ============================================================
export default HorarioAutomatico;