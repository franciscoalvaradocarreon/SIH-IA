package mx.sih.controlador;

import jakarta.validation.Valid;
import mx.sih.modelo.dto.MaestroCrearDTO;
import mx.sih.modelo.dto.MaestroDTO;
import mx.sih.modelo.dto.MaestroDetalleDTO;
import mx.sih.servicio.MaestroServicio;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/maestros")
public class MaestroControlador {

    private final MaestroServicio maestroServicio;

    public MaestroControlador(MaestroServicio maestroServicio) {
        this.maestroServicio = maestroServicio;
    }

    @GetMapping
    public ResponseEntity<Page<MaestroDTO>> listarMaestros(
            @PageableDefault(size = 10, sort = "apellidos", direction = Sort.Direction.ASC)
            Pageable pageable,
            @RequestParam(required = false) String busqueda,
            @RequestParam(required = false) Long semestreId,
            @RequestParam(required = false) Long turnoId) {   // 🔥 NUEVO
        Page<MaestroDTO> pagina = maestroServicio.listarMaestros(
                pageable, busqueda, semestreId, turnoId);
        return ResponseEntity.ok(pagina);
    }

    @GetMapping("/{id}")
    public ResponseEntity<MaestroDTO> obtenerMaestro(@PathVariable Long id) {
        MaestroDTO dto = maestroServicio.obtenerMaestro(id);
        return ResponseEntity.ok(dto);
    }

    @PostMapping(consumes = "multipart/form-data")
    @PreAuthorize("hasAnyRole('ADMIN', 'COORDINADOR')")
    public ResponseEntity<MaestroDTO> crearMaestro(@Valid @ModelAttribute MaestroCrearDTO dto) {
        MaestroDTO creado = maestroServicio.crearMaestro(dto);
        return ResponseEntity.status(201).body(creado);
    }

    @PutMapping(value = "/{id}", consumes = "multipart/form-data")
    @PreAuthorize("hasAnyRole('ADMIN', 'COORDINADOR')")
    public ResponseEntity<MaestroDTO> actualizarMaestro(
            @PathVariable Long id,
            @Valid @ModelAttribute MaestroCrearDTO dto) {
        MaestroDTO actualizado = maestroServicio.actualizarMaestro(id, dto);
        return ResponseEntity.ok(actualizado);
    }

    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasAnyRole('ADMIN', 'COORDINADOR')")
    public ResponseEntity<Void> cambiarEstado(
            @PathVariable Long id,
            @RequestParam boolean activo) {
        maestroServicio.cambiarEstado(id, activo);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'COORDINADOR')")
    public ResponseEntity<Void> eliminarMaestro(@PathVariable Long id) {
        maestroServicio.eliminarMaestro(id);
        return ResponseEntity.noContent().build();
    }
}