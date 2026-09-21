// src/components/EscuelaSelection.tsx
import React, { useState } from 'react';
import { useAuth } from '../context/AuthContext';
import { MdSchool, MdArrowForward } from 'react-icons/md';

const EscuelaSelection: React.FC = () => {
  const { escuelasDisponibles, seleccionarEscuela } = useAuth();
  const [cargando, setCargando] = useState(false);

  const handleSeleccionar = async (escuela: any) => {
    setCargando(true);
    try {
      await seleccionarEscuela(escuela);
      // Redirigir al dashboard
      window.location.href = '/';
    } catch (error) {
      console.error('Error al seleccionar escuela:', error);
    } finally {
      setCargando(false);
    }
  };

  if (!escuelasDisponibles || escuelasDisponibles.length === 0) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-blue-100 via-indigo-50 to-white dark:from-gray-900 dark:via-gray-800 dark:to-gray-900">
        <div className="text-center">
          <div className="animate-spin rounded-full h-12 w-12 border-4 border-blue-500 border-t-transparent mx-auto"></div>
          <p className="mt-4 text-gray-600 dark:text-gray-300">Cargando escuelas...</p>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-blue-100 via-indigo-50 to-white dark:from-gray-900 dark:via-gray-800 dark:to-gray-900">
      <div className="bg-white/80 dark:bg-gray-800/80 backdrop-blur-md p-8 rounded-2xl shadow-xl max-w-md w-full border border-white/30 dark:border-gray-700/30">
        <div className="text-center mb-8">
          <MdSchool className="text-5xl text-blue-600 dark:text-blue-400 mx-auto mb-4" />
          <h1 className="text-2xl font-bold text-gray-800 dark:text-white">
            Selecciona tu escuela
          </h1>
          <p className="text-sm text-gray-500 dark:text-gray-400 mt-2">
            Elige la escuela a la que deseas acceder
          </p>
        </div>

        <div className="space-y-3">
          {escuelasDisponibles.map((escuela) => (
            <button
              key={escuela.id}
              onClick={() => handleSeleccionar(escuela)}
              disabled={cargando}
              className="w-full flex items-center justify-between p-4 bg-white dark:bg-gray-700/50 rounded-xl border border-gray-400 dark:border-gray-600 hover:border-blue-500 dark:hover:border-blue-400 hover:shadow-md transition-all duration-200 disabled:opacity-50 disabled:cursor-not-allowed"
            >
              <div className="flex items-center gap-3">
                <div className="w-10 h-10 rounded-full bg-blue-100 dark:bg-blue-900/40 flex items-center justify-center text-blue-600 dark:text-blue-400 font-bold">
                  {escuela.nombre.charAt(0).toUpperCase()}
                </div>
                <div className="text-left">
                  <p className="font-medium text-gray-800 dark:text-white">
                    {escuela.nombre}
                  </p>
                  <p className="text-xs text-gray-500 dark:text-gray-400">
                    {escuela.direccion || 'Sin dirección'}
                  </p>
                </div>
              </div>
              {cargando ? (
                <div className="animate-spin rounded-full h-5 w-5 border-2 border-blue-500 border-t-transparent"></div>
              ) : (
                <MdArrowForward className="text-gray-400 dark:text-gray-500 text-xl" />
              )}
            </button>
          ))}
        </div>
      </div>
    </div>
  );
};

export default EscuelaSelection;