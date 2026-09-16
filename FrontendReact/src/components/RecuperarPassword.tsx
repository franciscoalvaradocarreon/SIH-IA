import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import api from '../api/axiosConfig';
import { Button } from '@thirdbracket/bracketui';
import {
  MdEmail,
  MdSchool,
  MdDarkMode,
  MdLightMode,
  MdArrowBack,
  MdCheckCircle,
} from 'react-icons/md';

const RecuperarPassword: React.FC = () => {
  const [correo, setCorreo] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [enviado, setEnviado] = useState(false);
  const navigate = useNavigate();

  const [isDark, setIsDark] = useState(() =>
    document.documentElement.classList.contains('dark')
  );

  const toggleTheme = () => {
    const newTheme = !isDark;
    setIsDark(newTheme);
    document.documentElement.classList.toggle('dark', newTheme);
    localStorage.setItem('theme', newTheme ? 'dark' : 'light');
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      await api.post('/auth/recuperar-password', { correo });
      setEnviado(true);
    } catch (err: any) {
      const status = err?.response?.status;
      const mensaje = err?.response?.data?.message;

      if (status === 429) {
        setError(mensaje || 'Demasiadas solicitudes. Inténtalo más tarde.');
      } else if (status === 400) {
        setError(mensaje || 'Revisa el correo ingresado.');
      } else if (!err?.response) {
        setError('No se pudo conectar con el servidor.');
      } else {
        setError(mensaje || 'Error al enviar la solicitud. Inténtalo de nuevo.');
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center relative overflow-hidden bg-gradient-to-br from-blue-100 via-indigo-50 to-white dark:from-gray-900 dark:via-gray-800 dark:to-gray-900 p-4">
      {/* Fondo decorativo */}
      <div className="absolute inset-0 pointer-events-none">
        <div className="absolute -top-20 -right-20 w-96 h-96 bg-blue-200/30 dark:bg-blue-900/20 rounded-full blur-3xl"></div>
        <div className="absolute -bottom-20 -left-20 w-96 h-96 bg-indigo-200/30 dark:bg-indigo-900/20 rounded-full blur-3xl"></div>
        <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[600px] h-[600px] bg-blue-100/10 dark:bg-blue-800/10 rounded-full blur-2xl"></div>
      </div>

      <div className="w-full max-w-md relative z-10">
        <div className="bg-white/80 dark:bg-gray-800/80 backdrop-blur-xl rounded-3xl shadow-2xl p-8 border border-white/20 dark:border-gray-700/30 transition-all duration-500">

          {/* Header: logo + toggle tema */}
          <div className="flex items-center justify-between mb-8">
            <div className="flex items-center gap-3">
              <div className="p-2 bg-blue-100 dark:bg-blue-900/40 rounded-xl text-blue-600 dark:text-blue-400 shadow-inner">
                <MdSchool className="text-3xl" />
              </div>
              <span className="text-2xl font-extrabold text-gray-800 dark:text-white tracking-tight">
                SIH
              </span>
            </div>
            <button
              onClick={toggleTheme}
              className="p-2 rounded-full hover:bg-gray-200/50 dark:hover:bg-gray-700/50 transition-colors"
              title={isDark ? 'Modo claro' : 'Modo oscuro'}
            >
              {isDark ? (
                <MdLightMode className="w-6 h-6 text-yellow-400" />
              ) : (
                <MdDarkMode className="w-6 h-6 text-gray-600" />
              )}
            </button>
          </div>

          {enviado ? (
            // ============================================================
            // Estado de éxito: mensaje neutro (no revela si el correo existe)
            // ============================================================
            <>
              <div className="flex justify-center mb-4">
                <div className="p-3 bg-green-100 dark:bg-green-900/30 rounded-full">
                  <MdCheckCircle className="text-5xl text-green-600 dark:text-green-400" />
                </div>
              </div>

              <h2 className="text-2xl font-bold text-gray-800 dark:text-white text-center">
                Revisa tu correo
              </h2>
              <p className="text-gray-500 dark:text-gray-400 text-center text-sm mt-3 mb-6 leading-relaxed">
                Si <span className="font-medium text-gray-700 dark:text-gray-300">{correo}</span> está
                registrado en el sistema, recibirás un enlace para restablecer tu contraseña.
                El enlace expira en 30 minutos.
              </p>

              <div className="bg-blue-50/80 dark:bg-blue-900/20 border border-blue-200 dark:border-blue-800 rounded-xl p-3 mb-6">
                <p className="text-xs text-blue-700 dark:text-blue-300 leading-relaxed">
                  💡 Si no ves el correo, revisa tu carpeta de spam o correo no deseado.
                </p>
              </div>

              <button
                type="button"
                onClick={() => navigate('/login')}
                className="w-full flex items-center justify-center gap-2 bg-blue-600 hover:bg-blue-700 text-white font-semibold py-3 rounded-xl transition-all duration-300"
              >
                <MdArrowBack className="text-xl" />
                Volver al inicio de sesión
              </button>
            </>
          ) : (
            // ============================================================
            // Formulario de solicitud
            // ============================================================
            <>
              <h2 className="text-3xl font-bold text-gray-800 dark:text-white text-center">
                Recuperar contraseña
              </h2>
              <p className="text-gray-500 dark:text-gray-400 text-center text-sm mt-1 mb-6">
                Ingresa tu correo y te enviaremos un enlace para restablecer tu contraseña.
              </p>

              {error && (
                <div className="bg-red-50/80 dark:bg-red-900/30 backdrop-blur-sm text-red-700 dark:text-red-300 p-3 rounded-xl border border-red-200 dark:border-red-800 mb-6 text-sm flex items-start gap-2">
                  <span className="text-lg leading-none">⚠️</span>
                  <span>{error}</span>
                </div>
              )}

              <form onSubmit={handleSubmit} className="space-y-5">
                <div>
                  <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1.5">
                    Correo electrónico
                  </label>
                  <div className="relative group">
                    <div className="absolute inset-y-0 left-0 pl-4 flex items-center pointer-events-none transition-colors group-focus-within:text-blue-500">
                      <MdEmail className="h-5 w-5 text-gray-400 dark:text-gray-500 group-focus-within:text-blue-500" />
                    </div>
                    <input
                      type="email"
                      value={correo}
                      onChange={(e) => setCorreo(e.target.value)}
                      required
                      autoFocus
                      className="w-full pl-12 pr-4 py-3 bg-white/70 dark:bg-gray-700/70 border border-gray-200 dark:border-gray-600 rounded-xl focus:ring-2 focus:ring-blue-500 focus:border-transparent transition-all duration-200 outline-none text-gray-900 dark:text-white placeholder-gray-400 dark:placeholder-gray-500"
                      placeholder="admin@escuela.com"
                    />
                  </div>
                </div>

                <Button
                  type="submit"
                  disabled={loading}
                  fullWidth
                  size="lg"
                  className="!bg-gradient-to-r !from-blue-600 !to-indigo-600 hover:!from-blue-700 hover:!to-indigo-700 shadow-lg shadow-blue-500/30 dark:shadow-blue-800/20 text-white font-semibold py-3 rounded-xl transition-all duration-300 transform hover:scale-[1.02] active:scale-[0.98] disabled:opacity-60 disabled:cursor-not-allowed disabled:transform-none"
                >
                  {loading ? (
                    <span className="flex items-center justify-center gap-2">
                      <svg className="animate-spin h-5 w-5 text-white" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                        <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
                        <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                      </svg>
                      Enviando...
                    </span>
                  ) : (
                    'Enviar enlace de recuperación'
                  )}
                </Button>
              </form>

              <button
                type="button"
                onClick={() => navigate('/login')}
                className="mt-6 w-full flex items-center justify-center gap-2 text-sm text-gray-600 dark:text-gray-400 hover:text-blue-600 dark:hover:text-blue-400 transition-colors"
              >
                <MdArrowBack className="text-lg" />
                Volver al inicio de sesión
              </button>
            </>
          )}
        </div>

        <p className="mt-6 text-center text-xs text-gray-400 dark:text-gray-500 tracking-wider">
          &copy; {new Date().getFullYear()} SIH · Todos los derechos reservados
        </p>
      </div>
    </div>
  );
};

export default RecuperarPassword;