// PrivateRoute.tsx
import { Navigate, Outlet } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

interface PrivateRouteProps {
  /**
   * Si se indica, el usuario debe tener al menos uno de estos roles según el JWT.
   * Es solo experiencia de usuario: la autorización real la hace el backend
   * (@PreAuthorize + reglas de ruta), que responde 403 si no corresponde.
   */
  roles?: string[];
}

const PrivateRoute: React.FC<PrivateRouteProps> = ({ roles }) => {
  const { isAuthenticated, escuelaActivaId, roles: rolesUsuario } = useAuth();

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  // Si está autenticado pero no tiene escuela, ir al selector
  if (!escuelaActivaId) {
    return <Navigate to="/seleccionar-escuela" replace />;
  }

  if (roles && roles.length > 0 && !roles.some((rol) => rolesUsuario.includes(rol))) {
    // Sin el rol necesario: se le devuelve al inicio en lugar de mostrarle una
    // pantalla cuyas peticiones el backend rechazará con 403.
    return <Navigate to="/dashboard" replace />;
  }

  return <Outlet />;
};

export default PrivateRoute;
