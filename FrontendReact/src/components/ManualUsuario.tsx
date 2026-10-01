import React, { useMemo } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { MdArrowBack, MdPrint, MdMenuBook, MdOpenInNew, MdInfo, MdWarning, MdLightbulb } from 'react-icons/md';
import { useAuth } from '../context/AuthContext';
import {
  seccionesParaRol,
  tituloManual,
  versionParaRol,
  type BloqueManual,
} from '../manual/manuales';

/**
 * MANUAL DE USUARIO, EN EL DASHBOARD.
 *
 * Muestra la versión que le toca al usuario que entró:
 *   - ADMIN  -> Manual del administrador (incluye usuarios, escuelas, menús, correo y operación).
 *   - Cualquier otro rol -> Manual del coordinador (el trabajo académico del día a día).
 *
 * Los roles se leen de la escuela activa si están disponibles (un mismo usuario puede tener distinto
 * rol en cada escuela); si todavía no cargaron, se usan los del token. La decisión vive en
 * manuales.ts (versionParaRol), así que la pantalla solo pinta lo que le devuelven.
 *
 * Los controles van con `no-print` para que al imprimir (o guardar en PDF desde el navegador) salga
 * solo el manual, limpio.
 */

/** Un bloque de texto del manual, con su estilo según el tipo. */
const Bloque: React.FC<{ bloque: BloqueManual }> = ({ bloque }) => {
  if (bloque.tipo === 'parrafo') {
    return <p className="text-sm leading-relaxed text-gray-700 dark:text-gray-300">{bloque.texto}</p>;
  }

  if (bloque.tipo === 'lista') {
    return (
      <ul className="space-y-1.5">
        {(bloque.items ?? []).map((item, i) => (
          <li key={i} className="flex gap-2 text-sm leading-relaxed text-gray-700 dark:text-gray-300">
            <span className="mt-2 h-1.5 w-1.5 shrink-0 rounded-full bg-blue-500" />
            <span>{item}</span>
          </li>
        ))}
      </ul>
    );
  }

  if (bloque.tipo === 'pasos') {
    return (
      <ol className="space-y-1.5">
        {(bloque.items ?? []).map((item, i) => (
          <li key={i} className="flex gap-2.5 text-sm leading-relaxed text-gray-700 dark:text-gray-300">
            <span className="mt-0.5 flex h-5 w-5 shrink-0 items-center justify-center rounded-full bg-blue-600 text-[11px] font-bold text-white">
              {i + 1}
            </span>
            <span>{item}</span>
          </li>
        ))}
      </ol>
    );
  }

  // Aviso: el color avisa sin necesidad de leer (información, cuidado o truco).
  const estilos = {
    info: {
      caja: 'border-blue-200 bg-blue-50 dark:border-blue-800 dark:bg-blue-900/20',
      texto: 'text-blue-800 dark:text-blue-200',
      icono: <MdInfo className="text-lg" />,
    },
    ojo: {
      caja: 'border-amber-200 bg-amber-50 dark:border-amber-800 dark:bg-amber-900/20',
      texto: 'text-amber-800 dark:text-amber-200',
      icono: <MdWarning className="text-lg" />,
    },
    tip: {
      caja: 'border-emerald-200 bg-emerald-50 dark:border-emerald-800 dark:bg-emerald-900/20',
      texto: 'text-emerald-800 dark:text-emerald-200',
      icono: <MdLightbulb className="text-lg" />,
    },
  }[bloque.tono ?? 'info'];

  return (
    <div className={`flex gap-2.5 rounded-lg border p-3 ${estilos.caja}`}>
      <span className={estilos.texto}>{estilos.icono}</span>
      <p className={`text-sm leading-relaxed ${estilos.texto}`}>{bloque.texto}</p>
    </div>
  );
};

const ManualUsuario: React.FC = () => {
  const { roles, rolesEscuelaActiva } = useAuth();
  const navigate = useNavigate();

  // Los roles de la escuela activa mandan; si aún no cargaron, se usan los del token.
  const rolesEfectivos = useMemo(
    () => (rolesEscuelaActiva && rolesEscuelaActiva.length > 0 ? rolesEscuelaActiva : roles ?? []),
    [rolesEscuelaActiva, roles],
  );

  const version = versionParaRol(rolesEfectivos);
  const secciones = useMemo(() => seccionesParaRol(rolesEfectivos), [rolesEfectivos]);

  return (
    <div className="mx-auto max-w-4xl space-y-6">
      {/* ── Encabezado y controles (no salen al imprimir) ── */}
      <div className="no-print rounded-xl border border-gray-400 bg-white p-5 shadow-md dark:border-gray-700 dark:bg-gray-800">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="flex items-center gap-3">
            <MdMenuBook className="text-3xl text-blue-600 dark:text-blue-400" />
            <div>
              <h1 className="text-2xl font-bold text-gray-900 dark:text-gray-100">
                {tituloManual(version)}
              </h1>
              <p className="text-sm text-gray-500 dark:text-gray-400">
                {secciones.length} secciones · Sistema Integral de Horarios
              </p>
            </div>
          </div>
          <div className="flex items-center gap-2">
            <span className="rounded-full bg-blue-100 px-3 py-1 text-xs font-semibold text-blue-800 dark:bg-blue-900/40 dark:text-blue-200">
              Versión {version}
            </span>
            <button
              type="button"
              onClick={() => navigate('/dashboard')}
              className="flex items-center gap-2 rounded-lg border border-gray-400 px-4 py-2.5 text-gray-700 transition hover:bg-gray-50 dark:border-gray-600 dark:text-gray-200 dark:hover:bg-gray-700"
            >
              <MdArrowBack /> Volver al dashboard
            </button>
            <button
              type="button"
              onClick={() => window.print()}
              className="flex items-center gap-2 rounded-lg bg-blue-600 px-4 py-2.5 text-white shadow-md transition hover:bg-blue-700"
            >
              <MdPrint /> Imprimir o guardar en PDF
            </button>
          </div>
        </div>
      </div>

      {/* ── Índice ── */}
      <nav className="no-print rounded-xl border border-gray-400 bg-white p-5 shadow-md dark:border-gray-700 dark:bg-gray-800">
        <h2 className="mb-3 text-sm font-bold uppercase tracking-wide text-gray-500 dark:text-gray-400">
          Contenido
        </h2>
        <ol className="grid gap-1.5 sm:grid-cols-2">
          {secciones.map((s, i) => (
            <li key={s.id} className="text-sm">
              <a
                href={`#${s.id}`}
                className="text-blue-700 hover:underline dark:text-blue-300"
              >
                {i + 1}. {s.titulo}
              </a>
            </li>
          ))}
        </ol>
      </nav>

      {/* ── Secciones ── */}
      {secciones.map((s, i) => (
        <section
          key={s.id}
          id={s.id}
          className="break-inside-avoid scroll-mt-4 rounded-xl border border-gray-400 bg-white p-5 shadow-md dark:border-gray-700 dark:bg-gray-800"
        >
          <div className="mb-3 flex flex-wrap items-baseline justify-between gap-2 border-b border-gray-200 pb-2 dark:border-gray-700">
            <h2 className="text-lg font-bold text-gray-900 dark:text-gray-100">
              {i + 1}. {s.titulo}
            </h2>
            {s.ruta && (
              <Link
                to={s.ruta}
                className="no-print flex items-center gap-1 text-xs font-medium text-blue-700 hover:underline dark:text-blue-300"
              >
                <MdOpenInNew /> Ir a la pantalla
              </Link>
            )}
          </div>
          {s.resumen && (
            <p className="mb-3 text-sm italic text-gray-500 dark:text-gray-400">{s.resumen}</p>
          )}
          <div className="space-y-3">
            {s.bloques.map((b, j) => (
              <Bloque key={j} bloque={b} />
            ))}
          </div>
        </section>
      ))}

      <p className="no-print pb-4 text-center text-xs text-gray-500 dark:text-gray-400">
        Si algo de este manual no coincide con lo que ves en pantalla, avísale al administrador: el
        manual se actualiza con cada versión del sistema.
      </p>
    </div>
  );
};

export default ManualUsuario;
