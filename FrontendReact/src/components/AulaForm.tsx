import React, { useState, useEffect } from 'react';
import { useNavigate, useParams, useLocation } from 'react-router-dom';
import { aulaService } from '../api/aulaService';
import { turnoService } from '../api/turnoService';
import type { AulaForm as AulaFormType, Turno } from '../types';
import { useAuth } from '../context/AuthContext';
import {
  MdSave, MdCancel, MdMeetingRoom, MdHome, MdElevator,
  MdDescription, MdClass, MdWarning, MdSchedule
} from 'react-icons/md';

const AulaForm: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const location = useLocation();
  const navigate = useNavigate();
  const isEdit = Boolean(id);
  const { semestreActivo } = useAuth();

  const queryParams = new URLSearchParams(location.search);
  const busquedaFiltro = queryParams.get('busqueda') || '';
  const turnoFiltro = queryParams.get('turno') || '0';

  const [form, setForm] = useState<AulaFormType>({
    nombre: '',
    edificio: '',
    piso: '',
    descripcion: '',
    activo: true,
    taller: false,
    semestreId: semestreActivo?.id || 0,
    turnoId: 0,                          // 🔥 NUEVO
  });
  const [turnos, setTurnos] = useState<Turno[]>([]);
  const [cargandoTurnos, setCargandoTurnos] = useState(true);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  // 🔥 Cargar turnos cuando cambia el semestre activo
  useEffect(() => {
    cargarTurnos();
  }, [semestreActivo?.id]);

  useEffect(() => {
    if (isEdit) {
      cargarAula();
    }
  }, [id]);

  const cargarTurnos = async () => {
    if (!semestreActivo?.id) {
      setTurnos([]);
      setCargandoTurnos(false);
      return;
    }
    setCargandoTurnos(true);
    try {
      const res = await turnoService.listar(0, 100, '', semestreActivo.id);
      const activos = res.data.content.filter((t: Turno) => t.activo === true);
      setTurnos(activos);

      // Preseleccionar turno si viene en la URL
      if (!isEdit && turnoFiltro !== '0') {
        const turnoId = Number(turnoFiltro);
        if (activos.some(t => t.id === turnoId)) {
          setForm(prev => ({ ...prev, turnoId }));
        }
      }
    } catch (error) {
      console.error('Error al cargar turnos:', error);
      setTurnos([]);
    } finally {
      setCargandoTurnos(false);
    }
  };

  const cargarAula = async () => {
    setLoading(true);
    try {
      const res = await aulaService.obtener(Number(id));
      setForm({
        nombre: res.data.nombre,
        edificio: res.data.edificio || '',
        piso: res.data.piso || '',
        descripcion: res.data.descripcion || '',
        activo: res.data.activo !== undefined ? res.data.activo : true,
        taller: res.data.taller === true,
        semestreId: res.data.semestreId || semestreActivo?.id || 0,
        turnoId: res.data.turnoId || 0,   // 🔥 NUEVO
      });
    } catch (err) {
      setError('Error al cargar los datos del aula');
    } finally {
      setLoading(false);
    }
  };

  const volverConFiltros = () => {
    const params = new URLSearchParams();
    if (busquedaFiltro) params.set('busqueda', busquedaFiltro);
    if (turnoFiltro && turnoFiltro !== '0') params.set('turno', turnoFiltro);
    const url = `/catalogo/aulas${params.toString() ? `?${params.toString()}` : ''}`;
    navigate(url);
  };

  const handleChange = (
    e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>
  ) => {
    const { name, value, type } = e.target;
    setForm({
      ...form,
      [name]: name === 'turnoId'
        ? Number(value)
        : type === 'number'
        ? Number(value)
        : value,
    });
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

    if (!semestreActivo?.id) {
      setError('No hay semestre activo. Selecciona uno en el menú lateral.');
      return;
    }
    if (!form.turnoId || form.turnoId === 0) {
      setError('Selecciona un turno');
      return;
    }

    setLoading(true);

    try {
      const dataToSend = {
        ...form,
        semestreId: semestreActivo.id,
      };

      if (isEdit) {
        await aulaService.actualizar(Number(id), dataToSend);
      } else {
        await aulaService.crear(dataToSend);
      }
      volverConFiltros();
    } catch (err: any) {
      const codigo = err.response?.data?.error;
      const mensaje = err.response?.data?.message;

      if (codigo === 'AULA_NOMBRE_DUPLICADO') {
        setError(mensaje || 'Ya existe un aula con ese nombre en este turno.');
      } else if (mensaje) {
        setError(mensaje);
      } else {
        setError('Error al guardar el aula');
      }
    } finally {
      setLoading(false);
    }
  };

  if ((loading && isEdit) || cargandoTurnos) {
    return (
      <div className="flex justify-center items-center h-64">
        <div className="animate-spin rounded-full h-12 w-12 border-4 border-blue-500 border-t-transparent"></div>
      </div>
    );
  }

  return (
    <div className="max-w-2xl mx-auto p-6">
      <div className="bg-white dark:bg-gray-800 rounded-xl shadow-md p-6 md:p-8 border border-gray-400 dark:border-gray-700">
        <h1 className="text-2xl font-bold text-gray-800 dark:text-white mb-6">
          {isEdit ? 'Editar Aula' : 'Nueva Aula'}
        </h1>

        {/* Aviso de semestre */}
        {semestreActivo ? (
          <div className="mb-4 p-3 bg-blue-50 dark:bg-blue-900/20 rounded-lg border border-blue-200 dark:border-blue-800">
            <div className="flex items-center gap-2">
              <MdClass className="text-blue-500 dark:text-blue-400 text-lg" />
              <span className="text-sm text-gray-700 dark:text-gray-300">
                <span className="font-medium">Semestre:</span> {semestreActivo.nombre}
              </span>
            </div>
          </div>
        ) : (
          <div className="mb-4 p-3 bg-yellow-50 dark:bg-yellow-900/20 rounded-lg border border-yellow-200 dark:border-yellow-800">
            <div className="flex items-center gap-2">
              <MdWarning className="text-yellow-500 dark:text-yellow-400 text-lg" />
              <span className="text-sm text-yellow-800 dark:text-yellow-200 font-medium">
                No hay semestre activo. Selecciona uno en el menú lateral.
              </span>
            </div>
          </div>
        )}

        {/* Aviso si no hay turnos */}
        {semestreActivo && turnos.length === 0 && (
          <div className="mb-4 p-3 bg-yellow-50 dark:bg-yellow-900/20 rounded-lg border border-yellow-200 dark:border-yellow-800">
            <div className="flex items-center gap-2">
              <MdWarning className="text-yellow-500 dark:text-yellow-400 text-lg" />
              <span className="text-sm text-yellow-800 dark:text-yellow-200 font-medium">
                No hay turnos activos en el semestre. Activa uno antes de crear aulas.
              </span>
            </div>
          </div>
        )}

        {error && (
          <div className="bg-red-50 dark:bg-red-900/30 text-red-700 dark:text-red-300 p-4 rounded-lg border border-red-200 dark:border-red-800 mb-6">
            {error}
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-5">
          {/* Nombre */}
          <div>
            <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
              Nombre *
            </label>
            <div className="relative">
              <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                <MdMeetingRoom className="text-gray-400 dark:text-gray-500 text-lg" />
              </div>
              <input
                type="text"
                name="nombre"
                value={form.nombre}
                onChange={handleChange}
                required
                maxLength={50}
                className="w-full pl-10 pr-4 py-2.5 border border-gray-400 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition"
                placeholder="Ej: Aula 101, Laboratorio 1"
              />
            </div>
            <p className="text-xs text-gray-400 mt-1">Único por semestre y turno</p>
          </div>

          {/* 🔥 Turno */}
          <div>
            <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
              Turno *
            </label>
            <div className="relative">
              <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                <MdSchedule className="text-gray-400 dark:text-gray-500 text-lg" />
              </div>
              <select
                name="turnoId"
                value={form.turnoId || 0}
                onChange={handleChange}
                required
                disabled={turnos.length === 0}
                className="w-full pl-10 pr-4 py-2.5 border border-gray-400 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition disabled:opacity-50"
              >
                <option value={0}>Seleccionar turno...</option>
                {turnos.map((t) => (
                  <option key={t.id} value={t.id}>
                    {t.nombre}
                  </option>
                ))}
              </select>
            </div>
            <p className="text-xs text-gray-400 mt-1">
              El aula quedará disponible en este turno dentro del semestre
            </p>
          </div>

          {/* Edificio + Piso */}
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                Edificio
              </label>
              <div className="relative">
                <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                  <MdHome className="text-gray-400 dark:text-gray-500 text-lg" />
                </div>
                <input
                  type="text"
                  name="edificio"
                  value={form.edificio || ''}
                  onChange={handleChange}
                  maxLength={50}
                  className="w-full pl-10 pr-4 py-2.5 border border-gray-400 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition"
                  placeholder="Ej: Edificio A"
                />
              </div>
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                Piso
              </label>
              <div className="relative">
                <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                  <MdElevator className="text-gray-400 dark:text-gray-500 text-lg" />
                </div>
                <input
                  type="text"
                  name="piso"
                  value={form.piso || ''}
                  onChange={handleChange}
                  maxLength={50}
                  className="w-full pl-10 pr-4 py-2.5 border border-gray-400 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition"
                  placeholder="Ej: Planta Baja, 1er Piso"
                />
              </div>
            </div>
          </div>

          {/* Descripción */}
          <div>
            <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
              Descripción
            </label>
            <div className="relative">
              <div className="absolute top-3 left-0 pl-3 flex items-start pointer-events-none">
                <MdDescription className="text-gray-400 dark:text-gray-500 text-lg" />
              </div>
              <textarea
                name="descripcion"
                value={form.descripcion || ''}
                onChange={handleChange}
                rows={3}
                className="w-full pl-10 pr-4 py-2.5 border border-gray-400 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition resize-none"
                placeholder="Información adicional del aula..."
              />
            </div>
          </div>

          {/* Taller */}
          <div className="flex items-center gap-2">
            <input
              type="checkbox"
              name="taller"
              checked={form.taller === true}
              onChange={(e) => setForm({ ...form, taller: e.target.checked })}
              className="w-4 h-4 text-blue-600 border-gray-400 rounded focus:ring-blue-500"
            />
            <label className="text-sm font-medium text-gray-700 dark:text-gray-300">
              Es taller (aula de practica)
            </label>
          </div>

          {/* Activo */}
          <div className="flex items-center gap-2">
            <input
              type="checkbox"
              name="activo"
              checked={form.activo !== false}
              onChange={(e) => setForm({ ...form, activo: e.target.checked })}
              className="w-4 h-4 text-blue-600 border-gray-400 rounded focus:ring-blue-500"
            />
            <label className="text-sm font-medium text-gray-700 dark:text-gray-300">
              Aula activa
            </label>
          </div>

          {/* Botones */}
          <div className="flex gap-3 pt-4">
            <button
              type="submit"
              disabled={loading || !semestreActivo || turnos.length === 0}
              className="flex items-center gap-2 flex-1 bg-blue-600 hover:bg-blue-700 text-white px-6 py-2.5 rounded-lg font-medium transition disabled:opacity-50 disabled:cursor-not-allowed shadow-sm"
            >
              <MdSave className="text-xl" />
              {loading ? 'Guardando...' : isEdit ? 'Actualizar' : 'Crear'}
            </button>
            <button
              type="button"
              onClick={volverConFiltros}
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

export default AulaForm;