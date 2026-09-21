package mx.sih.controlador;

import jakarta.validation.Valid;
import mx.sih.modelo.dto.EscuelaCrearDTO;
import mx.sih.modelo.dto.EscuelaDTO;
import mx.sih.repositorio.UsuarioEscuelaRolRepositorio;
import mx.sih.servicio.EscuelaServicio;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/escuelas")
public class EscuelaControlador {

    private final EscuelaServicio escuelaServicio;

    public EscuelaControlador(EscuelaServicio escuelaServicio,
                              UsuarioEscuelaRolRepositorio usuarioEscuelaRolRepositorio) {
        this.escuelaServicio = escuelaServicio;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<EscuelaDTO>> listarEscuelas(
            @PageableDefault(size = 10, sort = "nombre", direction = Sort.Direction.ASC) Pageable pageable,
            @RequestParam(required = false) String busqueda) {
        return ResponseEntity.ok(escuelaServicio.listarEscuelas(pageable, busqueda));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<EscuelaDTO> obtenerEscuela(@PathVariable Long id) {
        return ResponseEntity.ok(escuelaServicio.obtenerEscuela(id));
    }

    /**
     * Crear escuela con logo opcional.
     *
     * El frontend envía multipart/form-data con:
     *   - "datos": Blob JSON con el DTO (nombre, direccion, telefono, etc.)
     *   - "logoArchivo": opcional, imagen binaria.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<EscuelaDTO> crearEscuela(
            @Valid @RequestPart("datos") EscuelaCrearDTO dto,
            @RequestPart(value = "logoArchivo", required = false) MultipartFile logoArchivo) {
        dto.setLogoArchivo(logoArchivo);
        return ResponseEntity.status(201).body(escuelaServicio.crearEscuela(dto));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<EscuelaDTO> actualizarEscuela(
            @PathVariable Long id,
            @Valid @RequestPart("datos") EscuelaCrearDTO dto,
            @RequestPart(value = "logoArchivo", required = false) MultipartFile logoArchivo) {
        dto.setLogoArchivo(logoArchivo);
        return ResponseEntity.ok(escuelaServicio.actualizarEscuela(id, dto));
    }

    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> cambiarEstado(
            @PathVariable Long id,
            @RequestParam boolean activo) {
        escuelaServicio.cambiarEstado(id, activo);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> eliminarEscuela(@PathVariable Long id) {
        escuelaServicio.eliminarEscuela(id);
        return ResponseEntity.noContent().build();
    }
}