import React, { useState, useEffect } from 'react';
import { useNavigate, useParams, useLocation } from 'react-router-dom';
import { especialidadService } from '../api/especialidadService';
import { turnoService } from '../api/turnoService';
import type { EspecialidadForm as EspecialidadFormType, Turno } from '../types';
import { MdSave, MdCancel, MdCategory, MdSchedule } from 'react-icons/md';
import { useAuth } from '../context/AuthContext';

const EspecialidadForm: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const location = useLocation();
  const navigate = useNavigate();
  const isEdit = Boolean(id);
  const { semestreActivo } = useAuth();

  const queryParams = new URLSearchParams(location.search);
  const busquedaFiltro = queryParams.get('busqueda') || '';
  const turnoFiltro = queryParams.get('turno') || '0';

  const [form, setForm] = useState<EspecialidadFormType>({
    nombre: '',
    semestreId: 0,
    turnoId: 0,
  });
  const [turnos, setTurnos] = useState<Turno[]>([]);
  const [cargandoTurnos, setCargandoTurnos] = useState(true);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  // 🔥 Cargar turnos del semestre activo
  useEffect(() => {
    cargarTurnos();
  }, [semestreActivo?.id]);

  // 🔥 Sincronizar semestreId cuando cambia el activo
  useEffect(() => {
    if (semestreActivo) {
      setForm(prev => ({ ...prev, semestreId: semestreActivo.id }));
    }
  }, [semestreActivo]);

  useEffect(() => {
    if (isEdit) {
      cargarEspecialidad();
    }
  }, [id]);

  const cargarTurnos = async () => {
    if (!semestreActivo?.id) return;
    setCargandoTurnos(true);
    try {
      const res = await turnoService.listar(0, 100, '', semestreActivo.id);
      const activos = res.data.content.filter((t: Turno) => t.activo === true);
      setTurnos(activos);

      // Si el filtro de URL apunta a un turno válido, preseleccionarlo
      if (!isEdit && turnoFiltro !== '0') {
        const turnoId = Number(turnoFiltro);
        if (activos.some(t => t.id === turnoId)) {
          setForm(prev => ({ ...prev, turnoId }));
        }
      }
    } catch (error) {
      console.error('Error al cargar turnos:', error);
    } finally {
      setCargandoTurnos(false);
    }
  };

  const cargarEspecialidad = async () => {
    setLoading(true);
    try {
      const res = await especialidadService.obtener(Number(id));
      setForm({
        nombre: res.data.nombre,
        semestreId: res.data.semestreId || semestreActivo?.id || 0,
        turnoId: res.data.turnoId || 0,
      });
    } catch (err) {
      setError('Error al cargar los datos de la especialidad');
    } finally {
      setLoading(false);
    }
  };

  const volverConFiltros = () => {
    const params = new URLSearchParams();
    if (busquedaFiltro) params.set('busqueda', busquedaFiltro);
    if (turnoFiltro && turnoFiltro !== '0') params.set('turno', turnoFiltro);
    const url = `/catalogo/especialidades${params.toString() ? `?${params.toString()}` : ''}`;
    navigate(url);
  };

  const handleChange = (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => {
    const { name, value } = e.target;
    setForm({ ...form, [name]: value });
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

    // 🔥 Validación mínima: el turno es obligatorio
    if (!form.turnoId || form.turnoId === 0) {
      setError('Selecciona un turno');
      return;
    }

    setLoading(true);

    const dataToSend = {
      ...form,
      semestreId: semestreActivo?.id || form.semestreId,
    };

    try {
      if (isEdit) {
        await especialidadService.actualizar(Number(id), dataToSend);
      } else {
        await especialidadService.crear(dataToSend);
      }
      volverConFiltros();
    } catch (err: any) {
      if (err.response?.data?.message) {
        setError(err.response.data.message);
      } else {
        setError('Error al guardar la especialidad');
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
      <div className="bg-white dark:bg-gray-800 rounded-xl shadow-md p-6 md:p-8 border border-gray-100 dark:border-gray-700">
        <h1 className="text-2xl font-bold text-gray-800 dark:text-white mb-6">
          {isEdit ? 'Editar Especialidad' : 'Nueva Especialidad'}
        </h1>

        {error && (
          <div className="bg-red-50 dark:bg-red-900/30 text-red-700 dark:text-red-300 p-4 rounded-lg border border-red-200 dark:border-red-800 mb-6">
            {error}
          </div>
        )}

        {/* 🔥 Aviso si no hay turnos activos */}
        {turnos.length === 0 && (
          <div className="bg-yellow-50 dark:bg-yellow-900/30 border border-yellow-200 dark:border-yellow-800 rounded-lg p-4 mb-6">
            <p className="text-sm text-yellow-800 dark:text-yellow-200 font-medium">
              No hay turnos activos en el semestre actual
            </p>
            <p className="text-xs text-yellow-700 dark:text-yellow-300 mt-1">
              Activa al menos un turno antes de crear especialidades.
            </p>
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
                <MdCategory className="text-gray-400 dark:text-gray-500 text-lg" />
              </div>
              <input
                type="text"
                name="nombre"
                value={form.nombre}
                onChange={handleChange}
                required
                maxLength={50}
                className="w-full pl-10 pr-4 py-2.5 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition"
                placeholder="Ej: Programación, Contabilidad..."
              />
            </div>
            <p className="text-xs text-gray-400 mt-1">Máximo 50 caracteres</p>
          </div>

          {/* Turno */}
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
                className="w-full pl-10 pr-4 py-2.5 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition disabled:opacity-50"
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
              La especialidad solo estará disponible en este turno
            </p>
          </div>

          {/* Botones */}
          <div className="flex gap-3 pt-4">
            <button
              type="submit"
              disabled={loading || turnos.length === 0}
              className="flex items-center gap-2 flex-1 bg-blue-600 hover:bg-blue-700 text-white px-6 py-2.5 rounded-lg font-medium transition disabled:opacity-50 disabled:cursor-not-allowed shadow-sm"
            >
              <MdSave className="text-xl" />
              {loading ? 'Guardando...' : isEdit ? 'Actualizar' : 'Crear'}
            </button>
            <button
              type="button"
              onClick={volverConFiltros}
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

export default EspecialidadForm;