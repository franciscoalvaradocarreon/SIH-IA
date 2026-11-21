package mx.sih.Controlador;

import jakarta.validation.Valid;
import java.util.List;
import mx.sih.modelo.dto.TipoEspacioCrearDTO;
import mx.sih.modelo.dto.TipoEspacioDTO;
import mx.sih.servicio.TipoEspacioServicio;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Catalogo de laboratorios y talleres, por turno.
 *
 * <p>Mismo criterio que el resto de catalogos: leer puede cualquiera con sesion, escribir exige
 * ADMIN o COORDINADOR (la regla de ruta ya corta antes; esto es la segunda capa).
 *
 * <p>Todas las rutas piden el TURNO: el semestre se deduce de el y asi el catalogo queda alineado
 * con aulas, grupos y materias.
 */
@RestController
@RequestMapping("/api/tipos-espacio")
public class TipoEspacioControlador {

    private final TipoEspacioServicio servicio;

    public TipoEspacioControlador(TipoEspacioServicio servicio) {
        this.servicio = servicio;
    }

    /** Lista los tipos del turno. {@code soloActivos=false} incluye los dados de baja. */
    @GetMapping
    public ResponseEntity<List<TipoEspacioDTO>> listar(
            @RequestParam Long turnoId,
            @RequestParam(required = false, defaultValue = "true") boolean soloActivos) {
        return ResponseEntity.ok(servicio.listar(turnoId, soloActivos));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TipoEspacioDTO> obtener(@PathVariable Long id,
                                                  @RequestParam Long turnoId) {
        return ResponseEntity.ok(servicio.obtener(id, turnoId));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'COORDINADOR')")
    public ResponseEntity<TipoEspacioDTO> crear(@Valid @RequestBody TipoEspacioCrearDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(servicio.crear(dto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'COORDINADOR')")
    public ResponseEntity<TipoEspacioDTO> actualizar(@PathVariable Long id,
                                                     @Valid @RequestBody TipoEspacioCrearDTO dto) {
        return ResponseEntity.ok(servicio.actualizar(id, dto));
    }

    /** Baja logica: el tipo deja de ofrecerse, pero las aulas que lo usan no se tocan. */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'COORDINADOR')")
    public ResponseEntity<TipoEspacioDTO> desactivar(@PathVariable Long id,
                                                     @RequestParam Long turnoId) {
        return ResponseEntity.ok(servicio.desactivar(id, turnoId));
    }
}
