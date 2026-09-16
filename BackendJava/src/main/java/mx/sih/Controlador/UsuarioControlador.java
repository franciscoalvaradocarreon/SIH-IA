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
import mx.sih.seguridad.contexto.EscuelaContexto;
import mx.sih.servicio.UsuarioServicio;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

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

    // Endpoint existente: escuelas del usuario autenticado
    @GetMapping("/escuelas")
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
        System.out.println("Obtener Usuario Id: " + id);
        return ResponseEntity.ok(dto);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UsuarioDTO> crearUsuario(@Valid @RequestBody UsuarioCrearDTO dto) {
        UsuarioDTO creado = usuarioServicio.crearUsuario(dto);
        return ResponseEntity.status(201).body(creado);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UsuarioDTO> actualizarUsuario(
            @PathVariable Long id,
            @Valid @RequestBody UsuarioCrearDTO dto) {
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
    
        @GetMapping("/mis-roles")
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

}