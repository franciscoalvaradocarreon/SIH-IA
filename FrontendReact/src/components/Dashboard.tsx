import React from 'react';
import { Link } from 'react-router-dom';
import { MdMenuBook, MdArrowForward } from 'react-icons/md';
import { useAuth } from '../context/AuthContext';
import { tituloManual, versionParaRol } from '../manual/manuales';

/**
 * Pantalla de inicio.
 *
 * La portada es el emblema del sistema (public/sih-logo.png): el propio panel ya trae el dragon y
 * el nombre, asi que no hace falta repetirlo en grande. Debajo queda el saludo que ya tenia.
 *
 * El PNG tiene el FONDO TRANSPARENTE. Antes era un JPG con el fondo oscuro que trae el diseno
 * (un teal casi negro), y en modo claro eso se veia como un rectangulo negro pegado sobre el fondo
 * blanco. Al quitarle el fondo, el panel turquesa se apoya igual en los dos modos. Por eso la imagen
 * ya no lleva rounded/border/shadow: el borde lo pone el propio dibujo, y un borde o una sombra de
 * CSS dibujarian un cuadrado alrededor de una imagen que ya no lo es.
 */

/**
 * Tarjeta del MANUAL DE USUARIO.
 *
 * La version no se elige aqui: se deduce de los roles (ADMIN -> administrador, cualquier otro ->
 * coordinador) en manuales.ts, que es el mismo modulo que alimenta la pantalla del manual. Asi la
 * tarjeta y el documento no se pueden desfasar.
 */
const TarjetaManual: React.FC = () => {
  const { roles, rolesEscuelaActiva } = useAuth();
  const rolesEfectivos = rolesEscuelaActiva && rolesEscuelaActiva.length > 0 ? rolesEscuelaActiva : roles ?? [];
  const version = versionParaRol(rolesEfectivos);

  return (
    <div className="mx-auto mt-8 max-w-2xl rounded-xl border border-gray-400 bg-white p-5 shadow-md dark:border-gray-700 dark:bg-gray-800">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div className="flex items-start gap-3">
          <MdMenuBook className="mt-0.5 text-3xl text-blue-600 dark:text-blue-400" />
          <div>
            <h2 className="text-lg font-bold text-gray-900 dark:text-gray-100">
              {tituloManual(version)}
            </h2>
            <p className="text-sm text-gray-600 dark:text-gray-300">
              {version === 'administrador'
                ? 'Incluye la operación académica completa y la administración del sistema: usuarios, roles, menús, correo y respaldos.'
                : 'La guía de la operación académica: catálogos, disponibilidad, asignación, generación del horario y reportes.'}
            </p>
          </div>
        </div>
        <Link
          to="/manual"
          className="flex items-center gap-2 rounded-lg bg-blue-600 px-4 py-2.5 font-medium text-white shadow-md transition hover:bg-blue-700"
        >
          Abrir manual <MdArrowForward />
        </Link>
      </div>
    </div>
  );
};

const Dashboard: React.FC = () => {
  return (
    <div className="max-w-[84rem] mx-auto">
      {/* max-w-2xl = 672 px: al ser casi cuadrada, una imagen a todo lo ancho (1344 px) se comeria
          la pantalla entera. Centrada y a este ancho queda como portada sin dominar la pagina. */}
      <img
        src="/sih-logo.png"
        alt="Sistema Integral de Horarios"
        className="block w-full h-auto max-w-2xl mx-auto"
      />
      {/* text-center: el emblema va centrado, asi que el saludo queda debajo de el, no al margen. */}
      <p className="mt-2 text-center text-gray-600 dark:text-gray-300">
        Bienvenidos al Sistema
      </p>

      <TarjetaManual />
    </div>
  );
};

export default Dashboard;
