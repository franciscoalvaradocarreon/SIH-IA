// src/components/MaestroForm.tsx
import React, { useState, useEffect } from 'react';
import { useNavigate, useParams, useLocation } from 'react-router-dom';
import { maestroService } from '../api/maestroService';
import { turnoService } from '../api/turnoService';
import type { MaestroForm as MaestroFormType, Turno } from '../types';
import {
  MdDelete, MdSave, MdCancel, MdPerson, MdEmail, MdPhone,
  MdPhoto, MdClass, MdWarning, MdSchedule, MdBadge
} from 'react-icons/md';
import { useAuth } from '../context/AuthContext';
import { urlFoto } from '../utils/imagenes';

const MaestroForm: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const location = useLocation();
  const isEdit = Boolean(id);
  const { semestreActivo } = useAuth();

  const queryParams = new URLSearchParams(location.search);
  const busquedaFiltro = queryParams.get('busqueda') || '';

  const [form, setForm] = useState<MaestroFormType>({
    nombre: '',
    apellidos: '',
    email: '',
    telefono: '',
    titulo: '',
    apodo: '',
    fotoUrl: '',
    fotoArchivo: null,
    semestreId: semestreActivo?.id || 0,
    turnoId: 0,
  });
  const [fotoArchivo, setFotoArchivo] = useState<File | null>(null);
  const [previewUrl, setPreviewUrl] = useState<string>('');
  const [turnos, setTurnos] = useState<Turno[]>([]);
  const [cargandoTurnos, setCargandoTurnos] = useState(true);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    cargarTurnos();
  }, [semestreActivo?.id]);

  useEffect(() => {
    if (isEdit) {
      cargarMaestro();
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
    } catch (error) {
      console.error('Error al cargar turnos:', error);
      setTurnos([]);
    } finally {
      setCargandoTurnos(false);
    }
  };

  const cargarMaestro = async () => {
    setLoading(true);
    try {
      const res = await maestroService.obtener(Number(id));
      const data = res.data;
      console.log('📥 Maestro cargado:', data);

      setForm({
        nombre: data.nombre || '',
        apellidos: data.apellidos || '',
        email: data.email || '',
        telefono: data.telefono || '',
        titulo: data.titulo || '',
        apodo: data.apodo || '',
        fotoUrl: data.fotoUrl || '',
        fotoArchivo: null,
        semestreId: data.semestreId || semestreActivo?.id || 0,
        turnoId: data.turnoId || 0,
      });
      if (data.fotoUrl) {
        setPreviewUrl(urlFoto(data.fotoUrl) || '');
      }
    } catch (err) {
      console.error('❌ Error al cargar maestro:', err);
      setError('Error al cargar los datos del maestro');
    } finally {
      setLoading(false);
    }
  };

  const handleChange = (
    e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>
  ) => {
    const { name, value } = e.target;
    setForm({ ...form, [name]: name === 'turnoId' ? Number(value) : value });
  };

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    // Validación en el cliente. El backend valida además por CONTENIDO real
    // (magic bytes) y re-codifica la imagen, así que esto es solo para dar un
    // mensaje inmediato y no subir 5MB para recibir un error.
    const TIPOS_PERMITIDOS = ['image/jpeg', 'image/png', 'image/gif'];
    const MAX_BYTES = 5 * 1024 * 1024;

    if (!TIPOS_PERMITIDOS.includes(file.type)) {
      setError('Formato no permitido. Usa JPG, PNG o GIF.');
      e.target.value = '';
      return;
    }
    if (file.size > MAX_BYTES) {
      setError('La imagen no puede exceder 5MB.');
      e.target.value = '';
      return;
    }

    setError('');
    setFotoArchivo(file);
    // Liberar la URL anterior para no acumular blobs en memoria.
    if (previewUrl.startsWith('blob:')) {
      URL.revokeObjectURL(previewUrl);
    }
    setPreviewUrl(URL.createObjectURL(file));
  };

  const handleRemovePhoto = () => {
    setFotoArchivo(null);
    setPreviewUrl('');
    setForm({ ...form, fotoUrl: '' });
  };

  const volverConFiltros = () => {
    const params = new URLSearchParams();
    if (busquedaFiltro) params.set('busqueda', busquedaFiltro);
    const url = `/catalogo/maestros${params.toString() ? `?${params.toString()}` : ''}`;
    navigate(url);
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
      const formData = new FormData();
      formData.append('nombre', form.nombre);
      formData.append('apellidos', form.apellidos);
      if (form.email) formData.append('email', form.email);
      if (form.telefono) formData.append('telefono', form.telefono);
      if (form.titulo) formData.append('titulo', form.titulo);
      if (form.apodo) formData.append('apodo', form.apodo);
      if (fotoArchivo) {
        formData.append('fotoArchivo', fotoArchivo);
      } else if (form.fotoUrl) {
        formData.append('fotoUrl', form.fotoUrl);
      }
      formData.append('semestreId', String(semestreActivo.id));
      formData.append('turnoId', String(form.turnoId));



      if (isEdit) {
        await maestroService.actualizar(Number(id), formData);
      } else {
        await maestroService.crear(formData);
      }

      volverConFiltros();
    } catch (err: any) {
      console.error('❌ Error al guardar:', err);

      const codigo = err.response?.data?.error;
      const mensaje = err.response?.data?.message;

      if (codigo === 'MAESTRO_NOMBRE_DUPLICADO') {
        setError(mensaje || 'Ya existe un maestro con ese nombre en este turno.');
      } else if (mensaje) {
        setError(mensaje);
      } else {
        setError('Error al guardar el maestro');
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
    <div className="max-w-4xl mx-auto p-6">
      <div className="bg-white dark:bg-gray-800 rounded-xl shadow-md p-6 md:p-8 border border-gray-400 dark:border-gray-700">
        <h1 className="text-2xl font-bold text-gray-800 dark:text-white mb-6">
          {isEdit ? 'Editar Maestro' : 'Nuevo Maestro'}
        </h1>

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

        {semestreActivo && turnos.length === 0 && (
          <div className="mb-4 p-3 bg-yellow-50 dark:bg-yellow-900/20 rounded-lg border border-yellow-200 dark:border-yellow-800">
            <div className="flex items-center gap-2">
              <MdWarning className="text-yellow-500 dark:text-yellow-400 text-lg" />
              <span className="text-sm text-yellow-800 dark:text-yellow-200 font-medium">
                No hay turnos activos en el semestre. Activa uno antes de crear maestros.
              </span>
            </div>
          </div>
        )}

        {error && (
          <div className="bg-red-50 dark:bg-red-900/30 text-red-700 dark:text-red-300 p-4 rounded-lg border border-red-200 dark:border-red-800 mb-6">
            {error}
          </div>
        )}

        <form onSubmit={handleSubmit} className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {/* Título */}
          <div>
            <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
              Título
            </label>
            <div className="relative">
              <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                <MdPerson className="text-gray-400 dark:text-gray-500 text-lg" />
              </div>
              <input
                type="text"
                name="titulo"
                value={form.titulo || ''}
                onChange={handleChange}
                className="w-full pl-10 pr-4 py-2.5 border border-gray-400 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition"
                placeholder="Ej: Lic., Mtro., Dr., Mtra."
              />
            </div>
          </div>

          {/* Nombre */}
          <div>
            <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
              Nombre *
            </label>
            <div className="relative">
              <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                <MdPerson className="text-gray-400 dark:text-gray-500 text-lg" />
              </div>
              <input
                type="text"
                name="nombre"
                value={form.nombre}
                onChange={handleChange}
                required
                className="w-full pl-10 pr-4 py-2.5 border border-gray-400 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition"
                placeholder="Juan"
              />
            </div>
          </div>

          {/* Apellidos */}
          <div>
            <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
              Apellidos *
            </label>
            <div className="relative">
              <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                <MdPerson className="text-gray-400 dark:text-gray-500 text-lg" />
              </div>
              <input
                type="text"
                name="apellidos"
                value={form.apellidos}
                onChange={handleChange}
                required
                className="w-full pl-10 pr-4 py-2.5 border border-gray-400 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition"
                placeholder="Pérez"
              />
            </div>
          </div>

          {/* Apodo */}
          <div>
            <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
              Apodo
            </label>
            <div className="relative">
              <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                <MdBadge className="text-gray-400 dark:text-gray-500 text-lg" />
              </div>
              <input
                type="text"
                name="apodo"
                value={form.apodo || ''}
                onChange={handleChange}
                maxLength={15}
                className="w-full pl-10 pr-4 py-2.5 border border-gray-400 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition"
                placeholder="Ej: El Profe, Chava, La Mtra. Lupita"
              />
            </div>
          </div>

          {/* Email */}
          <div>
            <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
              Email
            </label>
            <div className="relative">
              <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                <MdEmail className="text-gray-400 dark:text-gray-500 text-lg" />
              </div>
              <input
                type="email"
                name="email"
                value={form.email}
                onChange={handleChange}
                className="w-full pl-10 pr-4 py-2.5 border border-gray-400 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition"
                placeholder="juan.perez@escuela.com"
              />
            </div>
          </div>

          {/* Teléfono */}
          <div>
            <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
              Teléfono
            </label>
            <div className="relative">
              <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                <MdPhone className="text-gray-400 dark:text-gray-500 text-lg" />
              </div>
              <input
                type="text"
                name="telefono"
                value={form.telefono}
                onChange={handleChange}
                className="w-full pl-10 pr-4 py-2.5 border border-gray-400 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition"
                placeholder="555-1234"
              />
            </div>
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
              El maestro quedará asignado a este turno dentro del semestre
            </p>
          </div>

          {/* Foto */}
          <div>
            <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
              Foto
            </label>
            <div className="flex items-center gap-4">
              {previewUrl && (
                <div className="relative">
                  <img
                    src={previewUrl}
                    alt="Vista previa"
                    className="w-20 h-20 rounded-full object-cover border-2 border-gray-400 dark:border-gray-600"
                    onError={(e) => {
                      console.error('❌ Error al cargar la vista previa:', previewUrl);
                      e.currentTarget.style.display = 'none';
                    }}
                  />
                  <button
                    type="button"
                    onClick={handleRemovePhoto}
                    className="absolute -top-2 -right-2 bg-red-500 text-white rounded-full p-1 hover:bg-red-600 transition"
                  >
                    <MdDelete className="text-sm" />
                  </button>
                </div>
              )}

              <div className="flex-1">
                <label className="cursor-pointer">
                  <div className="flex items-center gap-2 px-4 py-2.5 border border-gray-400 dark:border-gray-600 rounded-lg hover:bg-gray-50 dark:hover:bg-gray-700 transition">
                    <MdPhoto className="text-gray-400 text-xl" />
                    <span className="text-sm text-gray-600 dark:text-gray-300">
                      {previewUrl ? 'Cambiar foto' : 'Seleccionar foto'}
                    </span>
                  </div>
                  <input
                    type="file"
                    accept="image/*"
                    onChange={handleFileChange}
                    className="hidden"
                  />
                </label>
                <p className="text-xs text-gray-400 mt-1">
                  Formatos: JPG, PNG, GIF. Máximo 5MB
                </p>
              </div>
            </div>
          </div>

          {/* Botones */}
          <div className="md:col-span-2 flex gap-3 pt-4 border-t border-gray-400 dark:border-gray-700">
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

export default MaestroForm;