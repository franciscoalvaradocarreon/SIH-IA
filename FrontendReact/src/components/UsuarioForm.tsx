import React, { useState, useEffect } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { usuarioService } from '../api/usuarioService';
import { escuelaService } from '../api/escuelaService';
import { rolService } from '../api/rolService';
import type { UsuarioForm as UsuarioFormType, AsignacionEscuelaRol } from '../types';
import { urlFoto } from '../utils/imagenes';
import {
  MdSave,
  MdCancel,
  MdPerson,
  MdEmail,
  MdLock,
  MdSchool,
  MdSecurity,
  MdAdd,
  MdDelete,
  MdPhoto,
} from 'react-icons/md';

const UsuarioForm: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const isEdit = Boolean(id);

  const [form, setForm] = useState<UsuarioFormType>({
    usuario: '',
    nombreCompleto: '',
    email: '',
    fotoUrl: '',
    password: '',
    activo: true,
    asignaciones: [],
  });

  // 🔥 Estado para la foto
  const [fotoArchivo, setFotoArchivo] = useState<File | null>(null);
  const [previewUrl, setPreviewUrl] = useState<string>('');

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [escuelas, setEscuelas] = useState<any[]>([]);
  const [roles, setRoles] = useState<any[]>([]);
  const [cargandoCatalogos, setCargandoCatalogos] = useState(true);

  useEffect(() => {
    cargarCatalogos();
    if (isEdit) {
      cargarUsuario();
    }
  }, [id]);

  const cargarCatalogos = async () => {
    setCargandoCatalogos(true);
    try {
      const [escuelasRes, rolesRes] = await Promise.all([
        escuelaService.listar(0, 100),
        rolService.listar(0, 100),
      ]);
      setEscuelas(escuelasRes.data.content);
      setRoles(rolesRes.data.content);
    } catch (error) {
      console.error('Error al cargar catálogos:', error);
    } finally {
      setCargandoCatalogos(false);
    }
  };

  const cargarUsuario = async () => {
    setLoading(true);
    try {
      const res = await usuarioService.obtener(Number(id));
      const data = res.data;

      setForm({
        usuario: data.usuario,
        nombreCompleto: data.nombreCompleto,
        email: data.email,
        fotoUrl: data.fotoUrl || '',
        activo: data.activo,
        asignaciones: data.asignaciones?.map((a: any) => ({
          escuelaId: a.escuelaId,
          rolId: a.rolId,
          activo: a.activo,
        })) || [],
      });

      // Inicializar preview con la foto existente
      if (data.fotoUrl) {
        setPreviewUrl(urlFoto(data.fotoUrl) || '');
      }
    } catch (err) {
      setError('Error al cargar los datos del usuario');
    } finally {
      setLoading(false);
    }
  };

  const handleChange = (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => {
    const { name, value, type } = e.target;
    setForm({
      ...form,
      [name]: type === 'checkbox' ? (e.target as HTMLInputElement).checked : value,
    });
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

  const handleAsignacionChange = (
    index: number,
    field: keyof AsignacionEscuelaRol,
    value: any
  ) => {
    const nuevasAsignaciones = [...(form.asignaciones || [])];
    nuevasAsignaciones[index] = {
      ...nuevasAsignaciones[index],
      [field]: value,
    };
    setForm({ ...form, asignaciones: nuevasAsignaciones });
  };

  const agregarAsignacion = () => {
    setForm({
      ...form,
      asignaciones: [
        ...(form.asignaciones || []),
        { escuelaId: 0, rolId: 0, activo: true },
      ],
    });
  };

  const eliminarAsignacion = (index: number) => {
    const nuevasAsignaciones = [...(form.asignaciones || [])];
    nuevasAsignaciones.splice(index, 1);
    setForm({ ...form, asignaciones: nuevasAsignaciones });
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setLoading(true);

    try {
      // Adjuntar la foto al DTO antes de enviar
      const dataToSend: UsuarioFormType = {
        ...form,
        fotoArchivo,
      };

      if (isEdit) {
        await usuarioService.actualizar(Number(id), dataToSend);
      } else {
        await usuarioService.crear(dataToSend);
      }
      navigate('/administracion/usuarios');
    } catch (err: any) {
      if (err.response?.data?.message) {
        setError(err.response.data.message);
      } else {
        setError('Error al guardar el usuario');
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

  if (cargandoCatalogos) {
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
          {isEdit ? 'Editar Usuario' : 'Nuevo Usuario'}
        </h1>

        {error && (
          <div className="bg-red-50 dark:bg-red-900/30 text-red-700 dark:text-red-300 p-4 rounded-lg border border-red-200 dark:border-red-800 mb-6">
            {error}
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-5">
          {/* Datos básicos */}
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                Nombre de usuario *
              </label>
              <div className="relative">
                <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                  <MdPerson className="text-gray-400 dark:text-gray-500 text-lg" />
                </div>
                <input
                  type="text"
                  name="usuario"
                  value={form.usuario}
                  onChange={handleChange}
                  required
                  className="w-full pl-10 pr-4 py-2.5 border border-gray-400 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition"
                  placeholder="usuario123"
                />
              </div>
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                Correo electrónico *
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
                  required
                  className="w-full pl-10 pr-4 py-2.5 border border-gray-400 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition"
                  placeholder="usuario@escuela.com"
                />
              </div>
            </div>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                Nombre completo *
              </label>
              <div className="relative">
                <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                  <MdPerson className="text-gray-400 dark:text-gray-500 text-lg" />
                </div>
                <input
                  type="text"
                  name="nombreCompleto"
                  value={form.nombreCompleto}
                  onChange={handleChange}
                  required
                  className="w-full pl-10 pr-4 py-2.5 border border-gray-400 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition"
                  placeholder="Juan Pérez"
                />
              </div>
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                Contraseña {!isEdit && '*'}
              </label>
              <div className="relative">
                <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                  <MdLock className="text-gray-400 dark:text-gray-500 text-lg" />
                </div>
                <input
                  type="password"
                  name="password"
                  value={form.password || ''}
                  onChange={handleChange}
                  required={!isEdit}
                  className="w-full pl-10 pr-4 py-2.5 border border-gray-400 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition"
                  placeholder={isEdit ? 'Dejar en blanco para mantener' : '******'}
                />
              </div>
            </div>
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

          {/* Checkbox Activo */}
          <div className="flex items-center gap-2">
            <input
              type="checkbox"
              name="activo"
              checked={form.activo !== false}
              onChange={handleChange}
              className="w-4 h-4 text-blue-600 border-gray-400 rounded focus:ring-blue-500"
            />
            <label className="text-sm font-medium text-gray-700 dark:text-gray-300">
              Usuario activo
            </label>
          </div>

          {/* Asignaciones Escuela-Rol */}
          <div className="border-t border-gray-400 dark:border-gray-700 pt-5 mt-5">
            <div className="flex justify-between items-center mb-4">
              <h3 className="text-lg font-semibold text-gray-800 dark:text-white">
                Asignaciones
              </h3>
              <button
                type="button"
                onClick={agregarAsignacion}
                className="flex items-center gap-1 text-sm bg-blue-100 dark:bg-blue-900/30 text-blue-600 dark:text-blue-400 hover:bg-blue-200 dark:hover:bg-blue-800/40 px-3 py-1.5 rounded-lg transition"
              >
                <MdAdd className="text-lg" />
                Agregar
              </button>
            </div>

            <div className="space-y-3">
              {form.asignaciones?.map((asignacion, index) => (
                <div
                  key={index}
                  className="grid grid-cols-1 md:grid-cols-3 gap-3 items-end bg-gray-50 dark:bg-gray-700/50 p-3 rounded-lg border border-gray-400 dark:border-gray-600"
                >
                  <div>
                    <label className="block text-xs font-medium text-gray-600 dark:text-gray-400 mb-1">
                      Escuela
                    </label>
                    <select
                      value={asignacion.escuelaId}
                      onChange={(e) =>
                        handleAsignacionChange(index, 'escuelaId', Number(e.target.value))
                      }
                      className="w-full px-3 py-2 border border-gray-400 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-blue-500"
                      required
                    >
                      <option value={0}>Seleccionar</option>
                      {escuelas.map((escuela) => (
                        <option key={escuela.id} value={escuela.id}>
                          {escuela.nombre}
                        </option>
                      ))}
                    </select>
                  </div>

                  <div>
                    <label className="block text-xs font-medium text-gray-600 dark:text-gray-400 mb-1">
                      Rol
                    </label>
                    <select
                      value={asignacion.rolId}
                      onChange={(e) =>
                        handleAsignacionChange(index, 'rolId', Number(e.target.value))
                      }
                      className="w-full px-3 py-2 border border-gray-400 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-blue-500"
                      required
                    >
                      <option value={0}>Seleccionar</option>
                      {roles.map((rol) => (
                        <option key={rol.id} value={rol.id}>
                          {rol.nombre}
                        </option>
                      ))}
                    </select>
                  </div>

                  <div className="flex items-end gap-2">
                    <div className="flex-1">
                      <label className="block text-xs font-medium text-gray-600 dark:text-gray-400 mb-1">
                        Activo
                      </label>
                      <input
                        type="checkbox"
                        checked={asignacion.activo !== false}
                        onChange={(e) =>
                          handleAsignacionChange(index, 'activo', e.target.checked)
                        }
                        className="w-4 h-4 mt-1 text-blue-600 border-gray-400 rounded focus:ring-blue-500"
                      />
                    </div>
                    <button
                      type="button"
                      onClick={() => eliminarAsignacion(index)}
                      className="text-red-500 hover:text-red-700 p-1.5 rounded-lg hover:bg-red-50 dark:hover:bg-red-900/20 transition"
                    >
                      <MdDelete className="text-xl" />
                    </button>
                  </div>
                </div>
              ))}

              {(!form.asignaciones || form.asignaciones.length === 0) && (
                <p className="text-sm text-gray-500 dark:text-gray-400 text-center py-3">
                  No hay asignaciones. Haz clic en "Agregar" para asignar una escuela y rol.
                </p>
              )}
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
              onClick={() => navigate('/administracion/usuarios')}
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

export default UsuarioForm;