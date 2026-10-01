import React, { useEffect, useMemo, useState } from 'react';
import { turnoService } from '../api/turnoService';
import { horarioService } from '../api/horarioService';
import { useAuth } from '../context/AuthContext';
import type { Horario, Turno } from '../types';
import { MdPictureAsPdf, MdGridOn } from 'react-icons/md';
import jsPDF from 'jspdf';
import autoTable from 'jspdf-autotable';
import * as XLSX from 'xlsx';

/**
 * REPORTE ASIGNACIÓN: QUIÉN DA QUÉ Y A QUIÉN.
 *
 * Una lista plana de las clases que quedaron en el horario vigente, con una fila por
 * (maestro, materia, grupo) y las horas que tiene colocadas. Responde la pregunta "¿cómo quedó
 * repartido el trabajo?", que los otros reportes no contestan: el de Horario por Grupo mira un
 * grupo a la vez y el Cerebro mira una especialidad a la vez.
 *
 * AGRUPAMIENTO POR AULAS (lo que hay que entender): una misma materia de un grupo puede caer en
 * VARIAS aulas. Pasa cuando el motor va en modo "elegir el taller" (reparte entre los talleres de
 * la materia) y también cuando alguien mueve pines a mano. Aquí esas clases se juntan en UN SOLO
 * registro y se suman las horas: este reporte dice quién da qué, no en qué aula. Por eso el aula no
 * es una columna; para el detalle por aula está el reporte Horario por Grupo.
 *
 * De dónde sale cada dato: todo del horario vigente (version = 1). Cada fila ya trae el maestro de
 * la CLASE -no el titular de la asignación: con la bandera de reparto de maestros el motor puede
 * cambiarlo-, la clave y el nombre de la materia, y la especialidad y el grado del grupo, que son
 * columnas copiadas al horario (db/11). El nombre del maestro llega como titulo + nombre +
 * apellidos (Maestro.getTituloNombreCompleto()).
 *
 * Salidas: pantalla, PDF en carta vertical con encabezado en cada hoja y Excel con una hoja.
 */

/** Nombre de la especialidad del grupo. La API la manda como TEXTO; se toleran las dos formas. */
const SIN_ESPECIALIDAD = '(sin especialidad)';

/** Encabezados de la tabla, en un solo lugar: los usan la pantalla, el PDF y el Excel. */
const COLUMNAS = ['Maestro', 'Materia', 'Especialidad', 'Grupo', 'Horas'];

/** Una fila del reporte: un maestro dando una materia a un grupo, con sus horas. */
export interface RenglonAsignacion {
  maestroId: number;
  /** Titulo + nombre + apellidos, tal como lo manda el backend. */
  maestro: string;
  materiaClave: string;
  materia: string;
  especialidad: string;
  grado: number;
  grupo: string;
  /** Horas colocadas en el horario (la suma de TODAS las aulas de esa materia en ese grupo). */
  horas: number;
}

/**
 * Agrupa el horario vigente en renglones (maestro, materia, grupo), sumando las horas de todas
 * las aulas por las que haya pasado esa materia en ese grupo.
 *
 * Es una funcion pura a proposito: la pantalla, el PDF y el Excel la comparten, y las pruebas la
 * llaman directamente sin montar el componente. El filtro de version va aqui dentro para que la
 * funcion sea correcta por si sola: el endpoint devuelve TODAS las versiones del horario.
 */
export const agruparAsignaciones = (horarios: Horario[]): RenglonAsignacion[] => {
  const mapa = new Map<string, RenglonAsignacion>();

  horarios.forEach(h => {
    if (h.version !== 1) return;

    // La clave es (maestro, materia, grupo): el aula NO entra, que es justo lo que agrupa.
    const clave = `${h.maestroId}|${h.materiaClave}|${h.grupoId}`;
    let renglon = mapa.get(clave);
    if (!renglon) {
      renglon = {
        maestroId: h.maestroId,
        maestro: h.maestroNombre,
        materiaClave: h.materiaClave,
        materia: h.materiaNombre,
        especialidad: (h.especialidadNombre ?? '').trim() || SIN_ESPECIALIDAD,
        grado: h.grado ?? 0,
        grupo: h.grupoNombre,
        horas: 0,
      };
      mapa.set(clave, renglon);
    }
    renglon.horas += 1;
  });

  // Orden estable: por maestro, y dentro de cada maestro por materia y grupo. Asi dos
  // exportaciones del mismo horario salen identicas.
  return Array.from(mapa.values()).sort(
    (a, b) =>
      a.maestro.localeCompare(b.maestro) ||
      a.materia.localeCompare(b.materia) ||
      a.grupo.localeCompare(b.grupo),
  );
};

/** Un renglón como arreglo de textos, en el mismo orden que COLUMNAS. */
const renglon = (r: RenglonAsignacion): string[] => [
  r.maestro,
  r.materia,
  r.especialidad,
  r.grupo,
  String(r.horas),
];

const ReporteAsignacion: React.FC = () => {
  const { semestreActivo, escuelaActiva } = useAuth();
  const [turnos, setTurnos] = useState<Turno[]>([]);
  const [horarios, setHorarios] = useState<Horario[]>([]);
  const [turnoSeleccionado, setTurnoSeleccionado] = useState<number>(0);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!semestreActivo?.id) return;
    (async () => {
      setLoading(true);
      try {
        const [t, h] = await Promise.all([
          turnoService.listar(0, 100, '', semestreActivo.id),
          horarioService.obtenerTodos(semestreActivo.id),
        ]);
        setTurnos(t.data.content.filter((x: Turno) => x.activo === true));
        setHorarios(h.data);
      } catch {
        // sin datos no se dibuja nada
      } finally {
        setLoading(false);
      }
    })();
  }, [semestreActivo?.id]);

  /** Hasta que no se elija un turno no se muestra ni se exporta nada (este reporte es por turno). */
  const turnoListo = turnoSeleccionado > 0;

  const renglones = useMemo(() => {
    if (!turnoListo) return [];
    return agruparAsignaciones(horarios.filter(h => h.turnoId === turnoSeleccionado));
  }, [horarios, turnoSeleccionado, turnoListo]);

  const totalHoras = useMemo(() => renglones.reduce((s, r) => s + r.horas, 0), [renglones]);
  const totalMaestros = useMemo(
    () => new Set(renglones.map(r => r.maestroId)).size,
    [renglones],
  );

  const nombreTurno = turnos.find(t => t.id === turnoSeleccionado)?.nombre ?? '';

  const exportarPDF = () => {
    if (!turnoListo) return;
    const doc = new jsPDF({ orientation: 'portrait', format: 'letter' });
    const anchoPagina = doc.internal.pageSize.getWidth();
    // El tipo y el tamaño se fijan ANTES de medir, y no es un detalle: splitTextToSize corta el
    // texto con la fuente que esté puesta en ese momento. Antes salia en TRES lineas porque se
    // medía con la fuente por omisión (16 pt) y el nombre del centro todavía decía
    // "ESTUDIOS ESTUDIOS". Ya corregido el nombre en la base, a 16 pt entra en DOS líneas, así que
    // el encabezado conserva su tamaño original.
    doc.setFont('helvetica', 'bold');
    doc.setFontSize(16);
    const nombreCentro = (escuelaActiva?.nombreLargo || escuelaActiva?.nombre || 'Reporte de Asignación').trim();
    const lineasCentro: string[] = doc.splitTextToSize(nombreCentro, anchoPagina - 80);
    const ySemestre = 13 + lineasCentro.length * 7;
    const yTitulo = ySemestre + 6;
    const yTurno = yTitulo + 6;
    const inicioContenido = yTurno + 7;

    const paginasConEncabezado = new Set<number>();
    // El encabezado se dibuja una vez por hoja (didDrawPage cubre las que cree autoTable solo).
    const dibujarEncabezado = () => {
      const pagina = doc.getCurrentPageInfo().pageNumber;
      if (paginasConEncabezado.has(pagina)) return;
      paginasConEncabezado.add(pagina);

      // Franja blanca: si la tabla llegara hasta arriba, no debe verse por detras del texto.
      doc.setFillColor(255, 255, 255);
      doc.rect(0, 0, anchoPagina, inicioContenido - 3, 'F');

      doc.setTextColor(0, 0, 0);
      doc.setFont('helvetica', 'bold');
      doc.setFontSize(16);
      lineasCentro.forEach((linea, i) => doc.text(linea, anchoPagina / 2, 13 + i * 7, { align: 'center' }));
      doc.setFont('helvetica', 'normal');
      doc.setFontSize(11);
      doc.text(`Semestre ${semestreActivo?.nombre ?? ''}`, anchoPagina / 2, ySemestre, { align: 'center' });
      doc.setFont('helvetica', 'bold');
      doc.setFontSize(12);
      doc.text('Reporte de Asignación', anchoPagina / 2, yTitulo, { align: 'center' });
      doc.setFont('helvetica', 'normal');
      doc.setFontSize(10);
      doc.text(`Turno: ${nombreTurno}`, anchoPagina / 2, yTurno, { align: 'center' });
    };

    dibujarEncabezado();

    autoTable(doc, {
      startY: inicioContenido,
      head: [COLUMNAS],
      body: [
        ...renglones.map(r => renglon(r)),
        // Cierre: el total de horas, como en la pantalla.
        [
          { content: 'Total de horas', colSpan: 4, styles: { halign: 'right' as const, fontStyle: 'bold' as const } },
          { content: String(totalHoras), styles: { fontStyle: 'bold' as const } },
        ],
      ],
      margin: { left: 12, right: 12, top: inicioContenido, bottom: 14 },
      styles: {
        fontSize: 8,
        cellPadding: { top: 1.2, right: 2, bottom: 1.2, left: 2 },
        valign: 'middle',
        lineColor: [0, 0, 0],
        lineWidth: 0.3,
      },
      headStyles: { fillColor: [30, 64, 175], halign: 'center' },
      // Anchos por columna: Maestro y Materia anchos (texto largo), Grupo y Horas estrechos.
      columnStyles: {
        0: { cellWidth: 62, halign: 'left' },
        1: { cellWidth: 58, halign: 'left' },
        2: { cellWidth: 40, halign: 'left' },
        3: { cellWidth: 18, halign: 'center' },
        4: { cellWidth: 12, halign: 'center' },
      },
      didDrawPage: dibujarEncabezado,
    });

    doc.save(`reporte_asignacion_${semestreActivo?.nombre ?? 'semestre'}.pdf`);
  };

  const exportarExcel = () => {
    if (!turnoListo) return;
    const wb = XLSX.utils.book_new();
    const aoa: (string | number)[][] = [
      ['Reporte de Asignación'],
      [`Semestre ${semestreActivo?.nombre ?? ''} · Turno ${nombreTurno}`],
      [],
      COLUMNAS,
      ...renglones.map(r => renglon(r)),
      ['', '', '', 'Total de horas', totalHoras],
    ];
    const hoja = XLSX.utils.aoa_to_sheet(aoa);
    hoja['!cols'] = [{ wch: 46 }, { wch: 46 }, { wch: 30 }, { wch: 12 }, { wch: 8 }];
    XLSX.utils.book_append_sheet(wb, hoja, 'Asignaciones');
    XLSX.writeFile(wb, `reporte_asignacion_${semestreActivo?.nombre ?? 'semestre'}.xlsx`);
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center p-16">
        <div className="animate-spin rounded-full h-12 w-12 border-4 border-blue-500 border-t-transparent" />
      </div>
    );
  }

  return (
    <div className="p-4 md:p-6 space-y-6">
      {/* ── controles (no salen al imprimir) ── */}
      <div className="bg-white dark:bg-gray-800 rounded-xl shadow-md p-4 border border-gray-400 dark:border-gray-700">
        <div className="flex flex-wrap items-end gap-4">
          <div>
            <h1 className="text-2xl font-bold text-gray-900 dark:text-gray-100">Reporte Asignación</h1>
            <p className="text-sm text-gray-500 dark:text-gray-400">
              Maestros y materias que quedaron en el horario vigente · {semestreActivo?.nombre}
            </p>
          </div>
          <label className="flex flex-col text-sm text-gray-600 dark:text-gray-300">
            <span className="mb-1 font-medium">Turno</span>
            <select
              value={turnoSeleccionado}
              onChange={e => setTurnoSeleccionado(Number(e.target.value))}
              className="px-4 py-2.5 border border-gray-400 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-blue-500"
            >
              <option value={0}>Selecciona un turno…</option>
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

      {!turnoListo && (
        <div className="bg-white dark:bg-gray-800 border border-gray-400 dark:border-gray-700 rounded-xl shadow-md p-10 text-center text-gray-600 dark:text-gray-300">
          Selecciona un turno para ver el reporte.
        </div>
      )}

      {turnoListo && (
        <div className="bg-white dark:bg-gray-800 rounded-xl shadow-md overflow-hidden border border-gray-400 dark:border-gray-700">
          <div className="flex flex-wrap items-center justify-between gap-2 border-b border-gray-400 bg-gray-50 px-4 py-3 dark:border-gray-700 dark:bg-gray-700/50">
            <h2 className="text-base font-bold uppercase text-gray-800 dark:text-gray-100">
              Asignaciones · {nombreTurno}
            </h2>
            <span className="text-xs text-gray-600 dark:text-gray-300">
              {renglones.length} registro(s) · {totalMaestros} maestro(s) · <strong>{totalHoras}</strong> horas
            </span>
          </div>

          <div className="overflow-x-auto">
            <table className="w-full text-sm border-collapse">
              <thead className="sticky top-0 z-10 bg-gray-100 dark:bg-gray-700/50">
                <tr>
                  {COLUMNAS.map((c, i) => (
                    <th
                      key={c}
                      className={`px-3 py-2 font-semibold text-gray-700 dark:text-gray-300 border-b border-gray-400 dark:border-gray-700 ${
                        i >= 3 ? 'text-center' : 'text-left'
                      }`}
                    >
                      {c}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-200 dark:divide-gray-700">
                {renglones.map(r => (
                  <tr
                    key={`${r.maestroId}-${r.materiaClave}-${r.grupo}-${r.grado}`}
                    className="hover:bg-gray-50 dark:hover:bg-gray-700/30"
                  >
                    <td className="px-3 py-1.5 text-gray-800 dark:text-gray-100">{r.maestro}</td>
                    <td className="px-3 py-1.5 text-gray-700 dark:text-gray-300">{r.materia}</td>
                    <td className="px-3 py-1.5 text-gray-700 dark:text-gray-300">{r.especialidad}</td>
                    <td className="px-3 py-1.5 text-center whitespace-nowrap text-gray-700 dark:text-gray-300">{r.grupo}</td>
                    <td className="px-3 py-1.5 text-center font-semibold text-indigo-700 dark:text-indigo-300">{r.horas}</td>
                  </tr>
                ))}
                <tr className="bg-indigo-50 font-semibold dark:bg-indigo-900/20">
                  <td colSpan={4} className="px-3 py-2 text-right text-gray-700 dark:text-gray-300">
                    Total de horas
                  </td>
                  <td className="px-3 py-2 text-center text-indigo-700 dark:text-indigo-300">{totalHoras}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      )}

      {turnoListo && renglones.length === 0 && (
        <div className="bg-yellow-50 dark:bg-yellow-900/20 border border-yellow-200 dark:border-yellow-800 rounded-lg p-8 text-center text-gray-700 dark:text-gray-300">
          El horario vigente de este turno no tiene clases colocadas.
        </div>
      )}
    </div>
  );
};

export default ReporteAsignacion;
