import React, { useEffect, useMemo, useState } from 'react';
import { turnoService } from '../api/turnoService';
import { materiaService } from '../api/materiaService';
import { horarioService } from '../api/horarioService';
import { useAuth } from '../context/AuthContext';
import type { Horario, Materia, Turno } from '../types';
import { MdPictureAsPdf, MdGridOn } from 'react-icons/md';
import jsPDF from 'jspdf';
import autoTable from 'jspdf-autotable';
import * as XLSX from 'xlsx';

/**
 * REPORTE DE MAESTROS POR ESPECIALIDAD Y GRADO.
 *
 * Por cada especialidad y, dentro de ella, por cada grado de semestre, una tabla:
 *
 *     Especialidad 1
 *     Grado de Semestre 1
 *     ------------------------------------------------------------------
 *     Materia              | horas | 1°A     | 1°B    | 1°C
 *     ------------------------------------------------------------------
 *     Nombre materia 1     |   6   | Carlos  | Manuel | Julio
 *
 * Las columnas de grupo son las de ESE grado (1-3 según la escuela), no las del
 * plantel entero, así que la tabla sale ancha pero legible.
 *
 * De dónde sale cada dato:
 *   - Todo del horario vigente (version = 1): cada fila ya trae turno, especialidad, grado y el
 *     nombre del grupo, porque son columnas copiadas del grupo (db/11). Así el reporte no depende
 *     de cruzar con la lista de grupos.
 *   - El apodo del maestro también viene resuelto del backend.
 *   - Horas: de la MATERIA (materias.horasSemana), no de la asignación. Se eligió así porque es un
 *     valor por materia y la tabla tiene una sola columna de horas; asignacion.horas cambia de un
 *     grupo a otro (en esta escuela, TICS va de 2 a 4).
 *
 * Salidas: pantalla (una tarjeta por especialidad), PDF con corte de hoja por
 * especialidad y Excel con una hoja por especialidad. Mismo patrón que el reporte
 * "Horario por grupo".
 *
 * Sustituye al reporte "Materias x Especialidad": daba los mismos datos pero en
 * vertical y sin agrupar por grado. Ese reporte ya no existe y la migración 10 apaga
 * su entrada del menú.
 */

/** Nombre de la especialidad del grupo. La API la manda como TEXTO; se toleran las dos formas. */
const SIN_ESPECIALIDAD = '(sin especialidad)';

/** Una fila del bloque: una materia, con el apodo que le toca en cada grupo. */
interface FilaBloque {
  clave: string;
  materia: string;
  horas: number;
  /** grupoId -> apodo(s). Vacío = esa materia no se da en ese grupo. */
  maestros: Record<number, string>;
}

/** Un bloque = un grado dentro de una especialidad. */
interface BloqueGrado {
  grado: number;
  grupos: { id: number; nombre: string }[];
  filas: FilaBloque[];
}

/** Una sección = una especialidad, con sus grados. */
interface Seccion {
  nombre: string;
  bloques: BloqueGrado[];
}

/** Nombre de hoja de Excel válido: máximo 31 caracteres y sin caracteres prohibidos. */
const nombreHoja = (texto: string): string =>
  (texto || 'Especialidad').slice(0, 31).replace(/[\\/?*[\]:]/g, '');

const ReporteMaestrosPorEspecialidad: React.FC = () => {
  const { semestreActivo } = useAuth();
  const [turnos, setTurnos] = useState<Turno[]>([]);
  const [materias, setMaterias] = useState<Materia[]>([]);
  const [horarios, setHorarios] = useState<Horario[]>([]);
  const [turnoSeleccionado, setTurnoSeleccionado] = useState<number>(0);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!semestreActivo?.id) return;
    (async () => {
      setLoading(true);
      try {
        const [t, m, h] = await Promise.all([
          turnoService.listar(0, 100, '', semestreActivo.id),
          materiaService.listar(0, 500, '', semestreActivo.id),
          horarioService.obtenerTodos(semestreActivo.id),
        ]);
        setTurnos(t.data.content.filter((x: Turno) => x.activo === true));
        setMaterias(m.data.content);
        setHorarios(h.data);
      } catch {
        // sin datos no se dibuja nada
      } finally {
        setLoading(false);
      }
    })();
  }, [semestreActivo?.id]);

  /** Hasta que no se elija un turno no se habilitan las exportaciones (igual que el otro reporte). */
  const turnoListo = turnoSeleccionado > 0;

  /**
   * Arma la jerarquía completa: especialidad -> grado -> (materia x grupos).
   * Todo sale del horario vigente: cada fila ya trae turno, especialidad, grado y el nombre del
   * grupo (columnas copiadas, db/11), así que no hace falta cruzar con la lista de grupos.
   */
  const secciones = useMemo<Seccion[]>(() => {
    const horasDe = new Map<string, number>();
    for (const m of materias) horasDe.set(m.clave, m.horasSemana);

    // especialidad -> grado -> { grupos y filas }. Los grupos salen de las propias filas: son las
    // columnas que tendrá la tabla de ese bloque.
    type Bloque = { grupos: Map<number, string>; filas: Map<string, FilaBloque> };
    const arbol = new Map<string, Map<number, Bloque>>();

    for (const h of horarios) {
      // /horarios/todos devuelve todas las versiones: solo vale la vigente.
      if (h.version !== 1) continue;
      if (turnoSeleccionado !== 0 && h.turnoId !== turnoSeleccionado) continue;

      const esp = (h.especialidadNombre ?? '').trim() || SIN_ESPECIALIDAD;
      const grado = h.grado ?? 0;

      let porGrado = arbol.get(esp);
      if (!porGrado) {
        porGrado = new Map<number, Bloque>();
        arbol.set(esp, porGrado);
      }
      let bloque = porGrado.get(grado);
      if (!bloque) {
        bloque = { grupos: new Map(), filas: new Map() };
        porGrado.set(grado, bloque);
      }
      bloque.grupos.set(h.grupoId, h.grupoNombre);

      let fila = bloque.filas.get(h.materiaClave);
      if (!fila) {
        fila = {
          clave: h.materiaClave,
          materia: h.materiaNombre,
          horas: horasDe.get(h.materiaClave) ?? 0,
          maestros: {},
        };
        bloque.filas.set(h.materiaClave, fila);
      }
      // Una materia repartida entre dos maestros en el mismo grupo: los dos nombres, separados.
      const previo = fila.maestros[h.grupoId];
      fila.maestros[h.grupoId] = !previo
        ? h.maestroNombre
        : previo.includes(h.maestroNombre)
          ? previo
          : `${previo}, ${h.maestroNombre}`;
    }

    // Se pasa a la forma que espera el render: grupos por nombre y materias por clave.
    const salida: Seccion[] = [];
    for (const [esp, porGrado] of [...arbol.entries()].sort((a, b) => a[0].localeCompare(b[0]))) {
      const bloques: BloqueGrado[] = [];
      for (const [grado, bloque] of [...porGrado.entries()].sort((a, b) => a[0] - b[0])) {
        const grupos = [...bloque.grupos.entries()]
          .map(([id, nombre]) => ({ id, nombre }))
          .sort((a, b) => a.nombre.localeCompare(b.nombre));
        const filas = [...bloque.filas.values()].sort((a, b) => a.clave.localeCompare(b.clave));
        if (filas.length > 0) bloques.push({ grado, grupos, filas });
      }
      if (bloques.length > 0) salida.push({ nombre: esp, bloques });
    }
    return salida;
  }, [horarios, materias, turnoSeleccionado]);

  /** Encabezado común de un bloque: Materia, Horas y un campo por grupo. */
  const encabezado = (b: BloqueGrado): string[] => ['Materia', 'Horas', ...b.grupos.map(g => g.nombre)];

  /** Un renglón de datos de un bloque. */
  const renglon = (f: FilaBloque, b: BloqueGrado): string[] => [
    f.materia,
    String(f.horas),
    ...b.grupos.map(g => f.maestros[g.id] ?? ''),
  ];

  const exportarPDF = () => {
    if (!turnoListo) return;
    const doc = new jsPDF({ orientation: 'landscape' });
    const altoPagina = doc.internal.pageSize.getHeight();

    secciones.forEach((sec, idx) => {
      // Corte de hoja por especialidad: cada una empieza página nueva.
      if (idx > 0) doc.addPage();

      doc.setFontSize(14);
      doc.setFont('helvetica', 'bold');
      doc.setTextColor(0, 0, 0);
      doc.text('ESPECIALIDAD: ' + sec.nombre, 14, 18);

      let y = 26;
      for (const b of sec.bloques) {
        if (y > altoPagina - 40) {
          doc.addPage();
          y = 20;
        }
        doc.setFontSize(11);
        doc.setFont('helvetica', 'bold');
        doc.setTextColor(60, 60, 90);
        doc.text('Grado de Semestre ' + b.grado, 14, y);
        doc.setFont('helvetica', 'normal');
        doc.setTextColor(0, 0, 0);

        autoTable(doc, {
          startY: y + 3,
          head: [encabezado(b)],
          body: b.filas.map(f => renglon(f, b)),
          styles: { fontSize: 8, cellPadding: 2 },
          headStyles: { fillColor: [30, 64, 175], halign: 'center' },
          columnStyles: { 0: { cellWidth: 80 }, 1: { cellWidth: 16, halign: 'center' } },
        });
        y = (doc as any).lastAutoTable.finalY + 14;
      }
    });

    doc.save(`maestros_especialidad_${semestreActivo?.nombre ?? 'semestre'}.pdf`);
  };

  const exportarExcel = () => {
    if (!turnoListo) return;
    const wb = XLSX.utils.book_new();

    for (const sec of secciones) {
      const aoa: (string | number)[][] = [['ESPECIALIDAD: ' + sec.nombre], []];
      let anchoMax = 0;

      for (const b of sec.bloques) {
        aoa.push(['Grado de Semestre ' + b.grado]);
        aoa.push(encabezado(b) as (string | number)[]);
        for (const f of b.filas) aoa.push(renglon(f, b));
        aoa.push([]);
        anchoMax = Math.max(anchoMax, b.grupos.length);
      }

      const hoja = XLSX.utils.aoa_to_sheet(aoa);
      const cols = [{ wch: 46 }, { wch: 8 }];
      for (let i = 0; i < anchoMax; i++) cols.push({ wch: 18 });
      hoja['!cols'] = cols;
      XLSX.utils.book_append_sheet(wb, hoja, nombreHoja(sec.nombre));
    }

    XLSX.writeFile(wb, `maestros_especialidad_${semestreActivo?.nombre ?? 'semestre'}.xlsx`);
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center p-16">
        <div className="animate-spin rounded-full h-12 w-12 border-4 border-blue-500 border-t-transparent"></div>
      </div>
    );
  }

  return (
    <div className="p-4 md:p-6 space-y-6">
      {/* ── controles (no salen al imprimir) ── */}
      <div className="no-print bg-white dark:bg-gray-800 rounded-xl shadow-md p-4 mb-6 border border-gray-400 dark:border-gray-700">
        <div className="flex flex-wrap items-end gap-4">
          <div>
            <h1 className="text-2xl font-bold text-gray-900 dark:text-gray-100">Maestros por especialidad</h1>
            <p className="text-sm text-gray-500 dark:text-gray-400">
              Por especialidad y grado: materia, horas y quién la da en cada grupo · {semestreActivo?.nombre}
            </p>
          </div>
          <label className="flex flex-col text-sm text-gray-600 dark:text-gray-300">
            <span className="mb-1 font-medium">Turno</span>
            <select
              value={turnoSeleccionado}
              onChange={e => setTurnoSeleccionado(Number(e.target.value))}
              className="px-4 py-2.5 border border-gray-400 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-blue-500"
            >
              <option value={0}>Todos</option>
              {turnos.map(t => (
                <option key={t.id} value={t.id}>{t.nombre}</option>
              ))}
            </select>
          </label>
          <div className="flex items-center gap-2 ml-auto">
            {!turnoListo && (
              <span className="text-xs text-amber-700 dark:text-amber-400">
                Selecciona un turno para exportar.
              </span>
            )}
            <button
              onClick={exportarPDF}
              disabled={!turnoListo}
              className="flex items-center gap-2 bg-red-600 hover:bg-red-700 text-white px-4 py-2.5 rounded-lg shadow-md transition disabled:opacity-50"
            >
              <MdPictureAsPdf /> Guardar PDF
            </button>
            <button
              onClick={exportarExcel}
              disabled={!turnoListo}
              className="flex items-center gap-2 bg-green-600 hover:bg-green-700 text-white px-4 py-2.5 rounded-lg shadow-md transition disabled:opacity-50"
            >
              <MdGridOn /> Exportar Excel
            </button>
          </div>
        </div>
      </div>

      {/* ── una tarjeta por especialidad, con un bloque por grado ── */}
      {secciones.map(sec => (
        <div
          key={sec.nombre}
          className="bg-white dark:bg-gray-800 rounded-xl shadow-md overflow-hidden border border-gray-400 dark:border-gray-700"
        >
          <div className="px-4 py-3 bg-gray-50 dark:bg-gray-700/50 border-b border-gray-400 dark:border-gray-700">
            <h2 className="text-base font-bold uppercase text-gray-800 dark:text-gray-100">
              Especialidad: {sec.nombre}
            </h2>
          </div>

          {sec.bloques.map(b => (
            <div key={`${sec.nombre}-${b.grado}`} className="px-4 py-3 border-b border-gray-200 last:border-0 dark:border-gray-700">
              <h3 className="mb-2 text-sm font-bold text-indigo-700 dark:text-indigo-300">
                Grado de Semestre {b.grado}
              </h3>
              <div className="overflow-x-auto">
                <table className="min-w-full text-sm border-collapse">
                  <thead className="bg-gray-100 dark:bg-gray-700/50">
                    <tr>
                      {encabezado(b).map((t, i) => (
                        <th
                          key={i}
                          className={`px-3 py-2 font-semibold text-gray-700 dark:text-gray-300 border border-gray-400 dark:border-gray-700 ${
                            i === 1 ? 'text-center' : 'text-left'
                          }`}
                        >
                          {t}
                        </th>
                      ))}
                    </tr>
                  </thead>
                  <tbody className="bg-white dark:bg-gray-800 divide-y divide-gray-400 dark:divide-gray-700">
                    {b.filas.map(f => (
                      <tr key={f.clave} className="hover:bg-gray-50 dark:hover:bg-gray-700/50">
                        {renglon(f, b).map((v, i) => (
                          <td
                            key={i}
                            className={`px-3 py-2 border border-gray-400 dark:border-gray-700 ${
                              i === 1 ? 'text-center text-gray-700 dark:text-gray-300' : 'text-gray-800 dark:text-gray-100'
                            }`}
                          >
                            {v}
                          </td>
                        ))}
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          ))}
        </div>
      ))}

      {secciones.length === 0 && (
        <div className="bg-yellow-50 dark:bg-yellow-900/20 border border-yellow-200 dark:border-yellow-800 rounded-lg p-8 text-center text-gray-700 dark:text-gray-300">
          No hay clases en el horario vigente para estos grupos.
        </div>
      )}
    </div>
  );
};

export default ReporteMaestrosPorEspecialidad;
