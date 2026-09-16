// mx.sih.controlador.GrupoMateriaMaestroControlador.java
package mx.sih.controlador;

import jakarta.validation.Valid;
import mx.sih.modelo.dto.AsignacionCrearDTO;
import mx.sih.modelo.dto.AsignacionDTO;
import mx.sih.servicio.AsignacionServicio;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/asignaciones")
@PreAuthorize("isAuthenticated()")
public class AsignacionControlador {

    private final AsignacionServicio asignacionServicio;

    public AsignacionControlador(AsignacionServicio asignacionServicio) {
        this.asignacionServicio = asignacionServicio;
    }

    /**
     * Listar todas las asignaciones con paginación y búsqueda
     */
    @GetMapping
    public ResponseEntity<Page<AsignacionDTO>> listarAsignaciones(
            @PageableDefault(size = 10, sort = "id") Pageable pageable,
            @RequestParam(required = false) String busqueda,
            @RequestParam(required = false) Long grupoId,          // 🔥 NUEVO
            @RequestParam(required = false) Long especialidadId,
            @RequestParam(required = false) Long turnoId,
            @RequestParam(required = false) Long semestreId) {
        Page<AsignacionDTO> page = asignacionServicio.listarAsignaciones(
                pageable, busqueda, grupoId, especialidadId, turnoId, semestreId);
        return ResponseEntity.ok(page);
    }
    /**
     * Obtener una asignación por ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<AsignacionDTO> obtenerAsignacion(@PathVariable Long id) {
        AsignacionDTO dto = asignacionServicio.obtenerAsignacion(id);
        return ResponseEntity.ok(dto);
    }

    /**
     * Crear una nueva asignación
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'COORDINADOR')")
    public ResponseEntity<AsignacionDTO> crearAsignacion(
            @Valid @RequestBody AsignacionCrearDTO dto) {
        AsignacionDTO creado = asignacionServicio.crearAsignacion(dto);
        return ResponseEntity.status(201).body(creado);
    }

    /**
     * Actualizar una asignación existente
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'COORDINADOR')")
    public ResponseEntity<AsignacionDTO> actualizarAsignacion(
            @PathVariable Long id,
            @Valid @RequestBody AsignacionCrearDTO dto) {
        AsignacionDTO actualizado = asignacionServicio.actualizarAsignacion(id, dto);
        return ResponseEntity.ok(actualizado);
    }

    /**
     * Cambiar estado (activo/inactivo) de una asignación
     */
    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasAnyRole('ADMIN', 'COORDINADOR')")
    public ResponseEntity<Void> cambiarEstado(
            @PathVariable Long id,
            @RequestParam boolean activo) {
        asignacionServicio.cambiarEstado(id, activo);
        return ResponseEntity.noContent().build();
    }

    /**
     * Eliminar una asignación (físicamente)
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'COORDINADOR')")
    public ResponseEntity<Void> eliminarAsignacion(@PathVariable Long id) {
        asignacionServicio.eliminarAsignacion(id);
        return ResponseEntity.noContent().build();
    }
}