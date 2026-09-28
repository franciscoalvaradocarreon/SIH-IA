package mx.sih.controlador;

import jakarta.validation.Valid;
import mx.sih.modelo.dto.AulaCrearDTO;
import mx.sih.modelo.dto.AulaDTO;
import mx.sih.modelo.dto.AulaDetalleDTO;
import mx.sih.modelo.dto.ImportarAulasDTO;
import mx.sih.modelo.dto.ResultadoImportacionAulasDTO;
import mx.sih.servicio.AulaServicio;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/aulas")
@PreAuthorize("hasAnyRole('ADMIN', 'COORDINADOR')")
public class AulaControlador {

    private final AulaServicio aulaServicio;

    public AulaControlador(AulaServicio aulaServicio) {
        this.aulaServicio = aulaServicio;
    }

    @GetMapping
    public ResponseEntity<Page<AulaDTO>> listarAulas(
            @PageableDefault(size = 10, sort = "nombre") Pageable pageable,
            @RequestParam(required = false) String busqueda,
            @RequestParam(required = false) Long semestreId,
            @RequestParam(required = false) Long turnoId) {
        Page<AulaDTO> page = aulaServicio.listarAulas(pageable, busqueda, semestreId, turnoId);
        return ResponseEntity.ok(page);
    }

    @GetMapping("/{id}")
    public ResponseEntity<AulaDetalleDTO> obtenerAula(@PathVariable Long id) {
        AulaDetalleDTO dto = aulaServicio.obtenerAula(id);
        return ResponseEntity.ok(dto);
    }

    @PostMapping
    public ResponseEntity<AulaDTO> crearAula(@Valid @RequestBody AulaCrearDTO dto) {
        AulaDTO creada = aulaServicio.crearAula(dto);
        return ResponseEntity.status(201).body(creada);
    }

    /**
     * Trae al semestre de destino las aulas de otro semestre de la misma escuela.
     *
     * No lleva @PreAuthorize propio: la clase ya exige ADMIN o COORDINADOR, que es lo correcto para
     * una operacion que escribe.
     */
    @PostMapping("/importar")
    public ResponseEntity<ResultadoImportacionAulasDTO> importarAulas(
            @Valid @RequestBody ImportarAulasDTO dto) {
        ResultadoImportacionAulasDTO resultado = aulaServicio.importarAulas(dto);
        return ResponseEntity.ok(resultado);
    }

    @PutMapping("/{id}")
    public ResponseEntity<AulaDTO> actualizarAula(
            @PathVariable Long id,
            @Valid @RequestBody AulaCrearDTO dto) {
        AulaDTO actualizada = aulaServicio.actualizarAula(id, dto);
        return ResponseEntity.ok(actualizada);
    }

    @PatchMapping("/{id}/estado")
    public ResponseEntity<Void> cambiarEstado(
            @PathVariable Long id,
            @RequestParam boolean activo) {
        aulaServicio.cambiarEstado(id, activo);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarAula(@PathVariable Long id) {
        aulaServicio.eliminarAula(id);
        return ResponseEntity.noContent().build();
    }
}