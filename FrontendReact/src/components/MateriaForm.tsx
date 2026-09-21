import React, { useState, useEffect, useRef } from 'react';
import { useNavigate, useParams, useLocation } from 'react-router-dom';
import { materiaService } from '../api/materiaService';
import { turnoService } from '../api/turnoService';
import type { MateriaForm as MateriaFormType, Turno } from '../types';
import { useAuth } from '../context/AuthContext';
import {
  MdSave, MdCancel, MdBook, MdClass, MdSchedule,
  MdColorLens, MdNumbers, MdWarning
} from 'react-icons/md';
import { generarColorAleatorio } from '../utils/colores';

const MateriaForm: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const location = useLocation();
  const navigate = useNavigate();
  const isEdit = Boolean(id);
  const { semestreActivo } = useAuth();

  // Leer filtros de la URL
  const queryParams = new URLSearchParams(location.search);
  const busquedaFiltro = queryParams.get('busqueda') || '';
  const turnoFiltro = queryParams.get('turno') || '0';

  // 🔥 Estado para controlar el popover del color
  const [showColorPicker, setShowColorPicker] = useState(false);
  const colorPickerRef = useRef<HTMLDivElement>(null);

  const [form, setForm] = useState<MateriaFormType>({
    nombre: '',
    clave: '',
    descripcion: '',
    creditos: 0,
    horasSemana: 0,
    colorHex: generarColorAleatorio(),
    activo: true,
    semestreId: semestreActivo?.id || 0,
    turnoId: 0,                       // 🔥 NUEVO
  });
  const [turnos, setTurnos] = useState<Turno[]>([]);
  const [cargandoTurnos, setCargandoTurnos] = useState(true);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  // 🔥 Cerrar popover al hacer clic fuera
  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (colorPickerRef.current && !colorPickerRef.current.contains(event.target as Node)) {
        setShowColorPicker(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
    };
  }, []);

  // 🔥 Cargar turnos cuando cambia el semestre activo
  useEffect(() => {
    cargarTurnos();
  }, [semestreActivo?.id]);

  useEffect(() => {
    if (isEdit) {
      cargarMateria();
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

      // Si viene ?turno=X en la URL y es válido, preseleccionarlo
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

  const cargarMateria = async () => {
    setLoading(true);
    try {
      const res = await materiaService.obtener(Number(id));
      setForm({
        nombre: res.data.nombre,
        clave: res.data.clave,
        descripcion: res.data.descripcion || '',
        creditos: res.data.creditos || 0,
        horasSemana: res.data.horasSemana || 0,
        colorHex: res.data.colorHex || generarColorAleatorio(),
        activo: res.data.activo !== undefined ? res.data.activo : true,
        semestreId: res.data.semestreId || semestreActivo?.id || 0,
        turnoId: res.data.turnoId || 0,   // 🔥 NUEVO
      });
    } catch (err) {
      setError('Error al cargar los datos de la materia');
    } finally {
      setLoading(false);
    }
  };

  const volverConFiltros = () => {
    const params = new URLSearchParams();
    if (busquedaFiltro) params.set('busqueda', busquedaFiltro);
    if (turnoFiltro && turnoFiltro !== '0') params.set('turno', turnoFiltro);
    const url = `/catalogo/materias${params.toString() ? `?${params.toString()}` : ''}`;
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

  const handleColorChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    setForm({ ...form, colorHex: e.target.value });
  };

  const generarColorAleatorioLocal = () => {
    setForm({ ...form, colorHex: generarColorAleatorio() });
  };

  const toggleColorPicker = () => {
    setShowColorPicker(!showColorPicker);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

    // 🔥 Validaciones básicas
    if (!form.nombre || form.nombre.trim() === '') {
      setError('El nombre de la materia es obligatorio');
      return;
    }
    if (!form.clave || form.clave.trim() === '') {
      setError('La clave de la materia es obligatoria');
      return;
    }
    if (form.horasSemana <= 0) {
      setError('Las horas por semana deben ser mayor a 0');
      return;
    }
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
        nombre: form.nombre.trim(),
        clave: form.clave.trim(),
        descripcion: form.descripcion?.trim() || '',
        creditos: Number(form.creditos),
        horasSemana: Number(form.horasSemana),
        colorHex: form.colorHex || '#808080',
        activo: form.activo !== false,
        semestreId: semestreActivo.id,
        turnoId: form.turnoId,             // 🔥 NUEVO
      };

      if (isEdit) {
        await materiaService.actualizar(Number(id), dataToSend);
      } else {
        await materiaService.crear(dataToSend);
      }

      volverConFiltros();
    } catch (err: any) {
      const codigo = err.response?.data?.error;
      const mensaje = err.response?.data?.message;

      if (codigo === 'MATERIA_CLAVE_DUPLICADA') {
        setError(mensaje || 'Ya existe una materia con esa clave en este turno.');
      } else if (err.response?.data?.errors) {
        const errors = Object.values(err.response.data.errors).join(', ');
        setError(String(errors));
      } else if (mensaje) {
        setError(mensaje);
      } else {
        setError('Error al guardar la materia');
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
          {isEdit ? 'Editar Materia' : 'Nueva Materia'}
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
                No hay turnos activos en el semestre. Activa uno antes de crear materias.
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
          {/* Clave + Nombre */}
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                Clave *
              </label>
              <div className="relative">
                <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                  <MdClass className="text-gray-400 dark:text-gray-500 text-lg" />
                </div>
                <input
                  type="text"
                  name="clave"
                  value={form.clave}
                  onChange={handleChange}
                  required
                  maxLength={50}
                  className="w-full pl-10 pr-4 py-2.5 border border-gray-400 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition"
                  placeholder="MAT-101"
                />
              </div>
              <p className="text-xs text-gray-400 mt-1">Única por semestre y turno</p>
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                Nombre *
              </label>
              <div className="relative">
                <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                  <MdBook className="text-gray-400 dark:text-gray-500 text-lg" />
                </div>
                <input
                  type="text"
                  name="nombre"
                  value={form.nombre}
                  onChange={handleChange}
                  required
                  maxLength={100}
                  className="w-full pl-10 pr-4 py-2.5 border border-gray-400 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition"
                  placeholder="Matemáticas I"
                />
              </div>
            </div>
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
              La materia quedará asignada a este turno dentro del semestre
            </p>
          </div>

          {/* Descripción */}
          <div>
            <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
              Descripción
            </label>
            <textarea
              name="descripcion"
              value={form.descripcion}
              onChange={handleChange}
              rows={3}
              className="w-full px-4 py-2.5 border border-gray-400 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition resize-y"
              placeholder="Descripción detallada de la materia..."
            />
          </div>

          {/* Créditos + Horas Semana */}
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                Créditos
              </label>
              <div className="relative">
                <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                  <MdNumbers className="text-gray-400 dark:text-gray-500 text-lg" />
                </div>
                <input
                  type="number"
                  name="creditos"
                  value={form.creditos}
                  onChange={handleChange}
                  min="0"
                  step="1"
                  className="w-full pl-10 pr-4 py-2.5 border border-gray-400 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition"
                  placeholder="0"
                />
              </div>
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                Horas por Semana *
              </label>
              <div className="relative">
                <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                  <MdSchedule className="text-gray-400 dark:text-gray-500 text-lg" />
                </div>
                <input
                  type="number"
                  name="horasSemana"
                  value={form.horasSemana}
                  onChange={handleChange}
                  required
                  min="1"
                  step="1"
                  className="w-full pl-10 pr-4 py-2.5 border border-gray-400 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition"
                  placeholder="3"
                />
              </div>
            </div>
          </div>

          {/* Color */}
          <div>
            <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
              Color
            </label>
            <div className="flex items-center gap-3 relative">
              <div
                className="w-10 h-10 rounded-lg border-2 border-gray-400 dark:border-gray-600 cursor-pointer hover:scale-105 transition flex-shrink-0"
                style={{ backgroundColor: form.colorHex }}
                onClick={toggleColorPicker}
                title="Haz clic para cambiar el color"
              />

              <div className="flex-1 relative">
                <div className="absolute inset-y-0 left-0 pl-2 flex items-center pointer-events-none">
                  <MdColorLens className="text-gray-400 text-sm" />
                </div>
                <input
                  type="text"
                  name="colorHex"
                  value={form.colorHex}
                  onChange={handleChange}
                  maxLength={7}
                  className="w-full pl-7 pr-2 py-2 border border-gray-400 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition text-sm"
                  placeholder="#808080"
                />
              </div>

              <button
                type="button"
                onClick={generarColorAleatorioLocal}
                className="p-2 text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-200 hover:bg-gray-100 dark:hover:bg-gray-700 rounded-lg transition flex-shrink-0"
                title="Generar color aleatorio"
              >
                <MdColorLens className="text-xl" />
              </button>

              {showColorPicker && (
                <div
                  ref={colorPickerRef}
                  className="absolute z-50 top-full mt-2 left-0 bg-white dark:bg-gray-800 p-3 rounded-lg shadow-xl border border-gray-400 dark:border-gray-700"
                  style={{ minWidth: '200px' }}
                >
                  <div className="flex flex-col items-center gap-2">
                    <input
                      type="color"
                      value={form.colorHex}
                      onChange={handleColorChange}
                      className="w-full h-12 cursor-pointer border-none p-0 rounded"
                    />
                    <div className="flex items-center gap-2 w-full">
                      <input
                        type="text"
                        value={form.colorHex}
                        onChange={handleColorChange}
                        maxLength={7}
                        className="flex-1 px-2 py-1 text-sm border border-gray-400 dark:border-gray-600 rounded bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-blue-500"
                        placeholder="#808080"
                      />
                      <button
                        type="button"
                        onClick={generarColorAleatorioLocal}
                        className="px-3 py-1 text-sm bg-blue-600 hover:bg-blue-700 text-white rounded transition"
                      >
                        Aleatorio
                      </button>
                    </div>
                    <button
                      type="button"
                      onClick={() => setShowColorPicker(false)}
                      className="w-full px-3 py-1 text-sm bg-gray-200 dark:bg-gray-700 hover:bg-gray-300 dark:hover:bg-gray-600 rounded transition text-gray-700 dark:text-gray-300"
                    >
                      Cerrar
                    </button>
                  </div>
                </div>
              )}
            </div>
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
              Materia activa
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

export default MateriaForm;