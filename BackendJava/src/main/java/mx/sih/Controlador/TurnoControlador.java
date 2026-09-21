package mx.sih.controlador;

import jakarta.validation.Valid;
import mx.sih.modelo.dto.TurnoCrearDTO;
import mx.sih.modelo.dto.TurnoDTO;
import mx.sih.servicio.TurnoServicio;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * NOTA DE SEGURIDAD: este controlador no tenía NINGUNA anotación @PreAuthorize
 * (era el único junto con EspecialidadControlador). Con @EnableMethodSecurity ya
 * activo, las reglas de ruta solo exigían "autenticado", así que cualquier usuario
 * con el rol más bajo podía crear, modificar o borrar turnos de su escuela.
 * Ahora las escrituras siguen el mismo patrón que el resto del proyecto.
 */
@RestController
@RequestMapping("/api/turnos")
@PreAuthorize("hasAnyRole('ADMIN', 'COORDINADOR')")
public class TurnoControlador {

    private final TurnoServicio turnoServicio;

    public TurnoControlador(TurnoServicio turnoServicio) {
        this.turnoServicio = turnoServicio;
    }

    @GetMapping
    public ResponseEntity<Page<TurnoDTO>> listarTurnos(
            @PageableDefault(size = 10, sort = "nombre", direction = Sort.Direction.ASC) Pageable pageable,
            @RequestParam(required = false) String busqueda,
            @RequestParam(required = false) Long semestreId) {
        Page<TurnoDTO> pagina = turnoServicio.listarTurnos(pageable, busqueda, semestreId);
        return ResponseEntity.ok(pagina);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TurnoDTO> obtenerTurno(@PathVariable Long id) {
        TurnoDTO dto = turnoServicio.obtenerTurno(id);
        return ResponseEntity.ok(dto);
    }

    @PostMapping
    public ResponseEntity<TurnoDTO> crearTurno(@Valid @RequestBody TurnoCrearDTO dto) {
        TurnoDTO creado = turnoServicio.crearTurno(dto);
        return ResponseEntity.status(201).body(creado);
    }

    @PutMapping("/{id}")
    public ResponseEntity<TurnoDTO> actualizarTurno(
            @PathVariable Long id,
            @Valid @RequestBody TurnoCrearDTO dto) {
        TurnoDTO actualizado = turnoServicio.actualizarTurno(id, dto);
        return ResponseEntity.ok(actualizado);
    }

    @PatchMapping("/{id}/estado")
    public ResponseEntity<Void> cambiarEstado(
            @PathVariable Long id,
            @RequestParam boolean activo) {
        turnoServicio.cambiarEstado(id, activo);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarTurno(@PathVariable Long id) {
        turnoServicio.eliminarTurno(id);
        return ResponseEntity.noContent().build();
    }
}
