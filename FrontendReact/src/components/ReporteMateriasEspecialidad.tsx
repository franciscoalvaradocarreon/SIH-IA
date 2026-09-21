import React, { useEffect, useMemo, useState } from 'react';
import api from '../api/axiosConfig';
import { grupoService } from '../api/grupoService';
import { turnoService } from '../api/turnoService';
import { useAuth } from '../context/AuthContext';
import type { Grupo, Turno } from '../types';
import { MdPictureAsPdf, MdGridOn } from 'react-icons/md';
import jsPDF from 'jspdf';
import autoTable from 'jspdf-autotable';
import * as XLSX from 'xlsx';

/**
 * REPORTE DE MATERIAS POR ESPECIALIDAD: por cada especialidad, las materias que se imparten con sus
 * horas, el grupo y el maestro. Mismo diseño que el reporte "Horario por grupo" (una tarjeta por
 * sección con barra de cabecera y tabla con bordes), con exportación a PDF y Excel.
 */

interface AsignacionReporte {
  id: number;
  grupoId: number;
  materiaClave: string;
  materiaNombre: string;
  horas: number;
  maestroId: number;
  maestroNombre: string;
}

interface EspReporte {
  id: string;
  nombre: string;
}

interface FilaReporte {
  materiaNombre: string;
  horas: number;
  grupoNombre: string;
  especialidad: string;
  maestroNombre: string;
}

/**
 * Nombre de la especialidad del grupo. OJO: la API manda `especialidad` como TEXTO
 * ("RECURSOS HUMANOS"), no como objeto; se toleran las dos formas por si cambia.
 */
const especialidadDe = (g: Grupo | undefined): string => {
  const esp = g?.especialidad as unknown;
  if (typeof esp === 'string') return esp;
  if (esp && typeof esp === 'object' && 'nombre' in esp) {
    return String((esp as { nombre?: unknown }).nombre ?? '');
  }
  return '';
};

const ReporteMateriasEspecialidad: React.FC = () => {
  const { semestreActivo } = useAuth();
  const [grupos, setGrupos] = useState<Grupo[]>([]);
  const [turnos, setTurnos] = useState<Turno[]>([]);
  const [asignaciones, setAsignaciones] = useState<AsignacionReporte[]>([]);
  const [turnoSeleccionado, setTurnoSeleccionado] = useState<number>(0);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!semestreActivo?.id) return;
    (async () => {
      setLoading(true);
      try {
        const [g, t, a] = await Promise.all([
          grupoService.listar(0, 300, '', 0, semestreActivo.id),
          turnoService.listar(0, 100, '', semestreActivo.id),
          api.get<{ content: AsignacionReporte[] }>(
            `/asignaciones?semestreId=${semestreActivo.id}&size=1000`
          ),
        ]);
        setGrupos((g.data.content as Grupo[]).filter(x => x.activo));
        setTurnos(t.data.content.filter((x: Turno) => x.activo === true));
        setAsignaciones(a.data.content);
      } catch {
        // sin datos no se dibuja nada
      } finally {
        setLoading(false);
      }
    })();
  }, [semestreActivo?.id]);

  /** Grupos del turno elegido (o todos). */
  const gruposFiltrados = useMemo(
    () => (turnoSeleccionado === 0 ? grupos : grupos.filter(g => g.turnoId === turnoSeleccionado)),
    [grupos, turnoSeleccionado]
  );

  /** Especialidades que tienen grupos en el turno elegido (por NOMBRE: la API la manda como texto). */
  const especialidades = useMemo(() => {
    const map = new Map<string, EspReporte>();
    for (const g of gruposFiltrados) {
      const nombre = especialidadDe(g);
      if (nombre && !map.has(nombre)) {
        map.set(nombre, { id: nombre, nombre });
      }
    }
    return Array.from(map.values()).sort((a, b) => a.nombre.localeCompare(b.nombre));
  }, [gruposFiltrados]);

  const seleccionadas = especialidades;

  /** Hasta que no se elija un turno no se habilitan las exportaciones. */
  const turnoListo = turnoSeleccionado > 0;

  /** Filas de una especialidad: una por (materia, grupo) con su maestro y sus horas. */
  const filasDe = (espNombre: string): FilaReporte[] => {
    const ids = new Set(
      gruposFiltrados.filter(g => especialidadDe(g) === espNombre).map(g => g.id)
    );
    const grupoDe = (id: number): Grupo | undefined => grupos.find(g => g.id === id);
    return asignaciones
      .filter(a => ids.has(a.grupoId))
      .map(a => {
        const g = grupoDe(a.grupoId);
        return {
          materiaNombre: a.materiaNombre,
          horas: a.horas,
          grupoNombre: g?.nombre ?? `grupo ${a.grupoId}`,
          especialidad: especialidadDe(g),
          maestroNombre: a.maestroNombre,
        };
      })
      .sort((x, y) =>
        x.grupoNombre.localeCompare(y.grupoNombre)
        || x.materiaNombre.localeCompare(y.materiaNombre)
        || x.maestroNombre.localeCompare(y.maestroNombre)
      );
  };

  const exportarExcel = () => {
    if (!turnoListo) return;
    const wb = XLSX.utils.book_new();
    for (const esp of seleccionadas) {
      const filas = filasDe(esp.nombre);
      const aoa: (string | number)[][] = [
        ['ESPECIALIDAD: ' + esp.nombre],
        [],
        ['MATERIA', 'HORAS', 'GRUPO', 'ESPECIALIDAD', 'MAESTRO'],
        ...filas.map(f => [f.materiaNombre, f.horas, f.grupoNombre, f.especialidad, f.maestroNombre]),
        ['TOTAL', filas.reduce((acc, f) => acc + f.horas, 0), '', '', ''],
      ];
      const hoja = XLSX.utils.aoa_to_sheet(aoa);
      hoja['!cols'] = [{ wch: 46 }, { wch: 8 }, { wch: 14 }, { wch: 26 }, { wch: 34 }];
      const nombre = (esp.nombre || 'Especialidad').slice(0, 31).replace(/[\\/?*[\]:]/g, '');
      XLSX.utils.book_append_sheet(wb, hoja, nombre);
    }
    XLSX.writeFile(wb, `materias_especialidad_${semestreActivo?.nombre ?? 'semestre'}.xlsx`);
  };

  const exportarPDF = () => {
    if (!turnoListo) return;
    const doc = new jsPDF({ orientation: 'landscape' });
    seleccionadas.forEach((esp, idx) => {
      if (idx > 0) doc.addPage();
      const filas = filasDe(esp.nombre);
      doc.setFontSize(14);
      doc.setFont('helvetica', 'bold');
      doc.text('ESPECIALIDAD: ' + esp.nombre, 14, 18);
      autoTable(doc, {
        startY: 24,
        head: [['MATERIA', 'HORAS', 'GRUPO', 'ESPECIALIDAD', 'MAESTRO']],
        body: filas.map(f => [f.materiaNombre, String(f.horas), f.grupoNombre, f.especialidad, f.maestroNombre]),
        foot: [['TOTAL', String(filas.reduce((acc, f) => acc + f.horas, 0)), '', '', '']],
        styles: { fontSize: 8, cellPadding: 2 },
        headStyles: { fillColor: [30, 64, 175] },
      });
    });
    doc.save(`materias_especialidad_${semestreActivo?.nombre ?? 'semestre'}.pdf`);
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
            <h1 className="text-2xl font-bold text-gray-900 dark:text-gray-100">Materias por especialidad</h1>
            <p className="text-sm text-gray-500 dark:text-gray-400">
              Materia, horas, grupo y maestro por especialidad · {semestreActivo?.nombre}
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

      {/* ── una tarjeta por especialidad (mismo diseño que horario por grupo) ── */}
      {seleccionadas.map(esp => {
        const filas = filasDe(esp.nombre);
        return (
          <div
            key={esp.id}
            className="bg-white dark:bg-gray-800 rounded-xl shadow-md overflow-hidden border border-gray-400 dark:border-gray-700"
          >
            <div className="px-4 py-3 bg-gray-50 dark:bg-gray-700/50 border-b border-gray-400 dark:border-gray-700 flex flex-wrap items-center justify-between gap-3">
              <h2 className="text-base font-bold uppercase text-gray-800 dark:text-gray-100">
                Especialidad: {esp.nombre}
              </h2>
              <span className="text-sm font-semibold text-gray-600 dark:text-gray-300">
                {filas.length} materia(s) · {filas.reduce((acc, f) => acc + f.horas, 0)} h
              </span>
            </div>

            <div className="overflow-x-auto">
              <table className="min-w-full text-sm border-collapse">
                <thead className="bg-gray-100 dark:bg-gray-700/50">
                  <tr>
                    <th className="px-3 py-2 text-left font-semibold text-gray-700 dark:text-gray-300 border border-gray-400 dark:border-gray-700">Materia</th>
                    <th className="px-3 py-2 text-center font-semibold text-gray-700 dark:text-gray-300 border border-gray-400 dark:border-gray-700">Horas</th>
                    <th className="px-3 py-2 text-left font-semibold text-gray-700 dark:text-gray-300 border border-gray-400 dark:border-gray-700">Grupo</th>
                    <th className="px-3 py-2 text-left font-semibold text-gray-700 dark:text-gray-300 border border-gray-400 dark:border-gray-700">Especialidad</th>
                    <th className="px-3 py-2 text-left font-semibold text-gray-700 dark:text-gray-300 border border-gray-400 dark:border-gray-700">Maestro</th>
                  </tr>
                </thead>
                <tbody className="bg-white dark:bg-gray-800 divide-y divide-gray-400 dark:divide-gray-700">
                  {filas.map((f, i) => (
                    <tr key={i} className="hover:bg-gray-50 dark:hover:bg-gray-700/50">
                      <td className="px-3 py-2 text-gray-800 dark:text-gray-100 border border-gray-400 dark:border-gray-700">{f.materiaNombre}</td>
                      <td className="px-3 py-2 text-center text-gray-700 dark:text-gray-300 border border-gray-400 dark:border-gray-700">{f.horas}</td>
                      <td className="px-3 py-2 text-gray-700 dark:text-gray-300 border border-gray-400 dark:border-gray-700">{f.grupoNombre}</td>
                      <td className="px-3 py-2 text-gray-700 dark:text-gray-300 border border-gray-400 dark:border-gray-700">{f.especialidad}</td>
                      <td className="px-3 py-2 text-gray-700 dark:text-gray-300 border border-gray-400 dark:border-gray-700">{f.maestroNombre}</td>
                    </tr>
                  ))}
                  <tr className="bg-indigo-50 dark:bg-indigo-900/20 font-semibold">
                    <td className="px-3 py-2 text-right text-gray-700 dark:text-gray-300 border border-gray-400 dark:border-gray-700" colSpan={2}>
                      Total de horas
                    </td>
                    <td className="px-3 py-2 text-center text-indigo-700 dark:text-indigo-300 border border-gray-400 dark:border-gray-700" colSpan={3}>
                      {filas.reduce((acc, f) => acc + f.horas, 0)}
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        );
      })}

      {seleccionadas.length === 0 && (
        <div className="bg-yellow-50 dark:bg-yellow-900/20 border border-yellow-200 dark:border-yellow-800 rounded-lg p-8 text-center text-gray-700 dark:text-gray-300">
          No hay especialidades con grupos en este semestre.
        </div>
      )}
    </div>
  );
};

export default ReporteMateriasEspecialidad;
