import React, { useState, useEffect, useMemo } from 'react';
import { horarioService } from '../api/horarioService';
import { grupoService } from '../api/grupoService';
import { turnoService } from '../api/turnoService';
import { turnoHorarioService } from '../api/turnoHorarioService';
import { useAuth } from '../context/AuthContext';
import type { Horario, Grupo, Turno, TurnoHorario } from '../types';
import {
  MdPrint, MdPictureAsPdf, MdGridOn, MdRefresh, MdSchedule, MdWarning,
} from 'react-icons/md';
import jsPDF from 'jspdf';
import autoTable from 'jspdf-autotable';
import * as XLSX from 'xlsx';
import FilaBloqueHorario from '../components/FilaBloqueHorario';
import {
  DIAS_SEMANA,
  construirBloquesFilas,
  extraerLista,
  formatearHora as formatHoraUtil,
  indexarHorarios,
} from '../utils/horarioUtils';

// Adaptador local para mantener el nombre que ya usaba el archivo
const formatHora = (hora: string) => formatHoraUtil(hora);

interface MateriaResumen {
  asignacionId: number;
  materiaNombre: string;
  materiaClave: string;
  maestroNombre: string;
  aulaNombre: string;
  horas: number;
  colorHex: string;
}

interface GrupoConHorario {
  grupo: Grupo;
  horarios: Horario[];
  bloquesFilas: TurnoHorario[];              // filas de la matriz (únicas por horaInicio)
  horarioIndex: Map<string, Horario[]>;      // 🔥 agrupado, detecta solapamientos
  materias: MateriaResumen[];
  solapamientos: number;                     // bloques con más de 1 horario
}

const ReporteHorariosGrupos: React.FC = () => {
  const { semestreActivo, escuelaActiva } = useAuth();

  const [turnos, setTurnos] = useState<Turno[]>([]);
  const [turnoSeleccionado, setTurnoSeleccionado] = useState<number>(0);
  const [grupos, setGrupos] = useState<Grupo[]>([]);
  const [horarios, setHorarios] = useState<Horario[]>([]);
  const [bloquesPorTurno, setBloquesPorTurno] = useState<Map<number, TurnoHorario[]>>(new Map());
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    if (semestreActivo?.id) cargarTodo();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [semestreActivo?.id]);

  const cargarTodo = async () => {
    setLoading(true);
    setError('');
    try {
      const semestreId = semestreActivo!.id;

      // 1. Catálogos base en paralelo
      const [turnosRes, gruposRes, horariosRes] = await Promise.all([
        turnoService.listar(0, 100, '', semestreId),
        grupoService.listar(0, 100, '', 0, semestreId),
        horarioService.obtenerTodos(semestreId),
      ]);

      const turnosActivos = turnosRes.data.content.filter((t: Turno) => t.activo === true);
      const gruposActivos = gruposRes.data.content.filter((g: Grupo) => g.activo);

      setTurnos(turnosActivos);
      setGrupos(gruposActivos);
      setHorarios(horariosRes.data);

      // 2. Bloques del turno, deduplicando turnos para no repetir llamadas
      const turnosUnicos = Array.from(
        new Set(gruposActivos.map((g: Grupo) => g.turnoId).filter((id): id is number => !!id))
      );

      const bloquesMap = new Map<number, TurnoHorario[]>();
      await Promise.all(
        turnosUnicos.map(async (tId) => {
          try {
            const res = await turnoHorarioService.listar(tId, semestreId);
            bloquesMap.set(tId, extraerLista<TurnoHorario>(res));
          } catch (e) {
            console.error(`Error al cargar bloques del turno ${tId}:`, e);
            bloquesMap.set(tId, []);
          }
        })
      );
      setBloquesPorTurno(bloquesMap);
    } catch (err) {
      console.error('Error al cargar datos del reporte:', err);
      setError('Error al cargar los datos del reporte');
    } finally {
      setLoading(false);
    }
  };

  // Construir estructura: por cada grupo, sus horarios + bloques del turno + materias resumen
  const gruposConHorario: GrupoConHorario[] = useMemo(() => {
    const gruposFiltrados = turnoSeleccionado > 0
      ? grupos.filter(g => g.turnoId === turnoSeleccionado)
      : grupos;

    const horariosPorGrupo = new Map<number, Horario[]>();
    horarios.forEach(h => {
      if (!horariosPorGrupo.has(h.grupoId)) horariosPorGrupo.set(h.grupoId, []);
      horariosPorGrupo.get(h.grupoId)!.push(h);
    });

    const resultado: GrupoConHorario[] = [];
    gruposFiltrados.forEach(grupo => {
      const hs = horariosPorGrupo.get(grupo.id) || [];
      if (hs.length === 0) return;

      const bloquesDelTurno = grupo.turnoId ? (bloquesPorTurno.get(grupo.turnoId) ?? []) : [];
      const bloquesFilas = construirBloquesFilas(bloquesDelTurno);
      const horarioIndex = indexarHorarios(hs);

      // Contar solapamientos (bloques con más de 1 horario)
      let solapamientos = 0;
      for (const arr of horarioIndex.values()) {
        if (arr.length > 1) solapamientos++;
      }

      // Resumen de materias y maestros
      const materiasMap = new Map<number, MateriaResumen>();
      hs.forEach(h => {
        const id = h.asignacionId;
        if (!materiasMap.has(id)) {
          materiasMap.set(id, {
            asignacionId: id,
            materiaNombre: h.materiaNombre,
            materiaClave: h.materiaClave,
            maestroNombre: h.maestroNombre,
            aulaNombre: h.aulaNombre,
            horas: 0,
            colorHex: h.colorHex || '#808080',
          });
        }
        materiasMap.get(id)!.horas += 1;
      });

      const materias: MateriaResumen[] = Array.from(materiasMap.values())
        .sort((a, b) => a.materiaNombre.localeCompare(b.materiaNombre));

      resultado.push({ grupo, horarios: hs, bloquesFilas, horarioIndex, materias, solapamientos });
    });

    resultado.sort((a, b) => {
      if (a.grupo.grado !== b.grupo.grado) return a.grupo.grado - b.grupo.grado;
      return a.grupo.nombre.localeCompare(b.grupo.nombre);
    });

    return resultado;
  }, [grupos, horarios, turnoSeleccionado, bloquesPorTurno]);

  const getNombreEspecialidad = (grupo: Grupo): string => {
    if (!grupo.especialidad) return '';
    return typeof grupo.especialidad === 'string'
      ? grupo.especialidad
      : grupo.especialidad.nombre || '';
  };

  const getNombreDia = (dia: number) =>
    DIAS_SEMANA.find(d => d.value === dia)?.label || '';

  // ============================================================
  // IMPRIMIR
  // ============================================================
  const handleImprimir = () => {
    window.print();
  };

  // ============================================================
  // PDF — misma estructura que la vista, con descansos fusionados
  // ============================================================
  const handlePDF = () => {
    if (gruposConHorario.length === 0) {
      setError('No hay horarios para exportar');
      return;
    }

    const doc = new jsPDF({ orientation: 'portrait', unit: 'mm', format: 'letter' });
    const pageWidth = doc.internal.pageSize.getWidth();
    const pageHeight = doc.internal.pageSize.getHeight();
    const marginX = 12;

    gruposConHorario.forEach((g) => {
      // ── Encabezado ──
      doc.setFontSize(17);
      doc.setFont('helvetica', 'bold');
      const nombreLargoRaw = escuelaActiva?.nombreLargo || escuelaActiva?.nombre || 'Reporte de Horarios';
      const anchoMaximo = pageWidth - marginX * 5;
      const lineasNombre: string[] = doc.splitTextToSize(nombreLargoRaw, anchoMaximo);
      const lineasVisibles = lineasNombre.slice(0, 2);

      const lineHeight = 8;
      const altoNombre = lineasVisibles.length * lineHeight;
      const headerHeight = 15 + altoNombre + 3;

      doc.setFillColor(255, 255, 255);
      doc.rect(0, 0, pageWidth, headerHeight, 'F');
      doc.setTextColor(0, 0, 0);

      lineasVisibles.forEach((linea, i) => {
        doc.text(linea, pageWidth / 2, 9 + i * lineHeight, { align: 'center' });
      });

      doc.setFontSize(16);
      doc.setFont('helvetica', 'normal');
      doc.text(`Semestre ${semestreActivo?.nombre || ''}`, pageWidth / 2, 24, { align: 'center' });

      doc.setFontSize(16);
      doc.setFont('helvetica', 'bold');
      doc.text('Reporte de Horarios por Grupo', pageWidth / 2, 32, { align: 'center' });

      let y = headerHeight + 5;
      doc.setTextColor(0, 0, 0);

      const titulo =
        `${g.grupo.nombre} — ${g.grupo.turno || ''}` +
        (getNombreEspecialidad(g.grupo) ? ` · ${getNombreEspecialidad(g.grupo)}` : '');

      if (y > pageHeight - 60) {
        doc.addPage();
        y = 20;
      }

      doc.setFillColor(255, 255, 255);
      doc.rect(marginX, y - 5, pageWidth - marginX * 2, 8, 'F');
      doc.setFontSize(11);
      doc.setFont('helvetica', 'bold');
      doc.text(titulo, marginX + 2, y);
      y += 3;
      doc.setFont('helvetica', 'normal');
      doc.setFontSize(8);

      // ── Tabla matriz ──
      const head = [['Hora', ...DIAS_SEMANA.map(d => d.label)]];
      const body: any[][] = g.bloquesFilas.map(b => {
        const horaTexto = `${formatHora(b.horaInicio)} - ${formatHora(b.horaFin)}`;

        // 🔥 Descanso: una sola fila con colSpan sobre las columnas de días
        if (b.descanso) {
          return [
            horaTexto,
            {
              content: 'D   E   S   C   A   N   S   O',
              colSpan: DIAS_SEMANA.length,
              styles: {
                halign: 'center',
                fontStyle: 'italic',
                fontSize: 14,
                textColor: [0, 0, 0],
                fillColor: [255, 255, 255],
              },
            },
          ];
        }

        // Fila normal: buscar en el índice por (día + horaInicio)
        const fila: string[] = [horaTexto];
        DIAS_SEMANA.forEach(dia => {
          const arr = g.horarioIndex.get(`${dia.value}|${formatHora(b.horaInicio)}`) ?? [];
          if (arr.length === 0) {
            fila.push('—');
          } else if (arr.length === 1) {
            const h = arr[0];
            fila.push(`${h.materiaClave}\n${h.aulaNombre}`);
          } else {
            // Solapamiento: avisar en la celda
            const lineas = arr.map(h => `${h.materiaClave} · ${h.aulaNombre}`).join('\n');
            fila.push(`⚠️ ${arr.length} solapadas\n${lineas}`);
          }
        });
        return fila;
      });

      autoTable(doc, {
        head,
        body,
        startY: y,
        margin: { left: marginX, right: marginX },
        styles: {
          fontSize: 10,
          cellPadding: 2,
          valign: 'middle',
          halign: 'center',
          lineColor: [0, 0, 0],
          lineWidth: 0.5,
          textColor: 0,
          fillColor: [255, 255, 255],
        },
        headStyles: {
          fillColor: [240, 244, 250],
          textColor: 0,
          fontStyle: 'bold',
          halign: 'center',
        },
        alternateRowStyles: {
          fillColor: [250, 250, 252],
        },
        columnStyles: {
          0: {
            cellWidth: 20,
            fontStyle: 'bold',
            fillColor: [235, 235, 240],
            textColor: 0,
          },
        },
      });

      // ── Tabla materias/maestros ──
      y = (doc as any).lastAutoTable.finalY + 7;

      if (y > pageHeight - 40) {
        doc.addPage();
        y = 20;
      }

      doc.setFontSize(11);
      doc.setFont('helvetica', 'bold');
      doc.setTextColor(60, 60, 90);
      doc.text('Materias y maestros asignados', marginX, y);
      doc.setFont('helvetica', 'normal');
      doc.setTextColor(0, 0, 0);
      y += 2;

      const totalHoras = g.materias.reduce((s, m) => s + m.horas, 0);

      autoTable(doc, {
        head: [['MATERIA', 'MAESTRO', 'AULA', 'HORAS']],
        body: [
          ...g.materias.map(m => [
            m.materiaNombre,
            m.maestroNombre,
            m.aulaNombre,
            String(m.horas),
          ]),
          [
            {
              content: 'Total de horas',
              colSpan: 3,
              styles: { halign: 'right', fontStyle: 'bold' },
            },
            {
              content: String(totalHoras),
              styles: { halign: 'center', fontStyle: 'bold' },
            },
          ],
        ],
        startY: y,
        margin: { left: marginX, right: marginX },
        styles: {
          fontSize: 9,
          cellPadding: 1.8,
          valign: 'middle',
          lineColor: [0, 0, 0],
          lineWidth: 0.3,
          textColor: 0,
          fillColor: [255, 255, 255],
        },
        headStyles: {
          fillColor: [230, 230, 235],
          textColor: 0,
          fontStyle: 'bold',
          halign: 'left',
        },
        columnStyles: {
          0: { cellWidth: 85 },
          1: { cellWidth: 65 },
          2: { cellWidth: 25, halign: 'center' },
          3: { cellWidth: 16, halign: 'center' },
        },
        alternateRowStyles: {
          fillColor: [248, 250, 252],
        },
      });

      y = (doc as any).lastAutoTable.finalY + 12;

      doc.addPage();
    });

    // Pie de página
    const totalPages = doc.getNumberOfPages();
    for (let i = 1; i <= totalPages; i++) {
      doc.setPage(i);
      doc.setFontSize(8);
      doc.setTextColor(150);
      doc.text(
        `Página ${i} de ${totalPages}`,
        pageWidth - marginX,
        pageHeight - 5,
        { align: 'right' }
      );
      doc.text(new Date().toLocaleDateString('es-MX'), marginX, pageHeight - 5);
      doc.setTextColor(0);
    }

    doc.save(`horarios-grupos-${semestreActivo?.nombre || 'semestre'}.pdf`);
  };

  // ============================================================
  // EXCEL — sin cambios (mismas 3 hojas)
  // ============================================================
  const handleExcel = () => {
    if (gruposConHorario.length === 0) {
      setError('No hay horarios para exportar');
      return;
    }

    const wb = XLSX.utils.book_new();

    // Hoja 1: Resumen
    const resumenData: any[][] = [
      ['Reporte de Horarios por Grupo'],
      [`Escuela: ${escuelaActiva?.nombre || ''}`],
      [`Semestre: ${semestreActivo?.nombre || ''}`],
      [
        turnoSeleccionado > 0
          ? `Turno: ${turnos.find(t => t.id === turnoSeleccionado)?.nombre || ''}`
          : 'Turno: Todos',
      ],
      [`Fecha de generación: ${new Date().toLocaleString('es-MX')}`],
      [],
      ['#', 'Grupo', 'Grado', 'Turno', 'Especialidad', 'Total clases', 'Total horas'],
    ];

    gruposConHorario.forEach((g, i) => {
      resumenData.push([
        i + 1,
        g.grupo.nombre,
        g.grupo.grado,
        g.grupo.turno || '',
        getNombreEspecialidad(g.grupo),
        g.horarios.length,
        g.materias.reduce((s, m) => s + m.horas, 0),
      ]);
    });

    const wsResumen = XLSX.utils.aoa_to_sheet(resumenData);
    wsResumen['!cols'] = [
      { wch: 5 }, { wch: 12 }, { wch: 8 }, { wch: 15 }, { wch: 25 },
      { wch: 14 }, { wch: 12 },
    ];
    XLSX.utils.book_append_sheet(wb, wsResumen, 'Resumen');

    // Hoja 2: Materias y maestros
    const materiasData: any[][] = [
      ['Grupo', 'Grado', 'Turno', 'Clave', 'Materia', 'Maestro', 'Aula', 'Horas/semana'],
    ];

    gruposConHorario.forEach(g => {
      g.materias.forEach(m => {
        materiasData.push([
          g.grupo.nombre,
          g.grupo.grado,
          g.grupo.turno || '',
          m.materiaClave,
          m.materiaNombre,
          m.maestroNombre,
          m.aulaNombre,
          m.horas,
        ]);
      });
    });

    const wsMaterias = XLSX.utils.aoa_to_sheet(materiasData);
    wsMaterias['!cols'] = [
      { wch: 12 }, { wch: 8 }, { wch: 15 }, { wch: 12 }, { wch: 30 },
      { wch: 30 }, { wch: 15 }, { wch: 12 },
    ];
    XLSX.utils.book_append_sheet(wb, wsMaterias, 'Materias y maestros');

    // Hoja 3: Detalle de clases
    const detalleData: any[][] = [
      ['Grupo', 'Grado', 'Turno', 'Especialidad', 'Día', 'Hora inicio', 'Hora fin', 'Materia', 'Clave', 'Maestro', 'Aula'],
    ];

    gruposConHorario.forEach(g => {
      g.horarios
        .slice()
        .sort((a, b) => {
          if (a.diaSemana !== b.diaSemana) return a.diaSemana - b.diaSemana;
          return a.horaInicio.localeCompare(b.horaInicio);
        })
        .forEach(h => {
          detalleData.push([
            g.grupo.nombre,
            g.grupo.grado,
            g.grupo.turno || '',
            getNombreEspecialidad(g.grupo),
            getNombreDia(h.diaSemana),
            formatHora(h.horaInicio),
            formatHora(h.horaFin),
            h.materiaNombre,
            h.materiaClave,
            h.maestroNombre,
            h.aulaNombre,
          ]);
        });
    });

    const wsDetalle = XLSX.utils.aoa_to_sheet(detalleData);
    wsDetalle['!cols'] = [
      { wch: 12 }, { wch: 8 }, { wch: 15 }, { wch: 25 }, { wch: 10 },
      { wch: 10 }, { wch: 10 }, { wch: 30 }, { wch: 12 }, { wch: 30 }, { wch: 15 },
    ];
    XLSX.utils.book_append_sheet(wb, wsDetalle, 'Detalle clases');

    XLSX.writeFile(wb, `horarios-grupos-${semestreActivo?.nombre || 'semestre'}.xlsx`);
  };

  // ============================================================
  // RENDER
  // ============================================================
  if (!semestreActivo) {
    return (
      <div className="p-6 text-center text-yellow-600 dark:text-yellow-400">
        <p className="text-lg font-semibold">⚠️ No hay semestre activo</p>
      </div>
    );
  }

  return (
    <>
      {/* CSS de impresión */}
      <style>{`
        @media print {
          nav, .no-print { display: none !important; }
          body { background: white !important; color: black !important; }
          main {
            margin: 0 !important;
            padding: 0 !important;
            background: white !important;
            box-shadow: none !important;
            border: none !important;
            border-radius: 0 !important;
            overflow: visible !important;
            backdrop-filter: none !important;
          }
          * {
            -webkit-print-color-adjust: exact !important;
            print-color-adjust: exact !important;
          }
          .grupo-reporte {
            page-break-inside: avoid;
            margin-bottom: 24px;
          }
          .grupo-reporte table {
            page-break-inside: auto;
          }
          .grupo-reporte tbody tr {
            page-break-inside: avoid;
            page-break-after: auto;
          }
        }
      `}</style>

      <div className="p-6 max-w-7xl mx-auto">
        {/* Toolbar */}
        <div className="no-print flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4 mb-6">
          <div>
            <h1 className="text-3xl font-bold text-gray-800 dark:text-white">
              Reporte de Horarios por Grupo
            </h1>
            <p className="text-sm text-gray-500 dark:text-gray-400 mt-1">
              Semestre: <span className="font-medium">{semestreActivo.nombre}</span>
              {escuelaActiva?.nombre && <> · {escuelaActiva.nombre}</>}
            </p>
          </div>
          <div className="flex flex-wrap gap-2">
            <button
              onClick={cargarTodo}
              disabled={loading}
              className="flex items-center gap-2 bg-gray-200 dark:bg-gray-700 hover:bg-gray-300 dark:hover:bg-gray-600 text-gray-700 dark:text-gray-300 px-4 py-2.5 rounded-lg shadow-md transition disabled:opacity-50"
            >
              <MdRefresh className="text-xl" />
              Recargar
            </button>
            <button
              onClick={handleImprimir}
              disabled={loading || gruposConHorario.length === 0}
              className="flex items-center gap-2 bg-slate-600 hover:bg-slate-700 text-white px-4 py-2.5 rounded-lg shadow-md transition disabled:opacity-50"
            >
              <MdPrint className="text-xl" />
              Imprimir
            </button>
            <button
              onClick={handlePDF}
              disabled={loading || gruposConHorario.length === 0}
              className="flex items-center gap-2 bg-red-600 hover:bg-red-700 text-white px-4 py-2.5 rounded-lg shadow-md transition disabled:opacity-50"
            >
              <MdPictureAsPdf className="text-xl" />
              Guardar PDF
            </button>
            <button
              onClick={handleExcel}
              disabled={loading || gruposConHorario.length === 0}
              className="flex items-center gap-2 bg-green-600 hover:bg-green-700 text-white px-4 py-2.5 rounded-lg shadow-md transition disabled:opacity-50"
            >
              <MdGridOn className="text-xl" />
              Exportar Excel
            </button>
          </div>
        </div>

        {/* Filtro por turno */}
        <div className="no-print bg-white dark:bg-gray-800 rounded-xl shadow-md p-4 mb-6 border border-gray-400 dark:border-gray-700">
          <div className="flex flex-col sm:flex-row items-start sm:items-center gap-3">
            <label className="text-sm font-medium text-gray-700 dark:text-gray-300 whitespace-nowrap">
              <MdSchedule className="inline mr-1" />
              Turno:
            </label>
            <select
              value={turnoSeleccionado}
              onChange={(e) => setTurnoSeleccionado(Number(e.target.value))}
              disabled={loading}
              className="w-full sm:max-w-md px-4 py-2.5 border border-gray-400 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:opacity-50"
            >
              <option value={0}>Todos los turnos</option>
              {turnos.map(t => (
                <option key={t.id} value={t.id}>{t.nombre}</option>
              ))}
            </select>
            <span className="text-sm text-gray-500 dark:text-gray-400">
              {gruposConHorario.length} grupos con horario
            </span>
          </div>
        </div>

        {error && (
          <div className="no-print bg-red-50 dark:bg-red-900/30 text-red-700 dark:text-red-300 p-4 rounded-lg border border-red-200 dark:border-red-800 mb-6">
            {error}
          </div>
        )}

        {loading ? (
          <div className="flex justify-center items-center h-64">
            <div className="animate-spin rounded-full h-12 w-12 border-4 border-blue-500 border-t-transparent"></div>
          </div>
        ) : gruposConHorario.length === 0 ? (
          <div className="bg-yellow-50 dark:bg-yellow-900/20 border border-yellow-200 dark:border-yellow-800 rounded-lg p-8 text-center">
            <MdWarning className="text-5xl text-yellow-600 dark:text-yellow-400 mx-auto mb-3" />
            <h3 className="text-lg font-semibold text-yellow-800 dark:text-yellow-200">
              Sin horarios generados
            </h3>
            <p className="text-sm text-yellow-700 dark:text-yellow-300 mt-1">
              No hay horarios generados para {turnoSeleccionado > 0 ? 'el turno seleccionado' : 'este semestre'}.
            </p>
          </div>
        ) : (
          <div className="space-y-8">
            {/* Encabezado del reporte (solo al imprimir) */}
            <div className="hidden print:block mb-6 text-center border-b-2 border-gray-800 pb-3">
              <h1 className="text-2xl font-bold">
                {escuelaActiva?.nombreLargo || escuelaActiva?.nombre || 'Reporte de Horarios'}
              </h1>
              <p className="text-base mt-1">Horarios por Grupo</p>
              <p className="text-sm mt-1">
                Semestre: {semestreActivo.nombre}
                {turnoSeleccionado > 0 && (
                  <> · Turno: {turnos.find(t => t.id === turnoSeleccionado)?.nombre}</>
                )}
              </p>
            </div>

            {/* Matrices por grupo */}
            {gruposConHorario.map(g => (
              <div
                key={g.grupo.id}
                className="grupo-reporte bg-white dark:bg-gray-800 rounded-xl shadow-md overflow-hidden border border-gray-400 dark:border-gray-700"
              >
                {/* Header */}
                <div className="px-4 py-3 bg-gray-50 dark:bg-gray-700/50 border-b border-gray-400 dark:border-gray-700 flex flex-wrap items-center justify-between gap-3">
                  <div className="flex items-center gap-3 flex-wrap">
                    <h3 className="font-bold text-lg text-gray-800 dark:text-white">
                      {g.grupo.nombre} — {g.grupo.turno || ''}
                      {g.grupo.especialidad && (
                        <span className="ml-2 text-sm font-normal text-gray-500 dark:text-gray-400">
                          · {getNombreEspecialidad(g.grupo)}
                        </span>
                      )}
                    </h3>
                    {g.solapamientos > 0 && (
                      <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-bold bg-red-100 dark:bg-red-900/40 text-red-700 dark:text-red-300 border border-red-500">
                        <MdWarning className="text-sm" />
                        {g.solapamientos} solapamiento(s)
                      </span>
                    )}
                  </div>
                  <span className="text-sm text-gray-500 dark:text-gray-400">
                    {g.horarios.length} clases
                  </span>
                </div>

                {/* 🔥 Matriz: misma estructura que HorarioGrupo */}
                <div className="overflow-x-auto">
                  <table className="min-w-full divide-y divide-gray-400 dark:divide-gray-700">
                    <thead className="bg-gray-50 dark:bg-gray-700/50">
                      <tr>
                        <th className="px-3 py-2 text-left text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider">
                          Hora
                        </th>
                        {DIAS_SEMANA.map(dia => (
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
                      {g.bloquesFilas.map((bloque) => (
                        <FilaBloqueHorario
                          key={bloque.id}
                          bloque={bloque}
                          horarioIndex={g.horarioIndex}
                          variante="grupo"
                        />
                      ))}
                    </tbody>
                  </table>
                </div>

                {/* 🔥 Tabla de materias y maestros */}
                {g.materias.length > 0 && (
                  <div className="border-t-2 border-gray-400 dark:border-gray-700 px-4 py-4 bg-gray-50/50 dark:bg-gray-800/50">
                    <h4 className="text-sm font-bold text-gray-700 dark:text-gray-300 uppercase tracking-wide mb-3">
                      Materias y maestros asignados
                    </h4>
                    <div className="overflow-x-auto">
                      <table className="min-w-full text-sm border-collapse">
                        <thead className="bg-gray-100 dark:bg-gray-700/50">
                          <tr>
                            <th className="px-3 py-2 text-left font-semibold text-gray-700 dark:text-gray-300 border border-gray-400 dark:border-gray-700">
                              Clave
                            </th>
                            <th className="px-3 py-2 text-left font-semibold text-gray-700 dark:text-gray-300 border border-gray-400 dark:border-gray-700">
                              Materia
                            </th>
                            <th className="px-3 py-2 text-left font-semibold text-gray-700 dark:text-gray-300 border border-gray-400 dark:border-gray-700">
                              Maestro
                            </th>
                            <th className="px-3 py-2 text-left font-semibold text-gray-700 dark:text-gray-300 border border-gray-400 dark:border-gray-700">
                              Aula
                            </th>
                            <th className="px-3 py-2 text-center font-semibold text-gray-700 dark:text-gray-300 border border-gray-400 dark:border-gray-700">
                              Horas/sem
                            </th>
                          </tr>
                        </thead>
                        <tbody>
                          {g.materias.map((m, idx) => (
                            <tr
                              key={m.asignacionId}
                              className={idx % 2 === 0
                                ? 'bg-white dark:bg-gray-800'
                                : 'bg-gray-50 dark:bg-gray-800/50'}
                            >
                              <td className="px-3 py-2 whitespace-nowrap border border-gray-400 dark:border-gray-700">
                                <div className="flex items-center gap-2">
                                  <div
                                    className="w-3 h-3 rounded-full flex-shrink-0"
                                    style={{ backgroundColor: m.colorHex }}
                                  />
                                  <span className="font-mono font-semibold text-gray-800 dark:text-white">
                                    {m.materiaClave}
                                  </span>
                                </div>
                              </td>
                              <td className="px-3 py-2 text-gray-700 dark:text-gray-300 border border-gray-400 dark:border-gray-700">
                                {m.materiaNombre}
                              </td>
                              <td className="px-3 py-2 text-gray-700 dark:text-gray-300 border border-gray-400 dark:border-gray-700">
                                {m.maestroNombre}
                              </td>
                              <td className="px-3 py-2 text-gray-700 dark:text-gray-300 border border-gray-400 dark:border-gray-700">
                                {m.aulaNombre}
                              </td>
                              <td className="px-3 py-2 text-center font-semibold text-indigo-700 dark:text-indigo-300 border border-gray-400 dark:border-gray-700">
                                {m.horas}
                              </td>
                            </tr>
                          ))}
                          <tr className="bg-indigo-50 dark:bg-indigo-900/20 font-semibold">
                            <td
                              colSpan={4}
                              className="px-3 py-2 text-right text-gray-700 dark:text-gray-300 border border-gray-400 dark:border-gray-700"
                            >
                              Total de horas
                            </td>
                            <td className="px-3 py-2 text-center text-indigo-700 dark:text-indigo-300 border border-gray-400 dark:border-gray-700">
                              {g.materias.reduce((sum, m) => sum + m.horas, 0)}
                            </td>
                          </tr>
                        </tbody>
                      </table>
                    </div>
                  </div>
                )}
              </div>
            ))}
          </div>
        )}
      </div>
    </>
  );
};

export default ReporteHorariosGrupos;