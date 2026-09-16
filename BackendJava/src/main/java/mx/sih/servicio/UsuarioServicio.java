package mx.sih.servicio;

import mx.sih.excepcion.NegocioExcepcion;
import mx.sih.modelo.dto.*;
import mx.sih.modelo.entidad.Escuela;
import mx.sih.modelo.entidad.Rol;
import mx.sih.modelo.entidad.Usuario;
import mx.sih.modelo.entidad.UsuarioEscuelaRol;
import mx.sih.repositorio.EscuelaRepositorio;
import mx.sih.repositorio.RolRepositorio;
import mx.sih.repositorio.UsuarioEscuelaRolRepositorio;
import mx.sih.repositorio.UsuarioRepositorio;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Administración de usuarios.
 *
 * POLÍTICA DE ACCESO
 *   · Los usuarios son GLOBALES: se gestionan como una operación de plataforma.
 *   · Solo los ADMIN pueden crear/modificar/eliminar (ver @PreAuthorize en
 *     UsuarioControlador).
 *   · Un ADMIN puede ver y modificar usuarios de CUALQUIER escuela.
 *
 * REGLAS DE ASIGNACIONES
 *   · Un usuario puede tener roles en VARIAS escuelas.
 *   · En cada escuela, solo UN rol (no puede tener dos roles en la misma).
 *   · El rol puede ser distinto en cada escuela.
 *
 * DEFENSAS QUE SE MANTIENEN
 *   · Anti auto-escalada: un ADMIN no puede cambiarse a sí mismo los roles.
 *   · Anti auto-desactivación: no puede desactivar su propia cuenta.
 *   · Anti auto-eliminación: no puede eliminarse a sí mismo.
 *   · Contraseña obligatoria al crear y longitud mínima.
 */
@Service
public class UsuarioServicio {

    private static final int LONGITUD_MINIMA_PASSWORD = 8;

    private final UsuarioRepositorio usuarioRepositorio;
    private final UsuarioEscuelaRolRepositorio usuarioEscuelaRolRepositorio;
    private final EscuelaRepositorio escuelaRepositorio;
    private final RolRepositorio rolRepositorio;
    private final PasswordEncoder passwordEncoder;

    public UsuarioServicio(UsuarioRepositorio usuarioRepositorio,
                           UsuarioEscuelaRolRepositorio usuarioEscuelaRolRepositorio,
                           EscuelaRepositorio escuelaRepositorio,
                           RolRepositorio rolRepositorio,
                           PasswordEncoder passwordEncoder) {
        this.usuarioRepositorio = usuarioRepositorio;
        this.usuarioEscuelaRolRepositorio = usuarioEscuelaRolRepositorio;
        this.escuelaRepositorio = escuelaRepositorio;
        this.rolRepositorio = rolRepositorio;
        this.passwordEncoder = passwordEncoder;
    }

    // ============================================================
    // Contexto de seguridad
    // ============================================================

    private Long getUsuarioAutenticadoId() {
        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacion != null && autenticacion.getPrincipal() instanceof ClaimsUsuario claims) {
            return claims.usuarioId();
        }
        return null;
    }

    private Usuario obtenerUsuarioOError(Long usuarioId) {
        return usuarioRepositorio.findById(usuarioId)
                .orElseThrow(() -> new NegocioExcepcion("usuario_no_encontrado",
                        "Usuario no encontrado con ID: " + usuarioId));
    }

    // ============================================================
    // Consultas
    // ============================================================

    public Page<UsuarioDTO> listarUsuarios(Pageable pageable, String busqueda) {
        String busquedaNormalizada = (busqueda == null) ? "" : busqueda.trim();
        Page<Usuario> pagina = usuarioRepositorio.buscarPorTexto(busquedaNormalizada, pageable);
        return pagina.map(this::toDTO);
    }

    public UsuarioDetalleDTO obtenerUsuario(Long id) {
        return toDetalleDTO(obtenerUsuarioOError(id));
    }

    // ============================================================
    // Altas y cambios
    // ============================================================

    @Transactional
    public UsuarioDTO crearUsuario(UsuarioCrearDTO dto) {
        if (usuarioRepositorio.existsByEmail(dto.getEmail())) {
            throw new NegocioExcepcion("Ya existe un usuario con el email: " + dto.getEmail());
        }
        if (usuarioRepositorio.existsByUsuario(dto.getUsuario())) {
            throw new NegocioExcepcion("Ya existe un usuario con el nombre: " + dto.getUsuario());
        }

        validarPassword(dto.getPassword(), true);

        if (dto.getAsignaciones() != null) {
            validarAsignacionesUnicasPorEscuela(dto.getAsignaciones());
        }

        Usuario usuario = new Usuario();
        usuario.setUsuario(dto.getUsuario());
        usuario.setNombreCompleto(dto.getNombreCompleto());
        usuario.setEmail(dto.getEmail());
        usuario.setFotoUrl(dto.getFotoUrl());
        usuario.setActivo(dto.getActivo() != null ? dto.getActivo() : true);
        usuario.setUltimoAcceso(null);
        usuario.setPasswordHash(passwordEncoder.encode(dto.getPassword()));

        Usuario guardado = usuarioRepositorio.save(usuario);

        crearAsignaciones(guardado, dto.getAsignaciones());

        return toDTO(guardado);
    }

    @Transactional
    public UsuarioDTO actualizarUsuario(Long usuarioId, UsuarioCrearDTO dto) {
        Usuario usuario = obtenerUsuarioOError(usuarioId);

        // Unicidad (excluyendo el mismo registro)
        if (!usuario.getEmail().equals(dto.getEmail()) &&
            usuarioRepositorio.existsByEmailAndIdNot(dto.getEmail(), usuarioId)) {
            throw new NegocioExcepcion("Ya existe otro usuario con el email: " + dto.getEmail());
        }
        if (!usuario.getUsuario().equals(dto.getUsuario()) &&
            usuarioRepositorio.existsByUsuarioAndIdNot(dto.getUsuario(), usuarioId)) {
            throw new NegocioExcepcion("Ya existe otro usuario con el nombre: " + dto.getUsuario());
        }

        List<AsignacionEscuelaRolDTO> asignaciones = dto.getAsignaciones();

        // Anti auto-escalada: un ADMIN no puede cambiarse a sí mismo los roles.
        if (Objects.equals(usuarioId, getUsuarioAutenticadoId())
                && asignaciones != null
                && !mismasAsignaciones(usuarioId, asignaciones)) {
            throw new NegocioExcepcion("auto_asignacion_no_permitida",
                    "No puedes modificar tus propias asignaciones de escuela y rol. "
                    + "Solicítalo a otro administrador.");
        }

        if (asignaciones != null) {
            validarAsignacionesUnicasPorEscuela(asignaciones);
        }

        usuario.setUsuario(dto.getUsuario());
        usuario.setNombreCompleto(dto.getNombreCompleto());
        usuario.setEmail(dto.getEmail());
        usuario.setFotoUrl(dto.getFotoUrl());
        if (dto.getActivo() != null) {
            usuario.setActivo(dto.getActivo());
        }

        validarPassword(dto.getPassword(), false);
        if (dto.getPassword() != null && !dto.getPassword().isBlank()) {
            usuario.setPasswordHash(passwordEncoder.encode(dto.getPassword()));
        }

        usuarioRepositorio.save(usuario);

        // Si vienen asignaciones, se reemplazan todas por las del formulario.
        if (asignaciones != null) {
            usuarioEscuelaRolRepositorio.deleteByUsuarioId(usuarioId);
            usuarioEscuelaRolRepositorio.flush();
            Usuario recargado = usuarioRepositorio.findById(usuarioId)
                    .orElseThrow(() -> new NegocioExcepcion("usuario_no_encontrado",
                            "Usuario no encontrado con ID: " + usuarioId));
            crearAsignaciones(recargado, asignaciones);
            return toDTO(recargado);
        }

        return toDTO(usuario);
    }

    @Transactional
    public void cambiarEstado(Long id, boolean activo) {
        Usuario usuario = obtenerUsuarioOError(id);
        if (!activo && Objects.equals(id, getUsuarioAutenticadoId())) {
            throw new NegocioExcepcion("auto_desactivacion_no_permitida",
                    "No puedes desactivar tu propia cuenta.");
        }
        usuario.setActivo(activo);
        usuarioRepositorio.save(usuario);
    }

    @Transactional
    public void eliminarUsuario(Long usuarioId) {
        obtenerUsuarioOError(usuarioId);

        if (Objects.equals(usuarioId, getUsuarioAutenticadoId())) {
            throw new NegocioExcepcion("auto_eliminacion_no_permitida",
                    "No puedes eliminar tu propia cuenta.");
        }

        usuarioEscuelaRolRepositorio.deleteByUsuarioId(usuarioId);
        usuarioRepositorio.deleteById(usuarioId);
    }

    // ============================================================
    // Internos
    // ============================================================

    /**
     * Valida la lista de asignaciones ANTES de tocar la base de datos:
     *   · Cada asignación debe traer escuela y rol válidos.
     *   · No puede haber dos asignaciones para la MISMA escuela.
     */
    private void validarAsignacionesUnicasPorEscuela(List<AsignacionEscuelaRolDTO> asignaciones) {
        Set<Long> escuelasVistas = new HashSet<>();
        for (AsignacionEscuelaRolDTO asignacion : asignaciones) {
            if (asignacion == null
                    || asignacion.getEscuelaId() == null || asignacion.getEscuelaId() == 0L
                    || asignacion.getRolId() == null || asignacion.getRolId() == 0L) {
                throw new NegocioExcepcion("asignacion_invalida",
                        "Cada asignación debe tener una escuela y un rol válidos.");
            }
            if (!escuelasVistas.add(asignacion.getEscuelaId())) {
                throw new NegocioExcepcion("escuela_duplicada",
                        "Un usuario solo puede tener un rol por escuela. " +
                        "La escuela con ID " + asignacion.getEscuelaId() +
                        " aparece dos veces en la lista.");
            }
        }
    }

    private void crearAsignaciones(Usuario usuario, List<AsignacionEscuelaRolDTO> asignaciones) {
        if (asignaciones == null || asignaciones.isEmpty()) {
            return;
        }
        for (AsignacionEscuelaRolDTO asignacion : asignaciones) {
            crearAsignacion(usuario, asignacion);
        }
    }

    private void crearAsignacion(Usuario usuario, AsignacionEscuelaRolDTO dto) {
        Escuela escuela = escuelaRepositorio.findById(dto.getEscuelaId())
                .orElseThrow(() -> new NegocioExcepcion("Escuela no encontrada con ID: " + dto.getEscuelaId()));
        Rol rol = rolRepositorio.findById(dto.getRolId())
                .orElseThrow(() -> new NegocioExcepcion("Rol no encontrado con ID: " + dto.getRolId()));

        UsuarioEscuelaRol asignacion = new UsuarioEscuelaRol();
        asignacion.setUsuario(usuario);
        asignacion.setEscuela(escuela);
        asignacion.setRol(rol);
        asignacion.setActivo(dto.getActivo() != null ? dto.getActivo() : true);
        usuarioEscuelaRolRepositorio.save(asignacion);
    }

    /**
     * Compara las asignaciones actuales del usuario con las solicitadas.
     * Se comparan pares (escuelaId:rolId) en TODAS las escuelas, no solo en
     * la activa.
     */
    private boolean mismasAsignaciones(Long usuarioId, List<AsignacionEscuelaRolDTO> solicitadas) {
        Set<String> actuales = usuarioEscuelaRolRepositorio
                .findByUsuarioId(usuarioId)
                .stream()
                .filter(r -> Boolean.TRUE.equals(r.getActivo()))
                .filter(r -> r.getEscuela() != null && r.getRol() != null)
                .map(r -> r.getEscuela().getEscuelaId() + ":" + r.getRol().getRolId())
                .collect(Collectors.toSet());

        Set<String> nuevas = solicitadas.stream()
                .filter(a -> a.getEscuelaId() != null && a.getRolId() != null)
                .map(a -> a.getEscuelaId() + ":" + a.getRolId())
                .collect(Collectors.toSet());

        return actuales.equals(nuevas);
    }

    private void validarPassword(String password, boolean obligatoria) {
        if (password == null || password.isBlank()) {
            if (obligatoria) {
                throw new NegocioExcepcion("password_obligatoria",
                        "La contraseña es obligatoria para crear un usuario");
            }
            return;
        }
        if (password.length() < LONGITUD_MINIMA_PASSWORD) {
            throw new NegocioExcepcion("password_debil",
                    "La contraseña debe tener al menos " + LONGITUD_MINIMA_PASSWORD + " caracteres");
        }
    }

    private UsuarioDTO toDTO(Usuario usuario) {
        List<UsuarioEscuelaRol> asignaciones = usuarioEscuelaRolRepositorio
                .findByUsuarioId(usuario.getUsuarioId());

        List<String> roles = asignaciones.stream()
                .filter(a -> a.getRol() != null)
                .map(a -> a.getRol().getNombre())
                .distinct()
                .collect(Collectors.toList());

        List<String> escuelas = asignaciones.stream()
                .filter(a -> a.getEscuela() != null)
                .map(a -> a.getEscuela().getNombre())
                .distinct()
                .collect(Collectors.toList());

        return new UsuarioDTO(
                usuario.getUsuarioId(),
                usuario.getUsuario(),
                usuario.getNombreCompleto(),
                usuario.getEmail(),
                usuario.getFotoUrl(),
                usuario.getActivo(),
                usuario.getUltimoAcceso(),
                roles,
                escuelas
        );
    }

    private UsuarioDetalleDTO toDetalleDTO(Usuario usuario) {
        List<UsuarioEscuelaRol> asignaciones = usuarioEscuelaRolRepositorio
                .findByUsuarioId(usuario.getUsuarioId());

        List<UsuarioEscuelaRolDTO> asignacionesDTO = asignaciones.stream()
                .filter(a -> a.getEscuela() != null && a.getRol() != null)
                .map(a -> new UsuarioEscuelaRolDTO(
                        a.getUsuarioEscuelaRolId(),
                        a.getEscuela().getEscuelaId(),
                        a.getEscuela().getNombre(),
                        a.getRol().getRolId(),
                        a.getRol().getNombre(),
                        a.getActivo()
                ))
                .collect(Collectors.toList());

        return new UsuarioDetalleDTO(
                usuario.getUsuarioId(),
                usuario.getUsuario(),
                usuario.getNombreCompleto(),
                usuario.getEmail(),
                usuario.getFotoUrl(),
                usuario.getActivo(),
                usuario.getUltimoAcceso(),
                asignacionesDTO
        );
    }
}