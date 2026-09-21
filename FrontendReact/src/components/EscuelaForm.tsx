import React, { useState, useEffect } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { escuelaService } from '../api/escuelaService';
import type { EscuelaForm as EscuelaFormType } from '../types';
import { urlFoto } from '../utils/imagenes';
import {
  MdSave,
  MdCancel,
  MdSchool,
  MdLocationOn,
  MdPhone,
  MdKey,
  MdBusiness,
  MdPhoto,
  MdDelete,
} from 'react-icons/md';

const EscuelaForm: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const isEdit = Boolean(id);

  const [form, setForm] = useState<EscuelaFormType>({
    nombre: '',
    nombreLargo: '',
    direccion: '',
    telefono: '',
    clave: '',
    logoUrl: '',
  });

  // 🔥 Estado para el logo
  const [logoArchivo, setLogoArchivo] = useState<File | null>(null);
  const [previewUrl, setPreviewUrl] = useState<string>('');

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (isEdit) {
      cargarEscuela();
    }
  }, [id]);

  const cargarEscuela = async () => {
    setLoading(true);
    try {
      const res = await escuelaService.obtener(Number(id));
      setForm({
        nombre: res.data.nombre,
        nombreLargo: res.data.nombreLargo || '',
        direccion: res.data.direccion,
        telefono: res.data.telefono,
        clave: res.data.clave,
        logoUrl: res.data.logoUrl || '',
      });
      if (res.data.logoUrl) {
        setPreviewUrl(urlFoto(res.data.logoUrl) || '');
      }
    } catch (err) {
      setError('Error al cargar los datos de la escuela');
    } finally {
      setLoading(false);
    }
  };

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const { name, value } = e.target;
    setForm({ ...form, [name]: value });
  };

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    // Validación en el cliente. El backend valida además por CONTENIDO real
    // (magic bytes) y re-codifica la imagen.
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
    setLogoArchivo(file);
    if (previewUrl.startsWith('blob:')) {
      URL.revokeObjectURL(previewUrl);
    }
    setPreviewUrl(URL.createObjectURL(file));
  };

  const handleRemovePhoto = () => {
    setLogoArchivo(null);
    setPreviewUrl('');
    setForm({ ...form, logoUrl: '' });
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setLoading(true);

    try {
      const dataToSend: EscuelaFormType = {
        ...form,
        logoArchivo,
      };

      if (isEdit) {
        await escuelaService.actualizar(Number(id), dataToSend);
      } else {
        await escuelaService.crear(dataToSend);
      }
      navigate('/administracion/escuelas');
    } catch (err: any) {
      if (err.response?.data?.message) {
        setError(err.response.data.message);
      } else {
        setError('Error al guardar la escuela');
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
          {isEdit ? 'Editar Escuela' : 'Nueva Escuela'}
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
                <MdSchool className="text-gray-400 dark:text-gray-500 text-lg" />
              </div>
              <input
                type="text"
                name="nombre"
                value={form.nombre}
                onChange={handleChange}
                required
                maxLength={150}
                className="w-full pl-10 pr-4 py-2.5 border border-gray-400 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition"
                placeholder="Ej: CETI 66"
              />
            </div>
            <p className="text-xs text-gray-400 mt-1">
              Nombre corto o abreviatura de la escuela
            </p>
          </div>

          {/* Nombre largo */}
          <div>
            <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
              Nombre largo
            </label>
            <div className="relative">
              <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                <MdBusiness className="text-gray-400 dark:text-gray-500 text-lg" />
              </div>
              <input
                type="text"
                name="nombreLargo"
                value={form.nombreLargo || ''}
                onChange={handleChange}
                maxLength={150}
                className="w-full pl-10 pr-4 py-2.5 border border-gray-400 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition"
                placeholder="Ej: Centro de Estudios Tecnológicos Industrial y de Servicios No. 66"
              />
            </div>
            <p className="text-xs text-gray-400 mt-1">
              Nombre completo de la institución (opcional)
            </p>
          </div>

          {/* Clave */}
          <div>
            <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
              Clave *
            </label>
            <div className="relative">
              <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                <MdKey className="text-gray-400 dark:text-gray-500 text-lg" />
              </div>
              <input
                type="text"
                name="clave"
                value={form.clave}
                onChange={handleChange}
                required
                maxLength={30}
                className="w-full pl-10 pr-4 py-2.5 border border-gray-400 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition"
                placeholder="Ej: CETI66, UDG001"
              />
            </div>
            <p className="text-xs text-gray-400 mt-1">
              Clave única de la escuela
            </p>
          </div>

          {/* Dirección */}
          <div>
            <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
              Dirección
            </label>
            <div className="relative">
              <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                <MdLocationOn className="text-gray-400 dark:text-gray-500 text-lg" />
              </div>
              <input
                type="text"
                name="direccion"
                value={form.direccion || ''}
                onChange={handleChange}
                maxLength={255}
                className="w-full pl-10 pr-4 py-2.5 border border-gray-400 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition"
                placeholder="Calle, número, colonia..."
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
                value={form.telefono || ''}
                onChange={handleChange}
                maxLength={20}
                className="w-full pl-10 pr-4 py-2.5 border border-gray-400 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition"
                placeholder="555-1234"
              />
            </div>
          </div>

          {/* Logo */}
          <div>
            <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
              Logo
            </label>
            <div className="flex items-center gap-4">
              {previewUrl && (
                <div className="relative">
                  <img
                    src={previewUrl}
                    alt="Vista previa del logo"
                    className="w-20 h-20 rounded-lg object-contain border-2 border-gray-400 dark:border-gray-600 bg-white dark:bg-gray-700 p-1"
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
                      {previewUrl ? 'Cambiar logo' : 'Seleccionar logo'}
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
                  Formatos: JPG, PNG, GIF. Máximo 5MB. Recomendado: fondo transparente (PNG).
                </p>
              </div>
            </div>
          </div>

          {/* Botones */}
          <div className="flex gap-3 pt-4 border-t border-gray-400 dark:border-gray-700">
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
              onClick={() => navigate('/administracion/escuelas')}
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

export default EscuelaForm;