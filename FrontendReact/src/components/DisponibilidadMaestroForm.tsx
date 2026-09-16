import React, { useState, useEffect, useRef } from 'react';
import { useNavigate, useParams, useLocation } from 'react-router-dom';
import { disponibilidadService } from '../api/disponibilidadService';
import { maestroService } from '../api/maestroService';
import { turnoHorarioService } from '../api/turnoHorarioService';
import { turnoService } from '../api/turnoService';
import { useAuth } from '../context/AuthContext';
import type { Maestro, Turno, TurnoHorario } from '../types';
import {
  MdSave, MdCancel, MdPerson, MdSchedule,
  MdCheck, MdClose, MdWarning
} from 'react-icons/md';

const DIAS_SEMANA = [
  { value: 1, label: 'Lunes' },
  { value: 2, label: 'Martes' },
  { value: 3, label: 'Miércoles' },
  { value: 4, label: 'Jueves' },
  { value: 5, label: 'Viernes' },
];

const DisponibilidadMaestroForm: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const location = useLocation();
  const { semestreActivo } = useAuth();
  const isEdit = Boolean(id);

  // 🔥 Leer query params: turnoId y maestroId (por si vienen)
  const queryParams = new URLSearchParams(location.search);
  const turnoIdFromUrl = parseInt(queryParams.get('turnoId') || '0');
  const maestroIdFromUrl = parseInt(queryParams.get('maestroId') || '0');

  // 🔥 Refs para aplicar la preselección solo en la primera carga
  const aplicarTurnoInicial = useRef(turnoIdFromUrl > 0);
  const aplicarMaestroInicial = useRef(
    // Si venimos de edición, el maestro es el del path param
    isEdit ? true : maestroIdFromUrl > 0
  );

  // Estados
  const [maestros, setMaestros] = useState<Maestro[]>([]);
  const [turnos, setTurnos] = useState<Turno[]>([]);
  const [horarios, setHorarios] = useState<TurnoHorario[]>([]);
  const [maestroSeleccionado, setMaestroSeleccionado] = useState<number>(0);
  const [turnoSeleccionado, setTurnoSeleccionado] = useState<number>(0);
  const [disponibilidades, setDisponibilidades] = useState<Map<number, boolean>>(new Map());
  const [loading, setLoading] = useState(true);
  const [cargandoMaestros, setCargandoMaestros] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  // Helper para extraer horarios
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

  // 🔥 EFECTO 1: Carga inicial (turnos) al cambiar semestre
  useEffect(() => {
    cargarTurnos();
  }, [semestreActivo?.id]);

  // 🔥 EFECTO 2: Recargar maestros cuando cambia el turno
  useEffect(() => {
    if (!loading) {
      cargarMaestros(turnoSeleccionado);
    }
  }, [turnoSeleccionado]);

  // 🔥 EFECTO 3: Cargar horarios y disponibilidades cuando cambia maestro o turno
  useEffect(() => {
    if (turnoSeleccionado > 0 && maestroSeleccionado > 0) {
      cargarTodo();
    } else {
      setHorarios([]);
      setDisponibilidades(new Map());
    }
  }, [turnoSeleccionado, maestroSeleccionado]);

  const cargarTodo = async () => {
    if (turnoSeleccionado === 0 || maestroSeleccionado === 0) return;
    await cargarHorariosTurno(turnoSeleccionado);
    await cargarDisponibilidadesMaestro(maestroSeleccionado);
  };

  // 🔥 Cargar turnos activos y hacer la selección inicial
  const cargarTurnos = async () => {
    setLoading(true);
    try {
      const semestreId = semestreActivo?.id;
      if (!semestreId) {
        setError('No hay semestre activo');
        setLoading(false);
        return;
      }

      const turnosRes = await turnoService.listar(0, 100, '', semestreId);
      const turnosActivos = turnosRes.data.content.filter((t: Turno) => t.activo === true);
      setTurnos(turnosActivos);

      // 🔥 Determinar turno inicial
      let turnoId = 0;
      if (aplicarTurnoInicial.current && turnoIdFromUrl > 0) {
        const existe = turnosActivos.some((t: Turno) => t.id === turnoIdFromUrl);
        aplicarTurnoInicial.current = false;
        if (existe) {
          turnoId = turnoIdFromUrl;
        }
      }
      if (turnoId === 0 && turnosActivos.length > 0) {
        turnoId = turnosActivos[0].id;
      }

      setTurnoSeleccionado(turnoId);
    } catch (error) {
      console.error('Error al cargar turnos:', error);
      setError('Error al cargar los datos iniciales');
    } finally {
      setLoading(false);
    }
  };

  // 🔥 Cargar maestros filtrados por turno
  const cargarMaestros = async (turnoId: number) => {
    if (!semestreActivo?.id) return;
    setCargandoMaestros(true);
    try {
      const res = await maestroService.listar(
        0,
        100,
        '',
        semestreActivo.id,
        turnoId > 0 ? turnoId : undefined
      );
      const activos = res.data.content.filter((m: Maestro) => m.activo === true);
      setMaestros(activos);

      setMaestroSeleccionado((prev) => {
        // 🔥 1. Si venimos de edición, el maestro es el del path param
        if (isEdit && id) {
          const idNum = Number(id);
          if (activos.some((m: Maestro) => m.id === idNum)) {
            return idNum;
          }
        }

        // 🔥 2. Si hay maestroId en la URL y es válido para este turno
        if (aplicarMaestroInicial.current && maestroIdFromUrl > 0) {
          aplicarMaestroInicial.current = false;
          if (activos.some((m: Maestro) => m.id === maestroIdFromUrl)) {
            return maestroIdFromUrl;
          }
        }

        // 🔥 3. Mantener el actual si sigue en la lista
        if (prev > 0 && activos.some((m: Maestro) => m.id === prev)) {
          return prev;
        }

        // 🔥 4. Fallback: el primero del turno
        return activos.length > 0 ? activos[0].id : 0;
      });
    } catch (error) {
      console.error('Error al cargar maestros:', error);
      setMaestros([]);
    } finally {
      setCargandoMaestros(false);
    }
  };

  // Cargar horarios del turno
  const cargarHorariosTurno = async (turnoId: number) => {
    try {
      const semestreId = semestreActivo?.id;
      if (!semestreId) {
        setHorarios([]);
        return [];
      }

      const res = await turnoHorarioService.listar(turnoId, semestreId);
      const horariosData = extraerHorarios(res);
      const clases = horariosData.filter((h: TurnoHorario) => !h.descanso);
      setHorarios(clases);
      return clases;
    } catch (error) {
      console.error('Error al cargar horarios del turno:', error);
      setHorarios([]);
      return [];
    }
  };

  // Cargar disponibilidades del maestro
  const cargarDisponibilidadesMaestro = async (maestroId: number) => {
    try {
      const semestreId = semestreActivo?.id;
      if (!semestreId) {
        setDisponibilidades(new Map());
        return;
      }
      const res = await disponibilidadService.obtenerPorMaestro(maestroId);
      const disponibilidadesData = extraerDisponibilidades(res);

      const mapa = new Map<number, boolean>();
      disponibilidadesData.forEach((disp) => {
        mapa.set(disp.turnoHorarioId, disp.disponible);
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

  // 🔥 Volver al visor preservando maestro y turno
  const volverAlVisor = () => {
    if (maestroSeleccionado > 0) {
      navigate(`/horarios/disponibilidad-maestro?maestroId=${maestroSeleccionado}&turnoId=${turnoSeleccionado}`);
    } else {
      navigate('/horarios/disponibilidad-maestro');
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setSuccess('');
    setSaving(true);

    if (maestroSeleccionado === 0) {
      setError('Selecciona un maestro');
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
        await disponibilidadService.guardar({
          maestroId: maestroSeleccionado,
          turnoHorarioId,
          disponible,
          semestreId,
        });
      }

      setSuccess('Disponibilidad guardada correctamente');
      setTimeout(() => {
        volverAlVisor();
      }, 1500);
    } catch (error: any) {
      console.error('Error al guardar disponibilidad:', error);
      setError(error.response?.data?.message || 'Error al guardar la disponibilidad');
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

  const handleMaestroChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    setMaestroSeleccionado(Number(e.target.value));
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

  return (
    <div className="max-w-6xl mx-auto p-6">
      <div className="bg-white dark:bg-gray-800 rounded-xl shadow-md p-6 md:p-8 border border-gray-100 dark:border-gray-700">
        <h1 className="text-2xl font-bold text-gray-800 dark:text-white mb-6">
          {isEdit ? 'Editar Disponibilidad de Maestro' : 'Configurar Disponibilidad de Maestro'}
        </h1>
        <p className="text-sm text-gray-500 dark:text-gray-400 mb-6">
          {isEdit
            ? 'Edita los bloques horarios en los que el maestro está disponible'
            : 'Selecciona un turno, luego un maestro, y marca sus bloques disponibles'}
        </p>

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
          {/* Turno + Maestro */}
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
                className="w-full px-4 py-2.5 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:opacity-50"
              >
                <option value={0}>Seleccionar turno...</option>
                {turnos.map((turno) => (
                  <option key={turno.id} value={turno.id}>
                    {turno.nombre}
                  </option>
                ))}
              </select>
              <p className="text-xs text-gray-400 mt-1">
                El turno define qué maestros están disponibles
              </p>
              {turnos.length === 0 && (
                <p className="text-xs text-yellow-600 dark:text-yellow-400 mt-1">
                  No hay turnos activos en este semestre
                </p>
              )}
            </div>

            {/* Maestro (filtrado por turno) */}
            <div>
              <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                <MdPerson className="inline mr-1" />
                Maestro *
              </label>
              <select
                value={maestroSeleccionado}
                onChange={handleMaestroChange}
                required
                disabled={cargandoMaestros || maestros.length === 0}
                className="w-full px-4 py-2.5 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:opacity-50"
              >
                <option value={0}>
                  {cargandoMaestros
                    ? 'Cargando maestros...'
                    : maestros.length === 0
                    ? 'Sin maestros en este turno'
                    : 'Seleccionar maestro...'}
                </option>
                {maestros.map((maestro) => (
                  <option key={maestro.id} value={maestro.id}>
                    {maestro.nombreCompleto}
                  </option>
                ))}
              </select>
              {turnoSeleccionado > 0 && !cargandoMaestros && maestros.length === 0 && (
                <p className="text-xs text-yellow-600 dark:text-yellow-400 mt-1">
                  Este turno no tiene maestros asignados
                </p>
              )}
            </div>
          </div>

          {/* Matriz de horarios */}
          {maestroSeleccionado > 0 && horarios.length > 0 && (
            <div className="border border-gray-200 dark:border-gray-700 rounded-lg overflow-hidden">
              <div className="overflow-x-auto">
                <table className="min-w-full divide-y divide-gray-200 dark:divide-gray-700">
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
                  <tbody className="bg-white dark:bg-gray-800 divide-y divide-gray-200 dark:divide-gray-700">
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
                                    title={disponible ? 'Disponible' : 'No disponible'}
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
              <div className="px-4 py-3 bg-gray-50 dark:bg-gray-700/30 border-t border-gray-200 dark:border-gray-700 text-sm text-gray-500 dark:text-gray-400">
                <span className="inline-flex items-center gap-2 mr-4">
                  <span className="w-4 h-4 bg-green-500 rounded"></span> Disponible
                </span>
                <span className="inline-flex items-center gap-2">
                  <span className="w-4 h-4 bg-gray-300 dark:bg-gray-600 rounded"></span> No disponible
                </span>
                <span className="text-xs text-gray-400 ml-4">
                  {horarios.length} bloques · {Array.from(disponibilidades.values()).filter(v => v === true).length} disponibles
                </span>
              </div>
            </div>
          )}

          {maestroSeleccionado === 0 && (
            <div className="text-center py-8 text-gray-500 dark:text-gray-400">
              <MdPerson className="text-4xl mx-auto mb-2 text-gray-300 dark:text-gray-600" />
              <p>Selecciona un turno y un maestro para configurar su disponibilidad</p>
            </div>
          )}

          {maestroSeleccionado > 0 && horarios.length === 0 && !loading && !cargandoMaestros && (
            <div className="text-center py-8 text-yellow-600 dark:text-yellow-400 bg-yellow-50 dark:bg-yellow-900/20 rounded-lg border border-yellow-200 dark:border-yellow-800">
              <MdWarning className="text-4xl mx-auto mb-2" />
              <p className="font-medium">No hay horarios configurados para este turno</p>
              <p className="text-sm mt-1">Configura los horarios del turno antes de asignar disponibilidad</p>
            </div>
          )}

          {/* Botones */}
          <div className="flex gap-3 pt-4 border-t border-gray-200 dark:border-gray-700">
            <button
              type="submit"
              disabled={saving || maestroSeleccionado === 0 || horarios.length === 0}
              className={`flex items-center gap-2 flex-1 bg-blue-600 hover:bg-blue-700 text-white px-6 py-2.5 rounded-lg font-medium transition shadow-sm ${
                saving || maestroSeleccionado === 0 || horarios.length === 0
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
              className="flex items-center gap-2 px-6 py-2.5 border border-gray-300 dark:border-gray-600 rounded-lg text-gray-700 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700 transition"
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

export default DisponibilidadMaestroForm;