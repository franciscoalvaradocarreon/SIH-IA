import React, { useState, useEffect, useMemo } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import api from '../api/axiosConfig';
import { Button } from '@thirdbracket/bracketui';
import {
  MdLock,
  MdSchool,
  MdDarkMode,
  MdLightMode,
  MdArrowBack,
  MdVisibility,
  MdVisibilityOff,
  MdCheckCircle,
  MdError,
} from 'react-icons/md';

const LONGITUD_MINIMA_PASSWORD = 7;

// ============================================================
// Indicador de fortaleza de contraseña (puramente informativo)
// ============================================================
type Fuerza = 'vacia' | 'debil' | 'media' | 'fuerte';

const evaluarFuerza = (password: string): Fuerza => {
  if (!password) return 'vacia';
  let puntos = 0;
  if (password.length >= LONGITUD_MINIMA_PASSWORD) puntos++;
  if (password.length >= 16) puntos++;
  if (/[A-Z]/.test(password)) puntos++;
  if (/[a-z]/.test(password)) puntos++;
  if (/[0-9]/.test(password)) puntos++;
  if (/[^A-Za-z0-9]/.test(password)) puntos++;

  if (puntos <= 2) return 'debil';
  if (puntos <= 4) return 'media';
  return 'fuerte';
};

const COLOR_FUERZA: Record<Fuerza, { barra: string; texto: string; label: string }> = {
  vacia:  { barra: 'bg-gray-200 dark:bg-gray-700', texto: 'text-gray-400',                   label: '' },
  debil:  { barra: 'bg-red-500',                    texto: 'text-red-600 dark:text-red-400',  label: 'Débil' },
  media:  { barra: 'bg-yellow-500',                 texto: 'text-yellow-600 dark:text-yellow-400', label: 'Media' },
  fuerte: { barra: 'bg-green-500',                  texto: 'text-green-600 dark:text-green-400', label: 'Fuerte' },
};

const ANCHO_FUERZA: Record<Fuerza, string> = {
  vacia:  'w-0',
  debil:  'w-1/3',
  media:  'w-2/3',
  fuerte: 'w-full',
};


const RestablecerPassword: React.FC = () => {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const token = searchParams.get('token') || '';

  const [password, setPassword] = useState('');
  const [confirmar, setConfirmar] = useState('');
  const [mostrarPassword, setMostrarPassword] = useState(false);
  const [mostrarConfirmar, setMostrarConfirmar] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [exito, setExito] = useState(false);

  const [isDark, setIsDark] = useState(() =>
    document.documentElement.classList.contains('dark')
  );

  const toggleTheme = () => {
    const newTheme = !isDark;
    setIsDark(newTheme);
    document.documentElement.classList.toggle('dark', newTheme);
    localStorage.setItem('theme', newTheme ? 'dark' : 'light');
  };

  // Redirige al login 2.5 s después de éxito
  useEffect(() => {
    if (!exito) return;
    const t = window.setTimeout(() => {
      navigate('/login?reset=ok', { replace: true });
    }, 2500);
    return () => window.clearTimeout(t);
  }, [exito, navigate]);

  const fuerza = useMemo(() => evaluarFuerza(password), [password]);

  const errorValidacion = useMemo(() => {
    if (!password && !confirmar) return '';
    if (password.length > 0 && password.length < LONGITUD_MINIMA_PASSWORD) {
      return `La contraseña debe tener al menos ${LONGITUD_MINIMA_PASSWORD} caracteres.`;
    }
    if (confirmar.length > 0 && password !== confirmar) {
      return 'Las contraseñas no coinciden.';
    }
    return '';
  }, [password, confirmar]);

  const puedeEnviar = useMemo(() => {
    return Boolean(
      token &&
      password.length >= LONGITUD_MINIMA_PASSWORD &&
      password === confirmar &&
      !loading
    );
  }, [token, password, confirmar, loading]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

    if (!token) {
      setError('El enlace no contiene un token válido.');
      return;
    }
    if (password.length < LONGITUD_MINIMA_PASSWORD) {
      setError(`La contraseña debe tener al menos ${LONGITUD_MINIMA_PASSWORD} caracteres.`);
      return;
    }
    if (password !== confirmar) {
      setError('Las contraseñas no coinciden.');
      return;
    }

    setLoading(true);
    try {
      await api.post('/auth/restablecer-password', {
        token,
        nuevaPassword: password,
      });
      setExito(true);
    } catch (err: any) {
      const status = err?.response?.status;
      const mensaje = err?.response?.data?.message;

      if (status === 400 || status === 401) {
        setError(mensaje || 'El enlace es inválido o ya expiró. Solicita uno nuevo.');
      } else if (status === 429) {
        setError(mensaje || 'Demasiadas solicitudes. Inténtalo más tarde.');
      } else if (!err?.response) {
        setError('No se pudo conectar con el servidor.');
      } else {
        setError(mensaje || 'Error al restablecer la contraseña. Inténtalo de nuevo.');
      }
    } finally {
      setLoading(false);
    }
  };

  // ============================================================
  // Token ausente: la pantalla no tiene sentido
  // ============================================================
  if (!token) {
    return (
      <div className="min-h-screen flex items-center justify-center relative overflow-hidden bg-gradient-to-br from-blue-100 via-indigo-50 to-white dark:from-gray-900 dark:via-gray-800 dark:to-gray-900 p-4">
        <div className="w-full max-w-md relative z-10">
          <div className="bg-white/80 dark:bg-gray-800/80 backdrop-blur-xl rounded-3xl shadow-2xl p-8 border border-white/20 dark:border-gray-700/30">
            <div className="flex justify-center mb-4">
              <div className="p-3 bg-red-100 dark:bg-red-900/30 rounded-full">
                <MdError className="text-5xl text-red-600 dark:text-red-400" />
              </div>
            </div>
            <h2 className="text-2xl font-bold text-gray-800 dark:text-white text-center mb-2">
              Enlace inválido
            </h2>
            <p className="text-gray-500 dark:text-gray-400 text-center text-sm mb-6">
              Falta el token de recuperación. Solicita un nuevo enlace desde la pantalla de recuperación.
            </p>
            <button
              type="button"
              onClick={() => navigate('/recuperar-password')}
              className="w-full flex items-center justify-center gap-2 bg-blue-600 hover:bg-blue-700 text-white font-semibold py-3 rounded-xl transition-all duration-300"
            >
              Solicitar nuevo enlace
            </button>
            <button
              type="button"
              onClick={() => navigate('/login')}
              className="mt-4 w-full flex items-center justify-center gap-2 text-sm text-gray-600 dark:text-gray-400 hover:text-blue-600 dark:hover:text-blue-400 transition-colors"
            >
              <MdArrowBack className="text-lg" />
              Volver al inicio de sesión
            </button>
          </div>
        </div>
      </div>
    );
  }

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

          {/* Header */}
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

          {exito ? (
            // ============================================================
            // Estado de éxito
            // ============================================================
            <>
              <div className="flex justify-center mb-4">
                <div className="p-3 bg-green-100 dark:bg-green-900/30 rounded-full">
                  <MdCheckCircle className="text-5xl text-green-600 dark:text-green-400" />
                </div>
              </div>

              <h2 className="text-2xl font-bold text-gray-800 dark:text-white text-center">
                Contraseña actualizada
              </h2>
              <p className="text-gray-500 dark:text-gray-400 text-center text-sm mt-3 mb-6">
                Tu contraseña ha sido restablecida correctamente. Serás redirigido al inicio de sesión en unos segundos.
              </p>

              <div className="flex justify-center">
                <div className="animate-spin rounded-full h-6 w-6 border-4 border-blue-500 border-t-transparent"></div>
              </div>
            </>
          ) : (
            // ============================================================
            // Formulario
            // ============================================================
            <>
              <h2 className="text-3xl font-bold text-gray-800 dark:text-white text-center">
                Nueva contraseña
              </h2>
              <p className="text-gray-500 dark:text-gray-400 text-center text-sm mt-1 mb-6">
                Elige una contraseña segura de al menos {LONGITUD_MINIMA_PASSWORD} caracteres.
              </p>

              {error && (
                <div className="bg-red-50/80 dark:bg-red-900/30 backdrop-blur-sm text-red-700 dark:text-red-300 p-3 rounded-xl border border-red-200 dark:border-red-800 mb-6 text-sm flex items-start gap-2">
                  <span className="text-lg leading-none">⚠️</span>
                  <span>{error}</span>
                </div>
              )}

              <form onSubmit={handleSubmit} className="space-y-5">
                {/* Nueva contraseña */}
                <div>
                  <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1.5">
                    Nueva contraseña
                  </label>
                  <div className="relative group">
                    <div className="absolute inset-y-0 left-0 pl-4 flex items-center pointer-events-none transition-colors group-focus-within:text-blue-500">
                      <MdLock className="h-5 w-5 text-gray-400 dark:text-gray-500 group-focus-within:text-blue-500" />
                    </div>
                    <input
                      type={mostrarPassword ? 'text' : 'password'}
                      value={password}
                      onChange={(e) => setPassword(e.target.value)}
                      required
                      autoFocus
                      autoComplete="new-password"
                      className="w-full pl-12 pr-12 py-3 bg-white/70 dark:bg-gray-700/70 border border-gray-400 dark:border-gray-600 rounded-xl focus:ring-2 focus:ring-blue-500 focus:border-transparent transition-all duration-200 outline-none text-gray-900 dark:text-white placeholder-gray-400 dark:placeholder-gray-500"
                      placeholder="••••••••••••"
                    />
                    <button
                      type="button"
                      onClick={() => setMostrarPassword(!mostrarPassword)}
                      className="absolute inset-y-0 right-0 pr-3 flex items-center text-gray-400 hover:text-gray-600 dark:hover:text-gray-300 transition-colors"
                      title={mostrarPassword ? 'Ocultar' : 'Mostrar'}
                      tabIndex={-1}
                    >
                      {mostrarPassword
                        ? <MdVisibilityOff className="text-xl" />
                        : <MdVisibility className="text-xl" />}
                    </button>
                  </div>

                  {/* Barra de fortaleza */}
                  {password.length > 0 && (
                    <div className="mt-2">
                      <div className="h-1 w-full bg-gray-200 dark:bg-gray-700 rounded-full overflow-hidden">
                        <div className={`h-full ${COLOR_FUERZA[fuerza].barra} ${ANCHO_FUERZA[fuerza]} transition-all duration-300`} />
                      </div>
                      {fuerza !== 'vacia' && (
                        <p className={`text-xs mt-1 ${COLOR_FUERZA[fuerza].texto}`}>
                          Seguridad: <span className="font-semibold">{COLOR_FUERZA[fuerza].label}</span>
                        </p>
                      )}
                    </div>
                  )}
                </div>

                {/* Confirmar contraseña */}
                <div>
                  <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1.5">
                    Confirmar contraseña
                  </label>
                  <div className="relative group">
                    <div className="absolute inset-y-0 left-0 pl-4 flex items-center pointer-events-none transition-colors group-focus-within:text-blue-500">
                      <MdLock className="h-5 w-5 text-gray-400 dark:text-gray-500 group-focus-within:text-blue-500" />
                    </div>
                    <input
                      type={mostrarConfirmar ? 'text' : 'password'}
                      value={confirmar}
                      onChange={(e) => setConfirmar(e.target.value)}
                      required
                      autoComplete="new-password"
                      className="w-full pl-12 pr-12 py-3 bg-white/70 dark:bg-gray-700/70 border border-gray-400 dark:border-gray-600 rounded-xl focus:ring-2 focus:ring-blue-500 focus:border-transparent transition-all duration-200 outline-none text-gray-900 dark:text-white placeholder-gray-400 dark:placeholder-gray-500"
                      placeholder="••••••••••••"
                    />
                    <button
                      type="button"
                      onClick={() => setMostrarConfirmar(!mostrarConfirmar)}
                      className="absolute inset-y-0 right-0 pr-3 flex items-center text-gray-400 hover:text-gray-600 dark:hover:text-gray-300 transition-colors"
                      title={mostrarConfirmar ? 'Ocultar' : 'Mostrar'}
                      tabIndex={-1}
                    >
                      {mostrarConfirmar
                        ? <MdVisibilityOff className="text-xl" />
                        : <MdVisibility className="text-xl" />}
                    </button>
                  </div>

                  {errorValidacion && (
                    <p className="text-xs mt-1 text-red-600 dark:text-red-400">
                      {errorValidacion}
                    </p>
                  )}
                </div>

                <Button
                  type="submit"
                  disabled={!puedeEnviar}
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
                      Guardando...
                    </span>
                  ) : (
                    'Restablecer contraseña'
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

export default RestablecerPassword;