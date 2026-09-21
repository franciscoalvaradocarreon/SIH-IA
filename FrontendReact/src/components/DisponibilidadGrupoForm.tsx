import React, { useState, useEffect, useRef } from 'react';
import { useNavigate, useParams, useLocation } from 'react-router-dom';
import { disponibilidadGrupoService } from '../api/disponibilidadGrupoService';
import { grupoService } from '../api/grupoService';
import { turnoHorarioService } from '../api/turnoHorarioService';
import { turnoService } from '../api/turnoService';
import { useAuth } from '../context/AuthContext';
import type { Grupo, Turno, TurnoHorario } from '../types';
import {
  MdSave, MdCancel, MdClass, MdSchedule,
  MdCheck, MdClose, MdWarning
} from 'react-icons/md';

const DIAS_SEMANA = [
  { value: 1, label: 'Lunes' },
  { value: 2, label: 'Martes' },
  { value: 3, label: 'Miércoles' },
  { value: 4, label: 'Jueves' },
  { value: 5, label: 'Viernes' },
];

const DisponibilidadGrupoForm: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const location = useLocation();
  const { semestreActivo } = useAuth();
  const isEdit = Boolean(id);

  const queryParams = new URLSearchParams(location.search);
  const turnoIdFromUrl = parseInt(queryParams.get('turnoId') || '0');
  const grupoIdFromUrl = parseInt(queryParams.get('grupoId') || '0');

  const aplicarTurnoInicial = useRef(turnoIdFromUrl > 0);
  const aplicarGrupoInicial = useRef(isEdit ? true : grupoIdFromUrl > 0);

  const [grupos, setGrupos] = useState<Grupo[]>([]);
  const [turnos, setTurnos] = useState<Turno[]>([]);
  const [horarios, setHorarios] = useState<TurnoHorario[]>([]);
  const [grupoSeleccionado, setGrupoSeleccionado] = useState<number>(0);
  const [turnoSeleccionado, setTurnoSeleccionado] = useState<number>(0);
  const [disponibilidades, setDisponibilidades] = useState<Map<number, boolean>>(new Map());
  const [loading, setLoading] = useState(true);
  const [cargandoGrupos, setCargandoGrupos] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  const extraerHorarios = (respuesta: any): TurnoHorario[] => {
    if (respuesta?.data) {
      if (Array.isArray(respuesta.data)) return respuesta.data;
      if (respuesta.data.data && Array.isArray(respuesta.data.data)) return respuesta.data.data;
    }
    if (Array.isArray(respuesta)) return respuesta;
    return [];
  };

  const extraerDisponibilidades = (respuesta: any): any[] => {
    if (respuesta?.data) {
      if (Array.isArray(respuesta.data)) return respuesta.data;
      if (respuesta.data.data && Array.isArray(respuesta.data.data)) return respuesta.data.data;
    }
    if (Array.isArray(respuesta)) return respuesta;
    return [];
  };

  // 🔥 EFECTO 1: Carga inicial (turnos)
  useEffect(() => {
    cargarTurnos();
  }, [semestreActivo?.id]);

  // 🔥 EFECTO 2: Cargar grupos al cambiar turno
  useEffect(() => {
    if (!loading) {
      cargarGrupos(turnoSeleccionado);
    }
  }, [turnoSeleccionado]);

  // 🔥 EFECTO 3: Cargar horarios y disponibilidades
  useEffect(() => {
    if (turnoSeleccionado > 0 && grupoSeleccionado > 0) {
      cargarTodo();
    } else {
      setHorarios([]);
      setDisponibilidades(new Map());
    }
  }, [turnoSeleccionado, grupoSeleccionado]);

  const cargarTodo = async () => {
    if (turnoSeleccionado === 0 || grupoSeleccionado === 0) return;
    await cargarHorariosTurno(turnoSeleccionado);
    await cargarDisponibilidades(grupoSeleccionado);
  };

  // 🔥 Cargar turnos activos
  const cargarTurnos = async () => {
    setLoading(true);
    try {
      const semestreId = semestreActivo?.id;
      if (!semestreId) {
        setError('No hay semestre activo');
        setLoading(false);
        return;
      }

      const res = await turnoService.listar(0, 100, '', semestreId);
      const activos = res.data.content.filter((t: Turno) => t.activo === true);
      setTurnos(activos);

      let turnoId = 0;
      if (aplicarTurnoInicial.current && turnoIdFromUrl > 0) {
        const existe = activos.some((t: Turno) => t.id === turnoIdFromUrl);
        aplicarTurnoInicial.current = false;
        if (existe) {
          turnoId = turnoIdFromUrl;
        }
      }
      if (turnoId === 0 && activos.length > 0) {
        turnoId = activos[0].id;
      }

      setTurnoSeleccionado(turnoId);
    } catch (error) {
      console.error('Error al cargar turnos:', error);
      setError('Error al cargar los datos iniciales');
    } finally {
      setLoading(false);
    }
  };

  // 🔥 Cargar grupos filtrados por turno
  const cargarGrupos = async (turnoId: number) => {
    if (!semestreActivo?.id) return;
    setCargandoGrupos(true);
    try {
      const res = await grupoService.listar(
        0,
        100,
        '',
        0,
        semestreActivo.id,
        turnoId > 0 ? turnoId : undefined
      );
      const activos = res.data.content.filter((g: Grupo) => g.activo === true);
      setGrupos(activos);

      // 🔥 Fix: mutar el ref FUERA del updater (StrictMode invoca dos veces)
      const aplicarDesdeUrl = aplicarGrupoInicial.current && grupoIdFromUrl > 0;
      if (aplicarDesdeUrl) {
        aplicarGrupoInicial.current = false;
      }

      const grupoDesdeUrl =
        aplicarDesdeUrl && activos.some((g: Grupo) => g.id === grupoIdFromUrl)
          ? grupoIdFromUrl
          : null;

      setGrupoSeleccionado((prev) => {
        // 🔥 Si venimos de edición, el grupo es el del path param
        if (isEdit && id) {
          const idNum = Number(id);
          if (activos.some((g: Grupo) => g.id === idNum)) {
            return idNum;
          }
        }
        if (grupoDesdeUrl !== null) return grupoDesdeUrl;
        if (prev > 0 && activos.some((g: Grupo) => g.id === prev)) return prev;
        return activos.length > 0 ? activos[0].id : 0;
      });
    } catch (error) {
      console.error('Error al cargar grupos:', error);
      setGrupos([]);
    } finally {
      setCargandoGrupos(false);
    }
  };

  // Cargar horarios del turno
  const cargarHorariosTurno = async (turnoId: number) => {
    try {
      const semestreId = semestreActivo?.id;
      if (!semestreId) {
        setHorarios([]);
        return;
      }
      const res = await turnoHorarioService.listar(turnoId, semestreId);
      const data = extraerHorarios(res);
      setHorarios(data.filter((h: TurnoHorario) => !h.descanso));
    } catch (error) {
      console.error('Error al cargar horarios:', error);
      setHorarios([]);
    }
  };

  // Cargar disponibilidades del grupo
  const cargarDisponibilidades = async (grupoId: number) => {
    try {
      const semestreId = semestreActivo?.id;
      if (!semestreId) {
        setDisponibilidades(new Map());
        return;
      }
      const res = await disponibilidadGrupoService.obtenerPorGrupo(grupoId, semestreId);
      const data = extraerDisponibilidades(res);
      const mapa = new Map<number, boolean>();
      data.forEach((d) => {
        mapa.set(d.turnoHorarioId, d.disponible);
      });
      setDisponibilidades(mapa);
    } catch (error) {
      console.error('Error al cargar disponibilidades:', error);
      setDisponibilidades(new Map());
    }
  };

  const toggleDisponibilidad = (turnoHorarioId: number) => {
    const nuevoMapa = new Map(disponibilidades);
    const actual = nuevoMapa.get(turnoHorarioId) || false;
    nuevoMapa.set(turnoHorarioId, !actual);
    setDisponibilidades(nuevoMapa);
  };

  // 🔥 Volver al visor preservando grupo y turno
  const volverAlVisor = () => {
    if (grupoSeleccionado > 0) {
      navigate(`/horarios/disponibilidad-grupo?grupoId=${grupoSeleccionado}&turnoId=${turnoSeleccionado}`);
    } else {
      navigate('/horarios/disponibilidad-grupo');
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setSuccess('');
    setSaving(true);

    if (grupoSeleccionado === 0) {
      setError('Selecciona un grupo');
      setSaving(false);
      return;
    }

    try {
      const semestreId = semestreActivo?.id;
      if (!semestreId) {
        setError('No hay semestre activo');
        setSaving(false);
        return;
      }

      for (const [turnoHorarioId, disponible] of disponibilidades) {
        await disponibilidadGrupoService.guardar({
          grupoId: grupoSeleccionado,
          turnoHorarioId,
          disponible,
          semestreId,
        });
      }

      setSuccess('Disponibilidad guardada correctamente');
      setTimeout(() => {
        volverAlVisor();
      }, 1500);
    } catch (err: any) {
      console.error('Error al guardar:', err);
      setError(err.response?.data?.message || 'Error al guardar la disponibilidad');
    } finally {
      setSaving(false);
    }
  };

  const handleCancel = () => {
    volverAlVisor();
  };

  const handleTurnoChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    setTurnoSeleccionado(Number(e.target.value));
  };

  const handleGrupoChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    setGrupoSeleccionado(Number(e.target.value));
  };

  const getOrdenesUnicos = () => {
    const ordenes = horarios.map(h => h.orden || 0);
    return [...new Set(ordenes)].sort((a, b) => a - b);
  };

  const getHorarioPorDiaYOrden = (dia: number, orden: number) => {
    return horarios.find(h => h.diaSemana === dia && h.orden === orden);
  };

  const formatearHora = (hora: string) => {
    if (!hora) return '';
    return hora.substring(0, 5);
  };

  if (loading) {
    return (
      <div className="flex justify-center items-center h-64">
        <div className="animate-spin rounded-full h-12 w-12 border-4 border-blue-500 border-t-transparent"></div>
      </div>
    );
  }

  if (!semestreActivo) {
    return (
      <div className="p-6 text-center text-yellow-600 dark:text-yellow-400">
        <p className="text-lg font-semibold">⚠️ No hay semestre activo</p>
      </div>
    );
  }

  return (
    <div className="max-w-6xl mx-auto p-6">
      <div className="bg-white dark:bg-gray-800 rounded-xl shadow-md p-6 md:p-8 border border-gray-400 dark:border-gray-700">
        <h1 className="text-2xl font-bold text-gray-800 dark:text-white mb-6">
          {isEdit ? 'Editar Disponibilidad de Grupo' : 'Configurar Disponibilidad de Grupo'}
        </h1>
        {error && (
          <div className="bg-red-50 dark:bg-red-900/30 text-red-700 dark:text-red-300 p-4 rounded-lg border border-red-200 dark:border-red-800 mb-6">
            {error}
          </div>
        )}

        {success && (
          <div className="bg-green-50 dark:bg-green-900/30 text-green-700 dark:text-green-300 p-4 rounded-lg border border-green-200 dark:border-green-800 mb-6">
            {success}
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-6">
          {/* Turno + Grupo */}
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            {/* Turno */}
            <div>
              <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                <MdSchedule className="inline mr-1" />
                Turno *
              </label>
              <select
                value={turnoSeleccionado}
                onChange={handleTurnoChange}
                required
                disabled={turnos.length === 0}
                className="w-full px-4 py-2.5 border border-gray-400 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:opacity-50"
              >
                <option value={0}>Seleccionar turno...</option>
                {turnos.map((turno) => (
                  <option key={turno.id} value={turno.id}>
                    {turno.nombre}
                  </option>
                ))}
              </select>
              <p className="text-xs text-gray-400 mt-1">
                El turno define qué grupos están disponibles
              </p>
              {turnos.length === 0 && (
                <p className="text-xs text-yellow-600 dark:text-yellow-400 mt-1">
                  No hay turnos activos en este semestre
                </p>
              )}
            </div>

            {/* Grupo (filtrado por turno) */}
            <div>
              <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                <MdClass className="inline mr-1" />
                Grupo *
              </label>
              <select
                value={grupoSeleccionado}
                onChange={handleGrupoChange}
                required
                disabled={cargandoGrupos || grupos.length === 0}
                className="w-full px-4 py-2.5 border border-gray-400 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:opacity-50"
              >
                <option value={0}>
                  {cargandoGrupos
                    ? 'Cargando grupos...'
                    : grupos.length === 0
                    ? 'Sin grupos en este turno'
                    : 'Seleccionar grupo...'}
                </option>
                {grupos.map((g) => {
                  let espTexto = '';
                  if (g.especialidad) {
                    espTexto = typeof g.especialidad === 'string'
                      ? g.especialidad
                      : g.especialidad.nombre || '';
                  }
                  return (
                    <option key={g.id} value={g.id}>
                      {g.nombre} - {g.grado}°
                      {espTexto ? ` (${espTexto})` : ''}
                    </option>
                  );
                })}
              </select>
              {turnoSeleccionado > 0 && !cargandoGrupos && grupos.length === 0 && (
                <p className="text-xs text-yellow-600 dark:text-yellow-400 mt-1">
                  Este turno no tiene grupos asignados
                </p>
              )}
            </div>
          </div>

          {/* Matriz de bloques */}
          {grupoSeleccionado > 0 && horarios.length > 0 && (
            <div className="border border-gray-400 dark:border-gray-700 rounded-lg overflow-hidden">
              <div className="overflow-x-auto">
                <table className="min-w-full divide-y divide-gray-400 dark:divide-gray-700">
                  <thead className="bg-gray-50 dark:bg-gray-700/50">
                    <tr>
                      <th className="px-3 py-2 text-left text-xs font-medium text-gray-500 dark:text-gray-400 uppercase">
                        Hora
                      </th>
                      {DIAS_SEMANA.map((dia) => (
                        <th
                          key={dia.value}
                          className="px-3 py-2 text-center text-xs font-medium text-gray-500 dark:text-gray-400 uppercase"
                        >
                          {dia.label}
                        </th>
                      ))}
                    </tr>
                  </thead>
                  <tbody className="bg-white dark:bg-gray-800 divide-y divide-gray-400 dark:divide-gray-700">
                    {getOrdenesUnicos().map((orden) => {
                      const ref = horarios.find(h => h.orden === orden);
                      if (!ref) return null;

                      return (
                        <tr key={orden} className="hover:bg-gray-50 dark:hover:bg-gray-700/50">
                          <td className="px-3 py-2 whitespace-nowrap text-sm text-gray-700 dark:text-gray-300">
                            {formatearHora(ref.horaInicio)} - {formatearHora(ref.horaFin)}
                          </td>
                          {DIAS_SEMANA.map((dia) => {
                            const h = getHorarioPorDiaYOrden(dia.value, orden);
                            const disponible = h ? disponibilidades.get(h.id) || false : false;
                            const existe = h !== undefined;

                            return (
                              <td key={dia.value} className="px-3 py-2 text-center">
                                {existe ? (
                                  <button
                                    type="button"
                                    onClick={() => toggleDisponibilidad(h.id)}
                                    className={`w-9 h-9 rounded-lg transition-all flex items-center justify-center mx-auto ${
                                      disponible
                                        ? 'bg-green-500 hover:bg-green-600 text-white shadow-md shadow-green-200 dark:shadow-none'
                                        : 'bg-gray-200 dark:bg-gray-700 hover:bg-gray-300 dark:hover:bg-gray-600 text-gray-500 dark:text-gray-400'
                                    }`}
                                    title={disponible ? 'Habilitado' : 'Disponible'}
                                  >
                                    {disponible ? <MdCheck className="text-xl" /> : <MdClose className="text-xl" />}
                                  </button>
                                ) : (
                                  <span className="text-gray-300 dark:text-gray-600">-</span>
                                )}
                              </td>
                            );
                          })}
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
              <div className="px-4 py-3 bg-gray-50 dark:bg-gray-700/30 border-t border-gray-400 dark:border-gray-700 text-sm text-gray-500 dark:text-gray-400">
                <span className="inline-flex items-center gap-2 mr-4">
                  <span className="w-4 h-4 bg-green-500 rounded"></span> Habilitado
                </span>
                <span className="inline-flex items-center gap-2">
                  <span className="w-4 h-4 bg-gray-300 dark:bg-gray-600 rounded"></span> Disponible
                </span>
                <span className="text-lg text-gray-400 ml-12">
                  {horarios.length} bloques Disponibles· {Array.from(disponibilidades.values()).filter(v => v === true).length} habilitados
                </span>
              </div>
            </div>
          )}

          {grupoSeleccionado === 0 && (
            <div className="text-center py-8 text-gray-500 dark:text-gray-400">
              <MdClass className="text-4xl mx-auto mb-2 text-gray-300 dark:text-gray-600" />
              <p>Selecciona un turno y un grupo para configurar su disponibilidad</p>
            </div>
          )}

          {grupoSeleccionado > 0 && horarios.length === 0 && !loading && !cargandoGrupos && (
            <div className="text-center py-8 text-yellow-600 dark:text-yellow-400 bg-yellow-50 dark:bg-yellow-900/20 rounded-lg border border-yellow-200 dark:border-yellow-800">
              <MdWarning className="text-4xl mx-auto mb-2" />
              <p className="font-medium">El turno no tiene bloques configurados</p>
              <p className="text-sm mt-1">Configura los bloques del turno primero</p>
            </div>
          )}

          {/* Botones */}
          <div className="flex gap-3 pt-4 border-t border-gray-400 dark:border-gray-700">
            <button
              type="submit"
              disabled={saving || grupoSeleccionado === 0 || horarios.length === 0}
              className={`flex items-center gap-2 flex-1 bg-blue-600 hover:bg-blue-700 text-white px-6 py-2.5 rounded-lg font-medium transition shadow-sm ${
                saving || grupoSeleccionado === 0 || horarios.length === 0
                  ? 'opacity-50 cursor-not-allowed'
                  : ''
              }`}
            >
              <MdSave className="text-xl" />
              {saving ? 'Guardando...' : 'Guardar Disponibilidad'}
            </button>
            <button
              type="button"
              onClick={handleCancel}
              className="flex items-center gap-2 px-6 py-2.5 border border-gray-400 dark:border-gray-600 rounded-lg text-gray-700 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700 transition"
            >
              <MdCancel className="text-xl" />
              Cancelar
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

export default DisponibilidadGrupoForm;