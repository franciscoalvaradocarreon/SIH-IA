package mx.sih.controlador;

import jakarta.validation.Valid;
import mx.sih.modelo.dto.EscuelaCrearDTO;
import mx.sih.modelo.dto.EscuelaDTO;
import mx.sih.repositorio.UsuarioEscuelaRolRepositorio;
import mx.sih.servicio.EscuelaServicio;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/escuelas")
public class EscuelaControlador {

    private final EscuelaServicio escuelaServicio;


    public EscuelaControlador(EscuelaServicio escuelaServicio,
                              UsuarioEscuelaRolRepositorio usuarioEscuelaRolRepositorio) {
        this.escuelaServicio = escuelaServicio;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<EscuelaDTO>> listarEscuelas(
            @PageableDefault(size = 10, sort = "nombre", direction = Sort.Direction.ASC) Pageable pageable,
            @RequestParam(required = false) String busqueda) {
        Page<EscuelaDTO> pagina = escuelaServicio.listarEscuelas(pageable, busqueda);
        return ResponseEntity.ok(pagina);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<EscuelaDTO> obtenerEscuela(@PathVariable Long id) {
        EscuelaDTO dto = escuelaServicio.obtenerEscuela(id);
        return ResponseEntity.ok(dto);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<EscuelaDTO> crearEscuela(@Valid @RequestBody EscuelaCrearDTO dto) {
        EscuelaDTO creada = escuelaServicio.crearEscuela(dto);
        return ResponseEntity.status(201).body(creada);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<EscuelaDTO> actualizarEscuela(
            @PathVariable Long id,
            @Valid @RequestBody EscuelaCrearDTO dto) {
        EscuelaDTO actualizada = escuelaServicio.actualizarEscuela(id, dto);
        return ResponseEntity.ok(actualizada);
    }

    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> cambiarEstado(
            @PathVariable Long id,
            @RequestParam boolean activo) {
        escuelaServicio.cambiarEstado(id, activo);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> eliminarEscuela(@PathVariable Long id) {
        escuelaServicio.eliminarEscuela(id);
        return ResponseEntity.noContent().build();
    }


}