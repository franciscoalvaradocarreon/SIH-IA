// src/components/HorarioManual.tsx
import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { horarioService } from '../api/horarioService';
import { turnoService } from '../api/turnoService';
import { grupoService } from '../api/grupoService';
import { maestroService } from '../api/maestroService';
import { asignacionService } from '../api/asignacionService';
import { turnoHorarioService } from '../api/turnoHorarioService';
import { disponibilidadService } from '../api/disponibilidadService';
import { useAuth } from '../context/AuthContext';
import type { Asignacion, Grupo, Horario, Maestro, Turno, TurnoHorario } from '../types';
import PinHorario from './PinHorario';
import MarcaGrupo from './MarcaGrupo';
import { identidadGrupo } from '../utils/paletaGrupos';
import {
  MdSchedule, MdPerson, MdRefresh, MdWarning, MdInfo, MdVisibility, MdCheckCircle,
  MdSave, MdUndo, MdCancel,
} from 'react-icons/md';

/**
 * Trae TODA la disponibilidad de maestros del semestre (una vez por carga del tablero).
 * El endpoint pagina y Spring recorta el tamano de pagina, asi que se recorren las paginas
 * hasta juntar `totalElements`. Si la consulta falla se devuelve vacio: el tablero sigue
 * funcionando y simplemente no se sombrea ninguna casilla.
 */
const traerDisponibilidadSemestre = async (semestreId: number): Promise<any[]> => {
  const filas: any[] = [];
  let pagina = 0;
  let total = Number.POSITIVE_INFINITY;
  while (filas.length < total && pagina < 25) {
    const r: any = await disponibilidadService.listar(pagina, 2000, '', semestreId);
    const contenido: any[] = r?.data?.content ?? [];
    total = r?.data?.totalElements ?? contenido.length;
    if (contenido.length === 0) break;
    filas.push(...contenido);
    pagina += 1;
  }
  return filas;
};

/**
 * TABLERO MANUAL DE HORARIOS.
 *
 * Dos vistas del mismo horario:
 *  1. **Maestros × Semana** (por defecto): renglones = maestros (apodo) y columnas = los días con
 *     sus horas de clase (encabezado de dos niveles). La casilla es un hueco concreto
 *     (maestro + día + hora), así que ahí se suelta el pin directamente.
 *  2. **Grupos × Horas**: renglones = horas del turno (por día), columnas = grupos; sirve para ver
 *     el horario de un grupo completo.
 *
 * Arriba está la CAJA con los pines de las clases sin colocar: es el origen y el destino de los
 * pines que se quitan del tablero. Cada pin lleva el color del grupo, la clave de la materia y el
 * grupo al que pertenece.
 *
 * Los cambios viven en un BORRADOR LOCAL: nada se escribe hasta pulsar Guardar, que manda la tanda
 * completa al backend (POST /api/horarios/manual) y este la valida como conjunto. Si algo choca
 * (grupo, maestro, aula, disponibilidad, bloque de otro turno, horas de más) no se aplica ninguno y
 * los problemas se muestran numerados.
 */

const DIAS = [1, 2, 3, 4, 5];
const NOMBRE_DIA: Record<number, string> = {
  1: 'Lunes', 2: 'Martes', 3: 'Miércoles', 4: 'Jueves', 5: 'Viernes',
};

type Vista = 'semana' | 'grupos';

/** Una fila del tablero: un pin colocado (real o del borrador). */
type FilaPin = Horario & { borrador: boolean };

/** Un cambio del tablero; mismo contrato que POST /api/horarios/manual. */
interface CambioManual {
  tipo: 'COLOCAR' | 'MOVER' | 'QUITAR';
  asignacionId?: number;
  horarioId?: number;
  turnoHorarioId?: number;
}

/** Lo que se está arrastrando ahora mismo. */
type Arrastre =
  | { origen: 'caja'; asignacionId: number; grupoId: number; grupoNombre: string; etiqueta: string }
  | { origen: 'tablero'; horarioId: number; grupoId: number; grupoNombre: string; asignacionId: number;
      maestroId: number; bloqueId: number; sintetico: boolean; etiqueta: string };

/** Un cambio pendiente del borrador, con texto para la lista. */
interface CambioBorrador extends CambioManual {
  clave: string;
  grupoId: number;
  grupoNombre: string;
  etiqueta: string;
  detalle: string;
}

interface PinCaja {
  clave: string;
  asignacion: Asignacion;
  numero: number;
  total: number;
}

const HorarioManual: React.FC = () => {
  const { semestreActivo } = useAuth();
  const navigate = useNavigate();

  const [turnos, setTurnos] = useState<Turno[]>([]);
  const [turnoId, setTurnoId] = useState<number>(0);
  const [vista, setVista] = useState<Vista>('semana');

  const [grupos, setGrupos] = useState<Grupo[]>([]);
  const [maestros, setMaestros] = useState<Maestro[]>([]);
  const [horarios, setHorarios] = useState<Horario[]>([]);
  const [asignaciones, setAsignaciones] = useState<Asignacion[]>([]);
  const [bloques, setBloques] = useState<TurnoHorario[]>([]);
  /** Claves "maestroId|turnoHorarioId" donde el maestro marco disponibilidad en el semestre. */
  const [disponiblesMaestro, setDisponiblesMaestro] = useState<Set<string>>(new Set());

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  // ── Borrador local ──
  const [cambios, setCambios] = useState<CambioBorrador[]>([]);
  const [arrastrando, setArrastrando] = useState<Arrastre | null>(null);
  const [celdaSobre, setCeldaSobre] = useState<string | null>(null);
  const [guardando, setGuardando] = useState(false);
  const [mensaje, setMensaje] = useState('');
  /** Caja de pines plegable: arranca cerrada y se abre sola la primera vez que hay pendientes. */
  const [cajaAbierta, setCajaAbierta] = useState(false);
  const cajaAbiertaUnaVez = useRef(false);
  const [aviso, setAviso] = useState('');
  const [erroresGuardado, setErroresGuardado] = useState<string[]>([]);

  // ───────────────────────── carga de datos ─────────────────────────

  useEffect(() => {
    const cargarTurnos = async () => {
      if (!semestreActivo?.id) { setTurnos([]); return; }
      try {
        const res = await turnoService.listar(0, 50, undefined, semestreActivo.id);
        const lista = res.data.content ?? [];
        setTurnos(lista);
        setTurnoId((prev) => (prev && lista.some((t) => t.id === prev) ? prev : lista[0]?.id ?? 0));
      } catch (e: any) {
        console.error('Error al cargar turnos:', e);
        setError('No se pudieron cargar los turnos del semestre.');
      }
    };
    cargarTurnos();
  }, [semestreActivo?.id]);

  const cargarTablero = useCallback(async () => {
    if (!semestreActivo?.id || !turnoId) { setLoading(false); return; }
    setLoading(true);
    setError('');
    try {
      const sid = semestreActivo.id;
      const [rHor, rGru, rMae, rAsi, rBlo, rDisp] = await Promise.all([
        horarioService.obtenerTodos(sid),
        grupoService.listar(0, 300, undefined, undefined, sid, turnoId),
        maestroService.listar(0, 500, undefined, sid, turnoId),
        asignacionService.listar(0, 1000, '', 0, 0, sid, turnoId),
        turnoHorarioService.listar(turnoId, sid),
        // La disponibilidad no debe tumbar el tablero: si falla, se toma como vacia.
        traerDisponibilidadSemestre(sid).catch(() => []),
      ]);
      setHorarios(Array.isArray(rHor.data) ? rHor.data : []);
      setGrupos(rGru.data?.content ?? []);
      setMaestros(rMae.data?.content ?? []);
      setAsignaciones(rAsi.data?.content ?? []);
      // El endpoint de turno-horario responde { total, data, success }: el arreglo va en `data`.
      const b: any = rBlo?.data;
      setBloques(Array.isArray(b) ? b : Array.isArray(b?.data) ? b.data : (b?.content ?? []));

      // Disponibilidad del maestro por bloque: MISMA regla que valida el backend al guardar
      // (fila propia con disponible = true para ese maestro y ese turno_horario).
      const setDisp = new Set<string>();
      (rDisp ?? []).forEach((d: any) => {
        if (d?.disponible) setDisp.add(`${d.maestroId}|${d.turnoHorarioId}`);
      });
      setDisponiblesMaestro(setDisp);
    } catch (e: any) {
      console.error('Error al cargar el tablero:', e);
      setError(e.response?.data?.message || 'No se pudo cargar el tablero manual.');
    } finally {
      setLoading(false);
    }
  }, [semestreActivo?.id, turnoId]);

  useEffect(() => { cargarTablero(); }, [cargarTablero]);

  // Al cambiar de turno el borrador deja de tener sentido.
  useEffect(() => { setCambios([]); setMensaje(''); setErroresGuardado([]); }, [turnoId]);

  // Aviso al cerrar la pestaña con cambios sin guardar.
  useEffect(() => {
    if (cambios.length === 0) return;
    const aviso = (e: BeforeUnloadEvent) => { e.preventDefault(); e.returnValue = ''; };
    window.addEventListener('beforeunload', aviso);
    return () => window.removeEventListener('beforeunload', aviso);
  }, [cambios.length]);

  // ───────────────────────── datos derivados ─────────────────────────

  const gruposTurno = useMemo(
    () => [...grupos].sort((a, b) => (a.grado - b.grado) || a.nombre.localeCompare(b.nombre)),
    [grupos]
  );

  const indiceGrupo = useMemo(() => {
    const m = new Map<number, number>();
    gruposTurno.forEach((g, i) => m.set(g.id, i));
    return m;
  }, [gruposTurno]);

  const identidadDe = useCallback(
    (grupoId: number) => identidadGrupo(indiceGrupo.get(grupoId) ?? 0),
    [indiceGrupo]
  );

  const grupoIds = useMemo(() => new Set(gruposTurno.map((g) => g.id)), [gruposTurno]);

  /** Pines realmente guardados en la base (los del turno mostrado). */
  const pines = useMemo(
    () => horarios.filter((h) => grupoIds.has(h.grupoId)),
    [horarios, grupoIds]
  );

  const bloquesClase = useMemo(() => bloques.filter((b) => !b.descanso), [bloques]);

  const bloquePorId = useMemo(() => {
    const m = new Map<number, TurnoHorario>();
    bloques.forEach((b) => m.set(b.id, b));
    return m;
  }, [bloques]);

  const asignacionPorId = useMemo(() => {
    const m = new Map<number, Asignacion>();
    asignaciones.forEach((a) => m.set(a.id, a));
    return m;
  }, [asignaciones]);

  const bloquesPorDia = useMemo(() => {
    const m = new Map<number, TurnoHorario[]>();
    bloquesClase.forEach((b) => {
      const lista = m.get(b.diaSemana) ?? [];
      lista.push(b);
      m.set(b.diaSemana, lista);
    });
    m.forEach((lista) => lista.sort((a, b) => (a.horaInicio ?? '').localeCompare(b.horaInicio ?? '')));
    return m;
  }, [bloquesClase]);

  const diasConClase = useMemo(
    () => DIAS.filter((d) => bloquesPorDia.has(d)),
    [bloquesPorDia]
  );

  const numeroHora = useMemo(() => {
    const m = new Map<number, number>();
    bloquesPorDia.forEach((lista) => lista.forEach((b, i) => m.set(b.id, i + 1)));
    return m;
  }, [bloquesPorDia]);

  const apodosPorMaestro = useMemo(() => {
    const m = new Map<number, string>();
    maestros.forEach((x) => m.set(x.id, (x.apodo ?? '').trim() || x.nombre || 'Maestro'));
    return m;
  }, [maestros]);

  const nombreCompletoPorMaestro = useMemo(() => {
    const m = new Map<number, string>();
    maestros.forEach((x) => m.set(x.id, x.nombreCompleto || `${x.nombre} ${x.apellidos}`.trim()));
    return m;
  }, [maestros]);

  const apodo = useCallback(
    (maestroId: number) => apodosPorMaestro.get(maestroId) ?? `Maestro ${maestroId}`,
    [apodosPorMaestro]
  );

  const textoBloque = useCallback((bloqueId: number) => {
    const b = bloquePorId.get(bloqueId);
    if (!b) return `bloque ${bloqueId}`;
    return `${(NOMBRE_DIA[b.diaSemana] ?? '').toLowerCase()} ${numeroHora.get(b.id) ?? ''}a`;
  }, [bloquePorId, numeroHora]);

  /**
   * Estado del tablero CON el borrador aplicado: los pines guardados, menos los que se quitan, con
   * los movidos en su bloque nuevo y los colocados nuevos.
   */
  const filas = useMemo(() => {
    const quitados = new Set<number>();
    const destinos = new Map<number, number>();
    const colocados: CambioBorrador[] = [];

    cambios.forEach((c) => {
      if (c.tipo === 'QUITAR' && c.horarioId) {
        quitados.add(c.horarioId);
      } else if (c.tipo === 'MOVER' && c.horarioId && c.turnoHorarioId) {
        destinos.set(c.horarioId, c.turnoHorarioId);
        quitados.delete(c.horarioId);
      } else if (c.tipo === 'COLOCAR' && c.asignacionId && c.turnoHorarioId) {
        colocados.push(c);
      }
    });

    const resultado: FilaPin[] = [];
    pines.forEach((h) => {
      if (quitados.has(h.id)) return;
      const destino = destinos.get(h.id);
      if (destino) {
        const b = bloquePorId.get(destino);
        resultado.push({
          ...h,
          turnoHorarioId: destino,
          diaSemana: b?.diaSemana ?? h.diaSemana,
          horaInicio: b?.horaInicio ?? h.horaInicio,
          horaFin: b?.horaFin ?? h.horaFin,
          aulaNombre: 'por asignar',
          borrador: true,
        });
      } else {
        resultado.push({ ...h, borrador: false });
      }
    });

    colocados.forEach((c) => {
      const a = asignacionPorId.get(c.asignacionId as number);
      const b = bloquePorId.get(c.turnoHorarioId as number);
      if (!a || !b) return;
      resultado.push({
        id: -1 - resultado.length,
        grupoId: a.grupoId,
        grupoNombre: a.grupoNombre,
        asignacionId: a.id,
        materiaNombre: a.materiaNombre,
        materiaClave: a.materiaClave,
        maestroId: a.maestroId,
        maestroNombre: a.maestroNombre,
        turnoHorarioId: b.id,
        diaSemana: b.diaSemana,
        horaInicio: b.horaInicio,
        horaFin: b.horaFin,
        aulaId: 0,
        aulaNombre: 'por asignar',
        version: 1,
        borrador: true,
      });
    });
    return resultado;
  }, [pines, cambios, bloquePorId, asignacionPorId]);

  const colocadasPorAsignacion = useMemo(() => {
    const m = new Map<number, number>();
    filas.forEach((h) => m.set(h.asignacionId, (m.get(h.asignacionId) ?? 0) + 1));
    return m;
  }, [filas]);

  /** Pines de la caja: una entrada por hora que falta, ya con el borrador aplicado. */
  const pinesCaja = useMemo(() => {
    const lista: PinCaja[] = [];
    asignaciones
      .filter((a) => a.activo !== false && grupoIds.has(a.grupoId))
      .forEach((a) => {
        const faltan = Math.max(0, (a.horas ?? 0) - (colocadasPorAsignacion.get(a.id) ?? 0));
        for (let k = 0; k < faltan; k++) {
          lista.push({ clave: `${a.id}-${k}`, asignacion: a, numero: k + 1, total: faltan });
        }
      });
    return lista;
  }, [asignaciones, grupoIds, colocadasPorAsignacion]);

  // La caja se abre sola la primera vez que aparece algún pin pendiente; después respeta lo que
  // decida el usuario. Plegada, el tablero gana ese alto.
  useEffect(() => {
    if (!cajaAbiertaUnaVez.current && pinesCaja.length > 0) {
      setCajaAbierta(true);
      cajaAbiertaUnaVez.current = true;
    }
  }, [pinesCaja.length]);

  const cajaPorGrupo = useMemo(() => {
    const m = new Map<number, { grupoNombre: string; pines: PinCaja[] }>();
    pinesCaja.forEach((p) => {
      const actual = m.get(p.asignacion.grupoId) ?? { grupoNombre: p.asignacion.grupoNombre, pines: [] };
      actual.pines.push(p);
      m.set(p.asignacion.grupoId, actual);
    });
    return [...m.entries()].sort((a, b) => a[1].grupoNombre.localeCompare(b[1].grupoNombre));
  }, [pinesCaja]);

  const colocadasPorGrupo = useMemo(() => {
    const m = new Map<number, number>();
    filas.forEach((h) => m.set(h.grupoId, (m.get(h.grupoId) ?? 0) + 1));
    return m;
  }, [filas]);

  const requeridasPorGrupo = useMemo(() => {
    const m = new Map<number, number>();
    asignaciones
      .filter((a) => a.activo !== false && grupoIds.has(a.grupoId))
      .forEach((a) => m.set(a.grupoId, (m.get(a.grupoId) ?? 0) + (a.horas ?? 0)));
    return m;
  }, [asignaciones, grupoIds]);

  /** Choques detectados en el estado del borrador. */
  const conflictoIds = useMemo(() => {
    const ids = new Set<number>();
    const revisar = (clave: (h: FilaPin) => string) => {
      const m = new Map<string, FilaPin[]>();
      filas.forEach((h) => {
        const k = clave(h);
        if (!k) return;
        const lista = m.get(k) ?? [];
        lista.push(h);
        m.set(k, lista);
      });
      m.forEach((lista) => { if (lista.length > 1) lista.forEach((h) => ids.add(h.id)); });
    };
    revisar((h) => `${h.maestroId}|${h.turnoHorarioId}`);
    revisar((h) => `${h.grupoId}|${h.turnoHorarioId}`);
    revisar((h) => (h.aulaId ? `aula|${h.aulaId}|${h.turnoHorarioId}` : ''));
    return ids;
  }, [filas]);

  /** Rejilla maestros × (día, hora): una casilla por maestro y bloque. */
  /** true si el maestro marco disponibilidad en ese bloque del turno. */
  const maestroDisponible = useCallback(
    (maestroId: number, bloqueId: number) => disponiblesMaestro.has(`${maestroId}|${bloqueId}`),
    [disponiblesMaestro]
  );

  const mapaMaestroBloque = useMemo(() => {
    const m = new Map<string, FilaPin>();
    filas.forEach((h) => {
      const k = `${h.maestroId}|${h.turnoHorarioId}`;
      if (!m.has(k)) m.set(k, h);
    });
    return m;
  }, [filas]);

  /** Rejilla grupo × bloque (vista Grupos × Horas). */
  const mapaCelda = useMemo(() => {
    const m = new Map<string, FilaPin>();
    filas.forEach((h) => {
      const k = `${h.grupoId}-${h.turnoHorarioId}`;
      if (!m.has(k)) m.set(k, h);
    });
    return m;
  }, [filas]);

  const maestrosTurno = useMemo(() => {
    const ids = new Set<number>(maestros.map((m) => m.id));
    filas.forEach((h) => ids.add(h.maestroId));
    const apellidosPorId = new Map<number, string>();
    const activoPorId = new Map<number, boolean>();
    maestros.forEach((m) => {
      apellidosPorId.set(m.id, (m.apellidos ?? '').trim());
      activoPorId.set(m.id, m.activo !== false);
    });
    const lista = [...ids]
      .map((id) => ({
        id,
        apellidos: apellidosPorId.get(id) ?? '',
        apodo: apodo(id),
        nombre: nombreCompletoPorMaestro.get(id) ?? '',
        horas: filas.filter((h) => h.maestroId === id).length,
        inactivo: activoPorId.get(id) === false,
      }))
      // Solo maestros activos. Excepción: un inactivo CON clases se queda, porque ocultarlo
      // escondería pines del tablero (se marca con una etiqueta roja "inactivo").
      .filter((m) => !m.inactivo || m.horas > 0);
    // Orden por APELLIDO, con el nombre como desempate. Hay maestros con el apellido registrado
    // como "." (o vacio): esos se ordenan por su nombre para que no caigan al principio.
    const claveOrden = (m: { apellidos: string; nombre: string; apodo: string }) => {
      const ape = m.apellidos.replace(/^\.$/, '').trim();
      return ape || m.nombre.trim() || m.apodo;
    };
    lista.sort((a, b) => claveOrden(a).localeCompare(claveOrden(b), 'es'));
    return lista;
  }, [maestros, filas, apodo, nombreCompletoPorMaestro]);

  const turnoActual = turnos.find((t) => t.id === turnoId);
  const hayCambios = cambios.length > 0;

  // ───────────────────────── borrador: acciones ─────────────────────────

  const agregarCambio = useCallback((c: Omit<CambioBorrador, 'clave'>) => {
    setMensaje('');
    setErroresGuardado([]);
    setCambios((prev) => {
      let base = prev;
      if (c.tipo === 'MOVER' && c.horarioId) {
        base = prev.filter((x) => !(x.tipo === 'MOVER' && x.horarioId === c.horarioId));
      }
      if (c.tipo === 'COLOCAR' && c.asignacionId && c.turnoHorarioId) {
        base = base.filter((x) => !(x.tipo === 'COLOCAR' && x.asignacionId === c.asignacionId
          && x.turnoHorarioId === c.turnoHorarioId));
      }
      return [...base, { ...c, clave: `${c.tipo}-${c.horarioId ?? c.asignacionId}-${c.turnoHorarioId ?? 'x'}-${prev.length}` }];
    });
  }, []);

  const empezarArrastre = (e: React.DragEvent, dato: Arrastre) => {
    e.dataTransfer.effectAllowed = 'move';
    e.dataTransfer.setData('text/plain', dato.etiqueta);
    setArrastrando(dato);
  };

  const terminarArrastre = () => { setArrastrando(null); setCeldaSobre(null); };

  /** Comprueba que el grupo no tenga ya clase en ese bloque (regla del backend). */
  const grupoOcupado = (grupoId: number, bloqueId: number) => mapaCelda.has(`${grupoId}-${bloqueId}`);

  /** Soltar un pin en una casilla de la vista Maestros × Semana. */
  const soltarEnCasillaMaestro = (e: React.DragEvent, maestroId: number, bloqueId: number) => {
    e.preventDefault();
    const a = arrastrando;
    setCeldaSobre(null);
    setArrastrando(null);
    if (!a) return;

    const grupoId = a.grupoId;
    const maestroDelPin = a.origen === 'caja'
      ? asignacionPorId.get(a.asignacionId)?.maestroId
      : a.maestroId;
    if (maestroDelPin !== maestroId) {
      setAviso(`Ese pin es de ${apodo(maestroDelPin ?? maestroId)}; suéltalo en su renglón.`);
      return;
    }
    if (mapaMaestroBloque.has(`${maestroId}|${bloqueId}`)) {
      setAviso(`${apodo(maestroId)} ya tiene clase el ${textoBloque(bloqueId)}.`);
      return;
    }
    if (grupoOcupado(grupoId, bloqueId)) {
      setAviso(`El grupo ${a.grupoNombre} ya tiene clase el ${textoBloque(bloqueId)}; un grupo no puede estar en dos lugares a la vez.`);
      return;
    }

    if (a.origen === 'caja') {
      agregarCambio({
        tipo: 'COLOCAR', asignacionId: a.asignacionId, turnoHorarioId: bloqueId,
        grupoId, grupoNombre: a.grupoNombre, etiqueta: a.etiqueta,
        detalle: `caja -> ${textoBloque(bloqueId)}`,
      });
      return;
    }
    if (a.sintetico) {
      setCambios((prev) => prev.filter((x) => !(x.tipo === 'COLOCAR' && x.asignacionId === a.asignacionId)));
      agregarCambio({
        tipo: 'COLOCAR', asignacionId: a.asignacionId, turnoHorarioId: bloqueId,
        grupoId, grupoNombre: a.grupoNombre, etiqueta: a.etiqueta,
        detalle: `caja -> ${textoBloque(bloqueId)}`,
      });
      return;
    }
    agregarCambio({
      tipo: 'MOVER', horarioId: a.horarioId, turnoHorarioId: bloqueId,
      grupoId, grupoNombre: a.grupoNombre, etiqueta: a.etiqueta,
      detalle: `${textoBloque(a.bloqueId)} -> ${textoBloque(bloqueId)}`,
    });
  };

  /** Soltar un pin en una casilla de la vista Grupos × Horas. */
  const soltarEnCasillaGrupo = (e: React.DragEvent, grupoId: number, bloqueId: number) => {
    e.preventDefault();
    const a = arrastrando;
    setCeldaSobre(null);
    setArrastrando(null);
    if (!a) return;
    if (a.grupoId !== grupoId) {
      setAviso('Ese pin es de otro grupo; suéltalo en la columna de su grupo.');
      return;
    }
    if (mapaCelda.has(`${grupoId}-${bloqueId}`)) {
      setAviso('Esa casilla ya tiene clase. Quita primero esa clase (arrástrala a la caja) si quieres poner esta.');
      return;
    }

    if (a.origen === 'caja') {
      agregarCambio({
        tipo: 'COLOCAR', asignacionId: a.asignacionId, turnoHorarioId: bloqueId,
        grupoId, grupoNombre: a.grupoNombre, etiqueta: a.etiqueta,
        detalle: `caja -> ${textoBloque(bloqueId)}`,
      });
      return;
    }
    if (a.sintetico) {
      setCambios((prev) => prev.filter((x) => !(x.tipo === 'COLOCAR' && x.asignacionId === a.asignacionId)));
      agregarCambio({
        tipo: 'COLOCAR', asignacionId: a.asignacionId, turnoHorarioId: bloqueId,
        grupoId, grupoNombre: a.grupoNombre, etiqueta: a.etiqueta,
        detalle: `caja -> ${textoBloque(bloqueId)}`,
      });
      return;
    }
    agregarCambio({
      tipo: 'MOVER', horarioId: a.horarioId, turnoHorarioId: bloqueId,
      grupoId, grupoNombre: a.grupoNombre, etiqueta: a.etiqueta,
      detalle: `${textoBloque(a.bloqueId)} -> ${textoBloque(bloqueId)}`,
    });
  };

  /** Soltar un pin en la caja: sale del tablero (o se cancela su colocación del borrador). */
  const soltarEnCaja = (e: React.DragEvent) => {
    e.preventDefault();
    const a = arrastrando;
    setArrastrando(null);
    setCeldaSobre(null);
    if (!a || a.origen !== 'tablero') return;
    if (a.sintetico) {
      setCambios((prev) => prev.filter((x) => !(x.tipo === 'COLOCAR' && x.asignacionId === a.asignacionId)));
      setMensaje('Se descartó esa colocación del borrador.');
      return;
    }
    setCambios((prev) => prev.filter((x) => !(x.tipo === 'MOVER' && x.horarioId === a.horarioId)));
    agregarCambio({
      tipo: 'QUITAR', horarioId: a.horarioId, grupoId: a.grupoId, grupoNombre: a.grupoNombre,
      etiqueta: a.etiqueta, detalle: `${textoBloque(a.bloqueId)} -> caja`,
    });
  };

  const deshacer = () => {
    setCambios((prev) => prev.slice(0, -1));
    setMensaje('');
    setErroresGuardado([]);
  };

  const descartar = () => {
    if (hayCambios && !window.confirm(`¿Descartar ${cambios.length} cambio(s) sin guardar?`)) return;
    setCambios([]);
    setMensaje('');
    setErroresGuardado([]);
  };

  const cancelarYSalir = () => {
    if (hayCambios && !window.confirm(`¿Salir y descartar ${cambios.length} cambio(s) sin guardar?`)) return;
    setCambios([]);
    navigate('/horarios/generador/automatico');
  };

  const enviar = async (soloValidar: boolean) => {
    if (!hayCambios || !semestreActivo?.id) return;
    setGuardando(true);
    setMensaje('');
    setErroresGuardado([]);
    try {
      const res = await horarioService.aplicarCambiosManuales({
        semestreId: semestreActivo.id,
        validarSolo: soloValidar,
        cambios: cambios.map((c) => ({
          tipo: c.tipo,
          asignacionId: c.asignacionId,
          horarioId: c.horarioId,
          turnoHorarioId: c.turnoHorarioId,
        })),
      });
      const r = res.data;
      if (r.aplicado) {
        setCambios([]);
        setMensaje(r.mensaje || 'Cambios aplicados correctamente.');
        await cargarTablero();
      } else {
        setMensaje(r.mensaje || 'No se aplicó ningún cambio.');
        setErroresGuardado(r.errores ?? []);
      }
    } catch (e: any) {
      setErroresGuardado([e.response?.data?.message || 'No se pudo enviar la tanda de cambios.']);
    } finally {
      setGuardando(false);
    }
  };

  // ───────────────────────── render ─────────────────────────

  /**
   * Recorta el nombre de la materia a 2 caracteres, sin puntos suspensivos, para que la celda sea lo
   * más pequeña posible. El nombre completo sigue en el tooltip, así que no se pierde información.
   */
  const recortarMateria = (texto: string | null | undefined) => {
    const t = (texto ?? '').trim();
    return t.slice(0, 7);
  };

  const tituloPin = (h: FilaPin) =>
    `${apodo(h.maestroId)} · ${h.materiaNombre} · grupo ${h.grupoNombre} · `
    + `${NOMBRE_DIA[h.diaSemana] ?? ''} ${numeroHora.get(h.turnoHorarioId) ?? ''}a `
    + `(${h.horaInicio}-${h.horaFin}) · aula ${h.aulaNombre}`
    + (h.borrador ? ' · EN BORRADOR (sin guardar)' : '');

  /** Pin con su etiqueta. En la vista de semana la etiqueta dice el grupo; en la de grupos, el maestro. */
  const pin = (h: FilaPin, modo: Vista, tamano = 20, soloColor = false) => {
    const titulo = tituloPin(h);
    const idg = identidadDe(h.grupoId);
    const materia = recortarMateria(h.materiaClave || h.materiaNombre);
    const principal = modo === 'semana' ? (h.grupoNombre || '') : materia;
    const secundaria = modo === 'semana' ? materia : apodo(h.maestroId);
    return (
      <div
        className={`flex min-w-0 cursor-grab items-center gap-1 active:cursor-grabbing ${
          h.borrador ? 'rounded bg-blue-50 px-0.5 ring-1 ring-blue-300 dark:bg-blue-900/30 dark:ring-blue-700' : ''
        }`}
        title={`${titulo} — arrástralo a otra casilla o a la caja`}
        draggable
        onDragStart={(e) => empezarArrastre(e, {
          origen: 'tablero',
          horarioId: h.id,
          grupoId: h.grupoId,
          grupoNombre: h.grupoNombre,
          asignacionId: h.asignacionId,
          maestroId: h.maestroId,
          bloqueId: h.turnoHorarioId,
          sintetico: h.id < 0,
          etiqueta: `${h.materiaClave || h.materiaNombre} · ${h.grupoNombre}`,
        })}
        onDragEnd={terminarArrastre}
      >
        {soloColor ? (
          <span className="flex w-full items-center justify-center">
            <span className={`inline-flex rounded-[4px] ${conflictoIds.has(h.id) ? 'ring-2 ring-red-600' : ''}`}>
              <MarcaGrupo
                color={idg.color}
                patron={idg.patron}
                tinta={idg.tinta}
                tamano={tamano}
                titulo={titulo}
              />
            </span>
          </span>
        ) : (
          <>
            <PinHorario
              color={idg.color}
              patron={idg.patron}
              tinta={idg.tinta}
              conflicto={conflictoIds.has(h.id)}
              tamano={tamano}
              titulo={titulo}
              className="shrink-0"
            />
            <span className="min-w-0 leading-tight">
              <span className="block truncate text-[9px] font-semibold text-gray-800 dark:text-gray-100">
                {principal}
              </span>
              <span className="block truncate text-[8px] text-gray-500 dark:text-gray-400">
                {secundaria}
              </span>
            </span>
          </>
        )}
      </div>
    );
  };

  const celdaVacia = (clave: string, onDrop: (e: React.DragEvent) => void, extra = '') => {
    const sobre = celdaSobre === clave;
    return (
      <td
        onDragOver={(e) => { if (arrastrando) e.preventDefault(); }}
        onDragEnter={() => { if (arrastrando) setCeldaSobre(clave); }}
        onDragLeave={() => { if (celdaSobre === clave) setCeldaSobre(null); }}
        onDrop={onDrop}
        className={`border-b border-r border-gray-400 p-0.5 align-middle dark:border-gray-700 ${extra} ${
          sobre ? 'bg-blue-50 ring-2 ring-inset ring-blue-400 dark:bg-blue-900/30' : ''
        }`}
      >
        <div className={`h-5 rounded border border-dashed ${
          arrastrando ? 'border-blue-300 dark:border-blue-700' : 'border-gray-400 dark:border-gray-700'
        }`} />
      </td>
    );
  };

  return (
    <div className="mx-auto max-w-[99vw] p-0">
      {/* ── Encabezado ── */}
      <div className="mb-4 rounded-xl border border-gray-400 bg-white p-6 shadow-md dark:border-gray-700 dark:bg-gray-800">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div>
            <h1 className="flex items-center gap-2 text-2xl font-bold text-gray-800 dark:text-white">
              <MdSchedule className="text-blue-600 dark:text-blue-400" />
              Tablero manual de horarios
            </h1>
            <p className="mt-1 text-sm text-gray-500 dark:text-gray-400">
              Renglones = maestros · columnas = días y horas. Cada pin es una hora de clase: arrástralo
              de la caja a un hueco, muévelo de casilla o devuélvelo a la caja. Nada se guarda hasta
              pulsar Guardar.
            </p>
          </div>
          <div className="flex items-center gap-2">
            <span className={`inline-flex items-center gap-1 rounded-full px-3 py-1 text-xs font-medium ${
              hayCambios
                ? 'bg-blue-100 text-blue-800 dark:bg-blue-900/40 dark:text-blue-300'
                : 'bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-300'
            }`}>
              <MdVisibility />
              {hayCambios ? `${cambios.length} cambio(s) sin guardar` : 'Sin cambios pendientes'}
            </span>
            <button
              onClick={cargarTablero}
              className="flex items-center gap-2 rounded-lg bg-blue-600 px-4 py-2 text-white transition hover:bg-blue-700"
            >
              <MdRefresh /> Actualizar
            </button>
          </div>
        </div>

        <div className="mt-4 flex flex-wrap items-center gap-4">
          <label className="flex items-center gap-2 text-sm text-gray-700 dark:text-gray-300">
            <span className="font-medium">Turno:</span>
            <select
              value={turnoId}
              onChange={(e) => setTurnoId(Number(e.target.value))}
              className="rounded-lg border border-gray-400 bg-white px-3 py-2 text-sm text-gray-900 focus:border-transparent focus:outline-none focus:ring-2 focus:ring-blue-500 dark:border-gray-600 dark:bg-gray-700 dark:text-white"
            >
              {turnos.length === 0 && <option value={0}>Sin turnos activos</option>}
              {turnos.map((t) => (
                <option key={t.id} value={t.id}>{t.nombre}</option>
              ))}
            </select>
          </label>

          <div className="flex items-center gap-1 rounded-lg bg-gray-100 p-1 dark:bg-gray-700">
            <button
              onClick={() => setVista('semana')}
              className={`flex items-center gap-1 rounded-md px-3 py-1.5 text-sm font-medium transition ${
                vista === 'semana'
                  ? 'bg-white text-blue-700 shadow dark:bg-gray-800 dark:text-blue-300'
                  : 'text-gray-600 dark:text-gray-300'
              }`}
            >
              <MdPerson /> Maestros × Semana
            </button>
            <button
              onClick={() => setVista('grupos')}
              className={`flex items-center gap-1 rounded-md px-3 py-1.5 text-sm font-medium transition ${
                vista === 'grupos'
                  ? 'bg-white text-blue-700 shadow dark:bg-gray-800 dark:text-blue-300'
                  : 'text-gray-600 dark:text-gray-300'
              }`}
            >
              <MdSchedule /> Grupos × Horas
            </button>
          </div>

          {/* Acciones del borrador, en la misma fila del combo de turno */}
          <div className="ml-auto flex flex-wrap items-center gap-2">
            <button
              onClick={() => enviar(true)}
              disabled={!hayCambios || guardando}
              className="flex items-center gap-2 rounded-lg border border-blue-300 px-4 py-2 text-sm font-medium text-blue-700 transition hover:bg-blue-100 disabled:opacity-50 dark:border-blue-700 dark:text-blue-300 dark:hover:bg-blue-900/30"
            >
              <MdCheckCircle /> Comprobar
            </button>
            <button
              onClick={deshacer}
              disabled={!hayCambios || guardando}
              className="flex items-center gap-2 rounded-lg border border-gray-400 px-4 py-2 text-sm font-medium text-gray-700 transition hover:bg-gray-100 disabled:opacity-50 dark:border-gray-600 dark:text-gray-200 dark:hover:bg-gray-700"
            >
              <MdUndo /> Deshacer
            </button>
            <button
              onClick={descartar}
              disabled={!hayCambios || guardando}
              className="flex items-center gap-2 rounded-lg border border-gray-400 px-4 py-2 text-sm font-medium text-gray-700 transition hover:bg-gray-100 disabled:opacity-50 dark:border-gray-600 dark:text-gray-200 dark:hover:bg-gray-700"
            >
              <MdCancel /> Descartar
            </button>
            <button
              onClick={cancelarYSalir}
              className="flex items-center gap-2 rounded-lg border border-gray-400 px-4 py-2 text-sm font-medium text-gray-700 transition hover:bg-gray-100 dark:border-gray-600 dark:text-gray-200 dark:hover:bg-gray-700"
            >
              Salir
            </button>
            <button
              onClick={() => enviar(false)}
              disabled={!hayCambios || guardando}
              className="flex items-center gap-2 rounded-lg bg-green-600 px-5 py-2 text-sm font-semibold text-white transition hover:bg-green-700 disabled:opacity-50"
            >
              <MdSave /> {guardando ? 'Guardando...' : 'Guardar'}
            </button>
          </div>
        </div>



        {hayCambios && (
          <ul className="mt-3 flex flex-wrap gap-2">
            {cambios.map((c, i) => (
              <li
                key={c.clave}
                className="flex items-center gap-1.5 rounded-md bg-white px-2 py-1 text-[11px] text-gray-700 shadow-sm dark:bg-gray-800 dark:text-gray-200"
                title={`${c.tipo} · ${c.etiqueta} · ${c.detalle}`}
              >
                <span className={`font-bold ${
                  c.tipo === 'QUITAR' ? 'text-red-600 dark:text-red-400'
                    : c.tipo === 'MOVER' ? 'text-amber-600 dark:text-amber-400'
                    : 'text-green-600 dark:text-green-400'
                }`}>
                  {c.tipo}
                </span>
                <span>{c.etiqueta}</span>
                <span className="text-gray-400">{c.detalle}</span>
                <button
                  onClick={() => setCambios((prev) => prev.filter((_, k) => k !== i))}
                  className="text-gray-400 transition hover:text-red-600"
                  title="Quitar este cambio del borrador"
                >
                  ×
                </button>
              </li>
            ))}
          </ul>
        )}

        {mensaje && (
          <p className={`mt-3 text-sm ${erroresGuardado.length > 0 ? 'text-red-700 dark:text-red-400' : 'text-green-700 dark:text-green-400'}`}>
            {mensaje}
          </p>
        )}
        {erroresGuardado.length > 0 && (
          <ul className="mt-2 list-inside list-disc space-y-1 rounded-lg bg-red-50 p-3 text-sm text-red-700 dark:bg-red-900/30 dark:text-red-300">
            {erroresGuardado.map((e, i) => <li key={i}>{e}</li>)}
          </ul>
        )}
        {conflictoIds.size > 0 && (
          <p className="mt-3 flex items-center gap-2 rounded-lg bg-red-50 p-3 text-sm text-red-700 dark:bg-red-900/30 dark:text-red-300">
            <MdWarning /> {conflictoIds.size} pin(es) en choque (mismo maestro, grupo o aula en el
            mismo bloque). Se marcan con un anillo rojo.
          </p>
        )}
      </div>

      {/* ── Caja de pines sin colocar (origen y destino) ── */}
      <div
        onDragOver={(e) => { if (arrastrando?.origen === 'tablero') e.preventDefault(); }}
        onDrop={soltarEnCaja}
        className={`mb-4 rounded-xl border-2 border-dashed p-2 transition ${
          // La caja se queda PEGADA arriba SIEMPRE, tenga pines o no.
          //
          // Antes solo se pegaba cuando habia pines pendientes, y eso obligaba a subir hasta
          // arriba para soltar algo en ella: trabajando en los ultimos renglones, la caja se
          // quedaba fuera de la pantalla y no habia donde soltar. La caja es ORIGEN y DESTINO de
          // los arrastres, no solo un aviso, asi que tiene que estar siempre a mano.
          //
          // El precio es que ocupa su franja mientras se recorre el tablero, tambien vacia; si
          // molesta, el boton de plegar la deja en una sola linea.
          //
          // Dos detalles que hacen falta para que se vea bien al quedar encima:
          //  - los fondos son OPACOS: translúcidos dejarían ver las casillas por detrás;
          //  - z-[45] queda por encima del encabezado de la tabla (z-40) y por debajo de los
          //    modales (z-50).
          'sticky top-0 z-[45] shadow-lg'
        } ${
          arrastrando?.origen === 'tablero'
            ? 'border-red-400 bg-red-50 dark:border-red-600 dark:bg-red-950'
            : 'border-amber-300 bg-amber-50 dark:border-amber-700 dark:bg-amber-950'
        }`}
      >
        <span
          className={`pointer-events-none absolute -top-2 left-3 rounded px-1.5 text-[10px] font-semibold uppercase tracking-wide ${
            arrastrando?.origen === 'tablero'
              ? 'bg-red-50 text-red-700 dark:bg-red-950 dark:text-red-300'
              : 'bg-amber-50 text-amber-800 dark:bg-amber-950 dark:text-amber-300'
          }`}
        >
          Bloque de materias no asignadas
        </span>
        <button
          type="button"
          onClick={() => setCajaAbierta((v) => !v)}
          title={cajaAbierta ? 'Ocultar la caja de pines' : 'Mostrar la caja de pines'}
          className="absolute right-2 top-2 rounded-md border border-amber-300 bg-white/80 px-1.5 py-0.5 text-[10px] font-semibold text-amber-800 transition hover:bg-white dark:border-amber-700 dark:bg-gray-800/80 dark:text-amber-300"
        >
          {cajaAbierta ? 'Ocultar' : `Mostrar (${pinesCaja.length})`}
        </button>
        {!cajaAbierta ? (
          <p className="pr-20 text-xs text-amber-800 dark:text-amber-300">
            {pinesCaja.length === 0
              ? 'Todas las horas del turno están colocadas.'
              : `${pinesCaja.length} sin colocar`}
          </p>
        ) : pinesCaja.length === 0 ? (
          <p className="flex items-center gap-2 pr-20 text-sm text-green-700 dark:text-green-400">
            <MdCheckCircle /> Todas las horas del turno están colocadas.
          </p>
        ) : (
          <div className="flex flex-wrap gap-3">
            {cajaPorGrupo.map(([grupoId, datos]) => (
              <div
                key={grupoId}
                className="rounded-lg border border-gray-400 bg-white p-2 shadow-sm dark:border-gray-600 dark:bg-gray-800"
              >
                <div className="flex flex-wrap items-center gap-1.5">
                  <span className="text-xs font-bold text-gray-700 dark:text-gray-200">{datos.grupoNombre}</span>
                  {datos.pines.map((p) => {
                    const etiqueta = `${p.asignacion.materiaClave || p.asignacion.materiaNombre} · ${p.asignacion.grupoNombre}`;
                    const idg = identidadDe(p.asignacion.grupoId);
                    const titulo = `${apodo(p.asignacion.maestroId)} · ${p.asignacion.materiaNombre} · `
                      + `grupo ${p.asignacion.grupoNombre} · pendiente ${p.numero}/${p.total}`;
                    return (
                      <div
                        key={p.clave}
                        title={`${titulo} — arrástralo a un hueco`}
                        draggable
                        onDragStart={(e) => empezarArrastre(e, {
                          origen: 'caja',
                          asignacionId: p.asignacion.id,
                          grupoId: p.asignacion.grupoId,
                          grupoNombre: p.asignacion.grupoNombre,
                          etiqueta,
                        })}
                        onDragEnd={terminarArrastre}
                        className="flex cursor-grab items-center gap-1 rounded-md bg-amber-50 px-1.5 py-1 active:cursor-grabbing dark:bg-amber-900/20"
                      >
                        <PinHorario
                          color={idg.color}
                          patron={idg.patron}
                          tinta={idg.tinta}
                          estado="pendiente"
                          tamano={16}
                          titulo={titulo}
                        />
                        <span className="leading-tight">
                          <span className="block text-[10px] font-semibold text-gray-800 dark:text-gray-100">
                            {apodo(p.asignacion.maestroId)}
                          </span>
                          <span className="block text-[9px] text-gray-500 dark:text-gray-400">
                            {recortarMateria(p.asignacion.materiaClave || p.asignacion.materiaNombre)}
                            {p.total > 1 ? ` (${p.numero}/${p.total})` : ''}
                          </span>
                        </span>
                      </div>
                    );
                  })}
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {error && (
        <div className="mb-4 rounded-lg border border-red-200 bg-red-50 p-4 text-red-700 dark:border-red-800 dark:bg-red-900/30 dark:text-red-300">
          {error}
        </div>
      )}

      {loading ? (
        <div className="flex justify-center p-12">
          <div className="h-12 w-12 animate-spin rounded-full border-4 border-blue-500 border-t-transparent" />
        </div>
      ) : !turnoId ? (
        <div className="rounded-xl border border-yellow-200 bg-yellow-50 p-6 text-yellow-800 dark:border-yellow-800 dark:bg-yellow-900/20 dark:text-yellow-200">
          <MdWarning className="mb-1 text-2xl" />
          No hay turnos activos en el semestre. Activa uno en Catálogo › Turnos.
        </div>
      ) : gruposTurno.length === 0 ? (
        <div className="rounded-xl border border-yellow-200 bg-yellow-50 p-6 text-yellow-800 dark:border-yellow-800 dark:bg-yellow-900/20 dark:text-yellow-200">
          <MdWarning className="mb-1 text-2xl" />
          El turno {turnoActual?.nombre ?? ''} no tiene grupos activos.
        </div>
      ) : vista === 'semana' ? (
        /* ═══ Vista 1: Maestros × Semana (días y horas en el encabezado) ═══ */
        <div className="overflow-x-auto rounded-xl border border-gray-400 bg-white shadow-md dark:border-gray-700 dark:bg-gray-800">
          <table className="border-separate border-spacing-0 text-left">
            <thead>
              {/* Días */}
              <tr>
                <th
                  rowSpan={2}
                  className="sticky left-0 top-0 z-40 min-w-[120px] border-b border-r border-gray-400 bg-gray-100 px-3 py-2 text-xs font-semibold uppercase tracking-wide text-gray-500 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-300"
                >
                  Maestro
                </th>
                {diasConClase.map((dia) => (
                  <th
                    key={dia}
                    colSpan={(bloquesPorDia.get(dia) ?? []).length}
                        className="sticky top-0 z-30 border-b border-r-2 border-indigo-400 bg-indigo-50 px-2 py-1 text-center text-xs font-bold uppercase tracking-wide text-indigo-700 dark:border-indigo-500 dark:bg-indigo-900/40 dark:text-indigo-200"
                  >
                    {NOMBRE_DIA[dia]}
                  </th>
                ))}
              </tr>
              {/* Horas de cada día */}
              <tr>
                {diasConClase.map((dia) => (
                  <React.Fragment key={dia}>
                    {(bloquesPorDia.get(dia) ?? []).map((b, idx) => (
                      <th
                        key={b.id}
                        title={`${NOMBRE_DIA[dia]} ${idx + 1}a · ${b.horaInicio}-${b.horaFin}`}
                        className={`sticky top-[26px] z-30 min-w-[28px] border-b border-r bg-gray-50 px-1 py-0.5 text-center text-[10px] font-semibold text-gray-600 dark:bg-gray-700/60 dark:text-gray-300 ${
                          idx === (bloquesPorDia.get(dia) ?? []).length - 1
                            ? 'border-r-2 border-indigo-400 dark:border-indigo-500'
                            : 'border-gray-400 dark:border-gray-600'
                        }`}
                      >
                        {idx + 1}
                      </th>
                    ))}
                  </React.Fragment>
                ))}
              </tr>
            </thead>
            <tbody>
              {maestrosTurno.map((m) => (
                <tr key={m.id} className="hover:bg-gray-50 dark:hover:bg-gray-700/30">
                  <th
                    title={`${m.apellidos && m.apellidos !== '.' ? m.apellidos + ', ' : ''}${m.nombre}`}
                    className="sticky left-0 z-20 whitespace-nowrap border-b border-r border-gray-400 bg-white px-3 py-1 text-left dark:border-gray-600 dark:bg-gray-800"
                  >
                    <span className="text-xs font-semibold text-gray-800 dark:text-gray-100">{m.apodo}</span>
                    <span className="ml-2 text-[10px] text-gray-400">{m.horas} h</span>
                    {m.inactivo && (
                      <span className="ml-2 rounded bg-red-100 px-1 text-[9px] font-semibold text-red-700 dark:bg-red-900/40 dark:text-red-300">
                        inactivo
                      </span>
                    )}
                  </th>
                  {diasConClase.map((dia) => (
                    <React.Fragment key={dia}>
                      {(bloquesPorDia.get(dia) ?? []).map((b, idx) => {
                        const clave = `${m.id}|${b.id}`;
                        const h = mapaMaestroBloque.get(clave);
                        if (!h) {
                          return (
                            <React.Fragment key={b.id}>
                              {celdaVacia(`sem-${clave}`, (e) => soltarEnCasillaMaestro(e, m.id, b.id),
                                (idx === (bloquesPorDia.get(dia) ?? []).length - 1 ? 'border-r-2 border-indigo-400 dark:border-indigo-500' : '')
                                  + (maestroDisponible(m.id, b.id) && celdaSobre !== `sem-${clave}`
                                    ? ' bg-emerald-50 dark:bg-emerald-900/25'
                                    : '')
                              )}
                            </React.Fragment>
                          );
                        }
                        const sobre = celdaSobre === `sem-${clave}`;
                        return (
                          <td
                            key={b.id}
                            onDragOver={(e) => { if (arrastrando) e.preventDefault(); }}
                            onDragEnter={() => { if (arrastrando) setCeldaSobre(`sem-${clave}`); }}
                            onDragLeave={() => { if (celdaSobre === `sem-${clave}`) setCeldaSobre(null); }}
                            onDrop={(e) => soltarEnCasillaMaestro(e, m.id, b.id)}
                            className={`border-b border-r border-gray-400 p-0.5 align-middle dark:border-gray-700 ${
                              idx === (bloquesPorDia.get(dia) ?? []).length - 1 ? 'border-r-2 border-indigo-400 dark:border-indigo-500' : ''
                            } ${sobre ? 'bg-blue-50 ring-2 ring-inset ring-blue-400 dark:bg-blue-900/30' : ''}`}
                          >
                            <div className={h.borrador ? 'rounded ring-2 ring-blue-400 ring-offset-1 dark:ring-offset-gray-800' : ''}>
                              {pin(h, 'semana', 20, true)}
                            </div>
                          </td>
                        );
                      })}
                    </React.Fragment>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        /* ═══ Vista 2: Grupos × Horas ═══ */
        <div className="overflow-x-auto rounded-xl border border-gray-400 bg-white shadow-md dark:border-gray-700 dark:bg-gray-800">
          <table className="border-separate border-spacing-0 text-left">
            <thead>
              <tr>
                <th className="sticky left-0 top-0 z-30 border-b border-r border-gray-400 bg-gray-100 px-3 py-2 text-xs font-semibold uppercase tracking-wide text-gray-500 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-300">
                  Hora
                </th>
                {gruposTurno.map((g) => {
                  const requeridas = requeridasPorGrupo.get(g.id) ?? 0;
                  const idg = identidadDe(g.id);
                  const colocadas = colocadasPorGrupo.get(g.id) ?? 0;
                  const faltan = Math.max(0, requeridas - colocadas);
                  return (
                    <th
                      key={g.id}
                      className="sticky top-0 z-20 min-w-[74px] border-b border-r border-gray-400 bg-gray-100 px-2 py-2 text-center dark:border-gray-600 dark:bg-gray-700"
                    >
                      <div className="flex flex-col items-center gap-0.5">
                        <MarcaGrupo
                          color={idg.color}
                          patron={idg.patron}
                          tinta={idg.tinta}
                          tamano={14}
                          titulo={g.nombre}
                        />
                        <span className="text-xs font-bold text-gray-700 dark:text-gray-100">{g.nombre}</span>
                        <span className={`text-[10px] ${faltan > 0 ? 'font-semibold text-red-600 dark:text-red-400' : 'text-gray-500 dark:text-gray-400'}`}>
                          {colocadas}/{requeridas}{faltan > 0 ? ` · faltan ${faltan}` : ''}
                        </span>
                      </div>
                    </th>
                  );
                })}
              </tr>
            </thead>
            <tbody>
              {diasConClase.map((dia) => (
                <React.Fragment key={dia}>
                  <tr>
                    <th
                      colSpan={gruposTurno.length + 1}
                      className="sticky left-0 border-b border-gray-400 bg-indigo-50 px-3 py-1 text-left text-xs font-bold uppercase tracking-wide text-indigo-700 dark:border-gray-600 dark:bg-indigo-900/30 dark:text-indigo-300"
                    >
                      {NOMBRE_DIA[dia]}
                    </th>
                  </tr>
                  {(bloquesPorDia.get(dia) ?? []).map((b, idx) => (
                    <tr key={b.id} className="hover:bg-gray-50 dark:hover:bg-gray-700/30">
                      <th className="sticky left-0 z-10 whitespace-nowrap border-b border-r border-gray-400 bg-white px-3 py-1 text-left dark:border-gray-600 dark:bg-gray-800">
                        <span className="text-xs font-semibold text-gray-700 dark:text-gray-200">{idx + 1}a</span>
                        <span className="ml-2 text-[10px] text-gray-400">{(b.horaInicio ?? '').slice(0, 5)}</span>
                      </th>
                      {gruposTurno.map((g) => {
                        const clave = `${g.id}-${b.id}`;
                        const h = mapaCelda.get(clave);
                        if (!h) {
                          return (
                            <React.Fragment key={g.id}>
                            {celdaVacia(`grp-${clave}`, (e) => soltarEnCasillaGrupo(e, g.id, b.id),
                              idx === (bloquesPorDia.get(dia) ?? []).length - 1 ? 'border-b-2 border-indigo-400 dark:border-indigo-500' : '')}
                            </React.Fragment>
                          );
                        }
                        const sobre = celdaSobre === `grp-${clave}`;
                        return (
                          <td
                            key={g.id}
                            onDragOver={(e) => { if (arrastrando) e.preventDefault(); }}
                            onDragEnter={() => { if (arrastrando) setCeldaSobre(`grp-${clave}`); }}
                            onDragLeave={() => { if (celdaSobre === `grp-${clave}`) setCeldaSobre(null); }}
                            onDrop={(e) => soltarEnCasillaGrupo(e, g.id, b.id)}
                            className={`border-b border-r border-gray-400 p-0.5 align-middle dark:border-gray-700 ${
                              idx === (bloquesPorDia.get(dia) ?? []).length - 1 ? 'border-b-2 border-indigo-400 dark:border-indigo-500' : ''
                            } ${sobre ? 'bg-blue-50 ring-2 ring-inset ring-blue-400 dark:bg-blue-900/30' : ''}`}
                          >
                            <div className={h.borrador ? 'rounded ring-2 ring-blue-400 ring-offset-1 dark:ring-offset-gray-800' : ''}>
                              {pin(h, 'grupos', 20)}
                            </div>
                          </td>
                        );
                      })}
                    </tr>
                  ))}
                </React.Fragment>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* ── Leyenda ── */}
      {/* Aviso emergente: por ejemplo, al intentar mover un pin a un lugar ocupado */}
      {aviso && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4"
          onClick={() => setAviso('')}
        >
          <div
            className="w-full max-w-md rounded-xl bg-white p-5 shadow-xl dark:bg-gray-800"
            onClick={(e) => e.stopPropagation()}
          >
            <h3 className="mb-2 flex items-center gap-2 font-semibold text-gray-800 dark:text-white">
              <MdWarning className="text-amber-500" /> No se puede hacer ese movimiento
            </h3>
            <p className="text-sm text-gray-700 dark:text-gray-200">{aviso}</p>
            <div className="mt-4 flex justify-end">
              <button
                onClick={() => setAviso('')}
                className="rounded-lg bg-blue-600 px-4 py-2 text-sm font-medium text-white transition hover:bg-blue-700"
              >
                Entendido
              </button>
            </div>
          </div>
        </div>
      )}

      {gruposTurno.length > 0 && (
        <div className="mt-4 rounded-xl border border-gray-400 bg-white p-4 shadow-md dark:border-gray-700 dark:bg-gray-800">
          <h3 className="mb-2 text-sm font-semibold text-gray-700 dark:text-gray-200">
            Leyenda (color y patrón, uno por grupo)
          </h3>
          <div className="flex flex-wrap gap-x-4 gap-y-2">
            {gruposTurno.map((g) => {
              const idg = identidadDe(g.id);
              return (
                <span key={g.id} className="flex items-center gap-1.5 text-xs text-gray-600 dark:text-gray-300">
                  <MarcaGrupo
                    color={idg.color}
                    patron={idg.patron}
                    tinta={idg.tinta}
                    tamano={16}
                    titulo={`${g.nombre}: ${idg.nombre}`}
                  />
                  {g.nombre}
                </span>
              );
            })}
          </div>
          <div className="mt-3 flex flex-wrap gap-4 border-t border-gray-400 pt-3 text-xs text-gray-500 dark:border-gray-700 dark:text-gray-400">
            <span className="flex items-center gap-1.5"><PinHorario color="#64748b" tamano={14} /> colocado</span>
            <span className="flex items-center gap-1.5"><PinHorario color="#64748b" estado="pendiente" tamano={14} /> sin colocar (caja)</span>
            <span className="flex items-center gap-1.5"><PinHorario color="#64748b" conflicto tamano={14} /> en choque</span>
            <span className="flex items-center gap-1.5">
              <span className="inline-block h-4 w-4 rounded ring-2 ring-blue-400" /> en borrador (sin guardar)
            </span>
            <span className="flex items-center gap-1.5"><MdInfo /> casilla punteada = hueco libre</span>
            <span className="flex items-center gap-1.5">
              <span className="inline-block h-4 w-4 rounded border border-dashed border-gray-400 bg-emerald-50 dark:border-gray-600 dark:bg-emerald-900/25" />
              verde = el maestro tiene disponibilidad
            </span>
          </div>
          <p className="mt-3 text-xs text-gray-500 dark:text-gray-400">
            En <b>Maestros × Semana</b> la casilla es un maestro en un día y hora concretos: el círculo
            lleva el color y el patrón del grupo y se suelta directamente ahí; la materia, el grupo y el
            aula salen en el tooltip. En <b>Grupos × Horas</b> se
            ve el horario completo de un grupo. Al guardar, el backend valida la tanda completa: si algún
            choque de grupo, maestro o aula, una indisponibilidad o un bloque de otro turno lo impide,
            <b> no se aplica ningún cambio</b> y los problemas se listan arriba.
          </p>
        </div>
      )}
    </div>
  );
};

export default HorarioManual;
