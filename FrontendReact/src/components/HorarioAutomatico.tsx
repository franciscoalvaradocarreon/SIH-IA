import React, { useState, useEffect } from 'react';
import { horarioService } from '../api/horarioService';
import { grupoService } from '../api/grupoService';
import { turnoService } from '../api/turnoService';
import { useAuth } from '../context/AuthContext';
import type { Grupo, Turno, HorarioSolucionMasiva } from '../types';
import {
  MdRefresh, MdSchedule, MdCheckCircle, MdCancel,
  MdWarning, MdInfo, MdSchool, MdClass, MdTimer
} from 'react-icons/md';

interface GrupoResultado {
  grupoId: number;
  grupoNombre: string;
  estado: 'pendiente' | 'ok' | 'sin-asignaciones' | 'sin-disponibilidad' | 'error';
  mensaje?: string;
  clases?: number;
}

const HorarioAutomatico: React.FC = () => {
  const { semestreActivo } = useAuth();

  const [grupos, setGrupos] = useState<Grupo[]>([]);
  const [turnos, setTurnos] = useState<Turno[]>([]);
  const [turnoSeleccionado, setTurnoSeleccionado] = useState<number>(0);
  const [resultados, setResultados] = useState<GrupoResultado[]>([]);
  const [loading, setLoading] = useState(true);
  const [generando, setGenerando] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [metricas, setMetricas] = useState<HorarioSolucionMasiva | null>(null);

  const [validando, setValidando] = useState(false);
  const [problemas, setProblemas] = useState<string[]>([]);
  const [mostrarProblemas, setMostrarProblemas] = useState(false);

  // 🔥 Cargar turnos + grupos al cambiar semestre
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

  // 🔥 Reconstruir la lista de resultados cuando cambia grupos o turno
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

  // 🔥 Cuando cambia el turno, filtrar los grupos mostrados
  useEffect(() => {
    if (grupos.length > 0 || turnoSeleccionado === 0) {
      reconstruirResultados(grupos, turnoSeleccionado);
      setMetricas(null);
      setProblemas([]);
      setMostrarProblemas(false);
    }
  }, [turnoSeleccionado]);

  const handleTurnoChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    setTurnoSeleccionado(Number(e.target.value));
  };

  const validarAntesDeGenerar = async () => {
    setValidando(true);
    setProblemas([]);
    setError('');
    setSuccess('');
    try {
      const res = await horarioService.validar(
        semestreActivo!.id,
        turnoSeleccionado > 0 ? turnoSeleccionado : undefined
      );
      setProblemas(res.data);
      setMostrarProblemas(true);

      if (res.data.length === 0) {
        setSuccess('✅ Todo listo para generar. No se detectaron problemas.');
      } else {
        const criticos = res.data.filter(p => p.startsWith('❌'));
        if (criticos.length > 0) {
          setError(`⚠️ Se detectaron ${criticos.length} problemas críticos. Revisa el detalle.`);
        } else {
          setSuccess(`⚠️ Se detectaron ${res.data.length} advertencias. Puedes continuar.`);
        }
      }
    } catch (err: any) {
      console.error('Error al validar:', err);
      setError(err.response?.data?.message || 'Error al validar la factibilidad');
    } finally {
      setValidando(false);
    }
  };

  const generarTodos = async () => {
    if (resultados.length === 0) {
      setError('No hay grupos para generar horarios en la selección actual');
      return;
    }

    setGenerando(true);
    setError('');
    setSuccess('');

    setResultados(prev => prev.map(r => ({ ...r, estado: 'pendiente', mensaje: 'Generando...' })));

    try {
      const res = await horarioService.generarTodos(
        semestreActivo!.id,
        turnoSeleccionado > 0 ? turnoSeleccionado : undefined
      );
      const data = res.data;
      setMetricas(data);

      const resultadosFinales: GrupoResultado[] = resultados.map(r => {
        const detalle = data.detalles?.find(d => d.grupoId === r.grupoId);

        if (!detalle) {
          return {
            ...r,
            estado: 'error',
            mensaje: '⚠️ Sin información del solver',
          };
        }

        let estado: GrupoResultado['estado'];
        switch (detalle.estado) {
          case 'OK': estado = 'ok'; break;
          case 'SIN_ASIGNACIONES': estado = 'sin-asignaciones'; break;
          case 'SIN_DISPONIBILIDAD': estado = 'sin-disponibilidad'; break;
          default: estado = 'error';
        }

        return {
          ...r,
          estado,
          clases: detalle.clasesAsignadas,
          mensaje: detalle.mensaje,
        };
      });

      setResultados(resultadosFinales);

      const total = data.totalClasesAsignadas;
      const ok = data.gruposConHorario;
      const sinAsig = data.gruposSinAsignaciones;
      const sinDisp = data.gruposSinDisponibilidad;
      const tiempo = data.tiempoSegundos;
      const alcance = turnoSeleccionado > 0
        ? `turno ${turnos.find(t => t.id === turnoSeleccionado)?.nombre}`
        : 'todos los turnos';

      setSuccess(
        `✅ Proceso completado (${alcance}) en ${tiempo}s: ` +
        `${total} clases generadas · ` +
        `${ok} grupos OK · ` +
        `${sinAsig} sin asignaciones · ` +
        `${sinDisp} sin disponibilidad`
      );
    } catch (err: any) {
      console.error('❌ Error en generación masiva:', err);
      const msg = err.response?.data?.message || err.message || 'Error desconocido';

      if (msg.includes('No se puede generar el horario')) {
        setError(msg);
        setMostrarProblemas(true);
        setProblemas(msg.split('\n').filter((l: string) => l.trim().startsWith('❌')));
      } else {
        setError(`Error al generar horarios: ${msg}`);
      }

      setResultados(prev => prev.map(r => ({
        ...r,
        estado: 'error',
        mensaje: `❌ ${msg}`,
      })));
    } finally {
      setGenerando(false);
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

  const getColorEstado = (estado: string) => {
    switch (estado) {
      case 'pendiente': return 'bg-gray-100 dark:bg-gray-700 text-gray-600 dark:text-gray-400';
      case 'ok': return 'bg-green-100 dark:bg-green-900/30 text-green-600 dark:text-green-400';
      case 'sin-asignaciones': return 'bg-yellow-100 dark:bg-yellow-900/30 text-yellow-600 dark:text-yellow-400';
      case 'sin-disponibilidad': return 'bg-orange-100 dark:bg-orange-900/30 text-orange-600 dark:text-orange-400';
      case 'error': return 'bg-red-100 dark:bg-red-900/30 text-red-600 dark:text-red-400';
      default: return '';
    }
  };

  const getTextoEstado = (estado: string) => {
    switch (estado) {
      case 'pendiente': return 'Pendiente';
      case 'ok': return 'Completado';
      case 'sin-asignaciones': return 'Sin asignaciones';
      case 'sin-disponibilidad': return 'Sin disponibilidad';
      case 'error': return 'Error';
      default: return '';
    }
  };

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

  const ok = resultados.filter(r => r.estado === 'ok').length;
  const sinAsig = resultados.filter(r => r.estado === 'sin-asignaciones').length;
  const sinDisp = resultados.filter(r => r.estado === 'sin-disponibilidad').length;
  const errores = resultados.filter(r => r.estado === 'error').length;

  const turnoActual = turnos.find(t => t.id === turnoSeleccionado);

  return (
    <div className="p-6 max-w-7xl mx-auto">
      {/* Encabezado */}
      <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4 mb-6">
        <div>
          <h1 className="text-3xl font-bold text-gray-800 dark:text-white">
            📋 Generación Automática de Horarios
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
            onClick={cargarCatalogos}
            disabled={generando}
            className="flex items-center gap-2 bg-gray-200 dark:bg-gray-700 hover:bg-gray-300 dark:hover:bg-gray-600 text-gray-700 dark:text-gray-300 px-4 py-2.5 rounded-lg shadow-md transition disabled:opacity-50"
          >
            <MdRefresh className="text-xl" />
            Recargar
          </button>
          <button
            onClick={validarAntesDeGenerar}
            disabled={validando || generando || resultados.length === 0}
            className="flex items-center gap-2 bg-yellow-500 hover:bg-yellow-600 text-white px-4 py-2.5 rounded-lg shadow-md transition disabled:opacity-50"
          >
            <MdCheckCircle className="text-xl" />
            {validando ? 'Validando...' : 'Validar'}
          </button>
          <button
            onClick={generarTodos}
            disabled={generando || resultados.length === 0}
            className="flex items-center gap-2 bg-blue-600 hover:bg-blue-700 text-white px-6 py-2.5 rounded-lg shadow-md transition disabled:opacity-50"
          >
            <MdSchedule className="text-xl" />
            {generando ? 'Generando...' : 'Generar Todos'}
          </button>
        </div>
      </div>

      {/* 🔥 Selector de turno */}
      <div className="bg-white dark:bg-gray-800 rounded-xl shadow-md p-4 mb-6 border border-gray-100 dark:border-gray-700">
        <div className="flex flex-col sm:flex-row items-start sm:items-center gap-3">
          <label className="text-sm font-medium text-gray-700 dark:text-gray-300 whitespace-nowrap">
            <MdSchedule className="inline mr-1" />
            Turno a generar:
          </label>
          <select
            value={turnoSeleccionado}
            onChange={handleTurnoChange}
            disabled={generando}
            className="w-full sm:max-w-md px-4 py-2.5 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:opacity-50"
          >
            <option value={0}>Todos los turnos ({grupos.length} grupos)</option>
            {turnos.map((t) => {
              const cuenta = grupos.filter(g => g.turnoId === t.id).length;
              return (
                <option key={t.id} value={t.id}>
                  {t.nombre} ({cuenta} grupos)
                </option>
              );
            })}
          </select>
          <span className="text-sm text-gray-500 dark:text-gray-400">
            {resultados.length} grupos se incluirán
          </span>
        </div>
      </div>

      {/* Resumen */}
      {resultados.length > 0 && !generando && (
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mb-6">
          <div className="bg-white dark:bg-gray-800 rounded-xl shadow-md p-4 border border-gray-100 dark:border-gray-700">
            <div className="flex items-center gap-3">
              <div className="p-2 bg-gray-100 dark:bg-gray-700 rounded-lg">
                <MdSchool className="text-2xl text-gray-600 dark:text-gray-400" />
              </div>
              <div>
                <p className="text-xs text-gray-500 dark:text-gray-400">Total</p>
                <p className="text-2xl font-bold text-gray-800 dark:text-white">{resultados.length}</p>
              </div>
            </div>
          </div>
          <div className="bg-white dark:bg-gray-800 rounded-xl shadow-md p-4 border border-gray-100 dark:border-gray-700">
            <div className="flex items-center gap-3">
              <div className="p-2 bg-green-100 dark:bg-green-900/30 rounded-lg">
                <MdCheckCircle className="text-2xl text-green-600 dark:text-green-400" />
              </div>
              <div>
                <p className="text-xs text-gray-500 dark:text-gray-400">Completados</p>
                <p className="text-2xl font-bold text-green-600 dark:text-green-400">{ok}</p>
              </div>
            </div>
          </div>
          <div className="bg-white dark:bg-gray-800 rounded-xl shadow-md p-4 border border-gray-100 dark:border-gray-700">
            <div className="flex items-center gap-3">
              <div className="p-2 bg-yellow-100 dark:bg-yellow-900/30 rounded-lg">
                <MdWarning className="text-2xl text-yellow-600 dark:text-yellow-400" />
              </div>
              <div>
                <p className="text-xs text-gray-500 dark:text-gray-400">Sin asignaciones</p>
                <p className="text-2xl font-bold text-yellow-600 dark:text-yellow-400">{sinAsig}</p>
              </div>
            </div>
          </div>
          <div className="bg-white dark:bg-gray-800 rounded-xl shadow-md p-4 border border-gray-100 dark:border-gray-700">
            <div className="flex items-center gap-3">
              <div className="p-2 bg-orange-100 dark:bg-orange-900/30 rounded-lg">
                <MdCancel className="text-2xl text-orange-600 dark:text-orange-400" />
              </div>
              <div>
                <p className="text-xs text-gray-500 dark:text-gray-400">Sin disponibilidad</p>
                <p className="text-2xl font-bold text-orange-600 dark:text-orange-400">{sinDisp}</p>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Métricas */}
      {metricas && !generando && (
        <div className="bg-gradient-to-r from-blue-50 to-indigo-50 dark:from-blue-900/20 dark:to-indigo-900/20 rounded-xl shadow-md p-6 mb-6 border border-blue-200 dark:border-blue-800">
          <h3 className="text-lg font-bold text-gray-800 dark:text-white mb-4 flex items-center gap-2">
            <MdTimer className="text-2xl text-blue-600 dark:text-blue-400" />
            Métricas de la generación
          </h3>
          <div className="grid grid-cols-2 md:grid-cols-5 gap-4">
            <div>
              <p className="text-xs text-gray-600 dark:text-gray-400">Hard Score</p>
              <p className={`text-xl font-bold ${metricas.hardScore >= 0 ? 'text-green-600 dark:text-green-400' : 'text-red-600 dark:text-red-400'}`}>
                {metricas.hardScore}
              </p>
            </div>
            <div>
              <p className="text-xs text-gray-600 dark:text-gray-400">Soft Score</p>
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
              <p className="text-xs text-gray-600 dark:text-gray-400">Asignaciones</p>
              <p className="text-xl font-bold text-gray-800 dark:text-white">{metricas.totalAsignaciones}</p>
            </div>
          </div>
        </div>
      )}

      {/* Barra de progreso */}
      {generando && (
        <div className="bg-white dark:bg-gray-800 rounded-xl shadow-md p-6 mb-6 border border-gray-100 dark:border-gray-700">
          <div className="flex items-center justify-between mb-3">
            <span className="text-sm font-medium text-gray-700 dark:text-gray-300">
              🔄 El solver está resolviendo {resultados.length} grupos
              {turnoActual && ` del turno ${turnoActual.nombre}`}...
            </span>
          </div>
          <div className="w-full bg-gray-200 dark:bg-gray-700 rounded-full h-2.5 overflow-hidden">
            <div className="bg-blue-600 h-2.5 rounded-full animate-pulse w-3/4" />
          </div>
          <p className="text-xs text-gray-500 dark:text-gray-400 mt-2">
            Este proceso puede tardar entre 30 y 120 segundos.
          </p>
        </div>
      )}

      {error && (
        <div className="bg-red-50 dark:bg-red-900/30 text-red-700 dark:text-red-300 p-4 rounded-lg border border-red-200 dark:border-red-800 mb-6 whitespace-pre-line">
          {error}
        </div>
      )}

      {mostrarProblemas && problemas.length > 0 && (
        <div className="bg-yellow-50 dark:bg-yellow-900/20 border border-yellow-200 dark:border-yellow-800 rounded-xl p-4 mb-6">
          <div className="flex items-center justify-between mb-3">
            <h3 className="font-semibold text-yellow-800 dark:text-yellow-200 flex items-center gap-2">
              <MdWarning />
              Problemas detectados ({problemas.length})
            </h3>
            <button
              onClick={() => setMostrarProblemas(false)}
              className="text-yellow-600 dark:text-yellow-400 hover:text-yellow-800 text-sm"
            >
              Ocultar
            </button>
          </div>
          <ul className="space-y-2 max-h-60 overflow-y-auto">
            {problemas.map((p, idx) => (
              <li
                key={idx}
                className={`text-sm p-2 rounded ${
                  p.startsWith('❌')
                    ? 'bg-red-50 dark:bg-red-900/20 text-red-700 dark:text-red-300'
                    : 'bg-yellow-50 dark:bg-yellow-900/20 text-yellow-700 dark:text-yellow-300'
                }`}
              >
                {p}
              </li>
            ))}
          </ul>
          <p className="text-xs text-yellow-600 dark:text-yellow-400 mt-3">
            Los problemas con ❌ bloquean la generación. Los ⚠️ son advertencias.
          </p>
        </div>
      )}

      {success && (
        <div className="bg-green-50 dark:bg-green-900/30 text-green-700 dark:text-green-300 p-4 rounded-lg border border-green-200 dark:border-green-800 mb-6">
          {success}
        </div>
      )}

      {/* Tabla de resultados */}
      <div className="bg-white dark:bg-gray-800 rounded-xl shadow-md overflow-hidden border border-gray-100 dark:border-gray-700">
        <div className="px-4 py-3 bg-gray-50 dark:bg-gray-700/50 border-b border-gray-200 dark:border-gray-700 flex items-center gap-2">
          <MdClass className="text-xl text-blue-600 dark:text-blue-400" />
          <h3 className="font-semibold text-gray-800 dark:text-white">
            Grupos y Estado
            {turnoActual && (
              <span className="ml-2 text-sm font-normal text-indigo-600 dark:text-indigo-400">
                (Turno: {turnoActual.nombre})
              </span>
            )}
          </h3>
        </div>
        <div className="overflow-x-auto">
          <table className="min-w-full divide-y divide-gray-200 dark:divide-gray-700">
            <thead className="bg-gray-50 dark:bg-gray-700/50">
              <tr>
                <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-400 uppercase">
                  Grupo
                </th>
                <th className="px-4 py-3 text-center text-xs font-medium text-gray-500 dark:text-gray-400 uppercase">
                  Estado
                </th>
                <th className="px-4 py-3 text-center text-xs font-medium text-gray-500 dark:text-gray-400 uppercase">
                  Clases
                </th>
                <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-400 uppercase">
                  Detalle
                </th>
              </tr>
            </thead>
            <tbody className="bg-white dark:bg-gray-800 divide-y divide-gray-200 dark:divide-gray-700">
              {resultados.map((r) => (
                <tr key={r.grupoId} className="hover:bg-gray-50 dark:hover:bg-gray-700/50">
                  <td className="px-4 py-3 whitespace-nowrap">
                    <div className="flex items-center gap-2">
                      <div className="w-8 h-8 rounded-full bg-blue-100 dark:bg-blue-900/40 flex items-center justify-center text-blue-600 dark:text-blue-400 text-xs font-bold">
                        {r.grupoNombre.charAt(0)}
                      </div>
                      <span className="text-sm font-medium text-gray-900 dark:text-white">
                        {r.grupoNombre}
                      </span>
                    </div>
                  </td>
                  <td className="px-4 py-3 whitespace-nowrap text-center">
                    <div className="flex items-center justify-center gap-2">
                      {getIconoEstado(r.estado)}
                      <span className={`text-xs font-medium px-2 py-1 rounded-full ${getColorEstado(r.estado)}`}>
                        {getTextoEstado(r.estado)}
                      </span>
                    </div>
                  </td>
                  <td className="px-4 py-3 whitespace-nowrap text-center">
                    {r.clases != null ? (
                      <span className="inline-flex items-center px-2.5 py-1 rounded-full text-xs font-medium bg-blue-100 dark:bg-blue-900/30 text-blue-800 dark:text-blue-300">
                        {r.clases} clases
                      </span>
                    ) : (
                      <span className="text-gray-400">-</span>
                    )}
                  </td>
                  <td className="px-4 py-3 text-sm text-gray-600 dark:text-gray-400 max-w-md truncate">
                    {r.mensaje || '-'}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
};

export default HorarioAutomatico;