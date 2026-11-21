import React, { useEffect, useState } from 'react';
import { MdAdd, MdEdit, MdRefresh, MdScience, MdCancel, MdCheckCircle, MdDelete, MdWarning } from 'react-icons/md';
import { useAuth } from '../context/AuthContext';
import { tiposEspacioService } from '../api/tiposEspacioService';
import { turnoService } from '../api/turnoService';
import type { TipoEspacio, TipoEspacioForm, Turno } from '../types';

/**
 * Catalogo de laboratorios y talleres del semestre.
 *
 * Es la lista que despues aparece en el formulario de aula: aqui se decide que tipos de espacio
 * existen, y en el aula se elige uno. Los cuatro que vienen de la migracion son solo el punto de
 * partida.
 */

const PuntoClase: React.FC<{ clase: string }> = ({ clase }) => (
  <span
    className={`inline-block h-1.5 w-1.5 rounded-full ${
      clase === 'TALLER' ? 'bg-amber-500' : 'bg-blue-500'
    }`}
  />
);

const nombreClase = (clase: string) => (clase === 'TALLER' ? 'Taller' : 'Laboratorio');

const TiposEspacio: React.FC = () => {
  const { semestreActivo } = useAuth();
  const [tipos, setTipos] = useState<TipoEspacio[]>([]);
  const [turnos, setTurnos] = useState<Turno[]>([]);
  const [turnoId, setTurnoId] = useState<number>(0);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState('');

  const [modal, setModal] = useState(false);
  const [editando, setEditando] = useState<TipoEspacio | null>(null);
  const [form, setForm] = useState<TipoEspacioForm>({ clase: 'LABORATORIO', nombre: '', activo: true });
  const [guardando, setGuardando] = useState(false);

  const [aDesactivar, setADesactivar] = useState<TipoEspacio | null>(null);

  useEffect(() => {
    if (!semestreActivo?.id) return;
    // El catalogo es por turno: se cargan los turnos del semestre y se elige el primero activo.
    (async () => {
      try {
        const res = await turnoService.listar(0, 100, '', semestreActivo.id);
        const activos = res.data.content.filter((t) => t.activo !== false);
        setTurnos(activos);
        setTurnoId((prev) => (prev && activos.some((t) => t.id === prev) ? prev : activos[0]?.id ?? 0));
      } catch {
        setTurnos([]);
      }
    })();
  }, [semestreActivo?.id]);

  useEffect(() => {
    if (turnoId) cargar();
  }, [turnoId]);

  const cargar = async () => {
    if (!turnoId) return;
    setCargando(true);
    setError('');
    try {
      const res = await tiposEspacioService.listar(turnoId, false);
      setTipos(res.data);
    } catch (e: any) {
      setError(e.response?.data?.message ?? 'No se pudo cargar el catÃ¡logo');
    } finally {
      setCargando(false);
    }
  };

  const abrirNuevo = () => {
    setEditando(null);
    setForm({ clase: 'LABORATORIO', nombre: '', activo: true });
    setModal(true);
  };

  const abrirEditar = (t: TipoEspacio) => {
    setEditando(t);
    setForm({ clase: t.clase, nombre: t.nombre, activo: t.activo });
    setModal(true);
  };

  const guardar = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!turnoId) return;
    setGuardando(true);
    setError('');
    try {
      const datos: TipoEspacioForm = { ...form, turnoId };
      if (editando) {
        await tiposEspacioService.actualizar(editando.id, datos);
      } else {
        await tiposEspacioService.crear(datos);
      }
      setModal(false);
      await cargar();
    } catch (e: any) {
      setError(e.response?.data?.message ?? 'No se pudo guardar el tipo');
    } finally {
      setGuardando(false);
    }
  };

  const desactivar = async () => {
    if (!aDesactivar || !turnoId) return;
    try {
      await tiposEspacioService.desactivar(aDesactivar.id, turnoId);
      setADesactivar(null);
      await cargar();
    } catch (e: any) {
      setError(e.response?.data?.message ?? 'No se pudo dar de baja el tipo');
      setADesactivar(null);
    }
  };

  /**
   * Activar o desactivar sin modal: es reversible, como en Aulas y Grupos, donde se hace
   * directo. El modal se reserva para la papelera.
   */
  const cambiarEstado = async (t: TipoEspacio) => {
    if (!turnoId) return;
    try {
      if (t.activo) {
        await tiposEspacioService.desactivar(t.id, turnoId);
      } else {
        await tiposEspacioService.actualizar(t.id, {
          turnoId,
          clase: t.clase,
          nombre: t.nombre,
          activo: true,
        });
      }
      await cargar();
    } catch (e: any) {
      setError(e.response?.data?.message ?? 'No se pudo cambiar el estado del tipo');
    }
  };

  if (!semestreActivo) {
    return (
      <div className="p-8 text-center text-gray-600 dark:text-gray-400">
        Selecciona un semestre para ver sus laboratorios y talleres
      </div>
    );
  }

  if (turnos.length === 0) {
    return (
      <div className="p-8 text-center text-gray-600 dark:text-gray-400">
        Este semestre no tiene turnos activos. Crea un turno y vuelve a esta pantalla: el catÃ¡logo
        de laboratorios y talleres va por turno.
      </div>
    );
  }

  return (
    <div className="p-6 space-y-5">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1 className="flex items-center gap-2 text-2xl font-bold text-gray-900 dark:text-white">
            <MdScience className="text-blue-600 dark:text-blue-400" />
            Laboratorios y talleres
          </h1>
          <p className="mt-1 text-sm text-gray-600 dark:text-gray-400">
            Semestre: <strong>{semestreActivo.nombre}</strong> Â· el tipo se elige en el aula.
          </p>
        </div>
        <div className="flex gap-2">
          <button
            onClick={cargar}
            className="inline-flex items-center gap-2 rounded-lg bg-gray-100 px-4 py-2 text-sm font-medium text-gray-700 transition-colors hover:bg-gray-200 dark:bg-gray-700 dark:text-gray-200 dark:hover:bg-gray-600"
          >
            <MdRefresh /> Recargar
          </button>
          <button
            onClick={abrirNuevo}
            className="inline-flex items-center gap-2 rounded-lg bg-blue-600 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-blue-700"
          >
            <MdAdd /> Nuevo tipo
          </button>
        </div>
      </div>

      {/* Filtros: el turno va aqui, igual que en Aulas, Grupos y Materias. */}
      <div className="grid grid-cols-1 gap-4 rounded-xl border border-gray-200 bg-white p-4 dark:border-gray-700 dark:bg-gray-800 sm:grid-cols-3">
        <div>
          <label className="mb-1 block text-sm font-medium text-gray-700 dark:text-gray-300">
            Turno *
          </label>
          <select
            value={turnoId || ''}
            onChange={(e) => {
              setTurnoId(Number(e.target.value));
            }}
            className="w-full rounded-lg border border-gray-400 bg-white px-4 py-2.5 text-gray-900 transition focus:border-transparent focus:outline-none focus:ring-2 focus:ring-blue-500 dark:border-gray-600 dark:bg-gray-700 dark:text-white"
          >
            {turnos.length === 0 && <option value="">Sin turnos activos</option>}
            {turnos.map((t) => (
              <option key={t.id} value={t.id}>
                {t.nombre}
              </option>
            ))}
          </select>
        </div>
      </div>

      {error && (
        <div className="rounded-lg border border-red-300 bg-red-50 px-4 py-2 text-sm text-red-800 dark:border-red-800 dark:bg-red-900/30 dark:text-red-300">
          {error}
        </div>
      )}

      <div className="overflow-hidden rounded-xl border border-gray-200 bg-white dark:border-gray-700 dark:bg-gray-800">
        <table className="w-full text-left text-sm">
          <thead className="border-b border-gray-200 bg-gray-50 text-xs text-gray-500 dark:border-gray-700 dark:bg-gray-900/40 dark:text-gray-400">
            <tr>
              <th className="px-5 py-3 font-medium">Clase</th>
              <th className="px-5 py-3 font-medium">Nombre</th>
              <th className="px-5 py-3 font-medium">Estado</th>
              <th className="px-5 py-3 text-center font-medium">Acciones</th>
            </tr>
          </thead>
          <tbody>
            {cargando && (
              <tr>
                <td colSpan={4} className="px-5 py-8 text-center text-gray-500 dark:text-gray-400">
                  Cargandoâ€¦
                </td>
              </tr>
            )}
            {!cargando && tipos.length === 0 && (
              <tr>
                <td colSpan={4} className="px-5 py-8 text-center text-gray-500 dark:text-gray-400">
                  TodavÃ­a no hay tipos en este semestre. Crea el primero.
                </td>
              </tr>
            )}
            {!cargando &&
              tipos.map((t) => (
                <tr
                  key={t.id}
                  className="border-b border-gray-100 last:border-0 dark:border-gray-700/60"
                >
                  <td className="px-5 py-3">
                    <span className="inline-flex items-center gap-2 font-mono text-xs text-gray-600 dark:text-gray-400">
                      <PuntoClase clase={t.clase} />
                      {nombreClase(t.clase)}
                    </span>
                  </td>
                  <td className="px-5 py-3 text-gray-900 dark:text-white">{t.nombre}</td>
                  <td className="px-5 py-3">
                    <span
                      className={
                        t.activo
                          ? 'text-green-700 dark:text-green-400'
                          : 'text-gray-500 dark:text-gray-400'
                      }
                    >
                      {t.activo ? 'Activo' : 'Inactivo'}
                    </span>
                  </td>
                  <td className="px-5 py-3 text-center">
                    <div className="flex items-center justify-center gap-0.5">
                      <button
                        onClick={() => abrirEditar(t)}
                        className="p-1 text-blue-600 dark:text-blue-400 hover:text-blue-800 dark:hover:text-blue-300 hover:bg-blue-50 dark:hover:bg-blue-900/20 rounded transition"
                        title="Editar"
                      >
                        <MdEdit className="text-xl" />
                      </button>
                      {/* Activar / desactivar */}
                      <button
                        onClick={() => cambiarEstado(t)}
                        className={`p-1 rounded transition ${
                          t.activo
                            ? 'text-yellow-600 dark:text-yellow-400 hover:text-yellow-800 dark:hover:text-yellow-300 hover:bg-yellow-50 dark:hover:bg-yellow-900/20'
                            : 'text-green-600 dark:text-green-400 hover:text-green-800 dark:hover:text-green-300 hover:bg-green-50 dark:hover:bg-green-900/20'
                        }`}
                        title={t.activo ? 'Desactivar' : 'Activar'}
                      >
                        {t.activo ? <MdCancel className="text-xl" /> : <MdCheckCircle className="text-xl" />}
                      </button>
                      {/* Eliminar */}
                      <button
                        onClick={() => setADesactivar(t)}
                        className="p-1 text-red-600 dark:text-red-400 hover:text-red-800 dark:hover:text-red-300 hover:bg-red-50 dark:hover:bg-red-900/20 rounded transition"
                        title="Eliminar"
                      >
                        <MdDelete className="text-xl" />
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
          </tbody>
        </table>
      </div>

      {modal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
          <form
            onSubmit={guardar}
            className="w-full max-w-md space-y-4 rounded-xl bg-white p-6 dark:bg-gray-800"
          >
            <h2 className="text-lg font-bold text-gray-900 dark:text-white">
              {editando ? 'Editar tipo' : 'Nuevo tipo'}
            </h2>
            <div>
              <label className="mb-1 block text-sm font-medium text-gray-700 dark:text-gray-300">
                Clase
              </label>
              <select
                name="clase"
                value={form.clase}
                onChange={(e) =>
                  setForm({ ...form, clase: e.target.value as TipoEspacioForm['clase'] })
                }
                className="w-full rounded-lg border border-gray-400 bg-white px-3 py-2 text-gray-900 dark:border-gray-600 dark:bg-gray-700 dark:text-white"
              >
                <option value="LABORATORIO">Laboratorio</option>
                <option value="TALLER">Taller</option>
              </select>
            </div>
            <div>
              <label className="mb-1 block text-sm font-medium text-gray-700 dark:text-gray-300">
                Nombre
              </label>
              <input
                type="text"
                name="nombre"
                value={form.nombre}
                onChange={(e) => setForm({ ...form, nombre: e.target.value })}
                required
                maxLength={80}
                placeholder="Laboratorio de CÃ³mputo"
                className="w-full rounded-lg border border-gray-400 bg-white px-3 py-2 text-gray-900 dark:border-gray-600 dark:bg-gray-700 dark:text-white"
              />
            </div>
            <label className="flex items-center gap-2 text-sm text-gray-700 dark:text-gray-300">
              <input
                type="checkbox"
                checked={form.activo ?? true}
                onChange={(e) => setForm({ ...form, activo: e.target.checked })}
              />
              Activo
            </label>
            <div className="flex justify-end gap-2 pt-2">
              <button
                type="button"
                onClick={() => setModal(false)}
                className="rounded-lg bg-gray-100 px-4 py-2 text-sm font-medium text-gray-700 dark:bg-gray-700 dark:text-gray-200"
              >
                Cancelar
              </button>
              <button
                type="submit"
                disabled={guardando}
                className="rounded-lg bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-60"
              >
                {guardando ? 'Guardandoâ€¦' : 'Guardar'}
              </button>
            </div>
          </form>
        </div>
      )}

      {aDesactivar && (
        <div className="fixed inset-0 z-50 flex items-center justify-center overflow-y-auto bg-black/50 backdrop-blur-sm p-4">
          <div className="my-auto bg-white dark:bg-gray-800 rounded-xl shadow-2xl max-w-md w-full p-6 border border-gray-400 dark:border-gray-700">
            <div className="flex items-center gap-3 mb-4">
              <div className="p-2 bg-red-100 dark:bg-red-900/40 rounded-lg text-red-600 dark:text-red-400">
                <MdWarning className="text-2xl" />
              </div>
              <h3 className="text-lg font-bold text-gray-800 dark:text-white">
                Confirmar eliminaciÃ³n
              </h3>
            </div>

            <p className="text-sm text-gray-600 dark:text-gray-400 mb-4">
              Â¿EstÃ¡s seguro de que quieres eliminar este tipo de espacio?
            </p>

            <div className="bg-gray-50 dark:bg-gray-700/50 rounded-lg p-3 mb-4 text-sm">
              <div className="flex items-center gap-3 mb-2">
                <div className="w-10 h-10 rounded-lg bg-blue-100 dark:bg-blue-900/40 flex items-center justify-center text-blue-600 dark:text-blue-400">
                  <MdScience className="text-lg" />
                </div>
                <div>
                  <div className="font-medium text-gray-800 dark:text-white">
                    {aDesactivar.nombre}
                  </div>
                  <div className="text-xs text-gray-500 dark:text-gray-400">
                    {nombreClase(aDesactivar.clase)}
                    {turnos.find((t) => t.id === aDesactivar.turnoId)?.nombre
                      ? ` Â· ${turnos.find((t) => t.id === aDesactivar.turnoId)!.nombre}`
                      : ''}
                  </div>
                </div>
              </div>
              <div className="flex justify-between mt-1">
                <span className="text-gray-500 dark:text-gray-400">Estado:</span>
                <span className="font-medium text-gray-800 dark:text-white">
                  {aDesactivar.activo ? 'Activo' : 'Inactivo'}
                </span>
              </div>
            </div>

            <p className="text-xs text-red-600 dark:text-red-400 mb-4">
              DejarÃ¡ de ofrecerse al crear aulas. Las aulas que ya lo usan conservan su tipo.
            </p>

            <div className="flex gap-3">
              <button
                onClick={() => setADesactivar(null)}
                className="flex-1 px-4 py-2.5 bg-gray-100 dark:bg-gray-700 text-gray-700 dark:text-gray-300 rounded-lg hover:bg-gray-200 dark:hover:bg-gray-600 font-medium transition"
              >
                Cancelar
              </button>
              <button
                onClick={desactivar}
                className="flex-1 px-4 py-2.5 bg-red-600 hover:bg-red-700 text-white rounded-lg font-medium transition flex items-center justify-center gap-2"
              >
                <MdDelete /> Eliminar
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default TiposEspacio;
