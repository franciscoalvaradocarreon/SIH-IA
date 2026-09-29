// mx.sih.controlador.DisponibilidadMaestroControlador.java
package mx.sih.controlador;

import jakarta.validation.Valid;
import mx.sih.modelo.dto.DisponibilidadMaestroCrearDTO;
import mx.sih.modelo.dto.DisponibilidadMaestroDTO;
import mx.sih.modelo.dto.ImportarDisponibilidadDTO;
import mx.sih.modelo.dto.ResultadoImportacionDisponibilidadDTO;
import mx.sih.servicio.DisponibilidadMaestroServicio;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/disponibilidad-maestro")
@PreAuthorize("isAuthenticated()")
public class DisponibilidadMaestroControlador {

    private final DisponibilidadMaestroServicio disponibilidadServicio;

    public DisponibilidadMaestroControlador(DisponibilidadMaestroServicio disponibilidadServicio) {
        this.disponibilidadServicio = disponibilidadServicio;
    }

    @GetMapping
    public ResponseEntity<Page<DisponibilidadMaestroDTO>> listar(
            @PageableDefault(size = 10) Pageable pageable,
            @RequestParam(required = false) String busqueda,
            @RequestParam(required = false) Long semestreId) {
        return ResponseEntity.ok(disponibilidadServicio.listarDisponibilidades(pageable, busqueda, semestreId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DisponibilidadMaestroDTO> obtenerDisponibilidad(@PathVariable Long id) {
        DisponibilidadMaestroDTO dto = disponibilidadServicio.obtenerDisponibilidad(id);
        return ResponseEntity.ok(dto);
    }

    @GetMapping("/maestro/{maestroId}")
    public ResponseEntity<List<DisponibilidadMaestroDTO>> obtenerDisponibilidadPorMaestro(@PathVariable Long maestroId) {
        List<DisponibilidadMaestroDTO> lista = disponibilidadServicio.obtenerDisponibilidadPorMaestro(maestroId);
        return ResponseEntity.ok(lista);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'COORDINADOR')")
    public ResponseEntity<DisponibilidadMaestroDTO> guardarDisponibilidad(
            @Valid @RequestBody DisponibilidadMaestroCrearDTO dto) {
        DisponibilidadMaestroDTO creado = disponibilidadServicio.guardarDisponibilidad(dto);
        return ResponseEntity.status(201).body(creado);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'COORDINADOR')")
    public ResponseEntity<DisponibilidadMaestroDTO> actualizarDisponibilidad(
            @PathVariable Long id,
            @Valid @RequestBody DisponibilidadMaestroCrearDTO dto) {
        // Primero eliminamos la existente y creamos una nueva (o usamos guardar)
        DisponibilidadMaestroDTO actualizado = disponibilidadServicio.guardarDisponibilidad(dto);
        return ResponseEntity.ok(actualizado);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'COORDINADOR')")
    public ResponseEntity<Void> eliminarDisponibilidad(@PathVariable Long id) {
        disponibilidadServicio.eliminarDisponibilidad(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/maestro/{maestroId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> eliminarDisponibilidadPorMaestro(@PathVariable Long maestroId) {
        disponibilidadServicio.eliminarDisponibilidadPorMaestro(maestroId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Trae la disponibilidad de los maestros de un turno desde OTRO semestre.
     *
     * <p>OJO con el @PreAuthorize: esta clase esta anotada con isAuthenticated() (no con ADMIN/
     * COORDINADOR), asi que la restriccion de escritura hay que repetirla en cada metodo, como en el
     * resto de este controlador.
     *
     * <p>NegocioExcepcion se deja pasar al manejador global, que responde con el mismo cuerpo de error
     * (success/error/message) que ya lee la pantalla.
     */
    @PostMapping("/importar")
    @PreAuthorize("hasAnyRole('ADMIN', 'COORDINADOR')")
    public ResponseEntity<ResultadoImportacionDisponibilidadDTO> importarDisponibilidad(
            @Valid @RequestBody ImportarDisponibilidadDTO dto) {
        return ResponseEntity.ok(disponibilidadServicio.importarDisponibilidad(dto));
    }
}