import React, { useState, useEffect, useMemo } from 'react';
import { horarioIAService } from '../api/horarioIAService';
import { PROVEEDORES_IA } from '../api/horarioIAService';
import type {
  ConfigIA, IntentoIA, PendienteIA, TrabajoIA, ValidacionIA, ChequeoIA, MateriaImposible,
  AnalisisViabilidadIA, RevisionesViabilidadIA, SeveridadViabilidad,
  MaestroViabilidadIA, GrupoViabilidadIA, GrupoDesbalanceIA, ResumenViabilidadIA,
  CupoMaestroGrupoIA, BloquePocosMaestrosIA, GrupoBloquesApretadosIA,
  ResumenRevisionesIA, CorridaIA,
} from '../api/horarioIAService';
import { turnoService } from '../api/turnoService';
import { useAuth } from '../context/AuthContext';
import type { Turno, Validacion } from '../types';
import ErrorBoundary from './ErrorBoundary';
import {
  MdAutoAwesome, MdCheckCircle, MdError, MdWarning, MdInfo, MdPlayArrow,
  MdStop, MdSave, MdExpandMore, MdExpandLess, MdPerson, MdClass,
  MdTimer, MdRule, MdHourglassEmpty, MdScience, MdBookmarkAdd, MdDoneAll, MdDelete,
} from 'react-icons/md';
import { SwitchToggle } from '../utils/SwitchToggle';

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

/**
 * BLINDAJE DE LOS DATOS QUE VIENEN DEL BACKEND.
 *
 * La pre-validación pinta datos de diagnóstico que pueden faltar (el backend todavía no los manda,
 * llegaron con otro nombre o la respuesta es vieja). En React, un solo acceso a `null.length` tumba
 * TODA la pantalla. Estos ayudantes convierten cualquier cosa rara en un valor inofensivo para que el
 * bloque muestre lo que haya en lugar de dejar la página en blanco.
 */

/** La lista si de verdad es una lista; si no (null, undefined, objeto suelto), una vacía. */
function listaSegura<T>(valor: unknown): T[] {
  return Array.isArray(valor) ? (valor as T[]) : [];
}

/** El número si de verdad es un número; si no, 0: nunca se pinta NaN ni undefined. */
function numeroSeguro(valor: unknown): number {
  return typeof valor === 'number' && Number.isFinite(valor) ? valor : 0;
}

/** El texto si de verdad es un texto (o un número/booleano); si no, el sustituto. */
function texto(valor: unknown, porDefecto = '—'): string {
  if (typeof valor === 'string') return valor;
  if (typeof valor === 'number' || typeof valor === 'boolean') return String(valor);
  return porDefecto;
}

/** El objeto si de verdad es un objeto; si no (null, undefined, texto), undefined. */
function objetoSeguro<T extends object>(valor: unknown): T | undefined {
  return valor && typeof valor === 'object' ? (valor as T) : undefined;
}

/**
 * Mensaje de una acción (guardar, aplicar o borrar una corrida), para pintarlo JUNTO al botón que la
 * lanzó.
 *
 * <p>Por qué no vale el aviso global de arriba: la página es larga y el botón de guardar queda muy por
 * debajo del encabezado, así que el aviso se pintaba fuera de la vista. El usuario veía que "no pasaba
 * nada" cuando el backend sí estaba respondiendo con un mensaje (por ejemplo, el nombre repetido).
 */
type MensajeAccion = { tipo: 'error' | 'ok'; texto: string };

const HorarioIA: React.FC = () => {
  const { semestreActivo } = useAuth();

  const [turnos, setTurnos] = useState<Turno[]>([]);
  const [turnoSeleccionado, setTurnoSeleccionado] = useState(0);

  const [config, setConfig] = useState<ConfigIA>({
    intentos: 6, segundosPorIntento: 60, maxPasos: 60000, llmConfigurado: false,
    modeloPorDefecto: '',
    // 1 hasta que responda el servidor: así la estimación nunca promete menos tiempo del real.
    hilos: 1,
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
  // Dos modos INDEPENDIENTES: repartir los maestros desde el stock y/o elegir el taller desde el
  // stock de talleres de la materia (las aulas que ESA materia ya usa). Antes eran una sola bandera,
  // así que no se podía probar una sin la otra.
  const [asignarMaestros, setAsignarMaestros] = useState(false);
  const [asignarAulas, setAsignarAulas] = useState(false);

  const [validacion, setValidacion] = useState<ValidacionIA | null>(null);
  const [validando, setValidando] = useState(false);
  const [verValidacion, setVerValidacion] = useState(false);

  const [trabajo, setTrabajo] = useState<TrabajoIA | null>(null);
  const [generando, setGenerando] = useState(false);
  const [registrando, setRegistrando] = useState<number | null>(null);
  const [mostrarProblemas, setMostrarProblemas] = useState(false);
  const [abierto, setAbierto] = useState<number | null>(null);

  // ── corridas guardadas ──
  // Son opciones APARTADAS: guardarlas NO toca el horario. Solo aplicarlas lo reescribe.
  const [corridas, setCorridas] = useState<CorridaIA[]>([]);
  const [cargandoCorridas, setCargandoCorridas] = useState(false);
  const [guardandoCorrida, setGuardandoCorrida] = useState<number | null>(null);
  const [aplicandoCorrida, setAplicandoCorrida] = useState<number | null>(null);
  const [borrandoCorrida, setBorrandoCorrida] = useState<number | null>(null);

  // Mensajes de estas acciones, pintados junto al botón que las lanza. El aviso global vive arriba
  // del todo y, con la página larga, quedaba fuera de la vista (ver MensajeAccion).
  const [mensajeGuardado, setMensajeGuardado] =
    useState<{ trabajoId: string; numero: number; tipo: 'error' | 'ok'; texto: string } | null>(null);
  const [mensajeLista, setMensajeLista] = useState<MensajeAccion | null>(null);

  // Confirmación en modal, no window.confirm: el resto de la app usa modales con su estilo y el
  // diálogo nativo del navegador rompía la estética (y no respeta el modo oscuro).
  const [confirmacion, setConfirmacion] =
    useState<{ accion: 'aplicar' | 'borrar'; corrida: CorridaIA } | null>(null);

  const [error, setError] = useState('');
  const [aviso, setAviso] = useState('');

  const enCurso = trabajo?.estado === 'EN_COLA' || trabajo?.estado === 'EN_PROCESO';
  // Hasta que no haya un turno elegido no se habilita nada: generar sin turno no tiene sentido.
  const turnoListo = turnoSeleccionado > 0;

  // Datos de la pre-validación tratados como posiblemente ausentes: si una lista no llega (o llega
  // como null), su bloque se pinta vacío en vez de tumbar la pantalla entera.
  const chequeosValidacion = listaSegura<ChequeoIA>(validacion?.chequeos);
  const imposiblesValidacion = listaSegura<MateriaImposible>(validacion?.imposibles);
  const validacionesBackend = listaSegura<Validacion>(validacion?.backend?.validaciones);

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

  // Reloj de la banda de progreso. OJO con no confundir los dos presupuestos:
  //
  //   - el de CPU: intentos × segundosPorIntento. Es el trabajo total a repartir, y es lo que se
  //     le enseña al usuario como "cuánto cálculo" se ha pedido.
  //   - el de RELOJ: con 'hilos' intentos a la vez, los N intentos van en ceil(N / hilos) tandas,
  //     no en N veces. Si la barra se midiera contra el de CPU se quedaría en un tercio al
  //     terminar (medido: 6 intentos de 120 s tardan 242 s, no 720).
  const hilos = Math.max(1, config?.hilos ?? 1);
  const intentosPrevistos = trabajo?.intentosPlaneados || intentos;
  const segundosPrevistos = trabajo?.segundosPorIntento || segundos;
  const presupuestoReloj = Math.ceil(intentosPrevistos / hilos) * segundosPrevistos;
  const porcentaje = useMemo(() => {
    if (!trabajo) return 0;
    // Tope de 99 mientras corre: puede pasarse del presupuesto (por ejemplo si otra escuela está
    // generando y quedan menos hilos libres), y es mejor que no marque 100% antes de terminar.
    return Math.min(99, Math.max(3, (trabajo.segundosTranscurridos * 100) / (presupuestoReloj || 1)));
  }, [trabajo, presupuestoReloj]);

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
        // Dos banderas independientes: reparto de maestros y elección de taller.
        asignarMaestros: asignarMaestros || undefined,
        asignarAulas: asignarAulas || undefined,
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

  // ── corridas guardadas ────────────────────────────────────────────────────

  const cargarCorridas = async () => {
    if (!semestreActivo?.id) return;
    setCargandoCorridas(true);
    try {
      const res = await horarioIAService.corridas(semestreActivo.id);
      setCorridas(Array.isArray(res.data) ? res.data : []);
    } catch {
      // No poder leer la lista no debe tumbar la pantalla de generación: se muestra vacía.
      setCorridas([]);
    } finally {
      setCargandoCorridas(false);
    }
  };

  useEffect(() => {
    cargarCorridas();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [semestreActivo?.id]);

  /** Aparta ese intento con un nombre. NO escribe en el horario: eso es el botón "Usar este". */
  const guardarCorrida = async (numero: number, nombre: string): Promise<boolean> => {
    if (!trabajo) return false;
    setGuardandoCorrida(numero);
    // El mensaje se pinta DENTRO de la ficha del intento: es donde está el botón que se acaba de
    // pulsar. El aviso global de arriba quedaba fuera de la vista.
    setMensajeGuardado(null);
    try {
      const res = await horarioIAService.guardarCorrida(trabajo.id, numero, nombre);
      setMensajeGuardado({
        trabajoId: trabajo.id,
        numero,
        tipo: 'ok',
        texto: `Corrida "${res.data.nombre}" guardada como opción. El horario actual NO se ha tocado.`,
      });
      await cargarCorridas();
      return true;
    } catch (e) {
      setMensajeGuardado({
        trabajoId: trabajo.id,
        numero,
        tipo: 'error',
        texto: mensajeError(e, 'No se pudo guardar la corrida'),
      });
      return false;
    } finally {
      setGuardandoCorrida(null);
    }
  };

  /** Aplica una corrida guardada al horario vigente. Es lo único destructivo de esta pantalla. */
  const aplicarCorridaGuardada = async (corrida: CorridaIA) => {
    setAplicandoCorrida(corrida.id);
    setMensajeLista(null);
    try {
      const res = await horarioIAService.aplicarCorrida(corrida.id);
      setMensajeLista({
        tipo: 'ok',
        texto: `Corrida "${corrida.nombre}" aplicada al horario: ${res.data.horas} h en ` +
          `${res.data.filas} bloques de ${res.data.grupos} grupo(s).`,
      });
    } catch (e) {
      setMensajeLista({ tipo: 'error', texto: mensajeError(e, 'No se pudo aplicar la corrida') });
    } finally {
      setAplicandoCorrida(null);
    }
  };

  /** Quita una corrida de la lista. NO deshace lo aplicado: solo borra la opcion guardada. */
  const borrarCorridaGuardada = async (corrida: CorridaIA) => {
    setBorrandoCorrida(corrida.id);
    setMensajeLista(null);
    try {
      await horarioIAService.borrarCorrida(corrida.id);
      setMensajeLista({
        tipo: 'ok',
        texto: `Corrida "${corrida.nombre}" borrada de la lista. El horario no se ha tocado.`,
      });
      await cargarCorridas();
    } catch (e) {
      setMensajeLista({ tipo: 'error', texto: mensajeError(e, 'No se pudo borrar la corrida') });
    } finally {
      setBorrandoCorrida(null);
    }
  };

  /** Ejecuta la acción que el modal estaba confirmando. */
  const confirmarAccion = () => {
    if (!confirmacion) return;
    const { accion, corrida } = confirmacion;
    setConfirmacion(null);
    if (accion === 'aplicar') {
      aplicarCorridaGuardada(corrida);
    } else {
      borrarCorridaGuardada(corrida);
    }
  };

  /**
   * Mensaje de la última acción sobre un intento, SOLO si es del trabajo que se está viendo.
   *
   * <p>La comprobación del trabajo no es un adorno: los números de intento se repiten en cada
   * generación (1..6), así que sin ella el aviso "Corrida guardada" del intento 1 de la generación
   * anterior aparecía pegado en el intento 1 de la siguiente.
   */
  const mensajeDeIntento = (numero: number): MensajeAccion | null =>
    mensajeGuardado && trabajo && mensajeGuardado.trabajoId === trabajo.id
      && mensajeGuardado.numero === numero
      ? { tipo: mensajeGuardado.tipo, texto: mensajeGuardado.texto }
      : null;

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
              className="w-full px-4 py-2.5 border border-gray-400 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:ring-2 focus:ring-blue-500 focus:border-transparent transition disabled:opacity-50"
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
              className="w-full px-4 py-2.5 border border-gray-400 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:ring-2 focus:ring-blue-500 focus:border-transparent transition disabled:opacity-50"
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
              className="w-full px-4 py-2.5 border border-gray-400 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:ring-2 focus:ring-blue-500 focus:border-transparent transition disabled:opacity-50"
            />
          </label>

          <label className="text-sm">
            <span className="mb-1 block text-gray-600 dark:text-gray-300">Segundos por intento</span>
            <input
              type="number" min={5} max={1800} value={segundos}
              onChange={e => setSegundos(Number(e.target.value))}
              disabled={enCurso || !turnoListo}
              className="w-full px-4 py-2.5 border border-gray-400 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:ring-2 focus:ring-blue-500 focus:border-transparent transition disabled:opacity-50"
            />
          </label>
        </div>

        <div className="mt-3 flex flex-wrap items-center gap-2">
          <div className="flex items-center gap-2 rounded-md border border-gray-200 px-2 py-1.5 text-xs text-gray-700 hover:bg-gray-50 disabled:opacity-50 dark:border-gray-600 dark:text-gray-200 dark:hover:bg-gray-700">
            <SwitchToggle
              checked={asignarMaestros}
              onChange={setAsignarMaestros}
              label="Asignar maestros desde el stock"
              color="blue"
              size="md"
              disabled={enCurso || !turnoListo}
            />
          </div>
          <div className="flex items-center gap-2 rounded-md border border-gray-200 px-2 py-1.5 text-xs text-gray-700 hover:bg-gray-50 disabled:opacity-50 dark:border-gray-600 dark:text-gray-200 dark:hover:bg-gray-700">
            <SwitchToggle
              checked={asignarAulas}
              onChange={setAsignarAulas}
              label="Asignar talleres desde el stock"
              color="blue"
              size="md"
              disabled={enCurso || !turnoListo}
            />
          </div>
          <span className="text-xs text-gray-500 dark:text-gray-400">
            son dos decisiones independientes: <strong>maestros</strong> reparte quién da cada materia
            entre los que ya la imparten (Jóvenes: un maestro que dé otra clase en el grupo, y un grupo
            de Jóvenes por maestro) y <strong>talleres</strong> deja que el motor cambie el aula de una
            sesión por otra de las que <strong>ya usa esa materia</strong> —no entre todas las aulas del
            plantel—, en vez de respetar siempre la de la asignación. Con las dos apagadas el horario
            sale como siempre
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
                className="w-full px-4 py-2.5 border border-gray-400 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:ring-2 focus:ring-blue-500 focus:border-transparent transition disabled:opacity-50"
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
                  className="w-full px-4 py-2.5 border border-gray-400 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:ring-2 focus:ring-blue-500 focus:border-transparent transition"
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
                  className="w-full px-4 py-2.5 border border-gray-400 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:ring-2 focus:ring-blue-500 focus:border-transparent transition"
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
                  className="w-full px-4 py-2.5 border border-gray-400 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:ring-2 focus:ring-blue-500 focus:border-transparent transition"
                />
              ) : (
                <select
                  value={modeloIA}
                  onChange={e => setModeloIA(e.target.value)}
                  className="w-full px-4 py-2.5 border border-gray-400 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:ring-2 focus:ring-blue-500 focus:border-transparent transition disabled:opacity-50"
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
              {imposiblesValidacion.length > 0 && (
                <span className="flex items-center gap-1 text-xs font-normal text-amber-700 dark:text-amber-400">
                  <MdWarning /> {imposiblesValidacion.length} sin ventana legal
                </span>
              )}
            </span>
            <span className="flex items-center gap-3 text-xs text-gray-500 dark:text-gray-400">
              {numeroSeguro(validacion.asignaciones)} asignaturas · {numeroSeguro(validacion.sesiones)} sesiones ·{' '}
              {numeroSeguro(validacion.horasDemandadas)} h · {numeroSeguro(validacion.ventanasLegales)} ventanas
              {verValidacion ? <MdExpandLess /> : <MdExpandMore />}
            </span>
          </button>

          {verValidacion && (
            <div className="space-y-3 border-t border-gray-200 p-3 dark:border-gray-700">
              {/* chequeos del motor IA */}
              <div className="space-y-1">
                {chequeosValidacion.map((c, i) => (
                  <div key={i} className="flex items-start gap-2 text-sm">
                    <IconoEstado estado={c?.estado} />
                    <span className="font-medium text-gray-800 dark:text-gray-200">{texto(c?.nombre, 'Chequeo')}:</span>
                    <span className="text-gray-600 dark:text-gray-400">{texto(c?.detalle, '')}</span>
                  </div>
                ))}
              </div>

              {/* lo que ya validaba el backend */}
              {validacionesBackend.length > 0 && (
                <div className="space-y-1 border-t border-gray-200 pt-3 dark:border-gray-700">
                  {validacionesBackend.map((v, i) => (
                    <div key={i} className="flex items-start gap-2 text-sm">
                      <IconoEstado estado={v?.estado} />
                      <span className="font-medium text-gray-800 dark:text-gray-200">{texto(v?.titulo, 'Validación')}:</span>
                      <span className="text-gray-600 dark:text-gray-400">{texto(v?.mensaje, '')}</span>
                    </div>
                  ))}
                </div>
              )}

              {/* asignaturas que no caben */}
              {imposiblesValidacion.length > 0 && (
                <div className="border-t border-gray-200 pt-3 dark:border-gray-700">
                  <p className="mb-2 flex items-center gap-2 text-sm font-semibold text-amber-700 dark:text-amber-400">
                    <MdWarning /> Asignaturas sin ninguna ventana legal
                  </p>
                  <div className="space-y-1">
                    {imposiblesValidacion.map((m, i) => (
                      <div key={`${texto(m?.asignacionId, String(i))}-${i}`}
                        className="rounded-md bg-amber-50 p-2 text-xs text-amber-900 dark:bg-amber-900/20 dark:text-amber-200">
                        <span className="font-semibold">{texto(m?.materia, 'Asignatura')}</span> ·{' '}
                        {texto(m?.grupo, 'grupo ?')} · {texto(m?.maestro, 'maestro ?')} ·{' '}
                        {numeroSeguro(m?.horas)} h<br />{texto(m?.motivo, '')}
                      </div>
                    ))}
                  </div>
                </div>
              )}

              {/* análisis de viabilidad: ¿cabe esto? ¿y quién va más justo?
                  Va dentro de un ErrorBoundary: si aun así se nos escapa un dato raro, el usuario ve
                  el aviso con el mensaje en vez de una pantalla en blanco. */}
              <ErrorBoundary titulo="el análisis de viabilidad" resetKey={validacion}>
                <AnalisisViabilidadIA analisis={validacion.analisisViabilidad} />
              </ErrorBoundary>
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

          {/* Banda de progreso: segundos transcurridos contra el presupuesto de RELOJ
              (tandas × segundos), que es lo que de verdad va a tardar. El de CPU
              (intentos × segundos) se sigue mostrando abajo, porque es lo que se pidió calcular. */}
          <div className="h-2 w-full overflow-hidden rounded-full bg-gray-200 dark:bg-gray-700">
            <div
              className={`h-full rounded-full transition-all duration-1000 ${enCurso ? 'bg-indigo-500' : 'bg-emerald-500'}`}
              style={{ width: `${trabajo.estado === 'COMPLETADO' ? 100 : porcentaje}%` }}
            />
          </div>
          <p className="mt-1 text-xs text-gray-500 dark:text-gray-400">
            {enCurso
              ? `Presupuesto: ${presupuestoReloj} s (${trabajo.intentosPlaneados} × ${trabajo.segundosPorIntento} s en CPU${hilos > 1 ? `, ${hilos} a la vez` : ''})`
              : `Terminado en ${trabajo.segundosTranscurridos} s · demanda ${trabajo.horasDemandadas} h`}
            {trabajo.terminadoPorUsuario && ' · terminado por ti'}
          </p>

          {/* lista de intentos */}
          {trabajo.intentos.length > 0 && (
            <div className="mt-3 space-y-2">
              {trabajo.intentos.map(it => (
                <FichaIntento
                  key={`${trabajo.id}-${it.numero}`}
                  intento={it}
                  esMejor={trabajo.mejorNumero === it.numero}
                  registrado={trabajo.registrado === it.numero}
                  abierto={abierto === it.numero}
                  onAlternar={() => setAbierto(abierto === it.numero ? null : it.numero)}
                  onRegistrar={() => registrar(it.numero)}
                  registrando={registrando === it.numero}
                  puedeRegistrar={!enCurso && trabajo.registrado == null}
                  onGuardarCorrida={(nombre) => guardarCorrida(it.numero, nombre)}
                  guardandoCorrida={guardandoCorrida === it.numero}
                  mensaje={mensajeDeIntento(it.numero)}
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
          Pre-valida para revisar la información y luego genera. Cada intento parte de cero. Cualquiera
          se puede GUARDAR COMO OPCIÓN (sin tocar el horario) para compararlas, y la que sirva se aplica
          al horario real.
        </div>
      )}

      {/* ── corridas guardadas: comparar opciones sin tocar el horario ── */}
      <ListaCorridas
        corridas={corridas}
        cargando={cargandoCorridas}
        aplicando={aplicandoCorrida}
        borrando={borrandoCorrida}
        mensaje={mensajeLista}
        turnos={turnos}
        onAplicar={(corrida) => setConfirmacion({ accion: 'aplicar', corrida })}
        onBorrar={(corrida) => setConfirmacion({ accion: 'borrar', corrida })}
        onRefrescar={cargarCorridas}
      />

      {/* Confirmación en modal, con el estilo del resto de la app (no window.confirm). */}
      {confirmacion && (
        <ModalConfirmarCorrida
          corrida={confirmacion.corrida}
          accion={confirmacion.accion}
          ocupado={confirmacion.accion === 'aplicar'
            ? aplicandoCorrida !== null
            : borrandoCorrida !== null}
          turnos={turnos}
          onConfirmar={confirmarAccion}
          onCancelar={() => setConfirmacion(null)}
        />
      )}
    </div>
  );
};

/** Pinta el mensaje de una acción donde ocurrió. No pinta nada si no hay mensaje. */
const AvisoAccion: React.FC<{ mensaje: MensajeAccion | null }> = ({ mensaje }) => {
  if (!mensaje) return null;
  const esError = mensaje.tipo === 'error';
  return (
    <div className={`mt-2 flex items-start gap-2 rounded-md border p-2 text-xs ${esError
      ? 'border-red-200 bg-red-50 text-red-800 dark:border-red-900 dark:bg-red-900/20 dark:text-red-300'
      : 'border-emerald-200 bg-emerald-50 text-emerald-800 dark:border-emerald-900 dark:bg-emerald-900/20 dark:text-emerald-300'}`}>
      {esError
        ? <MdError className="mt-0.5 shrink-0" />
        : <MdCheckCircle className="mt-0.5 shrink-0" />}
      <span className="whitespace-pre-line">{mensaje.texto}</span>
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
  guardandoCorrida: boolean;
  /** Mensaje de la última acción sobre ESTE intento (guardar). Va aquí, junto al botón. */
  mensaje: MensajeAccion | null;
  onAlternar: () => void;
  onRegistrar: () => void;
  /** Devuelve true si se guardó. Si no, la ficha deja el campo abierto para corregir el nombre. */
  onGuardarCorrida: (nombre: string) => Promise<boolean>;
}> = ({ intento, esMejor, registrado, abierto, registrando, puedeRegistrar, guardandoCorrida,
       mensaje, onAlternar, onRegistrar, onGuardarCorrida }) => {
  const completo = intento.pendientes.length === 0;

  // El nombre se pide EN LINEA y no con window.prompt: prompt bloquea el navegador y tapa el intento
  // justo cuando estas decidiendo el nombre.
  const [pidiendoNombre, setPidiendoNombre] = useState(false);
  const [nombreCorrida, setNombreCorrida] = useState('');

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
          {/* Guardar como opcion: aparta la corrida SIN tocar el horario. Es distinto de "Usar este",
              que si reescribe el horario vigente. */}
          {intento.problemas.length === 0 && (
            <button
              onClick={() => {
                setNombreCorrida(`Opción intento ${intento.numero}`);
                setPidiendoNombre(true);
              }}
              disabled={guardandoCorrida}
              className="inline-flex items-center gap-1 rounded-md bg-indigo-600 px-2 py-1 text-xs font-medium text-white hover:bg-indigo-700 disabled:opacity-50"
            >
              <MdBookmarkAdd /> {guardandoCorrida ? 'Guardando…' : 'Guardar corrida'}
            </button>
          )}
          {puedeRegistrar && intento.problemas.length === 0 && (
            <button
              onClick={onRegistrar}
              disabled={registrando}
              className="inline-flex items-center gap-1 rounded-md bg-emerald-600 px-2 py-1 text-xs font-medium text-white hover:bg-emerald-700 disabled:opacity-50"
            >
              <MdSave /> {registrando ? 'Aplicando…' : 'Usar este'}
            </button>
          )}
        </div>
      </div>

      {pidiendoNombre && (
        <div className="mt-2 flex flex-wrap items-center gap-2 border-t border-gray-200 pt-2 dark:border-gray-700">
          <input
            autoFocus
            type="text"
            value={nombreCorrida}
            maxLength={120}
            onChange={e => setNombreCorrida(e.target.value)}
            onKeyDown={async e => {
              if (e.key === 'Enter' && nombreCorrida.trim()) {
                // Solo se cierra si de verdad se guardó: si el nombre estaba repetido, el campo se
                // queda abierto con lo que escribiste, para corregirlo sin volver a teclearlo.
                if (await onGuardarCorrida(nombreCorrida.trim())) setPidiendoNombre(false);
              }
              if (e.key === 'Escape') setPidiendoNombre(false);
            }}
            placeholder="Nombre de la corrida (ej: Opción A - sin huecos)"
            className="min-w-[15rem] flex-1 rounded-md border border-gray-300 px-2 py-1 text-xs dark:border-gray-600 dark:bg-gray-800 dark:text-gray-100"
          />
          <button
            onClick={async () => {
              if (await onGuardarCorrida(nombreCorrida.trim())) setPidiendoNombre(false);
            }}
            disabled={!nombreCorrida.trim() || guardandoCorrida}
            className="inline-flex items-center gap-1 rounded-md bg-indigo-600 px-2 py-1 text-xs font-medium text-white hover:bg-indigo-700 disabled:opacity-50"
          >
            <MdBookmarkAdd /> {guardandoCorrida ? 'Guardando…' : 'Guardar'}
          </button>
          <button
            onClick={() => setPidiendoNombre(false)}
            className="rounded-md border border-gray-300 px-2 py-1 text-xs text-gray-700 hover:bg-gray-50 dark:border-gray-600 dark:text-gray-200 dark:hover:bg-gray-700"
          >
            Cancelar
          </button>
          <span className="text-xs text-gray-500 dark:text-gray-400">No toca el horario actual.</span>
        </div>
      )}

      {/* El mensaje va FUERA del bloque de arriba para que siga viéndose cuando el campo de nombre se
          cierra al guardar: si no, el aviso de "nombre repetido" desaparecería con él. */}
      <AvisoAccion mensaje={mensaje} />

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

/** Cuántos grupos se enseñan en la tabla antes de tener que desplegar el resto. */
const GRUPOS_VISIBLES = 12;

/** Cuántos bloques con 2 maestros posibles se listan antes de resumir el resto (los de 1 se listan todos). */
const BLOQUES_DOS_VISIBLES = 10;

/** Colores de la etiqueta de severidad: imposible / ajustado / holgado. */
const BADGE_SEVERIDAD: Record<SeveridadViabilidad, string> = {
  IMPOSIBLE: 'bg-red-100 text-red-800 dark:bg-red-900/40 dark:text-red-300',
  AJUSTADO: 'bg-amber-100 text-amber-800 dark:bg-amber-900/40 dark:text-amber-300',
  HOLGADO: 'bg-emerald-100 text-emerald-800 dark:bg-emerald-900/40 dark:text-emerald-300',
};

/**
 * Etiqueta de severidad. El backend manda "IMPOSIBLE" / "AJUSTADO" / "HOLGADO", pero si manda otra
 * cosa (o nada) se pinta una etiqueta neutra: aquí no se puede caer por un valor inesperado.
 */
const EtiquetaSeveridad: React.FC<{ severidad?: SeveridadViabilidad | null }> = ({ severidad }) => {
  const clase = (severidad && BADGE_SEVERIDAD[severidad])
    || 'bg-gray-100 text-gray-600 dark:bg-gray-600 dark:text-gray-200';
  return (
    <span className={`whitespace-nowrap rounded-full px-2 py-0.5 text-[10px] font-semibold ${clase}`}>
      {typeof severidad === 'string' && severidad ? severidad.toLowerCase() : 'sin estado'}
    </span>
  );
};

/** Aviso de que un dato del backend no llegó o no llegó con la forma esperada. */
const AvisoDatoAusente: React.FC<{ children: React.ReactNode }> = ({ children }) => (
  <div className="flex items-start gap-2 rounded-lg border border-gray-400 bg-white px-4 py-2.5 text-xs text-gray-700 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-200">
    <span className="flex shrink-0 items-center gap-1 rounded-full bg-amber-100 px-2 py-0.5 text-[10px] font-semibold text-amber-800 dark:bg-amber-900/40 dark:text-amber-300">
      <MdWarning /> aviso
    </span>
    <span>{children}</span>
  </div>
);

/**
 * ANÁLISIS DE VIABILIDAD DEL REPARTO.
 *
 * Responde, ANTES de generar, a "¿por qué no cabe todo?": qué maestros tienen más horas asignadas
 * que huecos legales (imposible matemático), qué bloques de un grupo no los puede dar ningún maestro
 * y qué grupos van tan justos que conviene reacomodar maestros entre ellos.
 *
 * Es solo diagnóstico: nada de esto impide generar. La severidad es una pista, no un error
 * (imposible = no cabe; ajustado = cabe forzado; holgado = hay margen).
 */
const AnalisisViabilidadIA: React.FC<{ analisis?: AnalisisViabilidadIA | null }> = ({ analisis }) => {
  const [verTodos, setVerTodos] = useState(false);

  // Primer nivel del blindaje: si el campo no llegó (o llegó como null / texto / número), se avisa y
  // se sigue. Antes esto era `const { maestros } = analisis` y un campo ausente tumbaba la pantalla.
  if (!analisis || typeof analisis !== 'object') {
    return (
      <div className="space-y-3 border-t border-gray-200 pt-3 dark:border-gray-700">
        <p className="flex items-center gap-2 text-sm font-semibold text-gray-800 dark:text-gray-100">
          <MdRule className="text-indigo-600" /> Viabilidad del reparto de maestros
        </p>
        <AvisoDatoAusente>
          El backend no devolvió el análisis de viabilidad (<code>analisisViabilidad</code>), o lo
          devolvió con otro nombre o con un tipo inesperado. Los chequeos de la pre-validación de
          arriba siguen siendo válidos.
        </AvisoDatoAusente>
      </div>
    );
  }

  // Cada lista y cada objeto se normaliza: si no es lo que se espera, queda vacío y el bloque sigue.
  const maestros = listaSegura<MaestroViabilidadIA>(analisis.maestros);
  const grupos = listaSegura<GrupoViabilidadIA>(analisis.grupos);
  const desbalance = listaSegura<GrupoDesbalanceIA>(analisis.desbalance);
  const resumen = objetoSeguro<ResumenViabilidadIA>(analisis.resumen);
  const revisiones = objetoSeguro<RevisionesViabilidadIA>(analisis.revisiones);

  const maestrosEnDeficit = numeroSeguro(resumen?.maestrosEnDeficit);
  const horasSinHueco = numeroSeguro(resumen?.horasSinHueco);
  const bloquesSinMaestroTotal = numeroSeguro(resumen?.bloquesSinMaestro);
  const gruposAjustados = numeroSeguro(resumen?.gruposAjustados);

  const enDeficit = maestros.filter(m => numeroSeguro(m?.deficit) > 0);
  // Los bloques sin ningún maestro se listan por grupo; solo puede haberlos en grupos imposibles.
  const sinMaestro = grupos
    .map((g, i) => ({
      grupo: texto(g?.grupo, `grupo ${i + 1}`),
      bloques: listaSegura<string>(g?.bloquesSinMaestro),
    }))
    .filter(g => g.bloques.length > 0)
    .flatMap(g => g.bloques.map(b => ({ grupo: g.grupo, bloque: texto(b, 'bloque ?') })));
  const gruposMostrados = verTodos ? grupos : grupos.slice(0, GRUPOS_VISIBLES);

  return (
    <div className="space-y-3 border-t border-gray-200 pt-3 dark:border-gray-700">
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <p className="flex items-center gap-2 text-sm font-semibold text-gray-800 dark:text-gray-100">
          <MdRule className="text-indigo-600" /> Viabilidad del reparto de maestros
        </p>
        <span className="text-xs text-gray-500 dark:text-gray-400">
          solo diagnóstico: no impide generar
        </span>
      </div>

      {/* resumen: la respuesta corta a "¿esto cabe?" */}
      {!resumen && (
        <AvisoDatoAusente>
          El análisis de viabilidad vino sin el bloque <code>resumen</code>: se muestran las listas,
          pero los totales de la cabecera no están disponibles.
        </AvisoDatoAusente>
      )}
      <div className="flex flex-wrap gap-2 text-xs">
        <span className={`rounded-full px-2 py-1 ${maestrosEnDeficit > 0
          ? BADGE_SEVERIDAD.IMPOSIBLE : BADGE_SEVERIDAD.HOLGADO}`}>
          {maestrosEnDeficit > 0
            ? `⚠ ${maestrosEnDeficit} maestro(s) con más horas que huecos`
            : '✓ ningún maestro pide más horas que huecos legales'}
        </span>
        {horasSinHueco > 0 && (
          <span className={`rounded-full px-2 py-1 ${BADGE_SEVERIDAD.IMPOSIBLE}`}>
            {horasSinHueco} h que no caben en ningún lado
          </span>
        )}
        <span className={`rounded-full px-2 py-1 ${bloquesSinMaestroTotal > 0
          ? BADGE_SEVERIDAD.IMPOSIBLE : BADGE_SEVERIDAD.HOLGADO}`}>
          {bloquesSinMaestroTotal > 0
            ? `⚠ ${bloquesSinMaestroTotal} bloque(s) sin ningún maestro posible`
            : '✓ todos los bloques de todos los grupos tienen algún maestro'}
        </span>
        <span className={`rounded-full px-2 py-1 ${gruposAjustados > 0
          ? BADGE_SEVERIDAD.AJUSTADO : BADGE_SEVERIDAD.HOLGADO}`}>
          {gruposAjustados} grupo(s) ajustado(s)
        </span>
      </div>

      {/* maestros en déficit: el imposible matemático */}
      <div>
        <p className="mb-1 text-xs font-semibold text-gray-700 dark:text-gray-200">
          Maestros en déficit ({enDeficit.length})
        </p>
        {enDeficit.length === 0 ? (
          <p className="text-xs text-gray-500 dark:text-gray-400">
            Ningún maestro tiene más horas asignadas que bloques legales: el motor no está peleando
            contra un imposible de reparto.
          </p>
        ) : (
          <>
            <p className="mb-2 text-xs text-gray-500 dark:text-gray-400">
              Estos maestros no pueden dar todas sus horas aunque el motor acierte: no hay suficientes
              bloques legales. Hay que mover alguna de sus horas a otro maestro.
            </p>
            <div className="overflow-x-auto rounded-md border border-gray-200 dark:border-gray-700">
              <table className="w-full text-left text-xs">
                <thead className="bg-gray-50 text-gray-600 dark:bg-gray-900/40 dark:text-gray-300">
                  <tr>
                    <th className="px-3 py-2 font-medium">Maestro</th>
                    <th className="px-3 py-2 font-medium">Horas</th>
                    <th className="px-3 py-2 font-medium">Huecos legales</th>
                    <th className="px-3 py-2 font-medium">No caben</th>
                    <th className="px-3 py-2 font-medium">Grupos</th>
                    <th className="px-3 py-2 font-medium">Materias</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100 dark:divide-gray-700">
                  {enDeficit.map((m, i) => (
                    <tr key={`${texto(m?.maestro, 'maestro')}-${i}`}
                      className="bg-red-50/60 text-gray-800 dark:bg-red-900/10 dark:text-gray-200">
                      <td className="px-3 py-2 font-medium">{texto(m?.maestro, 'maestro ?')}</td>
                      <td className="px-3 py-2 tabular-nums">{numeroSeguro(m?.horasAsignadas)}</td>
                      <td className="px-3 py-2 tabular-nums">{numeroSeguro(m?.ventanasLegales)}</td>
                      <td className="px-3 py-2 font-semibold tabular-nums text-red-700 dark:text-red-400">
                        {numeroSeguro(m?.deficit)} h
                      </td>
                      <td className="px-3 py-2 tabular-nums">{numeroSeguro(m?.grupos)}</td>
                      <td className="px-3 py-2 text-gray-500 dark:text-gray-400">
                        {listaSegura<string>(m?.materias).map(x => texto(x, '?')).join(' · ') || '—'}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </>
        )}
      </div>

      {/* bloques que no puede dar nadie */}
      {sinMaestro.length > 0 && (
        <div>
          <p className="mb-1 flex items-center gap-1 text-xs font-semibold text-red-700 dark:text-red-400">
            <MdError /> Bloques sin ningún maestro posible ({sinMaestro.length})
          </p>
          <p className="mb-2 text-xs text-gray-500 dark:text-gray-400">
            Ese bloque está disponible para el grupo, pero ninguno de sus maestros puede dar clase en
            él: esa hora se queda vacía sí o sí. Se arregla poniendo en ese bloque a un maestro que
            pueda darlo.
          </p>
          <div className="flex flex-wrap gap-1">
            {sinMaestro.map((x, i) => (
              <span key={i}
                className="rounded-md bg-red-50 px-2 py-1 text-[11px] text-red-800 dark:bg-red-900/20 dark:text-red-300">
                <span className="font-semibold">{x.grupo}</span> · {x.bloque}
              </span>
            ))}
          </div>
        </div>
      )}

      {/* tabla de grupos por holgura */}
      <div>
        <div className="mb-1 flex flex-wrap items-baseline justify-between gap-2">
          <p className="text-xs font-semibold text-gray-700 dark:text-gray-200">
            Grupos por holgura ({grupos.length})
          </p>
          <span className="text-[11px] text-gray-500 dark:text-gray-400">
            índice = maestros disponibles por bloque + horas de sobra · más bajo = más justo
          </span>
        </div>
        <div className="overflow-x-auto rounded-md border border-gray-200 dark:border-gray-700">
          <table className="w-full text-left text-xs">
            <thead className="bg-gray-50 text-gray-600 dark:bg-gray-900/40 dark:text-gray-300">
              <tr>
                <th className="px-3 py-2 font-medium">Grupo</th>
                <th className="px-3 py-2 font-medium">Horas</th>
                <th className="px-3 py-2 font-medium">Bloques</th>
                <th className="px-3 py-2 font-medium">Maestros/bloque</th>
                <th className="px-3 py-2 font-medium">Sin maestro</th>
                <th className="px-3 py-2 font-medium">Holgura</th>
                <th className="px-3 py-2 font-medium">Estado</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100 dark:divide-gray-700">
              {gruposMostrados.map((g, i) => (
                <tr key={`${texto(g?.grupo, 'grupo')}-${i}`}
                  className={g?.severidad === 'IMPOSIBLE' ? 'bg-red-50/50 dark:bg-red-900/10' : ''}>
                  <td className="px-3 py-2 font-medium text-gray-800 dark:text-gray-200">{texto(g?.grupo, 'grupo ?')}</td>
                  <td className="px-3 py-2 tabular-nums text-gray-600 dark:text-gray-300">
                    {numeroSeguro(g?.horasNecesarias)}
                  </td>
                  <td className="px-3 py-2 tabular-nums text-gray-600 dark:text-gray-300">
                    {numeroSeguro(g?.bloquesDisponibles)}
                  </td>
                  <td className="px-3 py-2 tabular-nums text-gray-600 dark:text-gray-300">
                    {numeroSeguro(g?.maestrosPromedio)} <span className="text-gray-400">({numeroSeguro(g?.maestros)})</span>
                  </td>
                  <td className={`px-3 py-2 tabular-nums ${numeroSeguro(g?.bloquesConCero) > 0
                    ? 'font-semibold text-red-700 dark:text-red-400' : 'text-gray-600 dark:text-gray-300'}`}>
                    {numeroSeguro(g?.bloquesConCero)}
                  </td>
                  <td className="px-3 py-2 tabular-nums text-gray-800 dark:text-gray-200">
                    {numeroSeguro(g?.indiceHolgura)}
                  </td>
                  <td className="px-3 py-2"><EtiquetaSeveridad severidad={g?.severidad} /></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {grupos.length > GRUPOS_VISIBLES && (
          <button
            onClick={() => setVerTodos(v => !v)}
            className="mt-2 inline-flex items-center gap-1 rounded-md border border-gray-400 px-4 py-2.5 text-xs font-medium text-gray-700 hover:bg-gray-50 dark:border-gray-600 dark:text-gray-200 dark:hover:bg-gray-700"
          >
            {verTodos ? <MdExpandLess /> : <MdExpandMore />}
            {verTodos ? 'Ver solo los más ajustados' : `Ver los ${grupos.length} grupos`}
          </button>
        )}
      </div>

      {/* lista de desbalance: de más a menos ajustado */}
      {desbalance.length > 0 && (
        <div>
          <p className="mb-1 text-xs font-semibold text-gray-700 dark:text-gray-200">
            Desbalance entre grupos · de más a menos ajustado
          </p>
          <p className="mb-2 text-xs text-gray-500 dark:text-gray-400">
            Los de arriba son los que van más justos: entre ellos es donde conviene reacomodar
            maestros.
          </p>
          <div className="flex flex-wrap gap-1">
            {desbalance.map((g, i) => (
              <span key={`${texto(g?.grupo, 'grupo')}-${i}`}
                className={`rounded-md px-2 py-1 text-[11px] text-gray-800 dark:text-gray-200 ${i === 0
                  ? 'bg-amber-100 ring-1 ring-amber-400 dark:bg-amber-900/30'
                  : 'bg-gray-100 dark:bg-gray-700'}`}>
                <span className="font-semibold">{i + 1}. {texto(g?.grupo, 'grupo ?')}</span>{' '}
                <span className="text-gray-500 dark:text-gray-400">
                  {numeroSeguro(g?.horasNecesarias)} h · {numeroSeguro(g?.bloquesDisponibles)} bloques · holgura {numeroSeguro(g?.indiceHolgura)}
                </span>{' '}
                <EtiquetaSeveridad severidad={g?.severidad} />
              </span>
            ))}
          </div>
        </div>
      )}

      {/* las dos revisiones finas: qué impide colocar cada hora */}
      {revisiones
        ? <RevisionesViabilidadIA revisiones={revisiones} />
        : (
          <AvisoDatoAusente>
            El análisis de viabilidad vino sin las dos revisiones finas (<code>revisiones</code>):
            puede que el backend todavía no las mande o que las mande con otro nombre. Los totales de
            arriba siguen siendo válidos.
          </AvisoDatoAusente>
        )}
    </div>
  );
};

/** Estilo de las tarjetas del bloque de revisiones, el mismo que usa el resto de la pantalla. */
const CAJA_REVISION = 'px-4 py-2.5 border border-gray-400 dark:border-gray-600 rounded-lg '
  + 'bg-white dark:bg-gray-700';

/** Título pequeño de una revisión dentro del diagnóstico. */
const TituloRevision: React.FC<{ icono: React.ReactNode; children: React.ReactNode }> = ({ icono, children }) => (
  <p className="mb-1 flex items-center gap-1 text-xs font-semibold text-gray-700 dark:text-gray-200">
    {icono} {children}
  </p>
);

/**
 * LAS DOS REVISIONES QUE BUSCAN QUÉ IMPIDE COLOCAR CADA HORA.
 *
 * La holgura de horas es CERO por diseño: los grupos tienen exactamente las mismas horas que bloques
 * disponibles, así que un hueco equivale exactamente a una hora que no se colocó y no hay margen para
 * compensar moviendo clases. Estas dos listas dicen, hora a hora, QUÉ la bloquea:
 *
 *  1. cupo real de cada par (maestro, grupo): horas que debe dar contra bloques comunes;
 *  2. bloques donde el grupo solo tiene 1 o 2 maestros posibles (con 1, punto único de fallo).
 *
 * Es solo diagnóstico: nada de esto impide generar. Los contadores enseñan cuántos casos hay, no los
 * colores (que son una pista, no un error).
 */
const RevisionesViabilidadIA: React.FC<{ revisiones?: RevisionesViabilidadIA | null }> = ({ revisiones }) => {
  const [verTodosCupo, setVerTodosCupo] = useState(false);

  // Primer nivel: si el bloque de revisiones no llegó, se avisa y se sigue sin él.
  if (!revisiones || typeof revisiones !== 'object') {
    return (
      <div className="space-y-3 border-t border-gray-200 pt-3 dark:border-gray-700">
        <p className="flex items-center gap-2 text-sm font-semibold text-gray-800 dark:text-gray-100">
          <MdScience className="text-indigo-600" /> Qué impide colocar cada hora
        </p>
        <AvisoDatoAusente>
          El backend no devolvió las revisiones finas (<code>revisiones</code>), o las devolvió con
          otro nombre o con un tipo inesperado. La viabilidad de arriba sigue siendo válida.
        </AvisoDatoAusente>
      </div>
    );
  }

  // Ninguna de las dos listas ni el resumen son obligatorios: lo que falte queda vacío.
  const cupo = listaSegura<CupoMaestroGrupoIA>(revisiones.cupo);
  const bloquesApretados = listaSegura<GrupoBloquesApretadosIA>(revisiones.bloquesApretados);
  const resumen = objetoSeguro<ResumenRevisionesIA>(revisiones.resumen);

  const paresConDeficit = numeroSeguro(resumen?.paresConDeficit);
  const horasDeficit = numeroSeguro(resumen?.horasDeficit);
  const bloquesConUnMaestro = numeroSeguro(resumen?.bloquesConUnMaestro);
  const bloquesConDosMaestros = numeroSeguro(resumen?.bloquesConDosMaestros);

  // Qué campos no llegaron (o no llegaron como lista / objeto): se enseña en un aviso y se sigue.
  const faltantes: string[] = [];
  if (!Array.isArray(revisiones.cupo)) faltantes.push('cupo');
  if (!Array.isArray(revisiones.bloquesApretados)) faltantes.push('bloquesApretados');
  if (!resumen) faltantes.push('resumen');

  // Cupo: primero lo que de verdad no cabe (déficit) y luego lo que va justo (0 o 1 bloque de sobra).
  const cupoImposible = cupo.filter(c => numeroSeguro(c?.deficit) > 0);
  const cupoAjustado = cupo.filter(c => numeroSeguro(c?.deficit) === 0 && c?.severidad === 'AJUSTADO');
  const cupoMostrado = verTodosCupo ? cupo : [...cupoImposible, ...cupoAjustado].slice(0, GRUPOS_VISIBLES);

  // Bloques apretados: solo los grupos que tienen alguno con 1 o 2 maestros posibles.
  const apretados = bloquesApretados.filter(g => numeroSeguro(g?.bloquesConUno) > 0 || numeroSeguro(g?.bloquesConDos) > 0);

  const Vacio: React.FC<{ children: React.ReactNode }> = ({ children }) => (
    <p className="text-xs text-gray-500 dark:text-gray-400">{children}</p>
  );

  return (
    <div className="space-y-3 border-t border-gray-200 pt-3 dark:border-gray-700">
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <p className="flex items-center gap-2 text-sm font-semibold text-gray-800 dark:text-gray-100">
          <MdScience className="text-indigo-600" /> Qué impide colocar cada hora
        </p>
        <span className="text-xs text-gray-500 dark:text-gray-400">
          holgura de horas cero: un hueco es una hora perdida · solo diagnóstico
        </span>
      </div>

      {faltantes.length > 0 && (
        <AvisoDatoAusente>
          El backend no devolvió {faltantes.length === 1 ? 'el campo' : 'los campos'}{' '}
          <code>{faltantes.join(', ')}</code> de las revisiones (o los devolvió con otro tipo): esa
          parte se muestra vacía y el resto sigue funcionando.
        </AvisoDatoAusente>
      )}

      {/* totales de las dos revisiones */}
      <div className="flex flex-wrap gap-2 text-xs">
        <span className={`rounded-full px-2 py-1 ${paresConDeficit > 0
          ? BADGE_SEVERIDAD.IMPOSIBLE : BADGE_SEVERIDAD.HOLGADO}`}>
          {paresConDeficit > 0
            ? `${paresConDeficit} par(es) maestro-grupo con déficit (${horasDeficit} h)`
            : '✓ ningún par maestro-grupo debe más horas que bloques comunes'}
        </span>
        <span className={`rounded-full px-2 py-1 ${bloquesConUnMaestro > 0
          ? BADGE_SEVERIDAD.IMPOSIBLE : BADGE_SEVERIDAD.HOLGADO}`}>
          {bloquesConUnMaestro > 0
            ? `${bloquesConUnMaestro} bloque(s) con un solo maestro posible`
            : '✓ ningún bloque depende de un único maestro'}
        </span>
        <span className={`rounded-full px-2 py-1 ${bloquesConDosMaestros > 0
          ? BADGE_SEVERIDAD.AJUSTADO : BADGE_SEVERIDAD.HOLGADO}`}>
          {bloquesConDosMaestros} bloque(s) con solo 2 maestros posibles
        </span>
      </div>

      {/* ── REVISIÓN 1 · cupo real por maestro y grupo ── */}
      <div className={CAJA_REVISION}>
        <TituloRevision icono={<MdPerson className="text-indigo-600" />}>
          Cupo real por maestro y grupo ({cupoImposible.length} con déficit de {cupo.length} pares)
        </TituloRevision>
        <p className="mb-2 text-xs text-gray-500 dark:text-gray-400">
          Horas que ese maestro debe dar en ese grupo contra los bloques en los que los dos están
          disponibles a la vez. Comparar con los huecos GLOBALES del maestro engaña: un maestro que da
          la misma materia en varios grupos puede tener huecos de sobra y no coincidir con uno de ellos.
        </p>
        {cupoMostrado.length === 0 ? (
          <Vacio>Ningún par maestro-grupo va justo: todos tienen margen de bloques comunes.</Vacio>
        ) : (
          <>
            <div className="overflow-x-auto rounded-md border border-gray-200 dark:border-gray-600">
              <table className="w-full text-left text-xs">
                <thead className="bg-gray-50 text-gray-600 dark:bg-gray-900/40 dark:text-gray-300">
                  <tr>
                    <th className="px-3 py-2 font-medium">Maestro</th>
                    <th className="px-3 py-2 font-medium">Grupo</th>
                    <th className="px-3 py-2 font-medium">Horas</th>
                    <th className="px-3 py-2 font-medium">Bloques comunes</th>
                    <th className="px-3 py-2 font-medium">No caben</th>
                    <th className="px-3 py-2 font-medium">Materias</th>
                    <th className="px-3 py-2 font-medium">Estado</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100 dark:divide-gray-600">
                  {cupoMostrado.map((c, i) => (
                    <tr key={`${texto(c?.maestro, 'maestro')}-${texto(c?.grupo, 'grupo')}-${i}`}
                      className={numeroSeguro(c?.deficit) > 0 ? 'bg-red-50/60 dark:bg-red-900/10' : ''}>
                      <td className="px-3 py-2 font-medium text-gray-800 dark:text-gray-200">{texto(c?.maestro, 'maestro ?')}</td>
                      <td className="px-3 py-2 text-gray-600 dark:text-gray-300">{texto(c?.grupo, 'grupo ?')}</td>
                      <td className="px-3 py-2 tabular-nums text-gray-600 dark:text-gray-300">
                        {numeroSeguro(c?.horasEnGrupo)}
                      </td>
                      <td className="px-3 py-2 tabular-nums text-gray-600 dark:text-gray-300">
                        {numeroSeguro(c?.bloquesComunes)}
                      </td>
                      <td className={`px-3 py-2 tabular-nums ${numeroSeguro(c?.deficit) > 0
                        ? 'font-semibold text-red-700 dark:text-red-400' : 'text-gray-600 dark:text-gray-300'}`}>
                        {numeroSeguro(c?.deficit)} h
                      </td>
                      <td className="px-3 py-2 text-gray-500 dark:text-gray-400">
                        {listaSegura<string>(c?.materias).map(x => texto(x, '?')).join(' · ') || '—'}
                      </td>
                      <td className="px-3 py-2"><EtiquetaSeveridad severidad={c?.severidad} /></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            {cupo.length > cupoMostrado.length && (
              <button
                onClick={() => setVerTodosCupo(v => !v)}
                className="mt-2 inline-flex items-center gap-1 rounded-md border border-gray-400 px-4 py-2.5 text-xs font-medium text-gray-700 hover:bg-gray-50 dark:border-gray-600 dark:text-gray-200 dark:hover:bg-gray-700"
              >
                {verTodosCupo ? <MdExpandLess /> : <MdExpandMore />}
                {verTodosCupo ? 'Ver solo los que van justos' : `Ver los ${cupo.length} pares`}
              </button>
            )}
          </>
        )}
      </div>

      {/* ── REVISIÓN 2 · bloques con pocos maestros posibles ── */}
      <div className={CAJA_REVISION}>
        <TituloRevision icono={<MdError className="text-amber-600" />}>
          Bloques con pocos maestros posibles ({bloquesConUnMaestro} con 1 ·{' '}
          {bloquesConDosMaestros} con 2)
        </TituloRevision>
        <p className="mb-2 text-xs text-gray-500 dark:text-gray-400">
          Con UN solo maestro posible el bloque es un punto único de fallo: si ese maestro se ocupa en
          otro grupo, este bloque se queda libre garantizado. Y como la holgura de horas es cero, esa
          hora no se recupera después. Con 2, un solo choque de disponibilidad lo convierte en el mismo
          problema.
        </p>
        {apretados.length === 0 ? (
          <Vacio>Ningún bloque baja de 3 maestros posibles: el grupo siempre tiene de dónde elegir.</Vacio>
        ) : (
          <div className="space-y-2">
            {apretados.map((g, k) => {
              const detalle = listaSegura<BloquePocosMaestrosIA>(g?.detalle);
              const dos = detalle.filter(b => numeroSeguro(b?.maestrosPosibles) === 2);
              const unicos = detalle.filter(b => numeroSeguro(b?.maestrosPosibles) === 1);
              return (
                <div key={`${texto(g?.grupo, 'grupo')}-${k}`} className="rounded-md border border-gray-200 p-2 dark:border-gray-600">
                  <p className="text-xs font-semibold text-gray-800 dark:text-gray-100">
                    {texto(g?.grupo, 'grupo ?')}{' '}
                    <span className="font-normal text-gray-500 dark:text-gray-400">
                      {numeroSeguro(g?.bloquesDisponibles)} bloques disponibles · mínimo {numeroSeguro(g?.maestrosMinimo)} maestro(s)
                      por bloque
                    </span>
                  </p>
                  {unicos.length > 0 && (
                    <div className="mt-1 flex flex-wrap gap-1">
                      {unicos.map((b, i) => (
                        <span key={i}
                          className="rounded-md bg-red-50 px-2 py-1 text-[11px] text-red-800 dark:bg-red-900/20 dark:text-red-300">
                          <span className="font-semibold">{texto(b?.bloque, 'bloque ?')}</span> · solo {texto(b?.unicoMaestro, 'sin nombre')}
                        </span>
                      ))}
                    </div>
                  )}
                  {dos.length > 0 && (
                    <div className="mt-1 flex flex-wrap gap-1">
                      {dos.slice(0, BLOQUES_DOS_VISIBLES).map((b, i) => (
                        <span key={i}
                          className="rounded-md bg-amber-50 px-2 py-1 text-[11px] text-amber-800 dark:bg-amber-900/20 dark:text-amber-300">
                          {texto(b?.bloque, 'bloque ?')} · 2 maestros
                        </span>
                      ))}
                      {dos.length > BLOQUES_DOS_VISIBLES && (
                        <span className="px-2 py-1 text-[11px] text-gray-500 dark:text-gray-400">
                          y {dos.length - BLOQUES_DOS_VISIBLES} bloque(s) más con 2
                        </span>
                      )}
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        )}
      </div>

    </div>
  );
};

/** El turno se resuelve con la lista que ya tiene la pantalla: una consulta menos. */
const nombreDeTurno = (turnos: Turno[], turnoId: number | null): string => {
  if (!turnoId) return '—';
  const t = turnos.find(x => x.id === turnoId);
  return t ? t.nombre : `turno ${turnoId}`;
};

/**
 * Confirmación en modal para aplicar o borrar una corrida.
 *
 * <p>Antes era un window.confirm: el diálogo nativo del navegador rompía la estética (y no respeta el
 * modo oscuro), mientras el resto de la aplicación usa modales como este. Mismo estilo que el de
 * Especialidades/Aulas/Maestros, que es el patrón que sigue toda la app.
 */
const ModalConfirmarCorrida: React.FC<{
  corrida: CorridaIA;
  accion: 'aplicar' | 'borrar';
  ocupado: boolean;
  turnos: Turno[];
  onConfirmar: () => void;
  onCancelar: () => void;
}> = ({ corrida, accion, ocupado, turnos, onConfirmar, onCancelar }) => {
  const esAplicar = accion === 'aplicar';
  return (
    <div className="fixed inset-0 z-50 flex items-start justify-center bg-black/50 backdrop-blur-sm p-4 pt-24">
      <div className="bg-white dark:bg-gray-800 rounded-xl shadow-2xl max-w-md w-full p-6 border border-gray-400 dark:border-gray-700">
        <div className="flex items-center gap-3 mb-4">
          <div className="p-2 bg-red-100 dark:bg-red-900/40 rounded-lg text-red-600 dark:text-red-400">
            <MdWarning className="text-2xl" />
          </div>
          <h3 className="text-lg font-bold text-gray-800 dark:text-white">
            {esAplicar ? 'Aplicar al horario vigente' : 'Confirmar eliminación'}
          </h3>
        </div>

        <p className="text-sm text-gray-600 dark:text-gray-400 mb-4">
          {esAplicar
            ? 'Se REEMPLAZA el horario actual de los grupos de ese turno y semestre por esta corrida.'
            : '¿Estás seguro de que quieres borrar esta corrida de la lista de opciones?'}
        </p>

        <div className="bg-gray-50 dark:bg-gray-700/50 rounded-lg p-3 mb-4 text-sm">
          <div className="flex items-center gap-3 mb-2">
            <div className="w-10 h-10 rounded-lg bg-indigo-100 dark:bg-indigo-900/40 flex items-center justify-center text-indigo-600 dark:text-indigo-400">
              <MdBookmarkAdd className="text-lg" />
            </div>
            <div>
              <div className="font-medium text-gray-800 dark:text-white">{corrida.nombre}</div>
              <div className="text-xs text-gray-500 dark:text-gray-400">
                {numeroSeguro(corrida.horas)} h · {numeroSeguro(corrida.filasGuardadas)} bloques
              </div>
            </div>
          </div>
          <div className="flex justify-between mt-2">
            <span className="text-gray-500 dark:text-gray-400">Turno:</span>
            <span className="font-medium text-gray-800 dark:text-white">
              {nombreDeTurno(turnos, corrida.turnoId)}
            </span>
          </div>
        </div>

        <p className="text-xs text-red-600 dark:text-red-400 mb-4">
          {esAplicar
            ? '⚠️ El horario que hay ahora en esos grupos se pierde.'
            : '⚠️ Esta acción no se puede deshacer. El horario vigente no se toca.'}
        </p>

        <div className="flex gap-3">
          <button
            onClick={onCancelar}
            disabled={ocupado}
            className="flex-1 px-4 py-2.5 border border-gray-400 dark:border-gray-600 rounded-lg text-gray-700 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700 transition disabled:opacity-50"
          >
            Cancelar
          </button>
          <button
            onClick={onConfirmar}
            disabled={ocupado}
            className={`flex-1 px-4 py-2.5 text-white rounded-lg font-medium transition disabled:opacity-50 flex items-center justify-center gap-2 ${esAplicar
              ? 'bg-emerald-600 hover:bg-emerald-700'
              : 'bg-red-600 hover:bg-red-700'}`}
          >
            {ocupado ? (
              <>
                <div className="animate-spin rounded-full h-4 w-4 border-2 border-white border-t-transparent" />
                {esAplicar ? 'Aplicando...' : 'Borrando...'}
              </>
            ) : (
              <>
                {esAplicar ? <MdDoneAll className="text-lg" /> : <MdDelete className="text-lg" />}
                {esAplicar ? 'Aplicar' : 'Eliminar'}
              </>
            )}
          </button>
        </div>
      </div>
    </div>
  );
};

/**
 * CORRIDAS GUARDADAS: las opciones apartadas, con sus métricas, para poder compararlas.
 *
 * Guardar una corrida NO toca el horario; solo "Aplicar" lo reescribe (con confirmación, porque es
 * destructivo).
 *
 * La tabla NO marca cuál es la mejor, y es a propósito: la regla del motor es "sin problemas > más
 * horas > menos pendientes > mejor score" (HorarioIAServicio.comparar). Duplicarla aquí solo serviría
 * para que las dos versiones se separaran con el tiempo. En su lugar se enseñan los números y se
 * explica el criterio, que es lo que permite decidir con conocimiento.
 */
const ListaCorridas: React.FC<{
  corridas: CorridaIA[];
  cargando: boolean;
  aplicando: number | null;
  borrando: number | null;
  /** Mensaje de la última acción sobre la lista (aplicar o borrar). Va aquí, junto a los botones. */
  mensaje: MensajeAccion | null;
  turnos: Turno[];
  onAplicar: (corrida: CorridaIA) => void;
  onBorrar: (corrida: CorridaIA) => void;
  onRefrescar: () => void;
}> = ({ corridas, cargando, aplicando, borrando, mensaje, turnos, onAplicar, onBorrar, onRefrescar }) => {

  return (
    <div className="rounded-lg border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-800">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h2 className="flex items-center gap-2 text-lg font-semibold text-gray-900 dark:text-gray-100">
          <MdBookmarkAdd className="text-indigo-600" /> Corridas guardadas
          {corridas.length > 0 && (
            <span className="rounded-full bg-gray-100 px-2 py-0.5 text-xs font-normal text-gray-600 dark:bg-gray-700 dark:text-gray-300">
              {corridas.length}
            </span>
          )}
        </h2>
        <button
          onClick={onRefrescar}
          disabled={cargando}
          className="rounded-md border border-gray-300 px-2 py-1 text-xs text-gray-700 hover:bg-gray-50 disabled:opacity-50 dark:border-gray-600 dark:text-gray-200 dark:hover:bg-gray-700"
        >
          {cargando ? 'Cargando…' : 'Actualizar'}
        </button>
      </div>

      <p className="mt-1 text-xs text-gray-500 dark:text-gray-400">
        Son opciones apartadas: no afectan al horario hasta que pulsas Aplicar. El motor considera mejor
        la corrida sin problemas, con más horas colocadas, con menos pendientes y, a igualdad, mejor
        score (medium).
      </p>

      <AvisoAccion mensaje={mensaje} />

      {corridas.length === 0 ? (
        <p className="mt-3 rounded-md border border-dashed border-gray-300 p-4 text-sm text-gray-500 dark:border-gray-600 dark:text-gray-400">
          Todavía no has guardado ninguna corrida. Genera, y en el intento que te interese pulsa
          «Guardar corrida» y ponle un nombre.
        </p>
      ) : (
        <div className="mt-3 overflow-x-auto">
          <table className="min-w-full text-xs">
            <thead>
              <tr className="border-b border-gray-200 text-left text-gray-500 dark:border-gray-600 dark:text-gray-400">
                <th className="px-2 py-1 font-medium">Corrida</th>
                <th className="px-2 py-1 font-medium">Turno</th>
                <th className="px-2 py-1 text-right font-medium">Horas</th>
                <th className="px-2 py-1 text-right font-medium">Pendientes</th>
                <th className="px-2 py-1 text-right font-medium">Huecos</th>
                <th className="px-2 py-1 text-right font-medium">Adyacencias</th>
                <th className="px-2 py-1 text-right font-medium">Materias</th>
                <th className="px-2 py-1 text-right font-medium">Score</th>
                <th className="px-2 py-1 text-right font-medium">Tiempo</th>
                <th className="px-2 py-1" />
              </tr>
            </thead>
            <tbody>
              {corridas.map(c => (
                <tr key={c.id} className="border-b border-gray-100 last:border-0 dark:border-gray-700">
                  <td className="px-2 py-2">
                    <span className="font-medium text-gray-900 dark:text-gray-100">{c.nombre}</span>
                    <span className="block text-[11px] text-gray-500 dark:text-gray-400">
                      {texto(c.creadoPor, '—')} · {texto(c.asesor, '—')}
                    </span>
                    {c.notas && (
                      <span className="block text-[11px] text-gray-500 dark:text-gray-400">{c.notas}</span>
                    )}
                  </td>
                  <td className="px-2 py-2 text-gray-700 dark:text-gray-300">{nombreDeTurno(turnos, c.turnoId)}</td>
                  <td className="px-2 py-2 text-right text-gray-700 dark:text-gray-300">
                    {numeroSeguro(c.horas)}/{numeroSeguro(c.horasDemandadas)}
                  </td>
                  <td className={`px-2 py-2 text-right ${numeroSeguro(c.totalPendientes) > 0
                    ? 'font-medium text-amber-700 dark:text-amber-400'
                    : 'text-gray-700 dark:text-gray-300'}`}>
                    {numeroSeguro(c.totalPendientes)}
                  </td>
                  <td className="px-2 py-2 text-right text-gray-700 dark:text-gray-300">
                    {numeroSeguro(c.castigoHuecos)}
                  </td>
                  <td className="px-2 py-2 text-right text-gray-700 dark:text-gray-300">
                    {numeroSeguro(c.adyacencias)}
                  </td>
                  <td className="px-2 py-2 text-right text-gray-700 dark:text-gray-300">
                    {numeroSeguro(c.materiasCompletas)}/{numeroSeguro(c.materiasTotales)}
                  </td>
                  <td className="px-2 py-2 text-right text-gray-700 dark:text-gray-300">
                    {numeroSeguro(c.medium)}
                  </td>
                  <td className="px-2 py-2 text-right text-gray-700 dark:text-gray-300">
                    {(numeroSeguro(c.milisegundos) / 1000).toFixed(1)} s
                  </td>
                  <td className="px-2 py-2 text-right">
                    <div className="flex items-center justify-end gap-1">
                      {c.aplicable ? (
                        <button
                          onClick={() => onAplicar(c)}
                          disabled={aplicando === c.id}
                          className="inline-flex items-center gap-1 rounded-md bg-emerald-600 px-2 py-1 text-xs font-medium text-white hover:bg-emerald-700 disabled:opacity-50"
                        >
                          <MdDoneAll /> {aplicando === c.id ? 'Aplicando…' : 'Aplicar'}
                        </button>
                      ) : (
                        <span className="inline-flex items-center gap-1 rounded-md bg-gray-200 px-2 py-1 text-xs text-gray-600 dark:bg-gray-700 dark:text-gray-300">
                          <MdError /> No aplicable
                        </span>
                      )}
                      <button
                        onClick={() => onBorrar(c)}
                        disabled={borrando === c.id}
                        title="Borrar de la lista (no toca el horario)"
                        className="inline-flex items-center rounded-md border border-red-300 px-2 py-1 text-xs text-red-700 hover:bg-red-50 disabled:opacity-50 dark:border-red-800 dark:text-red-400 dark:hover:bg-red-900/20"
                      >
                        <MdDelete />
                      </button>
                    </div>
                    {/* Aviso corto bajo los botones: si no se puede aplicar, por que; y si el detalle
                        quedo corto, cuanto falta. Asi no hay que adivinar. */}
                    {!c.aplicable && c.motivoNoAplicable && (
                      <span className="mt-1 block max-w-[18rem] text-[11px] text-red-700 dark:text-red-400">
                        {c.motivoNoAplicable}
                      </span>
                    )}
                    {c.aplicable && numeroSeguro(c.filasGuardadas) !== numeroSeguro(c.totalFilas) && (
                      <span className="mt-1 block text-[11px] text-amber-700 dark:text-amber-400">
                        Guardados {numeroSeguro(c.filasGuardadas)} de {numeroSeguro(c.totalFilas)} bloques
                      </span>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
};

/** Mensaje del backend si lo trae; si no, uno genérico. */
function mensajeError(e: unknown, porDefecto: string): string {
  const err = e as { response?: { data?: { message?: string; error?: string } } };
  return err?.response?.data?.message || err?.response?.data?.error || porDefecto;
}

export default HorarioIA;
