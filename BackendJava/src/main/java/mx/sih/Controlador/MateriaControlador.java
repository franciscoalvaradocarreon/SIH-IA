package mx.sih.controlador;

import jakarta.validation.Valid;
import mx.sih.modelo.dto.MateriaCrearDTO;
import mx.sih.modelo.dto.MateriaDTO;
import mx.sih.modelo.dto.MateriaDetalleDTO;
import mx.sih.servicio.MateriaServicio;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/materias")
@PreAuthorize("isAuthenticated()")
public class MateriaControlador {

    private final MateriaServicio materiaServicio;

    public MateriaControlador(MateriaServicio materiaServicio) {
        this.materiaServicio = materiaServicio;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'COORDINADOR')")
    public ResponseEntity<Page<MateriaDTO>> listarMaterias(
            @PageableDefault(size = 10, sort = "nombre", direction = Sort.Direction.ASC)
            Pageable pageable,
            @RequestParam(required = false) String busqueda,
            @RequestParam(required = false) Long semestreId,
            @RequestParam(required = false) Long turnoId) {   // 🔥 NUEVO
        Page<MateriaDTO> pagina = materiaServicio.listarMaterias(
                pageable, busqueda, semestreId, turnoId);
        return ResponseEntity.ok(pagina);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'COORDINADOR')")
    public ResponseEntity<MateriaDetalleDTO> obtenerMateria(@PathVariable Long id) {
        MateriaDetalleDTO dto = materiaServicio.obtenerMateria(id);
        return ResponseEntity.ok(dto);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'COORDINADOR')")
    public ResponseEntity<MateriaDTO> crearMateria(@Valid @RequestBody MateriaCrearDTO dto) {
        MateriaDTO creada = materiaServicio.crearMateria(dto);
        return ResponseEntity.status(201).body(creada);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'COORDINADOR')")
    public ResponseEntity<MateriaDTO> actualizarMateria(
            @PathVariable Long id,
            @Valid @RequestBody MateriaCrearDTO dto) {
        MateriaDTO actualizada = materiaServicio.actualizarMateria(id, dto);
        return ResponseEntity.ok(actualizada);
    }

    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasAnyRole('ADMIN', 'COORDINADOR')")
    public ResponseEntity<Void> cambiarEstado(
            @PathVariable Long id,
            @RequestParam boolean activo) {
        materiaServicio.cambiarEstado(id, activo);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'COORDINADOR')")
    public ResponseEntity<Void> eliminarMateria(@PathVariable Long id) {
        materiaServicio.eliminarMateria(id);
        return ResponseEntity.noContent().build();
    }
}