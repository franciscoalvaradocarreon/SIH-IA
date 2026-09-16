import React, { useState, useEffect } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { Button } from '@thirdbracket/bracketui';
import { MdEmail, MdLock, MdSchool, MdDarkMode, MdLightMode } from 'react-icons/md';

const Login: React.FC = () => {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const { login, seleccionarEscuela, isAuthenticated, escuelaActivaId } = useAuth();
  const navigate = useNavigate();

  const [isDark, setIsDark] = useState(() =>
    document.documentElement.classList.contains('dark')
  );

  // 🔥 Redirigir automáticamente cuando el usuario esté autenticado Y tenga
  // escuela activa. Evita la race condition de llamar navigate() justo
  // después de seleccionarEscuela(), cuando el estado de React aún no se ha
  // propagado y PrivateRoute vería escuelaActivaId = null.
  useEffect(() => {
    if (isAuthenticated && escuelaActivaId) {
      navigate('/dashboard', { replace: true });
    }
  }, [isAuthenticated, escuelaActivaId, navigate]);

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
      const escuelas = await login(email, password);

      if (escuelas.length === 0) {
        // El backend no devolvió escuelas: el usuario no tiene acceso a ninguna.
        setError('Tu cuenta no está asignada a ninguna escuela. Contacta al administrador.');
        return;
      }

      if (escuelas.length === 1) {
        // Selecciona la escuela. La redirección la hace el useEffect de arriba
        // en cuanto el contexto se actualice.
        seleccionarEscuela(escuelas[0]);
        return;
      }

      // Varias escuelas: ir al selector.
      navigate('/seleccionar-escuela', { replace: true });
    } catch (err: any) {
      setError(err?.message || 'Correo o contraseña incorrectos');
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
            >
              {isDark ? (
                <MdLightMode className="w-6 h-6 text-yellow-400" />
              ) : (
                <MdDarkMode className="w-6 h-6 text-gray-600" />
              )}
            </button>
          </div>

          <h2 className="text-3xl font-bold text-gray-800 dark:text-white text-center">
            Bienvenido
          </h2>
          <p className="text-gray-500 dark:text-gray-400 text-center text-sm mt-1 mb-6">
            Ingresa tus credenciales para continuar
          </p>

          {error && (
            <div className="bg-red-50/80 dark:bg-red-900/30 backdrop-blur-sm text-red-700 dark:text-red-300 p-3 rounded-xl border border-red-200 dark:border-red-800 mb-6 text-sm flex items-center gap-2">
              <span className="text-lg">⚠️</span> {error}
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
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  required
                  className="w-full pl-12 pr-4 py-3 bg-white/70 dark:bg-gray-700/70 border border-gray-200 dark:border-gray-600 rounded-xl focus:ring-2 focus:ring-blue-500 focus:border-transparent transition-all duration-200 outline-none text-gray-900 dark:text-white placeholder-gray-400 dark:placeholder-gray-500"
                  placeholder="admin@escuela.com"
                />
              </div>
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1.5">
                Contraseña
              </label>
              <div className="relative group">
                <div className="absolute inset-y-0 left-0 pl-4 flex items-center pointer-events-none transition-colors group-focus-within:text-blue-500">
                  <MdLock className="h-5 w-5 text-gray-400 dark:text-gray-500 group-focus-within:text-blue-500" />
                </div>
                <input
                  type="password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  required
                  className="w-full pl-12 pr-4 py-3 bg-white/70 dark:bg-gray-700/70 border border-gray-200 dark:border-gray-600 rounded-xl focus:ring-2 focus:ring-blue-500 focus:border-transparent transition-all duration-200 outline-none text-gray-900 dark:text-white placeholder-gray-400 dark:placeholder-gray-500"
                  placeholder="••••••••"
                />
              </div>
            </div>

            <div className="flex justify-end text-sm">
              <Link
                to="/recuperar-password"
                className="text-blue-600 dark:text-blue-400 hover:underline font-medium"
              >
                ¿Olvidaste tu contraseña?
              </Link>
            </div>

            <Button
              type="submit"
              disabled={loading}
              fullWidth
              size="lg"
              className="mt-2 !bg-gradient-to-r !from-blue-600 !to-indigo-600 hover:!from-blue-700 hover:!to-indigo-700 shadow-lg shadow-blue-500/30 dark:shadow-blue-800/20 text-white font-semibold py-3 rounded-xl transition-all duration-300 transform hover:scale-[1.02] active:scale-[0.98]"
            >
              {loading ? (
                <span className="flex items-center justify-center gap-2">
                  <svg className="animate-spin h-5 w-5 text-white" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                    <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
                    <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                  </svg>
                  Cargando...
                </span>
              ) : (
                'Iniciar sesión'
              )}
            </Button>
          </form>
        </div>

        <p className="mt-6 text-center text-xs text-gray-400 dark:text-gray-500 tracking-wider">
          &copy; {new Date().getFullYear()} SIH · Todos los derechos reservados
        </p>
      </div>
    </div>
  );
};

export default Login;