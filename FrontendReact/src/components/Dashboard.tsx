import React from 'react';

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
    </div>
  );
};

export default Dashboard;