// src/components/GrupoForm.tsx
import React, { useState, useEffect } from 'react';
import { useNavigate, useParams, useLocation } from 'react-router-dom';
import { grupoService } from '../api/grupoService';
import { turnoService } from '../api/turnoService';
import { especialidadService } from '../api/especialidadService';
import type { GrupoForm as GrupoFormType } from '../types';
import { MdSave, MdCancel, MdGroup, MdSchool, MdSchedule, MdClass } from 'react-icons/md';
import { useAuth } from '../context/AuthContext';

const GrupoForm: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const location = useLocation();
  const navigate = useNavigate();
  const isEdit = Boolean(id);
  const { semestreActivo } = useAuth();
  

  // Leer filtros de la URL
  const queryParams = new URLSearchParams(location.search);
  const especialidadFiltro = queryParams.get('especialidad') || '0';
  const busquedaFiltro = queryParams.get('busqueda') || '';

  const [form, setForm] = useState<GrupoFormType>({
    nombre: '',
    grado: 1,
    turnoId: 0,
    especialidadId: null,
    capacidad: 0,
    activo: true,
    semestreId: semestreActivo?.id || 0,
  });
  const [turnos, setTurnos] = useState<any[]>([]);
  const [especialidades, setEspecialidades] = useState<any[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  
  // Función para volver con filtros
  const volverConFiltros = () => {
    const params = new URLSearchParams();
    if (especialidadFiltro && especialidadFiltro !== '0') {
      params.set('especialidad', especialidadFiltro);
    }
    if (busquedaFiltro) {
      params.set('busqueda', busquedaFiltro);
    }
    const url = `/catalogo/grupos${params.toString() ? `?${params.toString()}` : ''}`;
    navigate(url);
  };

  useEffect(() => {
    if (semestreActivo) {
      setForm(prev => ({
        ...prev,
        semestreId: semestreActivo.id,
      }));
    }
  }, [semestreActivo]);

  useEffect(() => {
    cargarCatalogos();
    if (isEdit) {
      cargarGrupo();
    }
  }, [id, semestreActivo?.id]);

  const cargarCatalogos = async () => {
    try {
      const semestreId = semestreActivo?.id;
    
      if (!semestreId) {
        setError('No hay un semestre activo seleccionado');
        return;
      }

      const [turnosRes, especialidadesRes] = await Promise.all([
        turnoService.listar(0, 100, '', semestreId),
        especialidadService.listar(0, 100, '' , semestreId),
      ]);
      const turnosActivos = turnosRes.data.content.filter(turno => turno.activo === true);
      setTurnos(turnosActivos);
      setEspecialidades(especialidadesRes.data.content);

      setForm(prev => ({
        ...prev,
        semestreId: semestreId
      }));

    } catch (error) {
      console.error('Error al cargar catálogos:', error);
      setError('Error al cargar los datos del formulario');

    }
  };

  const cargarGrupo = async () => {
    setLoading(true);
    try {
      const res = await grupoService.obtener(Number(id));
      setForm({
        nombre: res.data.nombre,
        grado: res.data.grado,
        turnoId: res.data.turnoId || 0,
        especialidadId: res.data.especialidadId || null,
        capacidad: res.data.capacidad || 0,
        activo: res.data.activo,
        semestreId: res.data.semestreId || semestreActivo?.id || 0,
      });
    } catch (err) {
      setError('Error al cargar los datos del grupo');
    } finally {
      setLoading(false);
    }
  };

  const handleChange = (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => {
    const { name, value, type } = e.target;
    setForm({
      ...form,
      [name]: type === 'number' ? Number(value) : value,
    });
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setLoading(true);

    if (!form.semestreId) {
      setError('No hay un semestre activo seleccionado');
      setLoading(false);
      return;
    }

    try {
      const dataToSend = {
        ...form,
        semestreId: form.semestreId || semestreActivo?.id || 0
      };
      if (isEdit) {
        await grupoService.actualizar(Number(id), dataToSend);  // ✅
      } else {
        await grupoService.crear(dataToSend);  // ✅
      }
      volverConFiltros();
    } catch (err: any) {
      if (err.response?.data?.message) {
        setError(err.response.data.message);
      } else {
        setError('Error al guardar el grupo');
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
      <div className="bg-white dark:bg-gray-800 rounded-xl shadow-md p-6 md:p-8 border border-gray-100 dark:border-gray-700">
        <h1 className="text-2xl font-bold text-gray-800 dark:text-white mb-6">
          {isEdit ? 'Editar Grupo' : 'Nuevo Grupo'}
        </h1>

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
                <MdGroup className="text-gray-400 dark:text-gray-500 text-lg" />
              </div>
              <input
                type="text"
                name="nombre"
                value={form.nombre}
                onChange={handleChange}
                required
                className="w-full pl-10 pr-4 py-2.5 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition"
                placeholder="Ej: 3A, 4B, 5C"
              />
            </div>
          </div>

          {/* Grado + Capacidad */}
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                Grado *
              </label>
              <div className="relative">
                <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                  <MdSchool className="text-gray-400 dark:text-gray-500 text-lg" />
                </div>
                <select
                  name="grado"
                  value={form.grado}
                  onChange={handleChange}
                  required
                  className="w-full pl-10 pr-4 py-2.5 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition"
                >
                  <option value={1}>1°</option>
                  <option value={2}>2°</option>
                  <option value={3}>3°</option>
                  <option value={4}>4°</option>
                  <option value={5}>5°</option>
                  <option value={6}>6°</option>
                </select>
              </div>
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                Capacidad
              </label>
              <div className="relative">
                <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                  <MdGroup className="text-gray-400 dark:text-gray-500 text-lg" />
                </div>
                <input
                  type="number"
                  name="capacidad"
                  value={form.capacidad}
                  onChange={handleChange}
                  min={0}
                  className="w-full pl-10 pr-4 py-2.5 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition"
                  placeholder="30"
                />
              </div>
            </div>
          </div>

          {/* Turno + Especialidad */}
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
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
                  value={form.turnoId}
                  onChange={handleChange}
                  required
                  className="w-full pl-10 pr-4 py-2.5 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition"
                >
                  <option value={0}>Seleccionar turno</option>
                  {turnos.map((turno) => (
                    <option key={turno.id} value={turno.id}>
                      {turno.nombre}
                    </option>
                  ))}
                </select>
              </div>
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                Especialidad
              </label>
              <div className="relative">
                <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                  <MdClass className="text-gray-400 dark:text-gray-500 text-lg" />
                </div>
                <select
                  name="especialidadId"
                  value={form.especialidadId || ''}
                  onChange={handleChange}
                  className="w-full pl-10 pr-4 py-2.5 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition"
                >
                  <option value="">Sin especialidad</option>
                  {especialidades.map((especialidad) => (
                    <option key={especialidad.id} value={especialidad.id}>
                      {especialidad.nombre}
                    </option>
                  ))}
                </select>
              </div>
            </div>
          </div>

          {/* Activo */}
          <div className="flex items-center gap-2">
            <input
              type="checkbox"
              name="activo"
              checked={form.activo !== false}
              onChange={(e) => setForm({ ...form, activo: e.target.checked })}
              className="w-4 h-4 text-blue-600 border-gray-300 rounded focus:ring-blue-500"
            />
            <label className="text-sm font-medium text-gray-700 dark:text-gray-300">
              Grupo activo
            </label>
          </div>

          {/* Botones */}
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
export default GrupoForm;