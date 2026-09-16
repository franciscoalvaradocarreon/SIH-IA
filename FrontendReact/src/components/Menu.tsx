import React, { useEffect, useMemo, useState } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import * as IconsMd from 'react-icons/md';
import * as IconsPi from "react-icons/pi";
import api from '../api/axiosConfig';
import type { MenuItem, Semestre } from '../types';
import { useAuth } from '../context/AuthContext';
import { IconRenderer } from '../utils/iconos';

// ============================================================
// FALLBACK: menú mínimo para escuelas sin menús configurados.
// Se activa automáticamente cuando el backend devuelve [] en /api/menu.
// Permite al usuario desbloquearse y llegar a /administracion/menus
// para configurar el menú real.
// ============================================================
const FALLBACK_BASE: MenuItem[] = [
  { id: -1, label: 'Dashboard', path: '/dashboard',          icono: 'MdDashboard', hijos: [] },
  { id: -2, label: 'Semestres', path: '/catalogo/semestres', icono: 'MdClass',     hijos: [] },
];

const FALLBACK_ADMIN: MenuItem[] = [
  ...FALLBACK_BASE,
  { id: -3, label: 'Escuelas', path: '/administracion/escuelas', icono: 'MdSchool', hijos: [] },
  { id: -4, label: 'Menús',    path: '/administracion/menus',    icono: 'MdMenu',   hijos: [] },
];

const construirFallback = (roles: string[]): MenuItem[] => {
  if (roles.includes('ADMIN')) return FALLBACK_ADMIN;
  // COORDINADOR, MAESTRO y otros roles: menú mínimo sin administración
  return FALLBACK_BASE;
};


const SemestreSelector: React.FC<{ collapsed: boolean }> = ({ collapsed }) => {
  const [semestres, setSemestres] = useState<Semestre[]>([]);
  const [semestreActivoLocal, setSemestreActivoLocal] = useState<Semestre | null>(null);
  const [loading, setLoading] = useState(true);
  const { escuelaActivaId, setSemestreActivo } = useAuth();

  useEffect(() => {
    if (escuelaActivaId) cargarSemestres();
  }, [escuelaActivaId]);

  const cargarSemestres = async () => {
    setLoading(true);
    try {
      const response = await api.get('/semestres/activos');

      const semestresMapeados = response.data.map((item: any) => ({
        id: item.semestreId || item.id,
        semestreId: item.semestreId,
        nombre: item.nombre || '',
        descripcion: item.descripcion || '',
        activo: item.activo !== undefined ? item.activo : true,
        creado: item.creado,
      }));

      const semestresOrdenados = [...semestresMapeados].sort((a, b) => {
        if (a.creado && b.creado) {
          return new Date(b.creado).getTime() - new Date(a.creado).getTime();
        }
        return (b.id || 0) - (a.id || 0);
      });

      setSemestres(semestresOrdenados);

      let semestreSeleccionado = null;
      const storedSemestre = localStorage.getItem('semestreActivo');
      if (storedSemestre) {
        try {
          const parsed = JSON.parse(storedSemestre);
          const existe = semestresOrdenados.some(s => s.id === parsed.id);
          if (existe) {
            semestreSeleccionado = parsed;
          }
        } catch (e) {
          console.error('Error al parsear semestre guardado:', e);
        }
      }

      if (!semestreSeleccionado && semestresOrdenados.length > 0) {
        semestreSeleccionado = semestresOrdenados[0];
      }

      if (semestreSeleccionado) {
        setSemestreActivoLocal(semestreSeleccionado);
        setSemestreActivo(semestreSeleccionado);
        localStorage.setItem('semestreActivo', JSON.stringify(semestreSeleccionado));
      }
    } catch (error) {
      console.error('❌ Error al cargar semestres:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleSemestreChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    const id = Number(e.target.value);
    const semestre = semestres.find(s => s.id === id);
    if (semestre) {
      setSemestreActivoLocal(semestre);
      setSemestreActivo(semestre);
      localStorage.setItem('semestreActivo', JSON.stringify(semestre));
    }
  };

  useEffect(() => {
    if (semestreActivoLocal) {
      setSemestreActivo(semestreActivoLocal);
    }
  }, [semestreActivoLocal]);

  if (loading) {
    return (
      <div className={`${collapsed ? 'flex justify-center' : ''}`}>
        <div className="animate-spin rounded-full h-4 w-4 border-2 border-blue-500 border-t-transparent"></div>
      </div>
    );
  }

  if (semestres.length === 0) {
    return (
      <div className={`${collapsed ? 'flex justify-center' : ''}`}>
        {!collapsed && (
          <span className="text-xs text-yellow-500 dark:text-yellow-400">
            Sin semestres activos
          </span>
        )}
        {collapsed && (
          <IconsMd.MdClass className="text-yellow-500 dark:text-yellow-400 text-xl" />
        )}
      </div>
    );
  }

  if (collapsed) {
    return (
      <IconsMd.MdClass className="text-blue-500 dark:text-blue-400 text-xl" />
    );
  }

  const selectedValue = semestreActivoLocal?.id ? String(semestreActivoLocal.id) : '';

  return (
    <select
      value={selectedValue}
      onChange={handleSemestreChange}
      className="w-42 px-0.5 py-1 text-sm bg-transparent border border-gray-200 dark:border-gray-600 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent text-gray-700 dark:text-gray-300 cursor-pointer"
    >
      <option value="">Seleccionar semestre</option>
      {semestres.map((semestre) => (
        <option key={semestre.id} value={String(semestre.id)}>
          {semestre.nombre}
        </option>
      ))}
    </select>
  );
};


const Menu: React.FC = () => {
  const [menuItems, setMenuItems] = useState<MenuItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [isDark, setIsDark] = useState(() =>
    document.documentElement.classList.contains('dark')
  );
  const [collapsed, setCollapsed] = useState(false);

  // 🔥 rolesEscuelaActiva: roles del usuario EN LA ESCUELA ACTIVA (del endpoint
  // /api/usuarios/mis-roles). Se usa para el banner y el fallback.
  // roles (del JWT) NO se usa aquí porque contiene la suma de roles de todas
  // las escuelas y mostraría ADMIN aunque en la escuela activa sea COORDINADOR.
  const { escuelaActivaId, escuelaActiva, logout, rolesEscuelaActiva } = useAuth();
  const location = useLocation();
  const navigate = useNavigate();

  useEffect(() => {
    const fetchMenu = async () => {
      if (!escuelaActivaId) {
        setMenuItems([]);
        setLoading(false);
        return;
      }
      try {
        const response = await api.get('/menu');
        setMenuItems(response.data);
      } catch (error) {
        console.error('❌ Error al cargar el menú:', error);
        setMenuItems([]);
      } finally {
        setLoading(false);
      }
    };
    fetchMenu();
  }, [escuelaActivaId, location.pathname]);

  // Si el backend no devolvió menús, usar el fallback por roles DE LA ESCUELA ACTIVA
  const usandoFallback = !loading && menuItems.length === 0 && !!escuelaActivaId;
  const menuAMostrar = useMemo(() => {
    if (usandoFallback) return construirFallback(rolesEscuelaActiva);
    return menuItems;
  }, [usandoFallback, rolesEscuelaActiva, menuItems]);

  // Rol principal EN LA ESCUELA ACTIVA
  const rolPrincipal = useMemo(() => {
    const jerarquia = ['ADMIN', 'COORDINADOR', 'MAESTRO'];
    for (const r of jerarquia) {
      if (rolesEscuelaActiva.includes(r)) return r;
    }
    return rolesEscuelaActiva[0] ?? null;
  }, [rolesEscuelaActiva]);

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  const toggleTheme = () => {
    const newTheme = !isDark;
    setIsDark(newTheme);
    document.documentElement.classList.toggle('dark', newTheme);
    localStorage.setItem('theme', newTheme ? 'dark' : 'light');
  };

  const toggleCollapse = () => {
    setCollapsed(!collapsed);
  };

  if (loading) {
    return (
      <div className={`h-screen bg-white/80 dark:bg-gray-900/80 backdrop-blur-sm border-r border-gray-200 dark:border-gray-700 p-4 animate-pulse transition-all duration-300 ${collapsed ? 'w-20' : 'w-64'}`}>
        <div className="h-6 bg-gray-200 dark:bg-gray-700 rounded w-3/4 mb-6"></div>
        <div className="space-y-3">
          <div className="h-10 bg-gray-200 dark:bg-gray-700 rounded"></div>
          <div className="h-10 bg-gray-200 dark:bg-gray-700 rounded"></div>
          <div className="h-10 bg-gray-200 dark:bg-gray-700 rounded"></div>
        </div>
      </div>
    );
  }

  if (!escuelaActivaId) {
    return (
      <div className={`h-screen bg-white/80 dark:bg-gray-900/80 backdrop-blur-sm border-r border-gray-200 dark:border-gray-700 p-4 transition-all duration-300 ${collapsed ? 'w-20' : 'w-64'}`}>
        <p className="text-yellow-600 dark:text-yellow-400 text-sm">Selecciona una escuela</p>
      </div>
    );
  }

  return (
    <nav className={`h-screen sticky top-0 bg-white/80 dark:bg-gray-900/80 backdrop-blur-sm border-r border-gray-200 dark:border-gray-700 flex flex-col shadow-lg transition-all duration-300 ${collapsed ? 'w-20' : 'w-64'}`}>

      {/* Header del menú */}
      <div className={`flex items-start ${collapsed ? 'justify-center' : 'justify-between'} px-4 py-5 border-b border-gray-200 dark:border-gray-700`}>
        {!collapsed && (
          <div className="flex items-center gap-3">
            <div className="p-2 bg-blue-100 dark:bg-blue-900/40 rounded-xl text-blue-600 dark:text-blue-400 shadow-inner">
              <IconsMd.MdSchool className="text-2xl" />
            </div>
            <div>
              <h2 className="text-lg font-bold text-gray-800 dark:text-white tracking-tight">SIH</h2>
              <p className="text-xs text-gray-500 dark:text-gray-400">Sistema de Horarios</p>
            </div>
          </div>
        )}

        {collapsed && (
          <div className="p-2 bg-blue-100 dark:bg-blue-900/40 rounded-xl text-blue-600 dark:text-blue-400 shadow-inner">
            <IconsMd.MdSchool className="text-2xl" />
          </div>
        )}

        <button
          onClick={toggleCollapse}
          className="rounded-lg hover:bg-gray-200/50 dark:hover:bg-gray-700/50 transition-colors"
          title={collapsed ? 'Expandir menú' : 'Colapsar menú'}
        >
          {collapsed ? (
            <IconsMd.MdMenu className="w-5 h-5 text-gray-600 dark:text-gray-400" />
          ) : (
            <IconsMd.MdMenuOpen className="w-5 h-5 text-gray-600 dark:text-gray-400" />
          )}
        </button>
      </div>

      {/* Recuadro: escuela + semestre */}
      <div className="px-4 py-3 border-b border-gray-200/50 dark:border-gray-700/50">
        {!collapsed ? (
          <div className="space-y-2">
            {/* Fila: icono + nombre escuela + badge rol */}
            <div className="flex items-center gap-2">
              <IconsPi.PiBuildingApartment className="text-blue-500 dark:text-blue-400 text-lg flex-shrink-0" />
              <span className="text-sm font-medium text-gray-700 dark:text-gray-300 truncate flex-1 min-w-0">
                {escuelaActiva?.nombre || 'Sin escuela'}
              </span>
              {rolPrincipal && (
                <span className="text-[12px] font-semibold uppercase tracking-wide px-1.5 py-0.5 rounded bg-blue-100 dark:bg-blue-900/40 text-blue-700 dark:text-blue-300 flex-shrink-0">
                  {rolPrincipal}
                </span>
              )}
            </div>

            <div className="border-t border-gray-200/30 dark:border-gray-700/30"></div>

            {/* Fila: icono + selector semestre */}
            <div className="flex items-center gap-2 justify-between">
              <IconsMd.MdClass className="text-blue-500 dark:text-blue-400 text-lg flex-shrink-0" />
              <SemestreSelector collapsed={collapsed} />
            </div>
          </div>
        ) : (
          <div className="flex flex-col items-center gap-2">
            <IconsPi.PiBuildingApartment className="text-blue-500 dark:text-blue-400 text-xl" />
            <IconsMd.MdClass className="text-blue-500 dark:text-blue-400 text-xl" />
            {rolPrincipal && (
              <span
                className="text-[9px] font-bold text-blue-700 dark:text-blue-300 bg-blue-100 dark:bg-blue-900/40 rounded w-6 h-6 flex items-center justify-center"
                title={rolPrincipal}
              >
                {rolPrincipal.charAt(0)}
              </span>
            )}
          </div>
        )}
      </div>

      {/* Aviso de fallback: solo cuando no hay menús configurados */}
      {usandoFallback && !collapsed && (
        <div className="mx-3 mt-3 px-3 py-2 bg-yellow-50 dark:bg-yellow-900/20 border border-yellow-200 dark:border-yellow-800 rounded-lg">
          <p className="text-xs text-yellow-800 dark:text-yellow-200 leading-tight">
            <span className="font-semibold">Menú sin configurar.</span>{' '}
            {rolesEscuelaActiva.includes('ADMIN')
              ? 'Ve a Administración → Menús para crear el menú de esta escuela.'
              : 'Pídele al administrador que configure los menús de esta escuela.'}
          </p>
        </div>
      )}

      {/* Lista de ítems del menú */}
      <ul className="flex-1 px-3 py-4 space-y-1 overflow-y-auto">
        {menuAMostrar.map((item) => (
          <MenuItemRenderer
            key={item.id}
            item={item}
            currentPath={location.pathname}
            collapsed={collapsed}
          />
        ))}
      </ul>

      {/* Footer: Controles */}
      <div className={`border-t border-gray-200 dark:border-gray-700 p-3 space-y-2 ${collapsed ? 'flex flex-col items-center' : ''}`}>
        <button
          onClick={toggleTheme}
          className={`flex items-center ${collapsed ? 'justify-center' : 'gap-3'} w-full px-4 py-2.5 rounded-lg text-gray-600 dark:text-gray-400 hover:bg-gray-100 dark:hover:bg-gray-800 transition-all duration-200`}
          title={isDark ? 'Modo claro' : 'Modo oscuro'}
        >
          {isDark ? (
            <>
              <IconsMd.MdLightMode className="w-5 h-5 text-yellow-400 flex-shrink-0" />
              {!collapsed && <span className="text-sm font-medium">Modo claro</span>}
            </>
          ) : (
            <>
              <IconsMd.MdDarkMode className="w-5 h-5 flex-shrink-0" />
              {!collapsed && <span className="text-sm font-medium">Modo oscuro</span>}
            </>
          )}
        </button>

        <button
          onClick={handleLogout}
          className={`flex items-center ${collapsed ? 'justify-center' : 'gap-3'} w-full px-4 py-2.5 rounded-lg text-red-600 dark:text-red-400 hover:bg-red-50 dark:hover:bg-red-900/30 transition-all duration-200`}
          title="Cerrar sesión"
        >
          <IconsMd.MdLogout className="w-5 h-5 flex-shrink-0" />
          {!collapsed && <span className="text-sm font-medium">Cerrar sesión</span>}
        </button>
      </div>
    </nav>
  );
};

// Componente recursivo para ítems del menú
const MenuItemRenderer: React.FC<{
  item: MenuItem;
  currentPath: string;
  level?: number;
  collapsed?: boolean;
}> = ({ item, currentPath, level = 0, collapsed = false }) => {
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const hasChildren = item.hijos && item.hijos.length > 0;
  const isActive = !hasChildren && item.path === currentPath;
  const isParentActive = hasChildren && item.hijos.some((h) => h.path === currentPath);

  useEffect(() => {
    if (isParentActive && !collapsed) {
      setOpen(true);
    }
  }, [isParentActive, collapsed]);

  const handleClick = () => {
    if (collapsed) {
      if (item.path) {
        navigate(item.path);
      }
      return;
    }
    if (hasChildren) {
      setOpen(!open);
    } else if (item.path) {
      navigate(item.path);
    }
  };

  const paddingLeft = collapsed ? 0 : 12 + level * 16;

  return (
    <li>
      <div
        onClick={handleClick}
        className={`
          relative flex items-center ${collapsed ? 'justify-center' : ''} px-3 py-2.5 rounded-lg cursor-pointer transition-all duration-200
          ${isActive || isParentActive
            ? 'text-blue-600 dark:text-blue-400 bg-blue-50 dark:bg-blue-900/30'
            : 'text-gray-700 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-800'
          }
          ${collapsed ? 'mx-auto w-10 h-10' : ''}
        `}
        style={{ paddingLeft: collapsed ? undefined : `${paddingLeft}px` }}
        title={collapsed ? item.label : undefined}
      >
        {(isActive || isParentActive) && !collapsed && (
          <span className="absolute left-0 top-1/2 transform -translate-y-1/2 w-1 h-8 bg-blue-600 dark:bg-blue-400 rounded-r-full"></span>
        )}

        {item.icono && (
          <span className={`${collapsed ? '' : 'mr-3'} ${isActive || isParentActive ? 'text-blue-600 dark:text-blue-400' : 'text-gray-400 dark:text-gray-500'}`}>
            <IconRenderer name={item.icono} className={`${collapsed ? 'w-6 h-6' : 'w-5 h-5'}`} />
          </span>
        )}

        {!collapsed && (
          <span className={`flex-1 text-base font-medium ${isActive || isParentActive ? 'text-blue-600 dark:text-blue-400' : ''}`}>
            {item.label}
          </span>
        )}

        {!collapsed && hasChildren && (
          <span className={`text-xs text-gray-400 transition-transform duration-200 ${open ? 'rotate-180' : ''}`}>
            ▼
          </span>
        )}
      </div>

      {!collapsed && hasChildren && open && (
        <ul className="mt-1 space-y-1 overflow-hidden transition-all duration-200">
          {item.hijos.map((child) => (
            <MenuItemRenderer
              key={child.id}
              item={child}
              currentPath={currentPath}
              level={level + 1}
              collapsed={collapsed}
            />
          ))}
        </ul>
      )}
    </li>
  );
};

export default Menu;