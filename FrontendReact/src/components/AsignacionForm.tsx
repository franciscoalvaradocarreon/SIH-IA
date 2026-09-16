// src/components/AsignacionForm.tsx
import React, { useState, useEffect, useRef } from 'react';
import { useNavigate, useParams, useLocation } from 'react-router-dom';
import { asignacionService } from '../api/asignacionService';
import { grupoService } from '../api/grupoService';
import { materiaService } from '../api/materiaService';
import { maestroService } from '../api/maestroService';
import { aulaService } from '../api/aulaService';
import { useAuth } from '../context/AuthContext';
import type { AsignacionForm as AsignacionFormType } from '../types';
import {
  MdSave, MdCancel, MdClass, MdBook, MdPerson, MdMeetingRoom,
  MdAccessTime, MdSchedule, MdWarning
} from 'react-icons/md';
import { generarColorAleatorio, CirculoColor, COLORES_SUGERIDOS } from '../utils/colores';

const AsignacionForm: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const location = useLocation();
  const navigate = useNavigate();
  const isEdit = Boolean(id);
  const { semestreActivo } = useAuth();

  const queryParams = new URLSearchParams(location.search);
  // 🔥 Leer TODOS los filtros de la URL
  const grupoFiltro = parseInt(queryParams.get('grupo') || '0');
  const especialidadFiltro = queryParams.get('especialidad') || '0';
  const turnoFiltro = queryParams.get('turno') || '0';
  const busquedaFiltro = queryParams.get('busqueda') || '';

  // 🔥 Ref: aplicar preselección del grupo solo una vez
  const aplicarGrupoInicial = useRef(grupoFiltro > 0 && !isEdit);

  const [form, setForm] = useState<AsignacionFormType>({
    grupoId: 0,
    materiaId: 0,
    maestroId: 0,
    aulaId: 0,
    horas: 0,
    colorHex: generarColorAleatorio(),
    distribucion: '',
    activo: true,
    semestreId: semestreActivo?.id || 0,
    turnoId: 0,
  });

  const [grupos, setGrupos] = useState<any[]>([]);
  const [materias, setMaterias] = useState<any[]>([]);
  const [maestros, setMaestros] = useState<any[]>([]);
  const [aulas, setAulas] = useState<any[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [cargandoCatalogos, setCargandoCatalogos] = useState(true);
  const [distribucionError, setDistribucionError] = useState('');

  // Cargar catálogos cuando cambia el semestre
  useEffect(() => {
    cargarCatalogos();
  }, [semestreActivo?.id]);

  // Cargar asignación en modo edición
  useEffect(() => {
    if (isEdit) {
      cargarAsignacion();
    }
  }, [id]);

  // Sincronizar semestreId con el activo
  useEffect(() => {
    if (semestreActivo) {
      setForm(prev => ({
        ...prev,
        semestreId: semestreActivo.id,
      }));
    }
  }, [semestreActivo]);

  // 🔥 Preseleccionar grupo desde la URL una vez cargados los catálogos
  useEffect(() => {
    if (isEdit) return;                        // en edición, el grupo viene del registro
    if (!aplicarGrupoInicial.current) return;  // ya se aplicó
    if (cargandoCatalogos) return;             // aún no hay catálogos
    if (grupoFiltro <= 0) return;              // no hay grupo en URL

    const grupoSel = grupos.find(g => g.id === grupoFiltro);
    if (!grupoSel) {
      aplicarGrupoInicial.current = false;
      return;
    }

    setForm(prev => ({
      ...prev,
      grupoId: grupoFiltro,
      turnoId: grupoSel.turnoId || 0,
    }));
    aplicarGrupoInicial.current = false;
  }, [cargandoCatalogos, grupos, isEdit, grupoFiltro]);

  const volverConFiltros = () => {
    const params = new URLSearchParams();
    if (grupoFiltro > 0) params.set('grupo', String(grupoFiltro));               // 🔥 NUEVO
    if (especialidadFiltro && especialidadFiltro !== '0') {
      params.set('especialidad', especialidadFiltro);
    }
    if (turnoFiltro && turnoFiltro !== '0') {
      params.set('turno', turnoFiltro);
    }
    if (busquedaFiltro) {
      params.set('busqueda', busquedaFiltro);
    }
    const url = `/horarios/asignacion${params.toString() ? `?${params.toString()}` : ''}`;
    navigate(url);
  };

  const cargarCatalogos = async () => {
    const semestreId = semestreActivo?.id;
    if (!semestreId) {
      setError('No hay semestre activo');
      return;
    }

    setCargandoCatalogos(true);
    try {
      const [gruposRes, materiasRes, maestrosRes, aulasRes] = await Promise.all([
        grupoService.listar(0, 100, '', 0, semestreId),
        materiaService.listar(0, 100, '', semestreId),
        maestroService.listar(0, 100, '', semestreId),
        aulaService.listar(0, 100, '', semestreId),
      ]);

      const gruposActivos = gruposRes.data.content.filter((g: any) => g.activo === true);
      const materiasActivas = materiasRes.data.content.filter((m: any) => m.activo === true);
      const maestrosActivos = maestrosRes.data.content.filter((m: any) => m.activo === true);
      const aulasActivas = aulasRes.data.content.filter((a: any) => a.activo === true);

      setGrupos(gruposActivos);
      setMaterias(materiasActivas);
      setMaestros(maestrosActivos);
      setAulas(aulasActivas);
    } catch (error) {
      console.error('Error al cargar catálogos:', error);
      setError('Error al cargar los catálogos');
    } finally {
      setCargandoCatalogos(false);
    }
  };

  const cargarAsignacion = async () => {
    setLoading(true);
    try {
      const res = await asignacionService.obtener(Number(id));
      setForm({
        grupoId: res.data.grupoId,
        materiaId: res.data.materiaId,
        maestroId: res.data.maestroId,
        aulaId: res.data.aulaId,
        horas: res.data.horas,
        colorHex: res.data.colorHex || generarColorAleatorio(),
        distribucion: res.data.distribucion || '',
        activo: res.data.activo !== undefined ? res.data.activo : true,
        semestreId: res.data.semestreId || semestreActivo?.id || 0,
        turnoId: res.data.turnoId || 0,
      });
    } catch (err) {
      setError('Error al cargar los datos de la asignación');
    } finally {
      setLoading(false);
    }
  };

  const handleChange = (
    e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>
  ) => {
    const { name, value, type } = e.target;
    const parsedValue = type === 'number' ? Number(value) : value;

    setForm(prev => {
      const updated: AsignacionFormType = { ...prev, [name]: parsedValue };

      if (name === 'grupoId') {
        const grupoSel = grupos.find(g => g.id === Number(parsedValue));
        const nuevoTurno = grupoSel?.turnoId || 0;
        updated.turnoId = nuevoTurno;

        if (nuevoTurno > 0) {
          const materiaActual = materias.find(m => m.id === updated.materiaId);
          if (materiaActual && materiaActual.turnoId !== nuevoTurno) {
            updated.materiaId = 0;
          }
          const maestroActual = maestros.find(m => m.id === updated.maestroId);
          if (maestroActual && maestroActual.turnoId !== nuevoTurno) {
            updated.maestroId = 0;
          }
          const aulaActual = aulas.find(a => a.id === updated.aulaId);
          if (aulaActual && aulaActual.turnoId !== nuevoTurno) {
            updated.aulaId = 0;
          }
        } else {
          updated.materiaId = 0;
          updated.maestroId = 0;
          updated.aulaId = 0;
        }
      }

      return updated;
    });

    if (name === 'horas' || name === 'distribucion') {
      setDistribucionError('');
    }
  };

  const validarFormatoDistribucion = (distribucion: string): boolean => {
    if (!distribucion) return true;
    return /^[\d,]+$/.test(distribucion);
  };

  const validarSumaDistribucion = (
    distribucion: string,
    horas: number
  ): { valido: boolean; suma: number; mensaje: string } => {
    if (!distribucion) {
      return { valido: true, suma: 0, mensaje: '' };
    }
    if (!validarFormatoDistribucion(distribucion)) {
      return { valido: false, suma: 0, mensaje: 'Formato inválido' };
    }
    const partes = distribucion.split(',').map(Number);
    const suma = partes.reduce((acc, val) => acc + val, 0);
    const valido = suma === horas;
    return {
      valido,
      suma,
      mensaje: valido
        ? ''
        : `La suma de la distribución (${suma}) no coincide con las horas (${horas})`,
    };
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setDistribucionError('');

    if (form.distribucion && !validarFormatoDistribucion(form.distribucion)) {
      setDistribucionError('La distribución solo puede contener números y comas (ej: 111, 21)');
      return;
    }

    if (form.distribucion && form.horas > 0) {
      const resultado = validarSumaDistribucion(form.distribucion, form.horas);
      if (!resultado.valido) {
        setDistribucionError(resultado.mensaje);
        return;
      }
    }

    if (!semestreActivo?.id) {
      setError('No hay semestre activo');
      return;
    }

    if (!form.grupoId || form.grupoId === 0) {
      setError('Selecciona un grupo');
      return;
    }

    if (!form.turnoId || form.turnoId === 0) {
      setError('No se pudo determinar el turno del grupo seleccionado');
      return;
    }

    setLoading(true);

    try {
      const dataToSend = {
        ...form,
        semestreId: semestreActivo.id,
      };

      if (isEdit) {
        await asignacionService.actualizar(Number(id), dataToSend);
      } else {
        await asignacionService.crear(dataToSend);
      }
      volverConFiltros();
    } catch (err: any) {
      if (err.response?.data?.message) {
        setError(err.response.data.message);
      } else {
        setError('Error al guardar la asignación');
      }
    } finally {
      setLoading(false);
    }
  };

  if ((loading || cargandoCatalogos) && isEdit) {
    return (
      <div className="flex justify-center items-center h-64">
        <div className="animate-spin rounded-full h-12 w-12 border-4 border-blue-500 border-t-transparent"></div>
      </div>
    );
  }

  const distribucionValida = form.distribucion
    ? validarFormatoDistribucion(form.distribucion)
    : true;
  const sumaResultado = form.distribucion && distribucionValida && form.horas > 0
    ? validarSumaDistribucion(form.distribucion, form.horas)
    : null;

  const grupoSeleccionado = grupos.find(g => g.id === form.grupoId);
  const turnoNombre = grupoSeleccionado?.turno || '';

  const materiasFiltradas = form.turnoId > 0
    ? materias.filter(m => m.turnoId === form.turnoId)
    : materias;
  const maestrosFiltrados = form.turnoId > 0
    ? maestros.filter(m => m.turnoId === form.turnoId)
    : maestros;
  const aulasFiltradas = form.turnoId > 0
    ? aulas.filter(a => a.turnoId === form.turnoId)
    : aulas;

  return (
    <div className="max-w-3xl mx-auto p-6">
      <div className="bg-white dark:bg-gray-800 rounded-xl shadow-md p-6 md:p-8 border border-gray-100 dark:border-gray-700">
        <h1 className="text-2xl font-bold text-gray-800 dark:text-white mb-6">
          {isEdit ? 'Editar Asignación' : 'Nueva Asignación'}
        </h1>

        {error && (
          <div className="bg-red-50 dark:bg-red-900/30 text-red-700 dark:text-red-300 p-4 rounded-lg border border-red-200 dark:border-red-800 mb-6">
            {error}
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-5">
          {/* Grupo + Turno */}
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                Grupo *
              </label>
              <div className="relative">
                <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                  <MdClass className="text-gray-400 dark:text-gray-500 text-lg" />
                </div>
                <select
                  name="grupoId"
                  value={form.grupoId}
                  onChange={handleChange}
                  required
                  className="w-full pl-10 pr-4 py-2.5 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-blue-500"
                >
                  <option value={0}>Seleccionar grupo</option>
                  {grupos.map((grupo) => {
                    let especialidadTexto = '';
                    if (grupo.especialidad) {
                      if (typeof grupo.especialidad === 'string') {
                        especialidadTexto = grupo.especialidad;
                      } else if (typeof grupo.especialidad === 'object' && grupo.especialidad.nombre) {
                        especialidadTexto = grupo.especialidad.nombre;
                      }
                    }
                    return (
                      <option key={grupo.id} value={grupo.id}>
                        {grupo.nombre} - {grupo.turno}
                        {especialidadTexto ? ` (${especialidadTexto})` : ''}
                      </option>
                    );
                  })}
                </select>
              </div>
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                Turno
              </label>
              <div className="relative">
                <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                  <MdSchedule className="text-gray-400 dark:text-gray-500 text-lg" />
                </div>
                <input
                  type="text"
                  value={turnoNombre || 'Selecciona un grupo primero'}
                  readOnly
                  className="w-full pl-10 pr-4 py-2.5 border border-gray-300 dark:border-gray-600 rounded-lg bg-gray-100 dark:bg-gray-800 text-gray-700 dark:text-gray-300 cursor-not-allowed"
                />
              </div>
              <p className="text-xs text-gray-400 mt-1">
                Se asigna automáticamente según el grupo
              </p>
            </div>
          </div>

          {/* Materia + Maestro */}
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                Materia *
              </label>
              <div className="relative">
                <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                  <MdBook className="text-gray-400 dark:text-gray-500 text-lg" />
                </div>
                <select
                  name="materiaId"
                  value={form.materiaId}
                  onChange={handleChange}
                  required
                  disabled={materiasFiltradas.length === 0}
                  className="w-full pl-10 pr-4 py-2.5 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:opacity-50"
                >
                  <option value={0}>
                    {form.turnoId > 0
                      ? materiasFiltradas.length === 0
                        ? 'Sin materias en este turno'
                        : 'Seleccionar materia'
                      : 'Selecciona un grupo primero'}
                  </option>
                  {materiasFiltradas.map((materia) => (
                    <option key={materia.id} value={materia.id}>
                      {materia.nombre} ({materia.clave})
                    </option>
                  ))}
                </select>
              </div>
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                Maestro *
              </label>
              <div className="relative">
                <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                  <MdPerson className="text-gray-400 dark:text-gray-500 text-lg" />
                </div>
                <select
                  name="maestroId"
                  value={form.maestroId}
                  onChange={handleChange}
                  required
                  disabled={maestrosFiltrados.length === 0}
                  className="w-full pl-10 pr-4 py-2.5 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:opacity-50"
                >
                  <option value={0}>
                    {form.turnoId > 0
                      ? maestrosFiltrados.length === 0
                        ? 'Sin maestros en este turno'
                        : 'Seleccionar maestro'
                      : 'Selecciona un grupo primero'}
                  </option>
                  {maestrosFiltrados.map((maestro) => (
                    <option key={maestro.id} value={maestro.id}>
                      {maestro.nombreCompleto}
                    </option>
                  ))}
                </select>
              </div>
            </div>
          </div>

          {/* Aula + Horas */}
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                Aula *
              </label>
              <div className="relative">
                <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                  <MdMeetingRoom className="text-gray-400 dark:text-gray-500 text-lg" />
                </div>
                <select
                  name="aulaId"
                  value={form.aulaId}
                  onChange={handleChange}
                  required
                  disabled={aulasFiltradas.length === 0}
                  className="w-full pl-10 pr-4 py-2.5 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:opacity-50"
                >
                  <option value={0}>
                    {form.turnoId > 0
                      ? aulasFiltradas.length === 0
                        ? 'Sin aulas en este turno'
                        : 'Seleccionar aula'
                      : 'Selecciona un grupo primero'}
                  </option>
                  {aulasFiltradas.map((aula) => (
                    <option key={aula.id} value={aula.id}>
                      {aula.nombre} {aula.edificio ? `- ${aula.edificio}` : ''}
                    </option>
                  ))}
                </select>
              </div>
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                Horas por materia/aula *
              </label>
              <div className="relative">
                <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                  <MdAccessTime className="text-gray-400 dark:text-gray-500 text-lg" />
                </div>
                <input
                  type="number"
                  name="horas"
                  value={form.horas}
                  onChange={handleChange}
                  min={1}
                  max={35}
                  required
                  className="w-full pl-10 pr-4 py-2.5 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-blue-500"
                  placeholder="Ej: 3"
                />
              </div>
            </div>
          </div>

          {/* Color */}
          <div>
            <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
              Color (hex)
            </label>
            <div className="flex items-center gap-3">
              <input
                type="color"
                name="colorHex"
                value={form.colorHex}
                onChange={handleChange}
                className="w-12 h-12 p-1 border border-gray-300 dark:border-gray-600 rounded-lg cursor-pointer bg-white dark:bg-gray-700"
              />
              <input
                type="text"
                name="colorHex"
                value={form.colorHex}
                onChange={handleChange}
                pattern="^#[0-9A-Fa-f]{6}$"
                className="flex-1 px-4 py-2.5 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition font-mono"
                placeholder="#3B82F6"
              />
            </div>
          </div>

          {/* Distribución */}
          <div>
            <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
              Distribución
            </label>
            <input
              type="text"
              name="distribucion"
              value={form.distribucion || ''}
              onChange={handleChange}
              placeholder="Ej: 111 o 21"
              className={`w-full px-4 py-2.5 border rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-blue-500 transition ${
                distribucionError
                  ? 'border-red-500 dark:border-red-500'
                  : form.distribucion && distribucionValida && sumaResultado?.valido
                  ? 'border-green-500 dark:border-green-500'
                  : 'border-gray-300 dark:border-gray-600'
              }`}
            />

            {distribucionError && (
              <p className="mt-1 text-sm text-red-600 dark:text-red-400">
                {distribucionError}
              </p>
            )}

            {form.distribucion && distribucionValida && form.horas > 0 && sumaResultado && (
              <div className={`mt-1 text-sm ${sumaResultado.valido ? 'text-green-600 dark:text-green-400' : 'text-red-600 dark:text-red-400'}`}>
                {sumaResultado.valido ? (
                  <span>✅ La suma de la distribución ({sumaResultado.suma}) coincide con las horas ({form.horas})</span>
                ) : (
                  <span>⚠️ {sumaResultado.mensaje}</span>
                )}
              </div>
            )}

            <div className="mt-2 p-3 bg-gray-50 dark:bg-gray-800/50 rounded-lg border border-gray-200 dark:border-gray-700">
              <p className="text-xs text-gray-500 dark:text-gray-400">
                <span className="font-medium">Formato:</span> Números separados por comas.
                La suma debe coincidir con las horas totales.
              </p>
              <div className="mt-1 flex flex-wrap gap-2">
                <span className="text-xs bg-blue-50 dark:bg-blue-900/20 text-blue-600 dark:text-blue-400 px-2 py-0.5 rounded">
                  Ej: 111 = 1h + 1h + 1h
                </span>
                <span className="text-xs bg-blue-50 dark:bg-blue-900/20 text-blue-600 dark:text-blue-400 px-2 py-0.5 rounded">
                  Ej: 21 = 2h + 1h
                </span>
                <span className="text-xs bg-blue-50 dark:bg-blue-900/20 text-blue-600 dark:text-blue-400 px-2 py-0.5 rounded">
                  Ej: 12 = 1h + 2h
                </span>
              </div>
            </div>
          </div>

          {/* Activo */}
          <div className="flex items-center gap-2 pt-2">
            <input
              type="checkbox"
              name="activo"
              checked={form.activo !== false}
              onChange={(e) => setForm({ ...form, activo: e.target.checked })}
              className="w-4 h-4 text-blue-600 border-gray-300 rounded focus:ring-blue-500"
            />
            <label className="text-sm font-medium text-gray-700 dark:text-gray-300">
              Asignación activa
            </label>
          </div>

          {/* Botones */}
          <div className="flex gap-3 pt-4 border-t border-gray-200 dark:border-gray-700">
            <button
              type="submit"
              disabled={loading || !semestreActivo}
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

export default AsignacionForm;