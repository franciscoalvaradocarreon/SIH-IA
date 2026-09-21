package mx.sih.controlador;

import jakarta.validation.Valid;
import mx.sih.modelo.dto.EspecialidadCrearDTO;
import mx.sih.modelo.dto.EspecialidadDTO;
import mx.sih.servicio.EspecialidadServicio;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * NOTA DE SEGURIDAD: igual que TurnoControlador, este controlador no tenía ninguna
 * anotación @PreAuthorize, así que cualquier usuario autenticado podía crear o
 * eliminar especialidades. Se alinea con el patrón del resto de controladores.
 */
@RestController
@RequestMapping("/api/especialidades")
@PreAuthorize("hasAnyRole('ADMIN', 'COORDINADOR')")
public class EspecialidadControlador {

    private final EspecialidadServicio especialidadServicio;

    public EspecialidadControlador(EspecialidadServicio especialidadServicio) {
        this.especialidadServicio = especialidadServicio;
    }

    @GetMapping
    public ResponseEntity<Page<EspecialidadDTO>> listarEspecialidades(
            @PageableDefault(size = 10, sort = "nombre", direction = Sort.Direction.ASC)
            Pageable pageable,
            @RequestParam(required = false) String busqueda,
            @RequestParam(required = false) Long semestreId,
            @RequestParam(required = false) Long turnoId) {
        Page<EspecialidadDTO> pagina = especialidadServicio.listarEspecialidades(
                pageable, busqueda, semestreId, turnoId);
        return ResponseEntity.ok(pagina);
    }

    @GetMapping("/{id}")
    public ResponseEntity<EspecialidadDTO> obtenerEspecialidad(@PathVariable Long id) {
        EspecialidadDTO dto = especialidadServicio.obtenerEspecialidad(id);
        return ResponseEntity.ok(dto);
    }

    @PostMapping
    public ResponseEntity<EspecialidadDTO> crearEspecialidad(
            @Valid @RequestBody EspecialidadCrearDTO dto) {
        EspecialidadDTO creada = especialidadServicio.crearEspecialidad(dto);
        return ResponseEntity.status(201).body(creada);
    }

    @PutMapping("/{id}")
    public ResponseEntity<EspecialidadDTO> actualizarEspecialidad(
            @PathVariable Long id,
            @Valid @RequestBody EspecialidadCrearDTO dto) {
        EspecialidadDTO actualizada = especialidadServicio.actualizarEspecialidad(id, dto);
        return ResponseEntity.ok(actualizada);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarEspecialidad(@PathVariable Long id) {
        especialidadServicio.eliminarEspecialidad(id);
        return ResponseEntity.noContent().build();
    }
}
