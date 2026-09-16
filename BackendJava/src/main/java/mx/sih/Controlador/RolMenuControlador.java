package mx.sih.controlador;

import jakarta.validation.Valid;
import mx.sih.modelo.dto.RolMenuCrearDTO;
import mx.sih.modelo.dto.RolMenuDTO;
import mx.sih.modelo.dto.RolMenuAsignacionDTO;
import mx.sih.servicio.RolMenuServicio;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/rol-menu")
public class RolMenuControlador {

    private final RolMenuServicio rolMenuServicio;

    public RolMenuControlador(RolMenuServicio rolMenuServicio) {
        this.rolMenuServicio = rolMenuServicio;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<RolMenuDTO>> listarRolMenu(
            @PageableDefault(size = 10, sort = "rol.nombre", direction = Sort.Direction.ASC) Pageable pageable) {
        Page<RolMenuDTO> pagina = rolMenuServicio.listarRolMenu(pageable);
        return ResponseEntity.ok(pagina);
    }

    @GetMapping("/rol/{rolId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<RolMenuDTO>> listarPorRol(@PathVariable Long rolId) {
        List<RolMenuDTO> lista = rolMenuServicio.listarPorRol(rolId);
        return ResponseEntity.ok(lista);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RolMenuDTO> asignarMenu(@Valid @RequestBody RolMenuCrearDTO dto) {
        RolMenuDTO creado = rolMenuServicio.asignarMenu(dto);
        return ResponseEntity.status(201).body(creado);
    }

    @PostMapping("/asignar-multiples")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> asignarMenus(@Valid @RequestBody RolMenuAsignacionDTO dto) {
        rolMenuServicio.asignarMenus(dto);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{rolId}/{menuId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> desasignarMenu(@PathVariable Long rolId,
                                               @PathVariable Long menuId) {
        rolMenuServicio.desasignarMenu(rolId, menuId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/rol/{rolId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> eliminarPorRol(@PathVariable Long rolId) {
        rolMenuServicio.eliminarPorRol(rolId);
        return ResponseEntity.noContent().build();
    }
}