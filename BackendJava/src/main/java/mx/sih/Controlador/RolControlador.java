package mx.sih.controlador;

import jakarta.validation.Valid;
import mx.sih.modelo.dto.RolCrearDTO;
import mx.sih.modelo.dto.RolDTO;
import mx.sih.servicio.RolServicio;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/roles")
public class RolControlador {

    private final RolServicio rolServicio;

    public RolControlador(RolServicio rolServicio) {
        this.rolServicio = rolServicio;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<RolDTO>> listarRoles(
            @PageableDefault(size = 10, sort = "nombre", direction = Sort.Direction.ASC) Pageable pageable,
            @RequestParam(required = false) String busqueda) {
        Page<RolDTO> pagina = rolServicio.listarRoles(pageable, busqueda);
        return ResponseEntity.ok(pagina);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RolDTO> obtenerRol(@PathVariable Long id) {
        RolDTO dto = rolServicio.obtenerRol(id);
        return ResponseEntity.ok(dto);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RolDTO> crearRol(@Valid @RequestBody RolCrearDTO dto) {
        RolDTO creado = rolServicio.crearRol(dto);
        return ResponseEntity.status(201).body(creado);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RolDTO> actualizarRol(
            @PathVariable Long id,
            @Valid @RequestBody RolCrearDTO dto) {
        RolDTO actualizado = rolServicio.actualizarRol(id, dto);
        return ResponseEntity.ok(actualizado);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> eliminarRol(@PathVariable Long id) {
        rolServicio.eliminarRol(id);
        return ResponseEntity.noContent().build();
    }
}