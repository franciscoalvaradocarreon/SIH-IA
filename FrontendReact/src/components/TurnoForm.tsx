import React, { useState, useEffect } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { turnoService } from '../api/turnoService';
import { useAuth } from '../context/AuthContext';
import type { TurnoForm as TurnoFormType } from '../types';
import { MdSave, MdCancel, MdSchedule } from 'react-icons/md';
import { SwitchToggle } from '../utils/SwitchToggle';

const TurnoForm: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const isEdit = Boolean(id);

  const { semestreActivo } = useAuth();

  const [form, setForm] = useState<TurnoFormType>({
    nombre: '',
    descripcion: '',
    activo: true,
    semestreId: semestreActivo?.id || undefined,
  });
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

 useEffect(() => {
    if (semestreActivo) {
      console.log('📌 Semestre activo desde el menú:', semestreActivo);
      setForm(prev => ({
        ...prev,
        semestreId: semestreActivo.id,
      }));
    }
  }, [semestreActivo]);

  useEffect(() => {
    if (isEdit) {
      cargarTurno();
    }
  }, [id]);

  const cargarTurno = async () => {
    setLoading(true);
    try {
      const res = await turnoService.obtener(Number(id));
      setForm({
        nombre: res.data.nombre,
        descripcion: res.data.descripcion || '',
        activo: res.data.activo,
        semestreId: semestreActivo?.id || res.data.semestreId || undefined,
      });
    } catch (err) {
      setError('Error al cargar los datos del turno');
    } finally {
      setLoading(false);
    }
  };

  const handleChange = (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => {
    const { name, value } = e.target;
    setForm({ ...form, [name]: value });
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setLoading(true);

  console.log('📤 Datos a enviar al backend:', JSON.stringify(form, null, 2));  // 🔥 Ver qué se envía

    try {
      if (isEdit) {
        await turnoService.actualizar(Number(id), form);
      } else {
        await turnoService.crear(form);
      }
      navigate('/catalogo/turnos');
    } catch (err: any) {
      if (err.response?.data?.message) {
        setError(err.response.data.message);
      } else {
        setError('Error al guardar el turno');
      }
    } finally {
      setLoading(false);
    }
  };

  if (loading && isEdit) {
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
          {isEdit ? 'Editar Turno' : 'Nuevo Turno'}
        </h1>

        {error && (
          <div className="bg-red-50 dark:bg-red-900/30 text-red-700 dark:text-red-300 p-4 rounded-lg border border-red-200 dark:border-red-800 mb-6">
            {error}
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-5">
          <div>
            <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
              Nombre *
            </label>
            <div className="relative">
              <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                <MdSchedule className="text-gray-400 dark:text-gray-500 text-lg" />
              </div>
              <input
                type="text"
                name="nombre"
                value={form.nombre}
                onChange={handleChange}
                required
                className="w-full pl-10 pr-4 py-2.5 border border-gray-400 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition"
                placeholder="Ej: MATUTINO, VESPERTINO, NOCTURNO"
              />
            </div>
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
              Descripción
            </label>
            <textarea
              name="descripcion"
              value={form.descripcion || ''}
              onChange={handleChange}
              rows={3}
              className="w-full px-4 py-2.5 border border-gray-400 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition resize-none"
              placeholder="Descripción del turno (ej. Horario de 7:00 a 13:00)"
            />
          </div>

          <SwitchToggle
            checked={form.activo !== false}
            onChange={(checked) => setForm({ ...form, activo: checked })}
            label="Turno activo"
            color="blue"
            size="md"
          />

          <div className="flex gap-3 pt-4">
            <button
              type="submit"
              disabled={loading}
              className="flex items-center gap-2 flex-1 bg-blue-600 hover:bg-blue-700 text-white px-6 py-2.5 rounded-lg font-medium transition disabled:opacity-50 disabled:cursor-not-allowed shadow-sm"
            >
              <MdSave className="text-xl" />
              {loading ? 'Guardando...' : isEdit ? 'Actualizar' : 'Crear'}
            </button>
            <button
              type="button"
              onClick={() => navigate('/catalogo/turnos')}
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

export default TurnoForm;