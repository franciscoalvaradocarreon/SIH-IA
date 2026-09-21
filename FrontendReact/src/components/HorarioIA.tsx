import React, { useState, useEffect, useMemo } from 'react';
import { horarioIAService } from '../api/horarioIAService';
import { PROVEEDORES_IA } from '../api/horarioIAService';
import type {
  ConfigIA, IntentoIA, PendienteIA, TrabajoIA, ValidacionIA,
} from '../api/horarioIAService';
import { turnoService } from '../api/turnoService';
import { useAuth } from '../context/AuthContext';
import type { Turno } from '../types';
import {
  MdAutoAwesome, MdCheckCircle, MdError, MdWarning, MdInfo, MdPlayArrow,
  MdStop, MdSave, MdExpandMore, MdExpandLess, MdPerson, MdClass,
  MdTimer, MdRule, MdHourglassEmpty, MdScience,
} from 'react-icons/md';

/**
 * GENERADOR DE HORARIOS IA (sin Timefold).
 *
 * Arma el horario con el motor propio: varios intentos, cada uno desde cero, quedándose con el
 * mejor. Antes de generar pre-valida la información y, además de lo que ya valida el módulo de
 * siempre, enseña los chequeos propios del motor IA (ventanas legales, tramos continuos...) y las
 * asignaturas que no caben con los datos actuales.
 *
 * Cada intento se puede mirar por dentro: las horas que dejó pendientes, con el motivo y con quién
 * ocupa sus ventanas. El que sirva se registra en el horario real con un botón.
 */
/** Qué es cada modelo, para que la lista no sean solo nombres. */
const DESCRIPCION_MODELO: Record<string, string> = {
  'deepseek-chat': 'el más reciente de chat de DeepSeek (nombre estable: se actualiza solo)',
  'deepseek-reasoner': 'razonamiento: más potente, piensa más y tarda más',
  'gpt-4o-mini': 'económico y rápido',
  'gpt-4o': 'potente',
  'gpt-4.1-mini': 'económico reciente',
};

const HorarioIA: React.FC = () => {
  const { semestreActivo } = useAuth();

  const [turnos, setTurnos] = useState<Turno[]>([]);
  const [turnoSeleccionado, setTurnoSeleccionado] = useState(0);

  const [config, setConfig] = useState<ConfigIA>({
    intentos: 6, segundosPorIntento: 60, maxPasos: 60000, llmConfigurado: false,
    modeloPorDefecto: '',
  });
  const [modo, setModo] = useState<'heuristica' | 'llm'>('heuristica');
  const [intentos, setIntentos] = useState(6);
  const [segundos, setSegundos] = useState(60);

  // Clave del asesor IA: se pide al elegir el modo y vive SOLO en memoria, para esta generación.
  const [pedirClave, setPedirClave] = useState(false);
  const [claveIA, setClaveIA] = useState('');
  const [modeloIA, setModeloIA] = useState('');
  const [proveedorIA, setProveedorIA] = useState('openai');
  const [urlIA, setUrlIA] = useState('');
  const [verClave, setVerClave] = useState(false);
  // Modo nuevo: el motor asigna los maestros desde el stock (materias de la misma asignación).
  const [asignarMaestros, setAsignarMaestros] = useState(false);

  const [validacion, setValidacion] = useState<ValidacionIA | null>(null);
  const [validando, setValidando] = useState(false);
  const [verValidacion, setVerValidacion] = useState(false);

  const [trabajo, setTrabajo] = useState<TrabajoIA | null>(null);
  const [generando, setGenerando] = useState(false);
  const [registrando, setRegistrando] = useState<number | null>(null);
  const [mostrarProblemas, setMostrarProblemas] = useState(false);
  const [abierto, setAbierto] = useState<number | null>(null);

  const [error, setError] = useState('');
  const [aviso, setAviso] = useState('');

  const enCurso = trabajo?.estado === 'EN_COLA' || trabajo?.estado === 'EN_PROCESO';
  // Hasta que no haya un turno elegido no se habilita nada: generar sin turno no tiene sentido.
  const turnoListo = turnoSeleccionado > 0;

  // Mientras hay una generación en curso, el menú no deja navegar a otra página (bandera global que
  // consulta el componente Menu): así no se pierde el seguimiento de los intentos.
  useEffect(() => {
    (window as unknown as { __generandoIA?: boolean }).__generandoIA = enCurso;
    return () => {
      (window as unknown as { __generandoIA?: boolean }).__generandoIA = false;
    };
  }, [enCurso]);

  useEffect(() => {
    if (semestreActivo?.id) {
      cargarCatalogos();
      horarioIAService.config()
        .then(res => {
          setConfig(res.data);
          setIntentos(res.data.intentos);
          setSegundos(res.data.segundosPorIntento);
          setModeloIA(res.data.modeloPorDefecto || '');
          // Se preselecciona el proveedor cuyo catálogo incluye el modelo configurado en el servidor.
          const prov = PROVEEDORES_IA.find(p => p.modelos.includes(res.data.modeloPorDefecto || ''));
          if (prov) {
            setProveedorIA(prov.id);
            setUrlIA(prov.url);
          }
        })
        .catch(() => { /* se usan los valores por defecto de la pantalla */ });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [semestreActivo?.id]);

  const cargarCatalogos = async () => {
    try {
      const res = await turnoService.listar(0, 100, '', semestreActivo!.id);
      setTurnos(res.data.content.filter((t: Turno) => t.activo === true));
    } catch {
      setError('No se pudieron cargar los turnos del semestre');
    }
  };

  // ── seguimiento del trabajo ──
  useEffect(() => {
    if (!trabajo || !enCurso) return;
    const consulta = setInterval(async () => {
      try {
        const res = await horarioIAService.consultar(trabajo.id);
        setTrabajo(res.data);
        if (res.data.estado === 'COMPLETADO' || res.data.estado === 'ERROR') {
          setGenerando(false);
        }
      } catch {
        // si falla una consulta se reintenta en la siguiente vuelta
      }
    }, 2000);
    return () => clearInterval(consulta);
  }, [trabajo?.id, enCurso]);

  // reloj de la banda de progreso: segundos contra el presupuesto total (intentos × segundos)
  const presupuestoSegundos = (trabajo?.intentosPlaneados || intentos) * (trabajo?.segundosPorIntento || segundos);
  const porcentaje = useMemo(() => {
    if (!trabajo) return 0;
    return Math.min(100, Math.max(3, (trabajo.segundosTranscurridos * 100) / (presupuestoSegundos || 1)));
  }, [trabajo, presupuestoSegundos]);

  const prevalidar = async () => {
    if (!semestreActivo?.id || !turnoListo) return;
    setValidando(true);
    setError('');
    setAviso('');
    try {
      const res = await horarioIAService.validar(semestreActivo.id, turnoSeleccionado || undefined);
      setValidacion(res.data);
      setVerValidacion(true);
      if (!res.data.aptoParaGenerar) {
        setAviso('La pre-validación encontró errores. Se puede generar igual, pero el motor no podrá colocarlo todo.');
      }
    } catch (e) {
      setError(mensajeError(e, 'No se pudo pre-validar la información'));
    } finally {
      setValidando(false);
    }
  };

  const generar = async () => {
    if (!semestreActivo?.id || !turnoListo) return;
    const usaLLM = modo === 'llm';
    const clave = claveIA.trim();
    // En modo IA hace falta una clave: la que acabas de escribir o, si no, la que tenga configurada
    // el servidor. Si no hay ninguna, se pide antes de lanzar nada.
    if (usaLLM && !clave && !config.llmConfigurado) {
      setPedirClave(true);
      setError('Para generar con el asesor IA hay que indicar la clave de la API.');
      return;
    }
    setGenerando(true);
    setError('');
    setAviso('');
    setAbierto(null);
    setMostrarProblemas(false);
    try {
      const res = await horarioIAService.generar({
        semestreId: semestreActivo.id,
        turnoId: turnoSeleccionado || undefined,
        modo,
        intentos,
        segundosPorIntento: segundos,
        // La clave viaja solo con esta petición; el servidor la descarta al terminar la generación.
        apiKey: usaLLM && clave ? clave : undefined,
        modelo: usaLLM && modeloIA.trim() ? modeloIA.trim() : undefined,
        // La URL del proveedor elegido (DeepSeek, OpenAI u otro) va solo para esta generación.
        url: usaLLM && urlIA.trim() ? urlIA.trim() : undefined,
        // El motor asigna los maestros desde el stock (los que ya dan la materia en las asignaciones).
        asignarMaestros: asignarMaestros || undefined,
      });
      setTrabajo(res.data);
      setClaveIA('');           // no se queda guardada ni en la pantalla
      setVerClave(false);
    } catch (e) {
      setGenerando(false);
      setError(mensajeError(e, 'No se pudo lanzar la generación'));
    }
  };

  const terminar = async () => {
    if (!trabajo) return;
    try {
      const res = await horarioIAService.terminar(trabajo.id);
      setTrabajo(res.data);
      setAviso('Se dejan de lanzar intentos. El que está en curso termina y queda disponible.');
    } catch (e) {
      setError(mensajeError(e, 'No se pudo terminar la generación'));
    }
  };

  const registrar = async (numero: number) => {
    if (!trabajo) return;
    setRegistrando(numero);
    setError('');
    setAviso('');
    try {
      const res = await horarioIAService.registrar(trabajo.id, numero);
      setTrabajo(res.data);
      setAviso(`Intento ${numero} guardado en el horario: ${res.data.horasRegistradas} h en ${res.data.filasRegistradas} bloques.`);
    } catch (e) {
      setError(mensajeError(e, 'No se pudo registrar el intento'));
    } finally {
      setRegistrando(null);
    }
  };

  if (!semestreActivo) {
    return (
      <div className="p-8 text-center text-gray-600 dark:text-gray-400">
        Selecciona un semestre para generar horarios.
      </div>
    );
  }

  return (
    <div className="p-4 md:p-6 space-y-4">
      {/* ── encabezado ── */}
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold flex items-center gap-2 text-gray-900 dark:text-gray-100">
            <MdScience className="text-indigo-600" /> Generador IA
          </h1>
          <p className="text-sm text-gray-500 dark:text-gray-400">
            Motor propio, sin Timefold · semestre {semestreActivo.nombre}
          </p>
        </div>
        <div className="flex items-center gap-2 text-xs">
          <span className={`px-2 py-1 rounded-full ${config.llmConfigurado
            ? 'bg-emerald-100 text-emerald-800 dark:bg-emerald-900/40 dark:text-emerald-300'
            : 'bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-300'}`}>
            {config.llmConfigurado
              ? 'Asesor IA: clave del servidor'
              : 'Asesor IA: se pide la clave al elegirlo'}
          </span>
        </div>
      </div>

      {error && (
        <div className="flex items-start gap-2 rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-800 dark:border-red-900 dark:bg-red-900/20 dark:text-red-300">
          <MdError className="mt-0.5 shrink-0" /> <span className="whitespace-pre-line">{error}</span>
        </div>
      )}
      {aviso && (
        <div className="flex items-start gap-2 rounded-lg border border-amber-200 bg-amber-50 p-3 text-sm text-amber-800 dark:border-amber-900 dark:bg-amber-900/20 dark:text-amber-300">
          <MdInfo className="mt-0.5 shrink-0" /> <span className="whitespace-pre-line">{aviso}</span>
        </div>
      )}

      {/* ── controles ── */}
      <div className="rounded-lg border border-gray-200 bg-white p-4 dark:border-gray-700 dark:bg-gray-800">
        <div className="grid grid-cols-1 gap-3 md:grid-cols-4">
          <label className="text-sm">
            <span className="mb-1 block text-gray-600 dark:text-gray-300">Turno</span>
            <select
              value={turnoSeleccionado}
              onChange={e => setTurnoSeleccionado(Number(e.target.value))}
              disabled={enCurso}
              className="w-full rounded-md border border-gray-300 bg-white px-2 py-1.5 text-sm dark:border-gray-600 dark:bg-gray-900 dark:text-gray-100"
            >
              <option value={0}>Selecciona un turno…</option>
              {turnos.map(t => (
                <option key={t.id} value={t.id}>{t.nombre}</option>
              ))}
            </select>
          </label>

          <label className="text-sm">
            <span className="mb-1 block text-gray-600 dark:text-gray-300">Motor</span>
            <select
              value={modo}
              onChange={e => {
                const elegido = e.target.value as 'heuristica' | 'llm';
                setModo(elegido);
                // Al elegir el asesor IA se pide la clave: se usa solo en esta generación.
                if (elegido === 'llm') {
                  setPedirClave(true);
                }
              }}
              disabled={enCurso || !turnoListo}
              className="w-full rounded-md border border-gray-300 bg-white px-2 py-1.5 text-sm dark:border-gray-600 dark:bg-gray-900 dark:text-gray-100"
            >
              <option value="heuristica">Motor propio</option>
              <option value="llm">Motor propio + asesor IA</option>
            </select>
          </label>

          <label className="text-sm">
            <span className="mb-1 block text-gray-600 dark:text-gray-300">Intentos</span>
            <input
              type="number" min={1} max={50} value={intentos}
              onChange={e => setIntentos(Number(e.target.value))}
              disabled={enCurso || !turnoListo}
              className="w-full rounded-md border border-gray-300 bg-white px-2 py-1.5 text-sm dark:border-gray-600 dark:bg-gray-900 dark:text-gray-100"
            />
          </label>

          <label className="text-sm">
            <span className="mb-1 block text-gray-600 dark:text-gray-300">Segundos por intento</span>
            <input
              type="number" min={5} max={1800} value={segundos}
              onChange={e => setSegundos(Number(e.target.value))}
              disabled={enCurso || !turnoListo}
              className="w-full rounded-md border border-gray-300 bg-white px-2 py-1.5 text-sm dark:border-gray-600 dark:bg-gray-900 dark:text-gray-100"
            />
          </label>
        </div>

        <div className="mt-3 flex flex-wrap items-center gap-2">
          <label className="flex items-center gap-2 rounded-md border border-gray-200 px-2 py-1.5 text-xs text-gray-700 hover:bg-gray-50 disabled:opacity-50 dark:border-gray-600 dark:text-gray-200 dark:hover:bg-gray-700">
            <input
              type="checkbox"
              checked={asignarMaestros}
              onChange={e => setAsignarMaestros(e.target.checked)}
              disabled={enCurso || !turnoListo}
            />
            Asignar maestros desde el stock
          </label>
          <span className="text-xs text-gray-500 dark:text-gray-400">
            el motor elige el maestro de cada materia y el taller (aula) entre los que ya usa la
            materia (Jóvenes: un maestro que dé otra clase en el grupo, y un grupo de Jóvenes por
            maestro)
          </span>
        </div>

        <div className="mt-3 flex flex-wrap items-center gap-2">
          <button
            onClick={prevalidar}
            disabled={validando || enCurso || !turnoListo}
            className="inline-flex items-center gap-2 rounded-md border border-gray-300 px-3 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50 disabled:opacity-50 dark:border-gray-600 dark:text-gray-200 dark:hover:bg-gray-700"
          >
            <MdRule /> {validando ? 'Revisando…' : 'Pre-validar'}
          </button>

          <button
            onClick={generar}
            disabled={generando || enCurso || !turnoListo}
            className="inline-flex items-center gap-2 rounded-md bg-indigo-600 px-3 py-2 text-sm font-medium text-white hover:bg-indigo-700 disabled:opacity-50"
          >
            <MdPlayArrow /> {enCurso ? 'Generando…' : 'Generar horario'}
          </button>

          {enCurso && (
            <button
              onClick={terminar}
              className="inline-flex items-center gap-2 rounded-md border border-red-300 px-3 py-2 text-sm font-medium text-red-700 hover:bg-red-50 dark:border-red-800 dark:text-red-300 dark:hover:bg-red-900/20"
            >
              <MdStop /> Terminar y quedarme con lo mejor
            </button>
          )}

          {!turnoListo && (
            <span className="text-xs text-amber-700 dark:text-amber-400">
              Selecciona un turno para habilitar la generación.
            </span>
          )}

          {trabajo && (
            <span className="ml-auto text-xs text-gray-500 dark:text-gray-400">
              {trabajo.intentos.length} de {trabajo.intentosPlaneados} intento(s) ·{' '}
              {trabajo.segundosPorIntento} s cada uno
            </span>
          )}
        </div>
      </div>

      {/* ── diálogo de la clave del asesor IA ── */}
      {pedirClave && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
          <div className="w-full max-w-md rounded-lg border border-gray-200 bg-white p-4 shadow-xl dark:border-gray-700 dark:bg-gray-800">
            <h3 className="flex items-center gap-2 text-base font-semibold text-gray-900 dark:text-gray-100">
              <MdAutoAwesome className="text-indigo-600" /> Clave del asesor IA
            </h3>
            <p className="mt-1 text-xs text-gray-500 dark:text-gray-400">
              Se usa <strong>solo en esta generación</strong>: viaja con la petición y el servidor la
              descarta al terminar. No se guarda ni se muestra después.
            </p>

            <label className="mt-3 block text-sm">
              <span className="mb-1 block text-gray-600 dark:text-gray-300">Proveedor</span>
              <select
                value={proveedorIA}
                onChange={e => {
                  const id = e.target.value;
                  setProveedorIA(id);
                  const prov = PROVEEDORES_IA.find(p => p.id === id);
                  if (prov) {
                    setUrlIA(prov.url);
                    setModeloIA(prov.modeloPorDefecto);
                  }
                }}
                className="w-full rounded-md border border-gray-300 bg-white px-2 py-1.5 text-sm dark:border-gray-600 dark:bg-gray-900 dark:text-gray-100"
              >
                {PROVEEDORES_IA.map(p => (
                  <option key={p.id} value={p.id}>{p.nombre}</option>
                ))}
              </select>
            </label>

            {proveedorIA === 'personalizado' && (
              <label className="mt-3 block text-sm">
                <span className="mb-1 block text-gray-600 dark:text-gray-300">
                  URL del endpoint
                </span>
                <input
                  type="text"
                  value={urlIA}
                  onChange={e => setUrlIA(e.target.value)}
                  placeholder="https://…/v1/chat/completions"
                  className="w-full rounded-md border border-gray-300 bg-white px-2 py-1.5 text-sm dark:border-gray-600 dark:bg-gray-900 dark:text-gray-100"
                />
              </label>
            )}

            <label className="mt-3 block text-sm">
              <span className="mb-1 block text-gray-600 dark:text-gray-300">Clave de la API</span>
              <div className="flex items-center gap-2">
                <input
                  type={verClave ? 'text' : 'password'}
                  value={claveIA}
                  autoFocus
                  placeholder={config.llmConfigurado ? 'vacío = usar la del servidor' : 'sk-…'}
                  onChange={e => setClaveIA(e.target.value)}
                  className="w-full rounded-md border border-gray-300 bg-white px-2 py-1.5 text-sm dark:border-gray-600 dark:bg-gray-900 dark:text-gray-100"
                />
                <button
                  type="button"
                  onClick={() => setVerClave(v => !v)}
                  className="shrink-0 rounded-md border border-gray-300 px-2 py-1.5 text-xs text-gray-600 hover:bg-gray-50 dark:border-gray-600 dark:text-gray-300 dark:hover:bg-gray-700"
                >
                  {verClave ? 'Ocultar' : 'Ver'}
                </button>
              </div>
            </label>

            <label className="mt-3 block text-sm">
              <span className="mb-1 block text-gray-600 dark:text-gray-300">Modelo</span>
              {proveedorIA === 'personalizado' ? (
                <input
                  type="text"
                  value={modeloIA}
                  onChange={e => setModeloIA(e.target.value)}
                  placeholder={config.modeloPorDefecto || 'gpt-4o-mini'}
                  className="w-full rounded-md border border-gray-300 bg-white px-2 py-1.5 text-sm dark:border-gray-600 dark:bg-gray-900 dark:text-gray-100"
                />
              ) : (
                <select
                  value={modeloIA}
                  onChange={e => setModeloIA(e.target.value)}
                  className="w-full rounded-md border border-gray-300 bg-white px-2 py-1.5 text-sm dark:border-gray-600 dark:bg-gray-900 dark:text-gray-100"
                >
                  {(() => {
                    const prov = PROVEEDORES_IA.find(p => p.id === proveedorIA);
                    const modelos = prov ? [...prov.modelos] : [];
                    if (modeloIA && !modelos.includes(modeloIA)) {
                      modelos.push(modeloIA);
                    }
                    return modelos.map(m => (
                      <option key={m} value={m}>
                        {m}{DESCRIPCION_MODELO[m] ? ` — ${DESCRIPCION_MODELO[m]}` : ''}
                      </option>
                    ));
                  })()}
                </select>
              )}
            </label>

            <div className="mt-4 flex justify-end gap-2">
              <button
                onClick={() => {
                  setPedirClave(false);
                  setClaveIA('');
                  setVerClave(false);
                  setModo('heuristica');
                }}
                className="rounded-md border border-gray-300 px-3 py-2 text-sm text-gray-700 hover:bg-gray-50 dark:border-gray-600 dark:text-gray-200 dark:hover:bg-gray-700"
              >
                Cancelar
              </button>
              <button
                onClick={() => {
                  if (!claveIA.trim() && !config.llmConfigurado) return;
                  setPedirClave(false);
                  setError('');
                }}
                disabled={!claveIA.trim() && !config.llmConfigurado}
                className="rounded-md bg-indigo-600 px-3 py-2 text-sm font-medium text-white hover:bg-indigo-700 disabled:opacity-50"
              >
                Usar esta clave
              </button>
            </div>
          </div>
        </div>
      )}

      {/* ── pre-validación ── */}
      {validacion && (
        <div className="rounded-lg border border-gray-200 bg-white dark:border-gray-700 dark:bg-gray-800">
          <button
            onClick={() => setVerValidacion(v => !v)}
            className="flex w-full items-center justify-between gap-2 p-3 text-left"
          >
            <span className="flex items-center gap-2 text-sm font-semibold text-gray-800 dark:text-gray-100">
              <MdRule className="text-indigo-600" /> Pre-validación
              {validacion.aptoParaGenerar
                ? <span className="flex items-center gap-1 text-xs font-normal text-emerald-700 dark:text-emerald-400">
                    <MdCheckCircle /> sin errores
                  </span>
                : <span className="flex items-center gap-1 text-xs font-normal text-red-700 dark:text-red-400">
                    <MdError /> con errores
                  </span>}
              {validacion.imposibles.length > 0 && (
                <span className="flex items-center gap-1 text-xs font-normal text-amber-700 dark:text-amber-400">
                  <MdWarning /> {validacion.imposibles.length} sin ventana legal
                </span>
              )}
            </span>
            <span className="flex items-center gap-3 text-xs text-gray-500 dark:text-gray-400">
              {validacion.asignaciones} asignaturas · {validacion.sesiones} sesiones ·{' '}
              {validacion.horasDemandadas} h · {validacion.ventanasLegales} ventanas
              {verValidacion ? <MdExpandLess /> : <MdExpandMore />}
            </span>
          </button>

          {verValidacion && (
            <div className="space-y-3 border-t border-gray-200 p-3 dark:border-gray-700">
              {/* chequeos del motor IA */}
              <div className="space-y-1">
                {validacion.chequeos.map((c, i) => (
                  <div key={i} className="flex items-start gap-2 text-sm">
                    <IconoEstado estado={c.estado} />
                    <span className="font-medium text-gray-800 dark:text-gray-200">{c.nombre}:</span>
                    <span className="text-gray-600 dark:text-gray-400">{c.detalle}</span>
                  </div>
                ))}
              </div>

              {/* lo que ya validaba el backend */}
              {validacion.backend?.validaciones?.length > 0 && (
                <div className="space-y-1 border-t border-gray-200 pt-3 dark:border-gray-700">
                  {validacion.backend.validaciones.map((v, i) => (
                    <div key={i} className="flex items-start gap-2 text-sm">
                      <IconoEstado estado={v.estado} />
                      <span className="font-medium text-gray-800 dark:text-gray-200">{v.titulo}:</span>
                      <span className="text-gray-600 dark:text-gray-400">{v.mensaje}</span>
                    </div>
                  ))}
                </div>
              )}

              {/* asignaturas que no caben */}
              {validacion.imposibles.length > 0 && (
                <div className="border-t border-gray-200 pt-3 dark:border-gray-700">
                  <p className="mb-2 flex items-center gap-2 text-sm font-semibold text-amber-700 dark:text-amber-400">
                    <MdWarning /> Asignaturas sin ninguna ventana legal
                  </p>
                  <div className="space-y-1">
                    {validacion.imposibles.map(m => (
                      <div key={m.asignacionId}
                        className="rounded-md bg-amber-50 p-2 text-xs text-amber-900 dark:bg-amber-900/20 dark:text-amber-200">
                        <span className="font-semibold">{m.materia}</span> · {m.grupo} · {m.maestro} ·{' '}
                        {m.horas} h<br />{m.motivo}
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </div>
          )}
        </div>
      )}

      {/* ── avance ── */}
      {trabajo && (
        <div className="rounded-lg border border-gray-200 bg-white p-4 dark:border-gray-700 dark:bg-gray-800">
          <div className="mb-2 flex flex-wrap items-center justify-between gap-2 text-sm">
            <span className="flex items-center gap-2 font-semibold text-gray-800 dark:text-gray-100">
              {enCurso ? <MdHourglassEmpty className="animate-pulse text-indigo-600" /> : <MdAutoAwesome className="text-indigo-600" />}
              {trabajo.mensaje}
            </span>
            <span className="text-xs text-gray-500 dark:text-gray-400">
              {trabajo.estado} · {trabajo.segundosTranscurridos} s
              {trabajo.error ? ` · ${trabajo.error}` : ''}
            </span>
          </div>

          {/* banda de progreso: segundos contra el presupuesto (intentos × segundos) */}
          <div className="h-2 w-full overflow-hidden rounded-full bg-gray-200 dark:bg-gray-700">
            <div
              className={`h-full rounded-full transition-all duration-1000 ${enCurso ? 'bg-indigo-500' : 'bg-emerald-500'}`}
              style={{ width: `${trabajo.estado === 'COMPLETADO' ? 100 : porcentaje}%` }}
            />
          </div>
          <p className="mt-1 text-xs text-gray-500 dark:text-gray-400">
            {enCurso
              ? `Presupuesto: ${presupuestoSegundos} s (${trabajo.intentosPlaneados} × ${trabajo.segundosPorIntento} s)`
              : `Terminado en ${trabajo.segundosTranscurridos} s · demanda ${trabajo.horasDemandadas} h`}
            {trabajo.terminadoPorUsuario && ' · terminado por ti'}
          </p>

          {/* lista de intentos */}
          {trabajo.intentos.length > 0 && (
            <div className="mt-3 space-y-2">
              {trabajo.intentos.map(it => (
                <FichaIntento
                  key={it.numero}
                  intento={it}
                  esMejor={trabajo.mejorNumero === it.numero}
                  registrado={trabajo.registrado === it.numero}
                  abierto={abierto === it.numero}
                  onAlternar={() => setAbierto(abierto === it.numero ? null : it.numero)}
                  onRegistrar={() => registrar(it.numero)}
                  registrando={registrando === it.numero}
                  puedeRegistrar={!enCurso && trabajo.registrado == null}
                />
              ))}
            </div>
          )}

          {/* problemas duros del mejor intento */}
          {trabajo.intentos.some(i => i.problemas.length > 0) && (
            <div className="mt-3 border-t border-gray-200 pt-3 dark:border-gray-700">
              <button
                onClick={() => setMostrarProblemas(v => !v)}
                className="flex items-center gap-2 text-xs font-medium text-red-700 dark:text-red-400"
              >
                {mostrarProblemas ? <MdExpandLess /> : <MdExpandMore />}
                Comprobación dura: hay intentos con problemas
              </button>
              {mostrarProblemas && (
                <ul className="mt-2 list-inside list-disc space-y-1 text-xs text-red-700 dark:text-red-300">
                  {trabajo.intentos.flatMap(i =>
                    i.problemas.map((p, k) => <li key={`${i.numero}-${k}`}>Intento {i.numero}: {p}</li>)
                  )}
                </ul>
              )}
            </div>
          )}
        </div>
      )}

      {!trabajo && !validando && (
        <div className="flex items-center gap-3 rounded-lg border border-dashed border-gray-300 p-6 text-sm text-gray-500 dark:border-gray-700 dark:text-gray-400">
          <MdInfo className="text-lg" />
          Pre-valida para revisar la información y luego genera. Cada intento parte de cero y el mejor
          se puede guardar en el horario real.
        </div>
      )}
    </div>
  );
};

/** Ficha de un intento, con sus métricas y sus horas pendientes. */
const FichaIntento: React.FC<{
  intento: IntentoIA;
  esMejor: boolean;
  registrado: boolean;
  abierto: boolean;
  registrando: boolean;
  puedeRegistrar: boolean;
  onAlternar: () => void;
  onRegistrar: () => void;
}> = ({ intento, esMejor, registrado, abierto, registrando, puedeRegistrar, onAlternar, onRegistrar }) => {
  const completo = intento.pendientes.length === 0;
  return (
    <div className={`rounded-md border p-3 ${esMejor
      ? 'border-indigo-300 bg-indigo-50/60 dark:border-indigo-800 dark:bg-indigo-900/10'
      : 'border-gray-200 dark:border-gray-700'}`}>
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="flex flex-wrap items-center gap-2 text-sm">
          <span className="font-semibold text-gray-900 dark:text-gray-100">Intento {intento.numero}</span>
          {esMejor && (
            <span className="rounded-full bg-indigo-600 px-2 py-0.5 text-xs text-white">mejor</span>
          )}
          {registrado && (
            <span className="flex items-center gap-1 rounded-full bg-emerald-600 px-2 py-0.5 text-xs text-white">
              <MdCheckCircle /> en el horario
            </span>
          )}
          {intento.problemas.length > 0 && (
            <span className="flex items-center gap-1 rounded-full bg-red-600 px-2 py-0.5 text-xs text-white">
              <MdError /> {intento.problemas.length} problema(s)
            </span>
          )}
        </div>
        <div className="flex items-center gap-2">
          <button
            onClick={onAlternar}
            className="inline-flex items-center gap-1 rounded-md border border-gray-300 px-2 py-1 text-xs text-gray-700 hover:bg-gray-50 dark:border-gray-600 dark:text-gray-200 dark:hover:bg-gray-700"
          >
            {abierto ? <MdExpandLess /> : <MdExpandMore />}
            {completo ? 'Sin pendientes' : `${intento.pendientes.length} pendiente(s)`}
          </button>
          {puedeRegistrar && intento.problemas.length === 0 && (
            <button
              onClick={onRegistrar}
              disabled={registrando}
              className="inline-flex items-center gap-1 rounded-md bg-emerald-600 px-2 py-1 text-xs font-medium text-white hover:bg-emerald-700 disabled:opacity-50"
            >
              <MdSave /> {registrando ? 'Guardando…' : 'Usar este'}
            </button>
          )}
        </div>
      </div>

      <div className="mt-2 flex flex-wrap gap-x-4 gap-y-1 text-xs text-gray-600 dark:text-gray-300">
        <span className="flex items-center gap-1">
          <MdClass /> {intento.horas}/{intento.horasDemandadas} h
        </span>
        <span className="flex items-center gap-1">
          <MdTimer /> {(intento.milisegundos / 1000).toFixed(1)} s
        </span>
        <span>medium {intento.medium}</span>
        <span>{intento.materiasCompletas}/{intento.materiasTotales} materias</span>
        <span>arranques tarde {intento.arranquesTarde}</span>
        <span>castigo huecos {intento.castigoHuecos}</span>
        <span>adyacencias {intento.adyacencias}</span>
        {intento.sesionesLargasPendientes > 0 && (
          <span className="text-amber-700 dark:text-amber-400">
            sesiones largas pendientes {intento.sesionesLargasPendientes}
          </span>
        )}
        <span className="text-gray-400">asesor: {intento.asesor}</span>
      </div>

      {abierto && (
        <div className="mt-3 space-y-1">
          {completo ? (
            <p className="flex items-center gap-2 text-xs text-emerald-700 dark:text-emerald-400">
              <MdCheckCircle /> Colocó todas las horas pedidas.
            </p>
          ) : (
            intento.pendientes.map((p: PendienteIA, i: number) => (
              <div key={i} className="rounded-md bg-white p-2 text-xs dark:bg-gray-900/40">
                <div className="flex flex-wrap items-center gap-2 font-medium text-gray-800 dark:text-gray-100">
                  <MdClass /> {p.materiaClave} {p.materiaNombre}
                  <span className="text-gray-500 dark:text-gray-400">· {p.grupoNombre}</span>
                  <span className="flex items-center gap-1 text-gray-500 dark:text-gray-400">
                    <MdPerson /> {p.maestroNombre}
                  </span>
                  <span className="rounded bg-gray-100 px-1.5 py-0.5 dark:bg-gray-700">
                    {p.duracion} h
                  </span>
                </div>
                <p className="mt-1 text-amber-700 dark:text-amber-400">{p.motivo}</p>
                {p.ventanas.length > 0 && (
                  <ul className="mt-1 space-y-0.5 text-gray-500 dark:text-gray-400">
                    {p.ventanas.map((v, k) => <li key={k}>· {v}</li>)}
                  </ul>
                )}
              </div>
            ))
          )}
        </div>
      )}
    </div>
  );
};

const IconoEstado: React.FC<{ estado: 'OK' | 'ADVERTENCIA' | 'ERROR' }> = ({ estado }) => {
  if (estado === 'OK') return <MdCheckCircle className="mt-0.5 shrink-0 text-emerald-600" />;
  if (estado === 'ERROR') return <MdError className="mt-0.5 shrink-0 text-red-600" />;
  return <MdWarning className="mt-0.5 shrink-0 text-amber-600" />;
};

/** Mensaje del backend si lo trae; si no, uno genérico. */
function mensajeError(e: unknown, porDefecto: string): string {
  const err = e as { response?: { data?: { message?: string; error?: string } } };
  return err?.response?.data?.message || err?.response?.data?.error || porDefecto;
}

export default HorarioIA;
