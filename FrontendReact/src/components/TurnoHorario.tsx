import React, { useState, useEffect } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { turnoService } from '../api/turnoService';
import { turnoHorarioService } from '../api/turnoHorarioService';
import { useAuth } from '../context/AuthContext';
import type { Turno, TurnoHorario, TurnoHorarioCrear } from '../types';
import ErrorScreen from '../utils/ErrorScreen';
import 'react-datepicker/dist/react-datepicker.css';
import { SwitchToggle } from '../utils/SwitchToggle.tsx';
import {
  MdAdd, MdEdit, MdDelete, MdRefresh, MdCancel, MdAccessTime,
  MdSave, MdClose, MdWarning, MdClass, MdContentCopy
} from 'react-icons/md';

const diasSemana = [
  { value: 1, label: 'Lunes' },
  { value: 2, label: 'Martes' },
  { value: 3, label: 'Miércoles' },
  { value: 4, label: 'Jueves' },
  { value: 5, label: 'Viernes' },
];

const TurnoHorario: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();

  // 🔥 Obtener el semestre activo del contexto
  const { semestreActivo } = useAuth();

  // Estado principal
  const [turnos, setTurnos] = useState<Turno[]>([]);
  const [turnosActivos, setTurnosActivos] = useState<Turno[]>([]);
  const [turnoSeleccionado, setTurnoSeleccionado] = useState<number>(
    id ? Number(id) : 0
  );
  const [turnoActual, setTurnoActual] = useState<Turno | null>(null);
  const [horarios, setHorarios] = useState<TurnoHorario[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  // Estado del formulario
  const [showForm, setShowForm] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [form, setForm] = useState<TurnoHorarioCrear>({
    diaSemana: 1,
    horaInicio: '07:00',
    horaFin: '07:50',
    descanso: false,
    orden: 0,
    semestreId: semestreActivo?.id || 0,
  });
  const [formError, setFormError] = useState('');

  // 🔥 Estados para modal de confirmación y pantalla de error
  const [modalEliminar, setModalEliminar] = useState<{
    abierto: boolean;
    horario: TurnoHorario | null;
  }>({ abierto: false, horario: null });

  const [eliminando, setEliminando] = useState(false);

  // 🔥 Estados para copiar día
  const [copiando, setCopiando] = useState(false);
  const [modalCopiar, setModalCopiar] = useState<{
    diaDestino: number;
    diaOrigen: number;
    horariosOrigen: TurnoHorario[];
  } | null>(null);

  const [errorPantalla, setErrorPantalla] = useState<{
    titulo?: string;
    mensaje: string;
    detalles?: string;
    sugerencia?: string;
    tipo?: 'error' | 'warning' | 'info';
    acciones?: {
      label: string;
      onClick: () => void;
      tipo?: 'primary' | 'secondary';
      icono?: React.ReactNode;
    }[];
  } | null>(null);

  // 🔥 Función helper para extraer el arreglo de horarios de la respuesta
  const extraerHorarios = (respuesta: any): TurnoHorario[] => {
    if (respuesta?.data) {
      if (Array.isArray(respuesta.data)) {
        return respuesta.data;
      }
      if (respuesta.data.data && Array.isArray(respuesta.data.data)) {
        return respuesta.data.data;
      }
    }

    if (Array.isArray(respuesta)) {
      return respuesta;
    }

    if (respuesta?.data && Array.isArray(respuesta.data)) {
      return respuesta.data;
    }

    console.warn('⚠️ No se pudo extraer el arreglo de horarios:', respuesta);
    return [];
  };

  // Cargar turnos al iniciar
  useEffect(() => {
    if (semestreActivo?.id) {
      cargarTurnos();
    }
  }, [semestreActivo]);

  // Cargar horarios cuando cambia el turno seleccionado o el semestre
  useEffect(() => {
    if (turnoSeleccionado > 0) {
      const turno = turnos.find(t => t.id === turnoSeleccionado);
      setTurnoActual(turno || null);

      if (turno && turno.activo) {
        cargarHorarios(turnoSeleccionado);
      } else {
        setHorarios([]);
      }
    } else {
      setHorarios([]);
      setTurnoActual(null);
    }
  }, [turnoSeleccionado, turnos, semestreActivo]);

  useEffect(() => {
    if (turnoSeleccionado > 0 && turnoActual?.activo) {
      console.log('🔄 Semestre cambiado, recargando horarios...');
      cargarHorarios(turnoSeleccionado);
    }
  }, [semestreActivo]);

  const cargarTurnos = async () => {
    setLoading(true);
    try {
      const semestreId = semestreActivo?.id;
      const res = await turnoService.listar(0, 100, '', semestreId);
      const todosLosTurnos = res.data.content || [];
      setTurnos(todosLosTurnos);

      const soloActivos = todosLosTurnos.filter(t => t.activo === true);
      setTurnosActivos(soloActivos);

      if (id) {
        const existeActivo = soloActivos.some(t => t.id === Number(id));
        if (existeActivo) {
          setTurnoSeleccionado(Number(id));
        } else if (soloActivos.length > 0) {
          setTurnoSeleccionado(soloActivos[0].id);
          navigate(`/catalogo/turnos/horarios/${soloActivos[0].id}`, { replace: true });
        } else {
          setTurnoSeleccionado(0);
        }
      } else if (soloActivos.length > 0) {
        setTurnoSeleccionado(soloActivos[0].id);
      }
    } catch (error) {
      console.error('Error al cargar turnos:', error);
      setError('Error al cargar los turnos');
    } finally {
      setLoading(false);
    }
  };

  const cargarHorarios = async (turnoId: number) => {
    const turno = turnos.find(t => t.id === turnoId);
    if (!turno || !turno.activo) {
      setHorarios([]);
      setError('El turno no está activo. Actívalo para ver sus horarios.');
      return;
    }

    setLoading(true);
    setError('');
    try {
      const res = await turnoHorarioService.listar(turnoId, semestreActivo?.id);
      const horariosData = extraerHorarios(res);
      setHorarios(horariosData);
    } catch (error: any) {
      console.error('Error al cargar horarios:', error);
      setHorarios([]);
      if (error.response?.data?.message?.includes('no está activo')) {
        setError('Este turno no está activo. Actívalo para gestionar sus horarios.');
      } else {
        setError('Error al cargar los horarios');
      }
    } finally {
      setLoading(false);
    }
  };

  const formatearHora = (hora: string): string => {
    if (!hora) return '';
    if (hora.length > 5 && hora.includes(':')) {
      return hora.substring(0, 5);
    }
    return hora;
  };

  const handleTurnoChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    const value = Number(e.target.value);
    setTurnoSeleccionado(value);
    navigate(`/catalogo/turnos/horarios/${value}`, { replace: true });
  };

  const handleFormChange = (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => {
    const { name, value, type } = e.target;
    setForm({
      ...form,
      [name]: type === 'checkbox' ? (e.target as HTMLInputElement).checked : value,
    });
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setFormError('');

    if (!turnoActual || !turnoActual.activo) {
      setFormError('No se pueden modificar horarios de un turno inactivo');
      return;
    }

    const dataToSend = {
      ...form,
      semestreId: semestreActivo?.id || form.semestreId,
    };

    if (!dataToSend.semestreId) {
      setFormError('No hay semestre activo. Selecciona un semestre primero.');
      return;
    }

    if (form.horaInicio >= form.horaFin) {
      setFormError('La hora de inicio debe ser anterior a la hora de fin');
      return;
    }

    try {
      if (editingId) {
        await turnoHorarioService.actualizar(turnoSeleccionado, editingId, dataToSend);
      } else {
        await turnoHorarioService.crear(turnoSeleccionado, dataToSend);
      }

      await cargarHorarios(turnoSeleccionado);

      setShowForm(false);
      setEditingId(null);
      setForm({
        diaSemana: 1,
        horaInicio: '07:00',
        horaFin: '07:50',
        descanso: false,
        orden: 0,
        semestreId: semestreActivo?.id || 0,
      });
      setFormError('');
    } catch (error: any) {
      console.error('Error al guardar:', error);
      const msg = error.response?.data?.message || 'Error al guardar la hora';
      setFormError(msg);

      if (msg.includes('inactivo')) {
        cargarTurnos();
      }
    }
  };

  const handleEdit = (horario: TurnoHorario) => {
    if (!turnoActual || !turnoActual.activo) {
      setFormError('No se puede editar un horario de un turno inactivo');
      return;
    }

    setEditingId(horario.id);
    setForm({
      diaSemana: horario.diaSemana,
      horaInicio: horario.horaInicio,
      horaFin: horario.horaFin,
      descanso: horario.descanso,
      orden: horario.orden || 0,
      semestreId: semestreActivo?.id || horario.semestreId || 0,
    });
    setShowForm(true);
    setFormError('');
  };

  // 🔥 Abre modal de confirmación de eliminación
  const handleEliminar = (horario: TurnoHorario) => {
    if (!turnoActual || !turnoActual.activo) {
      setErrorPantalla({
        titulo: 'Turno inactivo',
        mensaje: 'No se puede eliminar un horario de un turno inactivo.',
        sugerencia: 'Activa el turno primero desde la administración de turnos.',
        tipo: 'warning',
        acciones: [
          {
            label: 'Aceptar',
            onClick: () => {
              setErrorPantalla(null);
            },
            tipo: 'primary',
          },
        ],
      });
      return;
    }
    setModalEliminar({ abierto: true, horario });
  };

  const confirmarEliminar = async () => {
    if (!modalEliminar.horario) return;

    setEliminando(true);
    try {
      await turnoHorarioService.eliminar(turnoSeleccionado, modalEliminar.horario.id);
      setModalEliminar({ abierto: false, horario: null });
      await cargarHorarios(turnoSeleccionado);
    } catch (error: any) {
      console.error('Error al eliminar:', error);
      const msg = error.response?.data?.message || 'Error al eliminar el bloque';
      setModalEliminar({ abierto: false, horario: null });

      // 🔥 Detectar si es error de dependencias
      const esDependencia = msg.includes('está siendo usado') ||
                            msg.includes('dependencias') ||
                            msg.includes('foreign key') ||
                            msg.includes('viola la llave');

      if (esDependencia) {
        setErrorPantalla({
          titulo: 'No se puede eliminar el bloque',
          mensaje: msg,
          sugerencia: 'Desvincula primero este bloque desde la pantalla de disponibilidad de grupos, o regenera los horarios afectados.',
          tipo: 'error',
          acciones: [
            {
              label: 'Aceptar',
              onClick: () => {
                setErrorPantalla(null);
                cargarHorarios(turnoSeleccionado);
              },
              tipo: 'primary',
            },
          ],
        });
      } else {
        setErrorPantalla({
          titulo: 'Error al eliminar',
          mensaje: msg,
          tipo: 'error',
          acciones: [
            {
              label: 'Aceptar',
              onClick: () => {
                setErrorPantalla(null);
                cargarHorarios(turnoSeleccionado);
              },
              tipo: 'primary',
            },
          ],
        });
      }

      if (msg.includes('inactivo')) {
        cargarTurnos();
      }
    } finally {
      setEliminando(false);
    }
  };

  const handleCancel = () => {
    setShowForm(false);
    setEditingId(null);
    setForm({
      diaSemana: 1,
      horaInicio: '07:00',
      horaFin: '07:50',
      descanso: false,
      orden: 0,
      semestreId: semestreActivo?.id || 0,
    });
    setFormError('');
  };

  const getHorariosPorDia = (dia: number) => {
    const listaHorarios = Array.isArray(horarios) ? horarios : [];
    return listaHorarios
      .filter(h => h.diaSemana === dia)
      .sort((a, b) => (a.orden || 0) - (b.orden || 0));
  };

  // 🔥 Abrir modal o copiar directo (según si el destino tiene horarios)
  const handleCopiarDia = (diaDestino: number) => {
    if (!isTurnoActivo) {
      setFormError('No se puede copiar horarios de un turno inactivo');
      setTimeout(() => setFormError(''), 3000);
      return;
    }

    const diaOrigen = diaDestino - 1;
    const horariosOrigen = getHorariosPorDia(diaOrigen);
    const horariosDestino = getHorariosPorDia(diaDestino);

    if (horariosOrigen.length === 0) {
      setError(
        `El día ${diasSemana.find(d => d.value === diaOrigen)?.label} no tiene horarios para copiar`
      );
      setTimeout(() => setError(''), 3000);
      return;
    }

    // Si el destino está vacío, copiar directo sin modal
    if (horariosDestino.length === 0) {
      ejecutarCopia(diaDestino, horariosOrigen, false);
      return;
    }

    // Si el destino tiene horarios, mostrar modal
    setModalCopiar({
      diaDestino,
      diaOrigen,
      horariosOrigen,
    });
  };

  // 🔥 Lógica real de copiado
  const ejecutarCopia = async (
    diaDestino: number,
    horariosOrigen: TurnoHorario[],
    reemplazar: boolean
  ) => {
    setCopiando(true);
    setFormError('');

    try {
      const semestreId = semestreActivo?.id || 0;

      // 1. Si reemplazar, borrar primero los del día destino
      if (reemplazar) {
        const horariosDestino = getHorariosPorDia(diaDestino);
        for (const h of horariosDestino) {
          await turnoHorarioService.eliminar(turnoSeleccionado, h.id);
        }
      }

      // 2. Crear los nuevos
      for (const h of horariosOrigen) {
        await turnoHorarioService.crear(turnoSeleccionado, {
          diaSemana: diaDestino,
          horaInicio: h.horaInicio,
          horaFin: h.horaFin,
          descanso: h.descanso,
          orden: h.orden,
          semestreId,
        });
      }

      // 3. Recargar y limpiar
      await cargarHorarios(turnoSeleccionado);
      setModalCopiar(null);
    } catch (error: any) {
      console.error('Error al copiar horarios:', error);
      const msg = error.response?.data?.message || 'Error al copiar los horarios';
      setFormError(msg);
      setModalCopiar(null);
    } finally {
      setCopiando(false);
    }
  };

  // 🔥 Confirmar desde el modal
  const confirmarCopiar = () => {
    if (!modalCopiar) return;
    ejecutarCopia(
      modalCopiar.diaDestino,
      modalCopiar.horariosOrigen,
      true // siempre reemplazar cuando viene del modal
    );
  };

  const recargarDatos = async () => {
    console.log('🔄 Iniciando recarga de datos...');
    setLoading(true);
    try {
      const res = await turnoService.listar(0, 100, '', semestreActivo?.id);
      const todosLosTurnos = res.data.content || [];
      setTurnos(todosLosTurnos);

      const soloActivos = todosLosTurnos.filter(t => t.activo === true);
      setTurnosActivos(soloActivos);

      const turnoExiste = soloActivos.some(t => t.id === turnoSeleccionado);
      let turnoId = turnoSeleccionado;
      if (!turnoExiste && soloActivos.length > 0) {
        turnoId = soloActivos[0].id;
        setTurnoSeleccionado(turnoId);
        navigate(`/catalogo/turnos/horarios/${turnoId}`, { replace: true });
      }

      const turno = todosLosTurnos.find(t => t.id === turnoId);
      if (turnoId > 0 && turno && turno.activo) {
        const horariosRes = await turnoHorarioService.listar(turnoId, semestreActivo?.id);
        const horariosData = extraerHorarios(horariosRes);
        setHorarios(horariosData);
        setError('');
      } else if (turnoId > 0 && turno && !turno.activo) {
        setHorarios([]);
        setError('El turno seleccionado está inactivo');
      }

      console.log('✅ Recarga completada');
    } catch (error) {
      console.error('Error al recargar datos:', error);
      setHorarios([]);
      setError('Error al recargar los datos');
    } finally {
      setLoading(false);
    }
  };

  const isTurnoActivo = turnoActual?.activo || false;
  const horariosList = Array.isArray(horarios) ? horarios : [];

  if (loading && turnos.length === 0) {
    return (
      <div className="flex justify-center items-center h-64">
        <div className="animate-spin rounded-full h-12 w-12 border-4 border-blue-500 border-t-transparent"></div>
      </div>
    );
  }

  // 🔥 Pantalla de error especial
  if (errorPantalla) {
    return (
      <ErrorScreen
        titulo={errorPantalla.titulo}
        mensaje={errorPantalla.mensaje}
        detalles={errorPantalla.detalles}
        sugerencia={errorPantalla.sugerencia}
        tipo={errorPantalla.tipo || 'error'}
        acciones={errorPantalla.acciones}
        mostrarVolver={false}
        mostrarInicio={false}
      />
    );
  }

  return (
    <div className="p-6 max-w-7xl mx-auto">
      {/* Encabezado con info del semestre */}
      <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4 mb-6">
        <div>
          <h1 className="text-3xl font-bold text-gray-800 dark:text-white">
            Gestión de Horarios
          </h1>
          <p className="text-sm text-gray-500 dark:text-gray-400 mt-1">
            Configura las horas para cada turno
          </p>
          <div className="mt-1 inline-flex items-center gap-2 px-3 py-1 bg-blue-50 dark:bg-blue-900/30 rounded-lg text-sm text-blue-600 dark:text-blue-400">
            <MdClass className="text-base" />
            Semestre activo: {semestreActivo?.nombre || 'No seleccionado'}
          </div>
        </div>
        <button
          onClick={recargarDatos}
          className="flex items-center gap-2 bg-gray-200 dark:bg-gray-700 hover:bg-gray-300 dark:hover:bg-gray-600 text-gray-700 dark:text-gray-300 px-4 py-2.5 rounded-lg shadow-md transition duration-200"
          title="Recargar datos"
        >
          <MdRefresh className="text-xl" />
          Recargar
        </button>
      </div>

      {/* Selector de Turno */}
      <div className="bg-white dark:bg-gray-800 rounded-xl shadow-md p-6 mb-6 border border-gray-400 dark:border-gray-700">
        <div className="flex flex-col sm:flex-row items-start sm:items-center gap-4">
          <label className="text-sm font-medium text-gray-700 dark:text-gray-300 whitespace-nowrap">
            Seleccionar Turno:
          </label>
          <select
            value={turnoSeleccionado}
            onChange={handleTurnoChange}
            className="w-full sm:max-w-md px-4 py-2.5 border border-gray-400 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-blue-500"
          >
            <option value={0}>
              {turnosActivos.length === 0 ? 'No hay turnos activos' : 'Seleccionar un turno...'}
            </option>
            {turnosActivos.map((turno) => (
              <option key={turno.id} value={turno.id}>
                {turno.nombre}
              </option>
            ))}
          </select>
          <button
            onClick={() => {
              if (!isTurnoActivo) {
                setFormError('No se puede agregar horarios a un turno inactivo');
                setTimeout(() => setFormError(''), 3000);
                return;
              }
              setShowForm(true);
            }}
            disabled={turnoSeleccionado === 0 || !isTurnoActivo || turnosActivos.length === 0}
            className={`flex items-center gap-2 bg-blue-600 hover:bg-blue-700 text-white px-4 py-2.5 rounded-lg shadow-md transition duration-200 ${
              turnoSeleccionado === 0 || !isTurnoActivo || turnosActivos.length === 0 ? 'opacity-50 cursor-not-allowed' : ''
            }`}
          >
            <MdAdd className="text-xl" />
            Agregar Hora
          </button>
        </div>

        {turnosActivos.length === 0 && (
          <div className="mt-3 bg-yellow-50 dark:bg-yellow-900/30 border border-yellow-200 dark:border-yellow-800 rounded-lg p-3 flex items-start gap-3">
            <MdWarning className="text-xl text-yellow-600 dark:text-yellow-400 mt-0.5" />
            <div>
              <p className="text-sm font-medium text-yellow-800 dark:text-yellow-200">
                No hay turnos activos
              </p>
              <p className="text-sm text-yellow-700 dark:text-yellow-300">
                Activa un turno desde la administración para poder gestionar sus horarios.
              </p>
            </div>
          </div>
        )}

        {error && (
          <div className="mt-3 text-red-600 dark:text-red-400 text-sm">{error}</div>
        )}
      </div>

      {/* Formulario de horario */}
      {showForm && turnoSeleccionado > 0 && isTurnoActivo && (
        <div className="bg-white dark:bg-gray-800 rounded-xl shadow-md p-6 mb-6 border border-gray-400 dark:border-gray-700">
          <div className="flex items-center justify-between mb-4">
            <h2 className="text-xl font-bold text-gray-800 dark:text-white">
              {editingId ? 'Editar Hora' : 'Nueva Hora'}
            </h2>
            <button
              onClick={handleCancel}
              className="text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-200"
            >
              <MdClose className="text-2xl" />
            </button>
          </div>

          {formError && (
            <div className="bg-red-50 dark:bg-red-900/30 text-red-700 dark:text-red-300 p-3 rounded-lg border border-red-200 dark:border-red-800 mb-4">
              {formError}
            </div>
          )}

          <form onSubmit={handleSubmit} className="grid grid-cols-1 md:grid-cols-6 gap-4">
            <div>
              <label className="block text-lg font-medium text-gray-700 dark:text-gray-300 mb-1">
                Día *
              </label>
              <select
                name="diaSemana"
                value={form.diaSemana}
                onChange={handleFormChange}
                className="w-full text-xl px-4 py-3 border border-gray-400 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-blue-500"
              >
                {diasSemana.map(d => (
                  <option key={d.value} value={d.value}>{d.label}</option>
                ))}
              </select>
            </div>

            <div>
              <label className="block text-lg font-medium text-gray-700 dark:text-gray-300 mb-1">
                Hora Inicio *
              </label>
              <input
                type="time"
                name="horaInicio"
                value={form.horaInicio}
                onChange={handleFormChange}
                step="60"
                lang="es-419"
                className="w-full text-lg px-4 py-2.5 border border-gray-400 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>

            <div>
              <label className="block text-lg font-medium text-gray-700 dark:text-gray-300 mb-1">
                Hora Fin *
              </label>
              <input
                type="time"
                name="horaFin"
                value={form.horaFin}
                onChange={handleFormChange}
                step="60"
                lang="es-419"
                className="w-full text-lg px-4 py-2.5 border border-gray-400 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>

            <div>
              <label className="block text-lg font-medium text-gray-700 dark:text-gray-300 mb-1">
                Orden
              </label>
              <input
                type="number"
                name="orden"
                value={form.orden}
                onChange={handleFormChange}
                min={0}
                step={1}
                className="w-full text-lg px-4 py-3 border border-gray-400 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>

            <div className="flex items-center justify-center">
              <SwitchToggle
                checked={form.descanso}
                onChange={(checked) => setForm({ ...form, descanso: checked })}
                labelOff="Es clase"
                labelOn="Es descanso"
                layout="vertical"
                textSize="lg"
                color="blue"
                size="lg"
              />
            </div>

            <div className="flex gap-2">
              <button
                type="submit"
                className="flex-1 bg-blue-600 hover:bg-blue-700 text-white px-4 py-2.5 rounded-lg font-medium transition"
              >
                <MdSave className="inline mr-2" />
                {editingId ? 'Actualizar' : 'Guardar'}
              </button>
            </div>
          </form>
        </div>
      )}

      {/* Tabla de horarios por día */}
      {turnoSeleccionado > 0 && isTurnoActivo && horariosList.length > 0 && (
        <div className="grid grid-cols-1 gap-6">
          {diasSemana.map(dia => {
            const horariosDia = getHorariosPorDia(dia.value);
            return (
              <div key={dia.value} className="bg-white dark:bg-gray-800 rounded-xl shadow-md overflow-hidden border border-gray-400 dark:border-gray-700">
                <div className="px-6 py-3 bg-gray-50 dark:bg-gray-700/50 border-b border-gray-400 dark:border-gray-700 flex items-center justify-between gap-4">
                  <h3 className="text-lg font-semibold text-gray-800 dark:text-white flex items-center gap-2">
                    <span className="text-blue-500">{dia.label}</span>
                    <span className="text-sm text-gray-400 font-normal">
                      ({horariosDia.length} horas)
                    </span>
                  </h3>

                  {/* 🔥 Botón copiar del día anterior (no se muestra en Lunes) */}
                  {dia.value > 1 && (
                    <button
                      type="button"
                      onClick={() => handleCopiarDia(dia.value)}
                      disabled={copiando || !isTurnoActivo}
                      className={`
                        flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-sm font-medium
                        transition-all duration-200
                        ${copiando || !isTurnoActivo
                          ? 'bg-gray-100 dark:bg-gray-700 text-gray-400 cursor-not-allowed'
                          : 'bg-indigo-50 dark:bg-indigo-900/30 text-indigo-600 dark:text-indigo-400 hover:bg-indigo-100 dark:hover:bg-indigo-900/50 hover:scale-105 active:scale-95'
                        }
                      `}
                      title={`Copiar horarios de ${diasSemana.find(d => d.value === dia.value - 1)?.label}`}
                    >
                      <MdContentCopy className="text-base" />
                      Copiar {diasSemana.find(d => d.value === dia.value - 1)?.label}
                    </button>
                  )}
                </div>
                <div className="overflow-x-auto">
                  <table className="min-w-full divide-y divide-gray-400 dark:divide-gray-700">
                    <thead className="bg-gray-50 dark:bg-gray-700/50">
                      <tr>
                        <th className="px-4 py-2.5 text-left text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider">
                          Orden
                        </th>
                        <th className="px-4 py-2.5 text-left text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider">
                          Inicio
                        </th>
                        <th className="px-4 py-2.5 text-left text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider">
                          Fin
                        </th>
                        <th className="px-4 py-2.5 text-left text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider">
                          Tipo
                        </th>
                        <th className="px-4 py-2.5 text-center text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider">
                          Acciones
                        </th>
                      </tr>
                    </thead>
                    <tbody className="bg-white dark:bg-gray-800 divide-y divide-gray-400 dark:divide-gray-700">
                      {horariosDia.length === 0 ? (
                        <tr>
                          <td colSpan={5} className="px-4 py-6 text-center text-gray-500 dark:text-gray-400">
                            No hay horas configuradas para este día
                          </td>
                        </tr>
                      ) : (
                        horariosDia.map((horario) => (
                          <tr key={horario.id} className="hover:bg-gray-50 dark:hover:bg-gray-700/50 transition">
                            <td className="px-4 py-2 whitespace-nowrap text-lg text-gray-700 dark:text-gray-300">
                              {horario.orden}
                            </td>
                            <td className="px-4 py-2 whitespace-nowrap text-lg text-gray-700 dark:text-gray-300">
                              {formatearHora(horario.horaInicio)}
                            </td>
                            <td className="px-4 py-2 whitespace-nowrap text-lg text-gray-700 dark:text-gray-300">
                              {formatearHora(horario.horaFin)}
                            </td>
                            <td className="px-4 py-2 whitespace-nowrap">
                              <span className={`px-2.5 py-1 text-sm font-medium rounded-full flex items-center gap-1 inline-flex ${
                                horario.descanso
                                  ? 'bg-yellow-100 dark:bg-yellow-900/30 text-yellow-800 dark:text-yellow-400'
                                  : 'bg-blue-100 dark:bg-blue-900/30 text-blue-800 dark:text-blue-400'
                              }`}>
                                {horario.descanso ? (
                                  <>
                                    <MdCancel className="text-sm" />
                                    Descanso
                                  </>
                                ) : (
                                  <>
                                    <MdAccessTime className="text-sm" />
                                    Clase
                                  </>
                                )}
                              </span>
                            </td>
                            <td className="px-4 py-3 whitespace-nowrap text-center">
                              <div className="flex items-center justify-center gap-1.5">
                                <button
                                  onClick={() => handleEdit(horario)}
                                  className="text-blue-600 dark:text-blue-400 hover:text-blue-800 dark:hover:text-blue-300 transition p-1 rounded-lg hover:bg-blue-50 dark:hover:bg-blue-900/20"
                                  title="Editar"
                                >
                                  <MdEdit className="text-xl" />
                                </button>
                                <button
                                  onClick={() => handleEliminar(horario)}
                                  className="text-red-600 dark:text-red-400 hover:text-red-800 dark:hover:text-red-300 transition p-1 rounded-lg hover:bg-red-50 dark:hover:bg-red-900/20"
                                  title="Eliminar"
                                >
                                  <MdDelete className="text-xl" />
                                </button>
                              </div>
                            </td>
                          </tr>
                        ))
                      )}
                    </tbody>
                  </table>
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* Mensaje cuando el turno está activo pero no tiene horarios */}
      {turnoSeleccionado > 0 && isTurnoActivo && horariosList.length === 0 && !loading && (
        <div className="bg-blue-50 dark:bg-blue-900/30 border border-blue-200 dark:border-blue-800 rounded-lg p-8 text-center">
          <MdAccessTime className="text-5xl text-blue-600 dark:text-blue-400 mx-auto mb-3" />
          <h3 className="text-lg font-semibold text-blue-800 dark:text-blue-200">
            No hay horarios configurados
          </h3>
          <p className="text-sm text-blue-700 dark:text-blue-300 mt-1">
            Este turno no tiene horarios configurados.
            Haz clic en "Agregar Hora" para comenzar.
          </p>
        </div>
      )}

      {/* Mensaje cuando no hay turnos activos */}
      {turnoSeleccionado === 0 && turnosActivos.length === 0 && !loading && (
        <div className="bg-yellow-50 dark:bg-yellow-900/30 border border-yellow-200 dark:border-yellow-800 rounded-lg p-8 text-center">
          <MdWarning className="text-5xl text-yellow-600 dark:text-yellow-400 mx-auto mb-3" />
          <h3 className="text-lg font-semibold text-yellow-800 dark:text-yellow-200">
            No hay turnos activos
          </h3>
          <p className="text-sm text-yellow-700 dark:text-yellow-300 mt-1">
            Para comenzar a gestionar horarios, primero debes activar un turno
            desde la sección de administración de turnos.
          </p>
        </div>
      )}

      {/* 🔥 Modal de confirmación de copiado */}
      {modalCopiar && (
        <div className="fixed inset-0 z-50 flex items-start justify-center bg-black/50 backdrop-blur-sm p-4 pt-24">
          <div className="bg-white dark:bg-gray-800 rounded-xl shadow-2xl max-w-md w-full p-6 border border-gray-400 dark:border-gray-700">
            <div className="flex items-center gap-3 mb-4">
              <div className="p-2 bg-indigo-100 dark:bg-indigo-900/40 rounded-lg text-indigo-600 dark:text-indigo-400">
                <MdContentCopy className="text-2xl" />
              </div>
              <h3 className="text-lg font-bold text-gray-800 dark:text-white">
                Copiar horarios
              </h3>
            </div>

            <p className="text-sm text-gray-600 dark:text-gray-400 mb-4">
              ¿Copiar todos los bloques de{' '}
              <span className="font-semibold text-gray-800 dark:text-white">
                {diasSemana.find(d => d.value === modalCopiar.diaOrigen)?.label}
              </span>{' '}
              a{' '}
              <span className="font-semibold text-gray-800 dark:text-white">
                {diasSemana.find(d => d.value === modalCopiar.diaDestino)?.label}
              </span>?
            </p>

            <div className="bg-gray-50 dark:bg-gray-700/50 rounded-lg p-3 mb-4 text-sm">
              <div className="flex justify-between items-center mb-2">
                <span className="text-gray-500 dark:text-gray-400">Bloques a copiar:</span>
                <span className="font-bold text-indigo-600 dark:text-indigo-400">
                  {modalCopiar.horariosOrigen.length}
                </span>
              </div>

              <div className="max-h-40 overflow-y-auto space-y-1">
                {[...modalCopiar.horariosOrigen]
                  .sort((a, b) => (a.orden || 0) - (b.orden || 0))
                  .map((h, idx) => (
                    <div
                      key={idx}
                      className="flex justify-between items-center text-xs py-1 px-2 rounded bg-white dark:bg-gray-800"
                    >
                      <span className="font-mono text-gray-600 dark:text-gray-300">
                        {formatearHora(h.horaInicio)} - {formatearHora(h.horaFin)}
                      </span>
                      <span className={`px-2 py-0.5 rounded-full text-[10px] font-medium ${
                        h.descanso
                          ? 'bg-yellow-100 dark:bg-yellow-900/30 text-yellow-700 dark:text-yellow-400'
                          : 'bg-blue-100 dark:bg-blue-900/30 text-blue-700 dark:text-blue-400'
                      }`}>
                        {h.descanso ? 'Descanso' : 'Clase'}
                      </span>
                    </div>
                  ))}
              </div>
            </div>

            {/* Aviso de reemplazo */}
            {getHorariosPorDia(modalCopiar.diaDestino).length > 0 && (
              <div className="bg-amber-50 dark:bg-amber-900/20 border border-amber-200 dark:border-amber-800 rounded-lg p-3 mb-4 flex items-start gap-2">
                <MdWarning className="text-amber-600 dark:text-amber-400 text-lg flex-shrink-0 mt-0.5" />
                <p className="text-xs text-amber-700 dark:text-amber-300">
                  El día <strong>{diasSemana.find(d => d.value === modalCopiar.diaDestino)?.label}</strong>{' '}
                  ya tiene {getHorariosPorDia(modalCopiar.diaDestino).length} bloques que serán{' '}
                  <strong>reemplazados</strong>.
                </p>
              </div>
            )}

            <div className="flex gap-3">
              <button
                onClick={() => setModalCopiar(null)}
                disabled={copiando}
                className="flex-1 px-4 py-2.5 border border-gray-400 dark:border-gray-600 rounded-lg text-gray-700 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700 transition disabled:opacity-50"
              >
                Cancelar
              </button>
              <button
                onClick={confirmarCopiar}
                disabled={copiando}
                className="flex-1 px-4 py-2.5 bg-indigo-600 hover:bg-indigo-700 text-white rounded-lg font-medium transition disabled:opacity-50 flex items-center justify-center gap-2"
              >
                {copiando ? (
                  <>
                    <div className="animate-spin rounded-full h-4 w-4 border-2 border-white border-t-transparent" />
                    Copiando...
                  </>
                ) : (
                  <>
                    <MdContentCopy className="text-lg" />
                    Copiar
                  </>
                )}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* 🔥 Modal de confirmación de eliminación */}
      {modalEliminar.abierto && modalEliminar.horario && (
        <div className="fixed inset-0 z-50 flex items-start justify-center bg-black/50 backdrop-blur-sm p-4 pt-24">
          <div className="bg-white dark:bg-gray-800 rounded-xl shadow-2xl max-w-md w-full p-6 border border-gray-400 dark:border-gray-700">
            <div className="flex items-center gap-3 mb-4">
              <div className="p-2 bg-red-100 dark:bg-red-900/40 rounded-lg text-red-600 dark:text-red-400">
                <MdWarning className="text-2xl" />
              </div>
              <h3 className="text-lg font-bold text-gray-800 dark:text-white">
                Confirmar eliminación
              </h3>
            </div>

            <p className="text-sm text-gray-600 dark:text-gray-400 mb-4">
              ¿Estás seguro de que quieres eliminar este bloque horario?
            </p>

            <div className="bg-gray-50 dark:bg-gray-700/50 rounded-lg p-3 mb-4 text-sm">
              <div className="flex justify-between">
                <span className="text-gray-500 dark:text-gray-400">Día:</span>
                <span className="font-medium text-gray-800 dark:text-white">
                  {diasSemana.find(d => d.value === modalEliminar.horario?.diaSemana)?.label}
                </span>
              </div>
              <div className="flex justify-between mt-1">
                <span className="text-gray-500 dark:text-gray-400">Horario:</span>
                <span className="font-medium text-gray-800 dark:text-white">
                  {formatearHora(modalEliminar.horario.horaInicio)} - {formatearHora(modalEliminar.horario.horaFin)}
                </span>
              </div>
              <div className="flex justify-between mt-1">
                <span className="text-gray-500 dark:text-gray-400">Orden:</span>
                <span className="font-medium text-gray-800 dark:text-white">
                  {modalEliminar.horario.orden}
                </span>
              </div>
              <div className="flex justify-between mt-1">
                <span className="text-gray-500 dark:text-gray-400">Tipo:</span>
                <span className={`font-medium ${modalEliminar.horario.descanso ? 'text-yellow-600 dark:text-yellow-400' : 'text-blue-600 dark:text-blue-400'}`}>
                  {modalEliminar.horario.descanso ? 'Descanso' : 'Clase'}
                </span>
              </div>
            </div>

            <p className="text-xs text-red-600 dark:text-red-400 mb-4">
              ⚠️ Esta acción no se puede deshacer.
            </p>

            <div className="flex gap-3">
              <button
                onClick={() => setModalEliminar({ abierto: false, horario: null })}
                disabled={eliminando}
                className="flex-1 px-4 py-2.5 border border-gray-400 dark:border-gray-600 rounded-lg text-gray-700 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700 transition disabled:opacity-50"
              >
                Cancelar
              </button>
              <button
                onClick={confirmarEliminar}
                disabled={eliminando}
                className="flex-1 px-4 py-2.5 bg-red-600 hover:bg-red-700 text-white rounded-lg font-medium transition disabled:opacity-50 flex items-center justify-center gap-2"
              >
                {eliminando ? (
                  <>
                    <div className="animate-spin rounded-full h-4 w-4 border-2 border-white border-t-transparent" />
                    Eliminando...
                  </>
                ) : (
                  <>
                    <MdDelete className="text-lg" />
                    Eliminar
                  </>
                )}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default TurnoHorario;