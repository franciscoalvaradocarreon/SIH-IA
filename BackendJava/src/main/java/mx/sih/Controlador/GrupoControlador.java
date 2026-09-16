package mx.sih.controlador;

import jakarta.validation.Valid;
import mx.sih.modelo.dto.GrupoCrearDTO;
import mx.sih.modelo.dto.GrupoDTO;
import mx.sih.modelo.dto.GrupoDetalleDTO;
import mx.sih.servicio.GrupoServicio;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/grupos")
public class GrupoControlador {

    private final GrupoServicio grupoServicio;

    public GrupoControlador(GrupoServicio grupoServicio) {
        this.grupoServicio = grupoServicio;
    }

    @GetMapping
    public ResponseEntity<Page<GrupoDTO>> listarGrupos(
            @PageableDefault(size = 10, sort = "grado", direction = Sort.Direction.ASC)
            Pageable pageable,
            @RequestParam(required = false) String busqueda,
            @RequestParam(required = false) Long especialidadId,
            @RequestParam(required = false) Long turnoId,       // 🔥 NUEVO
            @RequestParam(required = false) Long semestreId) {
        Page<GrupoDTO> pagina = grupoServicio.listarGrupos(
                pageable, busqueda, especialidadId, turnoId, semestreId);
        return ResponseEntity.ok(pagina);
    }

    @GetMapping("/{id}")
    public ResponseEntity<GrupoDetalleDTO> obtenerGrupo(@PathVariable Long id) {
        GrupoDetalleDTO dto = grupoServicio.obtenerGrupo(id);
        return ResponseEntity.ok(dto);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'COORDINADOR')")
    public ResponseEntity<GrupoDTO> crearGrupo(@Valid @RequestBody GrupoCrearDTO dto) {
        GrupoDTO creado = grupoServicio.crearGrupo(dto);
        return ResponseEntity.status(201).body(creado);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'COORDINADOR')")
    public ResponseEntity<GrupoDTO> actualizarGrupo(
            @PathVariable Long id,
            @Valid @RequestBody GrupoCrearDTO dto) {
        GrupoDTO actualizado = grupoServicio.actualizarGrupo(id, dto);
        return ResponseEntity.ok(actualizado);
    }

    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasAnyRole('ADMIN', 'COORDINADOR')")
    public ResponseEntity<Void> cambiarEstado(
            @PathVariable Long id,
            @RequestParam boolean activo) {
        System.out.println("📌 Cambiando estado del grupo " + id + " a " + activo);
        grupoServicio.cambiarEstado(id, activo);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'COORDINADOR')")
    public ResponseEntity<Void> eliminarGrupo(@PathVariable Long id) {
        grupoServicio.eliminarGrupo(id);
        return ResponseEntity.noContent().build();
    }
}