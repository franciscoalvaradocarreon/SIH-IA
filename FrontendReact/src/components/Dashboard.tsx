import React from 'react';

/**
 * Pantalla de inicio.
 *
 * La portada es el emblema del sistema (public/sih-logo.jpg): el propio panel ya trae el dragon y
 * el nombre, asi que no hace falta repetirlo en grande. Debajo queda el saludo que ya tenia.
 */
const Dashboard: React.FC = () => {
  return (
    <div className="max-w-[84rem] mx-auto">
      {/* max-w-[84rem] = 1344 px: un 50% mas que el max-w-4xl (896 px) que tenia antes. */}
      <img
        src="/sih-logo.jpg"
        alt="Sistema Integral de Horarios"
        className="w-full h-auto rounded-2xl shadow-lg border border-gray-400 dark:border-gray-700"
      />
      <p className="mt-2 text-gray-600 dark:text-gray-300">
        Bienvenido al Sistema Integral de Horarios.
      </p>
    </div>
  );
};

export default Dashboard;