package mx.sih.servicio;

import java.util.Locale;
import java.util.Set;
import mx.sih.excepcion.NegocioExcepcion;
import mx.sih.modelo.dto.RolCrearDTO;
import mx.sih.modelo.dto.RolDTO;
import mx.sih.modelo.entidad.Rol;
import mx.sih.repositorio.RolRepositorio;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RolServicio {

    /**
     * Roles de los que depende la autorización del sistema: los controladores usan
     * literalmente hasRole('ADMIN') y hasRole('COORDINADOR') en sus @PreAuthorize.
     * Renombrarlos o borrarlos dejaba a TODO el SaaS sin autorización (o con roles
     * huérfanos en usuario_escuela_rol).
     */
    private static final Set<String> ROLES_PROTEGIDOS = Set.of("ADMIN", "COORDINADOR", "SUPERADMIN");

    private final RolRepositorio rolRepositorio;

    public RolServicio(RolRepositorio rolRepositorio) {
        this.rolRepositorio = rolRepositorio;
    }

    public Page<RolDTO> listarRoles(Pageable pageable, String busqueda) {
        String busquedaNormalizada = (busqueda == null) ? "" : busqueda.trim();
        Page<Rol> pagina;
        if (busquedaNormalizada.isEmpty()) {
            pagina = rolRepositorio.findAll(pageable);
        } else {
            pagina = rolRepositorio.buscarPorNombre(busquedaNormalizada, pageable);
        }
        return pagina.map(this::toDTO);
    }

    public RolDTO obtenerRol(Long id) {
        Rol rol = rolRepositorio.findById(id)
                .orElseThrow(() -> new NegocioExcepcion("Rol no encontrado con ID: " + id));
        return toDTO(rol);
    }

    @Transactional
    public RolDTO crearRol(RolCrearDTO dto) {
        // Verificar nombre único
        if (rolRepositorio.existsByNombreIgnoreCase(dto.getNombre())) {
            throw new NegocioExcepcion("Ya existe un rol con el nombre: " + dto.getNombre());
        }

        String nombre = dto.getNombre().toUpperCase(Locale.ROOT);
        if (ROLES_PROTEGIDOS.contains(nombre)) {
            throw new NegocioExcepcion("rol_reservado",
                    "El nombre '" + nombre + "' está reservado para un rol del sistema.");
        }

        Rol rol = new Rol();
        rol.setNombre(nombre);
        rol.setDescripcion(dto.getDescripcion());

        Rol guardado = rolRepositorio.save(rol);
        return toDTO(guardado);
    }

    @Transactional
    public RolDTO actualizarRol(Long id, RolCrearDTO dto) {
        Rol rol = rolRepositorio.findById(id)
                .orElseThrow(() -> new NegocioExcepcion("Rol no encontrado con ID: " + id));

        exigirRolNoProtegido(rol);

        // Verificar nombre único (excluyendo el mismo registro)
        if (!rol.getNombre().equalsIgnoreCase(dto.getNombre()) &&
            rolRepositorio.existsByNombreIgnoreCaseAndIdNot(dto.getNombre(), id)) {
            throw new NegocioExcepcion("Ya existe otro rol con el nombre: " + dto.getNombre());
        }

        String nombre = dto.getNombre().toUpperCase(Locale.ROOT);
        if (ROLES_PROTEGIDOS.contains(nombre)) {
            throw new NegocioExcepcion("rol_reservado",
                    "El nombre '" + nombre + "' está reservado para un rol del sistema.");
        }

        rol.setNombre(nombre);
        rol.setDescripcion(dto.getDescripcion());

        Rol actualizado = rolRepositorio.save(rol);
        return toDTO(actualizado);
    }

    @Transactional
    public void eliminarRol(Long id) {
        Rol rol = rolRepositorio.findById(id)
                .orElseThrow(() -> new NegocioExcepcion("Rol no encontrado con ID: " + id));

        exigirRolNoProtegido(rol);

        // Verificar que no haya usuarios con este rol
        if (rolRepositorio.tieneUsuariosAsignados(id) > 0) {
            throw new NegocioExcepcion("No se puede eliminar el rol porque tiene usuarios asignados");
        }

        rolRepositorio.deleteById(id);
    }

    private void exigirRolNoProtegido(Rol rol) {
        if (rol.getNombre() != null && ROLES_PROTEGIDOS.contains(rol.getNombre().toUpperCase(Locale.ROOT))) {
            throw new NegocioExcepcion("rol_protegido",
                    "El rol '" + rol.getNombre() + "' es un rol de sistema: no puede renombrarse ni eliminarse "
                    + "porque la autorización de la aplicación depende de él.");
        }
    }

    private RolDTO toDTO(Rol rol) {
        return new RolDTO(
                rol.getRolId(),
                rol.getNombre(),
                rol.getDescripcion()
        );
    }
}
