package mx.sih.controlador;

import jakarta.validation.Valid;
import mx.sih.modelo.dto.DisponibilidadGrupoCrearDTO;
import mx.sih.modelo.dto.DisponibilidadGrupoDTO;
import mx.sih.servicio.DisponibilidadGrupoServicio;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/disponibilidad-grupo")
public class DisponibilidadGrupoControlador {

    private final DisponibilidadGrupoServicio disponibilidadServicio;

    public DisponibilidadGrupoControlador(DisponibilidadGrupoServicio disponibilidadServicio) {
        this.disponibilidadServicio = disponibilidadServicio;
    }

    /**
     *  Obtener disponibilidad de un grupo (por semestre o el activo)
     */
    @GetMapping("/grupo/{grupoId}")
    public ResponseEntity<List<DisponibilidadGrupoDTO>> obtenerPorGrupo(
            @PathVariable Long grupoId,
            @RequestParam(required = false) Long semestreId) {
        return ResponseEntity.ok(disponibilidadServicio.obtenerDisponibilidadPorGrupo(grupoId, semestreId));
    }

    /**
     *  Obtener por ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<DisponibilidadGrupoDTO> obtener(@PathVariable Long id) {
        return ResponseEntity.ok(disponibilidadServicio.obtenerDisponibilidad(id));
    }

    /**
     *  Crear o actualizar disponibilidad
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'COORDINADOR')")
    public ResponseEntity<DisponibilidadGrupoDTO> guardar(
            @Valid @RequestBody DisponibilidadGrupoCrearDTO dto) {
        DisponibilidadGrupoDTO guardado = disponibilidadServicio.guardarDisponibilidad(dto);
        return new ResponseEntity<>(guardado, HttpStatus.CREATED);
    }

    /**
     *  Eliminar por ID
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'COORDINADOR')")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        disponibilidadServicio.eliminarDisponibilidad(id);
        return ResponseEntity.noContent().build();
    }

    /**
     *  Eliminar todas las disponibilidades de un grupo
     */
    @DeleteMapping("/grupo/{grupoId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'COORDINADOR')")
    public ResponseEntity<Void> eliminarPorGrupo(
            @PathVariable Long grupoId,
            @RequestParam Long semestreId) {
        disponibilidadServicio.eliminarDisponibilidadPorGrupo(grupoId, semestreId);
        return ResponseEntity.noContent().build();
    }
    
    @GetMapping("/grupo/{grupoId}/count")
    public ResponseEntity<Long> contarDisponibles(
            @PathVariable Long grupoId,
            @RequestParam(required = false) Long semestreId) {
        return ResponseEntity.ok(disponibilidadServicio.contarBloquesDisponibles(grupoId, semestreId));
    }
    
}