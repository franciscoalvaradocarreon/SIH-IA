import { BrowserRouter, Routes, Route } from 'react-router-dom';
import { AuthProvider } from './context/AuthContext';
import Login from './components/Login';
import EscuelaSelector from './components/EscuelaSelector';
import PrivateRoute from './components/PrivateRoute';
import Layout from './components/Layout';
import Dashboard from './components/Dashboard';
import Escuelas from './components/Escuelas';
import EscuelaForm from './components/EscuelaForm';
import Especialidades from './components/Especialidades';
import EspecialidadForm from './components/EspecialidadForm';
import Maestros from './components/Maestros';
import MaestroForm from './components/MaestroForm';
import Materias from './components/Materias';
import MateriaForm from './components/MateriaForm';
import Usuarios from './components/Usuarios';
import UsuarioForm from './components/UsuarioForm';
import Roles from './components/Roles';
import RolForm from './components/RolForm';
import Turnos from './components/Turnos';
import TurnoForm from './components/TurnoForm';
import TurnoHorario from './components/TurnoHorario';
import Grupos from './components/Grupos';
import GrupoForm from './components/GrupoForm';
import AulaForm from './components/AulaForm';
import Aulas from './components/Aulas';
import RolMenu from './components/RolMenu';
import MenuCrudForm from './components/MenuCrudForm';
import MenuCrud from './components/MenuCrud';
import AsignacionForm from './components/AsignacionForm.tsx';
import Asignaciones from './components/Asignacion.tsx';
import DisponibilidadMaestro from './components/DisponibilidadMaestro';
import DisponibilidadMaestroForm from './components/DisponibilidadMaestroForm';
import DisponibilidadGrupo from './components/DisponibilidadGrupo';
import DisponibilidadGrupoForm from './components/DisponibilidadGrupoForm';
import HorarioView from './components/HorarioView.tsx';
import HorarioMaestroView from './components/HorarioMaestro.tsx';
import HorarioIA from './components/HorarioIA';
import HorarioManual from './components/HorarioManual';
import SemestreForm from './components/SemestreForm.tsx';
import Semestre from './components/Semestre.tsx';
import HorarioAula from './components/HorarioAula.tsx';
import ReporteHorariosGrupos from './components/ReporteHorarioGrupos.tsx';
import ReporteCerebro from './components/ReporteCerebro';
import RecuperarPassword from './components/RecuperarPassword.tsx';
import RestablecerPassword from './components/RestablecerPassword.tsx';


function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Routes>
          {/* Rutas públicas */}
          <Route path="/login" element={<Login />} />
          <Route path="/seleccionar-escuela" element={<EscuelaSelector />} />
          <Route path="/recuperar-password" element={<RecuperarPassword />} />
          <Route path="/restablecer-password" element={<RestablecerPassword />} />

          {/* Rutas protegidas con layout */}
          <Route element={<PrivateRoute />}>
            <Route element={<Layout />}>
              <Route path="/" element={<Dashboard />} />
              <Route path="/dashboard" element={<Dashboard />} />

              {/* =====================================================
                  Solo ADMIN: pantallas que el backend restringe con
                  hasRole('ADMIN') en @PreAuthorize y en las reglas de ruta.
                  Si un COORDINADOR o MAESTRO entra por URL, se le redirige
                  al inicio en lugar de mostrarle una pantalla que fallará.
                  ===================================================== */}
              <Route element={<PrivateRoute roles={['ADMIN']} />}>
                {/* Escuelas */}
                <Route path="/administracion/escuelas" element={<Escuelas />} />
                <Route path="/administracion/escuelas/new" element={<EscuelaForm />} />
                <Route path="/administracion/escuelas/edit/:id" element={<EscuelaForm />} />

                {/* Usuarios */}
                <Route path="/administracion/usuarios" element={<Usuarios />} />
                <Route path="/administracion/usuarios/new" element={<UsuarioForm />} />
                <Route path="/administracion/usuarios/edit/:id" element={<UsuarioForm />} />

                {/* Roles */}
                <Route path="/administracion/roles" element={<Roles />} />
                <Route path="/administracion/roles/new" element={<RolForm />} />
                <Route path="/administracion/roles/edit/:id" element={<RolForm />} />

                {/* Rol - Menu */}
                <Route path="/administracion/rolmenu" element={<RolMenu />} />

                {/* Menu */}
                <Route path="/administracion/menus" element={<MenuCrud />} />
                <Route path="/administracion/menus/new" element={<MenuCrudForm />} />
                <Route path="/administracion/menus/edit/:id" element={<MenuCrudForm />} />
              </Route>

              {/* =====================================================
                  ADMIN o COORDINADOR (el backend lo valida por endpoint)
                  ===================================================== */}

              {/* Especialidades */}
              <Route path="/catalogo/especialidades" element={<Especialidades />} />
              <Route path="/catalogo/especialidades/new" element={<EspecialidadForm />} />
              <Route path="/catalogo/especialidades/edit/:id" element={<EspecialidadForm />} />

              {/* Maestros */}
              <Route path="/catalogo/maestros" element={<Maestros />} />
              <Route path="/catalogo/maestros/new" element={<MaestroForm />} />
              <Route path="/catalogo/maestros/edit/:id" element={<MaestroForm />} />

              {/* Materias */}
              <Route path="/catalogo/materias" element={<Materias />} />
              <Route path="/catalogo/materias/new" element={<MateriaForm />} />
              <Route path="/catalogo/materias/edit/:id" element={<MateriaForm />} />

              {/* Semestres */}
              <Route path="/catalogo/semestres" element={<Semestre />} />
              <Route path="/catalogo/semestres/new" element={<SemestreForm />} />
              <Route path="/catalogo/semestres/edit/:id" element={<SemestreForm />} />

              {/* Turnos */}
              <Route path="/catalogo/turnos" element={<Turnos />} />
              <Route path="/catalogo/turnos/new" element={<TurnoForm />} />
              <Route path="/catalogo/turnos/edit/:id" element={<TurnoForm />} />

              {/* Turno Horario */}
              <Route path="/catalogo/turnos/horarios" element={<TurnoHorario />} />
              <Route path="/catalogo/turnos/horarios/:id" element={<TurnoHorario />} />

              {/* Grupos */}
              <Route path="/catalogo/grupos" element={<Grupos />} />
              <Route path="/catalogo/grupos/new" element={<GrupoForm />} />
              <Route path="/catalogo/grupos/edit/:id" element={<GrupoForm />} />

              {/* Aulas */}
              <Route path="/catalogo/aulas" element={<Aulas />} />
              <Route path="/catalogo/aulas/new" element={<AulaForm />} />
              <Route path="/catalogo/aulas/edit/:id" element={<AulaForm />} />

              {/* Asignaciones */}
              <Route path="/horarios/asignacion" element={<Asignaciones />} />
              <Route path="/horarios/asignacion/new" element={<AsignacionForm />} />
              <Route path="/horarios/asignacion/edit/:id" element={<AsignacionForm />} />

              {/* Disponibilidad Maestro */}
              <Route path="/horarios/disponibilidad-maestro" element={<DisponibilidadMaestro />} />
              <Route path="/horarios/disponibilidad-maestro/new" element={<DisponibilidadMaestroForm />} />
              <Route path="/horarios/disponibilidad-maestro/edit/:id" element={<DisponibilidadMaestroForm />} />

              {/* Disponibilidad Grupo */}
              <Route path="/horarios/disponibilidad-grupo" element={<DisponibilidadGrupo />} />
              <Route path="/horarios/disponibilidad-grupo/edit/:id" element={<DisponibilidadGrupoForm />} />

              {/* Horario View */}
              <Route path="/horarios/view" element={<HorarioView />} />
              <Route path="/horarios/maestro" element={<HorarioMaestroView />} />
              <Route path="/horarios/aula" element={<HorarioAula />} />

              {/* Generador de Horarios */}
              <Route path="/horarios/generador/ia" element={<HorarioIA />} />
              <Route path="/horarios/generador/manual" element={<HorarioManual />} />

              {/* Reportes */}
              <Route path="/reportes/horario-grupos" element={<ReporteHorariosGrupos />} />
              <Route path="/reportes/cerebro" element={<ReporteCerebro />} />

              {/* Agrega más rutas aquí */}
            </Route>
          </Route>

          {/* Ruta no encontrada (404) */}
          <Route path="*" element={<div className="p-8 text-center text-2xl text-gray-600 dark:text-gray-400">Página no encontrada</div>} />
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  );
}

export default App;
