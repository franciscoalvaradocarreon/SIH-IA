package mx.sih.controlador;

import jakarta.validation.Valid;
import java.util.List;
import java.util.stream.Collectors;

import mx.sih.modelo.dto.ClaimsUsuario;
import mx.sih.modelo.dto.EscuelaDTO;
import mx.sih.modelo.dto.UsuarioCrearDTO;
import mx.sih.modelo.dto.UsuarioDTO;
import mx.sih.modelo.dto.UsuarioDetalleDTO;
import mx.sih.modelo.entidad.Escuela;
import mx.sih.repositorio.UsuarioEscuelaRolRepositorio;
import mx.sih.servicio.UsuarioServicio;
import mx.sih.seguridad.contexto.EscuelaContexto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/usuarios")
public class UsuarioControlador {

    private final UsuarioServicio usuarioServicio;
    private final UsuarioEscuelaRolRepositorio usuarioEscuelaRolRepositorio;

    public UsuarioControlador(UsuarioServicio usuarioServicio,
                              UsuarioEscuelaRolRepositorio usuarioEscuelaRolRepositorio) {
        this.usuarioServicio = usuarioServicio;
        this.usuarioEscuelaRolRepositorio = usuarioEscuelaRolRepositorio;
    }

    /**
     * Escuelas del usuario autenticado (para el selector de escuela).
     */
    @GetMapping("/escuelas")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<EscuelaDTO>> getEscuelasDelUsuario(Authentication auth) {
        ClaimsUsuario user = (ClaimsUsuario) auth.getPrincipal();
        Long usuarioId = user.usuarioId();

        List<Escuela> escuelas = usuarioEscuelaRolRepositorio.findEscuelasByUsuarioId(usuarioId);
        List<EscuelaDTO> dtos = escuelas.stream()
            .map(e -> new EscuelaDTO(
                e.getEscuelaId(),
                e.getNombre(),
                e.getNombreLargo(),
                e.getDireccion(),
                e.getTelefono(),
                e.getLogoUrl(),
                e.getClave(),
                e.getActivo()
            ))
            .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    /**
     * Roles del usuario autenticado en la escuela activa (X-School-ID).
     */
    @GetMapping("/mis-roles")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<String>> getMisRoles(Authentication auth) {
        ClaimsUsuario user = (ClaimsUsuario) auth.getPrincipal();
        Long usuarioId = user.usuarioId();
        Long escuelaId = EscuelaContexto.getEscuelaId();

        if (escuelaId == null) {
            return ResponseEntity.ok(List.of());
        }

        List<String> roles = usuarioEscuelaRolRepositorio
                .findByUsuarioIdAndEscuelaIdAndActivoTrue(usuarioId, escuelaId)
                .stream()
                .filter(r -> r.getRol() != null)
                .map(r -> r.getRol().getNombre())
                .distinct()
                .toList();

        return ResponseEntity.ok(roles);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<UsuarioDTO>> listarUsuarios(
            @PageableDefault(size = 10, sort = "nombreCompleto", direction = Sort.Direction.ASC) Pageable pageable,
            @RequestParam(required = false) String busqueda) {
        Page<UsuarioDTO> pagina = usuarioServicio.listarUsuarios(pageable, busqueda);
        return ResponseEntity.ok(pagina);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UsuarioDetalleDTO> obtenerUsuario(@PathVariable Long id) {
        UsuarioDetalleDTO dto = usuarioServicio.obtenerUsuario(id);
        return ResponseEntity.ok(dto);
    }

    /**
     * Crear usuario con foto opcional.
     *
     * El frontend envía multipart/form-data con:
     *   - "datos": Blob JSON con el DTO (usuario, email, password, asignaciones, etc.)
     *   - "fotoArchivo": opcional, imagen binaria.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UsuarioDTO> crearUsuario(
            @Valid @RequestPart("datos") UsuarioCrearDTO dto,
            @RequestPart(value = "fotoArchivo", required = false) MultipartFile fotoArchivo) {
        dto.setFotoArchivo(fotoArchivo);
        UsuarioDTO creado = usuarioServicio.crearUsuario(dto);
        return ResponseEntity.status(201).body(creado);
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UsuarioDTO> actualizarUsuario(
            @PathVariable Long id,
            @Valid @RequestPart("datos") UsuarioCrearDTO dto,
            @RequestPart(value = "fotoArchivo", required = false) MultipartFile fotoArchivo) {
        dto.setFotoArchivo(fotoArchivo);
        UsuarioDTO actualizado = usuarioServicio.actualizarUsuario(id, dto);
        return ResponseEntity.ok(actualizado);
    }

    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> cambiarEstado(
            @PathVariable Long id,
            @RequestParam boolean activo) {
        usuarioServicio.cambiarEstado(id, activo);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> eliminarUsuario(@PathVariable Long id) {
        usuarioServicio.eliminarUsuario(id);
        return ResponseEntity.noContent().build();
    }
}