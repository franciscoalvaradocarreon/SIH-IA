// axiosConfig.ts
import axios from 'axios';
import { tokenExpirado } from '../utils/jwt';

/**
 * URL base del backend. Se define en .env.development / .env.production:
 *     VITE_API_URL=http://localhost:8080/api
 * Si no existe, se usa la ruta relativa /api (que en desarrollo resuelve el proxy
 * de Vite configurado en vite.config.ts).
 */
const API_URL: string = import.meta.env.VITE_API_URL ?? '/api';

const api = axios.create({
  baseURL: API_URL,
});

/** Olvida la sesión local. */
const limpiarSesion = () => {
  localStorage.removeItem('token');
  localStorage.removeItem('escuelaActiva');
  delete api.defaults.headers.common['Authorization'];
};

/** Cierra la sesión y manda al login, sin bucles si ya estamos allí. */
const irAlLogin = (motivo: string) => {
  limpiarSesion();
  if (!window.location.pathname.startsWith('/login')) {
    window.location.assign(`/login?sesion=${motivo}`);
  }
};

api.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token');

    // Token caducado: no se envía una petición condenada a 401 y se limpia la sesión.
    if (token && tokenExpirado(token)) {
      irAlLogin('expirada');
      return Promise.reject(new Error('Sesión expirada'));
    }

    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }

    const escuelaStr = localStorage.getItem('escuelaActiva');
    if (escuelaStr) {
      try {
        const escuela = JSON.parse(escuelaStr);
        if (escuela?.id) {
          config.headers['X-School-ID'] = String(escuela.id);
        }
      } catch {
        // Dato corrupto en localStorage: se descarta en lugar de romper cada petición.
        localStorage.removeItem('escuelaActiva');
      }
    }

    return config;
  },
  (error) => Promise.reject(error)
);

api.interceptors.response.use(
  (response) => response,
  (error) => {
    const estado: number | undefined = error?.response?.status;

    // 401 = no autenticado (sin token, token inválido o expirado) -> volver al login.
    if (estado === 401) {
      irAlLogin('expirada');
    }
    // 403 = autenticado pero sin permisos: NO se cierra la sesión, se informa al usuario.

    if (import.meta.env.DEV) {
      const metodo = error?.config?.method?.toUpperCase() ?? '?';
      const url = error?.config?.url ?? '?';
      console.warn(`[api] ${estado ?? 'sin respuesta'} ${metodo} ${url}`);
    }

    return Promise.reject(error);
  }
);

export default api;
