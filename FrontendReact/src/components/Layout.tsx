// src/components/Layout.tsx
import React from 'react';
import { Outlet } from 'react-router-dom';
import Menu from './Menu';

const Layout: React.FC = () => {
  return (
    <div className="min-h-screen flex relative overflow-hidden bg-gradient-to-br from-blue-100 via-indigo-50 to-white dark:from-gray-900 dark:via-gray-800 dark:to-gray-900">
      
      <div className="absolute inset-0 pointer-events-none">
        {/* Círculo 1: Arriba derecha - Tamaño grande */}
        <div className="absolute -top-20 -right-20 w-96 h-96 
                        bg-blue-200/40 dark:bg-blue-900/30 
                        rounded-full blur-3xl">
        </div>

        {/* Círculo 2: Abajo izquierda - Tamaño grande */}
        <div className="absolute -bottom-20 -left-20 w-96 h-96 
                        bg-indigo-200/40 dark:bg-indigo-900/30 
                        rounded-full blur-3xl">
        </div>

        {/* Círculo 3: Centro - Tamaño extra grande */}
        <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 
                        w-[600px] h-[600px] 
                        bg-blue-100/15 dark:bg-blue-800/10 
                        rounded-full blur-2xl">
        </div>

        {/* Círculo 4: Arriba izquierda - Tamaño mediano */}
        <div className="absolute -top-10 -left-10 w-72 h-72 
                        bg-purple-200/30 dark:bg-purple-900/20 
                        rounded-full blur-3xl">
        </div>

        {/* Círculo 5: Abajo derecha - Tamaño mediano */}
        <div className="absolute -bottom-10 -right-10 w-72 h-72 
                        bg-cyan-200/30 dark:bg-cyan-900/20 
                        rounded-full blur-3xl">
        </div>

        {/* Círculo 6: Centro-izquierda - Tamaño pequeño */}
        <div className="absolute top-1/3 left-1/4 w-48 h-48 
                        bg-pink-200/20 dark:bg-pink-900/15 
                        rounded-full blur-3xl">
        </div>

        {/* Círculo 7: Centro-derecha - Tamaño pequeño */}
        <div className="absolute bottom-1/4 right-1/4 w-56 h-56 
                        bg-yellow-200/15 dark:bg-yellow-900/10 
                        rounded-full blur-3xl">
        </div>
      </div>

      {/* MENÚ LATERAL */}
      <div className="relative z-10">
        <Menu />
      </div>

      {/* CONTENIDO PRINCIPAL */}
      <main className="
        flex-1 p-6 overflow-auto relative z-10 
        m-4 rounded-2xl 
        bg-white/40 dark:bg-gray-900/40
        backdrop-blur-md
        shadow-lg 
        border border-white/30 dark:border-gray-700/30
        text-gray-800 dark:text-gray-100
      ">
        <Outlet />
      </main>
    </div>
  );
};

export default Layout;