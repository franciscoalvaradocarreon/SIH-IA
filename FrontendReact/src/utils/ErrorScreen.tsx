// src/components/ErrorScreen.tsx
import React from 'react';
import { useNavigate } from 'react-router-dom';
import {
  MdError, MdWarning, MdArrowBack, MdRefresh, MdHome
} from 'react-icons/md';

export interface ErrorScreenProps {
  /** Título del error */
  titulo?: string;
  /** Mensaje principal */
  mensaje: string;
  /** Detalles adicionales (opcional) */
  detalles?: string;
  /** Sugerencia al usuario (opcional) */
  sugerencia?: string;
  /** Tipo de error: 'error' | 'warning' | 'info' */
  tipo?: 'error' | 'warning' | 'info';
  /** Acciones personalizadas */
  acciones?: {
    label: string;
    onClick: () => void;
    tipo?: 'primary' | 'secondary';
    icono?: React.ReactNode;
  }[];
  /** Mostrar botón de volver atrás */
  mostrarVolver?: boolean;
  /** Mostrar botón de ir a inicio */
  mostrarInicio?: boolean;
  /** Ancho máximo */
  maxWidth?: 'sm' | 'md' | 'lg';
}

const ErrorScreen: React.FC<ErrorScreenProps> = ({
  titulo,
  mensaje,
  detalles,
  sugerencia,
  tipo = 'error',
  acciones = [],
  mostrarVolver = true,
  mostrarInicio = false,
  maxWidth = 'md',
}) => {
  const navigate = useNavigate();

  const config = {
    error: {
      icono: <MdError className="text-6xl" />,
      colorIcono: 'text-red-500 dark:text-red-400',
      colorBg: 'from-red-50 to-white dark:from-red-900/20 dark:to-gray-900',
      colorBorder: 'border-red-200 dark:border-red-800',
      colorTitulo: 'text-red-800 dark:text-red-200',
      colorMensaje: 'text-red-700 dark:text-red-300',
      colorDetalles: 'bg-red-50 dark:bg-red-900/20 border-red-200 dark:border-red-800 text-red-700 dark:text-red-300',
      colorSugerencia: 'bg-red-100 dark:bg-red-900/30 border-red-300 dark:border-red-700 text-red-800 dark:text-red-200',
    },
    warning: {
      icono: <MdWarning className="text-6xl" />,
      colorIcono: 'text-yellow-500 dark:text-yellow-400',
      colorBg: 'from-yellow-50 to-white dark:from-yellow-900/20 dark:to-gray-900',
      colorBorder: 'border-yellow-200 dark:border-yellow-800',
      colorTitulo: 'text-yellow-800 dark:text-yellow-200',
      colorMensaje: 'text-yellow-700 dark:text-yellow-300',
      colorDetalles: 'bg-yellow-50 dark:bg-yellow-900/20 border-yellow-200 dark:border-yellow-800 text-yellow-700 dark:text-yellow-300',
      colorSugerencia: 'bg-yellow-100 dark:bg-yellow-900/30 border-yellow-300 dark:border-yellow-700 text-yellow-800 dark:text-yellow-200',
    },
    info: {
      icono: <MdWarning className="text-6xl" />,
      colorIcono: 'text-blue-500 dark:text-blue-400',
      colorBg: 'from-blue-50 to-white dark:from-blue-900/20 dark:to-gray-900',
      colorBorder: 'border-blue-200 dark:border-blue-800',
      colorTitulo: 'text-blue-800 dark:text-blue-200',
      colorMensaje: 'text-blue-700 dark:text-blue-300',
      colorDetalles: 'bg-blue-50 dark:bg-blue-900/20 border-blue-200 dark:border-blue-800 text-blue-700 dark:text-blue-300',
      colorSugerencia: 'bg-blue-100 dark:bg-blue-900/30 border-blue-300 dark:border-blue-700 text-blue-800 dark:text-blue-200',
    },
  }[tipo];

  const maxWidthClass = {
    sm: 'max-w-md',
    md: 'max-w-2xl',
    lg: 'max-w-4xl',
  }[maxWidth];

  return (
    <div className={`min-h-[60vh] flex items-center justify-center p-6 bg-gradient-to-br ${config.colorBg}`}>
      <div className={`w-full ${maxWidthClass} bg-white dark:bg-gray-800 rounded-2xl shadow-xl border ${config.colorBorder} p-8`}>
        {/* Icono */}
        <div className={`flex justify-center mb-6 ${config.colorIcono}`}>
          {config.icono}
        </div>

        {/* Título */}
        {titulo && (
          <h1 className={`text-2xl font-bold text-center mb-4 ${config.colorTitulo}`}>
            {titulo}
          </h1>
        )}

        {/* Mensaje principal */}
        <p className={`text-center ${config.colorMensaje} mb-6 whitespace-pre-line leading-relaxed`}>
          {mensaje}
        </p>

        {/* Detalles */}
        {detalles && (
          <div className={`rounded-lg p-4 mb-4 border ${config.colorDetalles}`}>
            <p className="text-sm whitespace-pre-line">
              {detalles}
            </p>
          </div>
        )}

        {/* Sugerencia */}
        {sugerencia && (
          <div className={`rounded-lg p-4 mb-6 border ${config.colorSugerencia}`}>
            <p className="text-sm">
              <span className="font-semibold">💡 Sugerencia: </span>
              {sugerencia}
            </p>
          </div>
        )}

        {/* Botones */}
        <div className="flex flex-wrap gap-3 justify-center">
          {mostrarVolver && (
            <button
              onClick={() => navigate(-1)}
              className="flex items-center gap-2 px-5 py-2.5 border border-gray-300 dark:border-gray-600 rounded-lg text-gray-700 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700 transition"
            >
              <MdArrowBack className="text-lg" />
              Volver
            </button>
          )}

          {acciones.map((accion, idx) => (
            <button
              key={idx}
              onClick={accion.onClick}
              className={`flex items-center gap-2 px-5 py-2.5 rounded-lg font-medium transition ${
                accion.tipo === 'primary'
                  ? 'bg-blue-600 hover:bg-blue-700 text-white'
                  : 'bg-gray-100 dark:bg-gray-700 hover:bg-gray-200 dark:hover:bg-gray-600 text-gray-700 dark:text-gray-300'
              }`}
            >
              {accion.icono}
              {accion.label}
            </button>
          ))}

          {mostrarInicio && (
            <button
              onClick={() => navigate('/')}
              className="flex items-center gap-2 px-5 py-2.5 border border-gray-300 dark:border-gray-600 rounded-lg text-gray-700 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700 transition"
            >
              <MdHome className="text-lg" />
              Ir al inicio
            </button>
          )}
        </div>
      </div>
    </div>
  );
};

export default ErrorScreen;