import React, { createContext, useContext, useState, type ReactNode, useEffect, useCallback } from 'react';
import api from '../api/axiosConfig';
import { rolesDelToken, tokenExpirado, milisegundosParaExpirar } from '../utils/jwt';
import type { LoginResponse, Escuela, MenuItem, Semestre } from '../types';

interface AuthContextType {
  user: LoginResponse | null;
  token: string | null;
  roles: string[];
  rolesEscuelaActiva: string[];   // 🔥 NUEVO
  escuelaActiva: Escuela | null;
  escuelaActivaId: number | null;
  escuelasDisponibles: Escuela[];
  menuItems: MenuItem[];
  login: (email: string, password: string) => Promise<Escuela[]>;
  logout: () => void;
  seleccionarEscuela: (escuela: Escuela) => void;
  setMenuItems: (items: MenuItem[]) => void;
  isAuthenticated: boolean;
  necesitaSeleccionarEscuela: boolean;
  semestreActivo: Semestre | null;
  setSemestreActivo: (semestre: Semestre | null) => void;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider: React.FC<{ children: ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<LoginResponse | null>(null);
  const [token, setToken] = useState<string | null>(() => {
    const guardado = localStorage.getItem('token');
    return guardado && !tokenExpirado(guardado) ? guardado : null;
  });
  const [escuelasDisponibles, setEscuelasDisponibles] = useState<Escuela[]>([]);
  const [menuItems, setMenuItems] = useState<MenuItem[]>([]);
  const [semestreActivo, setSemestreActivo] = useState<Semestre | null>(null);
  const [rolesEscuelaActiva, setRolesEscuelaActiva] = useState<string[]>([]);   // 🔥 NUEVO

  const [escuelaActiva, setEscuelaActiva] = useState<Escuela | null>(() => {
    const stored = localStorage.getItem('escuelaActiva');
    try {
      return stored ? (JSON.parse(stored) as Escuela) : null;
    } catch {
      localStorage.removeItem('escuelaActiva');
      return null;
    }
  });

  const logout = useCallback(() => {
    setUser(null);
    setToken(null);
    setEscuelaActiva(null);
    setEscuelasDisponibles([]);
    setMenuItems([]);
    setRolesEscuelaActiva([]);   // 🔥 limpiar también
    localStorage.removeItem('token');
    localStorage.removeItem('escuelaActiva');
    delete api.defaults.headers.common['Authorization'];
    setSemestreActivo(null);
  }, []);

  // Vigila la caducidad del token
  useEffect(() => {
    if (!token) return;
    const restante = milisegundosParaExpirar(token);
    if (restante === null || restante <= 0) {
      logout();
      return;
    }
    const temporizador = window.setTimeout(logout, restante);
    return () => window.clearTimeout(temporizador);
  }, [token, logout]);

  // Refresca la escuela activa desde el backend
  useEffect(() => {
    const refrescarEscuelaActiva = async () => {
      if (!token || !escuelaActiva?.id) return;
      try {
        const res = await api.get<Escuela[]>('/usuarios/escuelas');
        const fresca = res.data.find(e => e.id === escuelaActiva.id);
        if (fresca) {
          const cambio = JSON.stringify(fresca) !== JSON.stringify(escuelaActiva);
          if (cambio) {
            setEscuelaActiva(fresca);
            localStorage.setItem('escuelaActiva', JSON.stringify(fresca));
          }
        }
      } catch {
        // silencio
      }
    };
    refrescarEscuelaActiva();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token]);

  // 🔥 Carga los roles del usuario EN LA ESCUELA ACTIVA.
  // Se re-ejecuta cada vez que cambia la escuela o el token.
  useEffect(() => {
    const cargarRolesEscuela = async () => {
      if (!token || !escuelaActiva?.id) {
        setRolesEscuelaActiva([]);
        return;
      }
      try {
        const res = await api.get<string[]>('/usuarios/mis-roles');
        setRolesEscuelaActiva(res.data);
      } catch (error) {
        console.error('Error al cargar roles de la escuela activa:', error);
        setRolesEscuelaActiva([]);
      }
    };
    cargarRolesEscuela();
  }, [token, escuelaActiva?.id]);

  const login = async (email: string, password: string): Promise<Escuela[]> => {
    try {
      const response = await api.post<LoginResponse>('/auth/login', {
        correo: email,
        contrasenia: password,
      });

      const data = response.data;
      setUser(data);
      setToken(data.tokenJwt);
      localStorage.setItem('token', data.tokenJwt);
      api.defaults.headers.common['Authorization'] = `Bearer ${data.tokenJwt}`;

      try {
        const escuelasResp = await api.get<Escuela[]>('/usuarios/escuelas');
        const escuelas = escuelasResp.data || [];
        setEscuelasDisponibles(escuelas);

        if (escuelas.length === 1) {
          seleccionarEscuela(escuelas[0]);
        }

        return escuelas;
      } catch {
        if (data.escuelaActivaId) {
          const escuelaFallback: Escuela = {
            id: data.escuelaActivaId,
            nombre: 'Escuela Principal',
            nombreLargo: '',
            direccion: '',
            telefono: '',
            clave: '',
          };
          setEscuelasDisponibles([escuelaFallback]);
          seleccionarEscuela(escuelaFallback);
          return [escuelaFallback];
        }
        setEscuelasDisponibles([]);
        return [];
      }
    } catch (error: any) {
      if (error.response) {
        const mensaje = error.response.data?.message;
        if (error.response.status === 401 || error.response.status === 403) {
          throw new Error(mensaje || 'Correo o contraseña incorrectos.');
        }
        throw new Error(mensaje || 'Error al iniciar sesión');
      } else if (error.request) {
        throw new Error('No se pudo conectar con el servidor.');
      } else {
        throw new Error(error.message || 'Error al iniciar sesión');
      }
    }
  };

  const seleccionarEscuela = (escuela: Escuela) => {
    setEscuelaActiva(escuela);
    localStorage.setItem('escuelaActiva', JSON.stringify(escuela));
  };

  const roles = rolesDelToken(token);
  const isAuthenticated = !!token && !tokenExpirado(token);
  const escuelaActivaId = escuelaActiva?.id ?? null;
  const necesitaSeleccionarEscuela = isAuthenticated && escuelasDisponibles.length > 0 && !escuelaActiva;

  return (
    <AuthContext.Provider
      value={{
        user,
        token,
        roles,
        rolesEscuelaActiva,   // 🔥 NUEVO
        escuelaActiva,
        escuelaActivaId,
        escuelasDisponibles,
        menuItems,
        login,
        logout,
        seleccionarEscuela,
        setMenuItems,
        isAuthenticated,
        necesitaSeleccionarEscuela,
        semestreActivo,
        setSemestreActivo,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (context === undefined) {
    throw new Error('useAuth debe usarse dentro de un AuthProvider');
  }
  return context;
};