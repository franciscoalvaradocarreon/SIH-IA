// mx.sih.controlador.SemestreControlador.java
package mx.sih.controlador;

import jakarta.validation.Valid;
import mx.sih.modelo.dto.SemestreCrearDTO;
import mx.sih.modelo.dto.SemestreDTO;
import mx.sih.servicio.SemestreServicio;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/semestres")
@PreAuthorize("hasAnyRole('ADMIN', 'COORDINADOR')")
public class SemestreControlador {

    private final SemestreServicio semestreServicio;

    public SemestreControlador(SemestreServicio semestreServicio) {
        this.semestreServicio = semestreServicio;
    }

    /**
     * Listar semestres con paginación y búsqueda
     */
    @GetMapping
    public ResponseEntity<Page<SemestreDTO>> listarSemestres(
            @PageableDefault(size = 10, sort = "semestreId", direction = Sort.Direction.DESC) Pageable pageable,
            @RequestParam(required = false) String busqueda) {
        Page<SemestreDTO> pagina = semestreServicio.listarSemestres(pageable, busqueda);
        return ResponseEntity.ok(pagina);
    }

    /**
     * Listar todos los semestres (sin paginación)
     */
    @GetMapping("/todos")
    public ResponseEntity<List<SemestreDTO>> listarTodosSemestres() {
        List<SemestreDTO> semestres = semestreServicio.listarTodosSemestres();
        return ResponseEntity.ok(semestres);
    }

    /**
     * Listar semestres activos
     */
    @GetMapping("/activos")
    public ResponseEntity<List<SemestreDTO>> listarSemestresActivos() {
        List<SemestreDTO> semestres = semestreServicio.listarSemestresActivos();
        return ResponseEntity.ok(semestres);
    }

    /**
     * Obtener semestre actual (activo)
     */
    @GetMapping("/actual")
    public ResponseEntity<SemestreDTO> obtenerSemestreActual() {
        SemestreDTO semestre = semestreServicio.obtenerSemestreActual();
        return ResponseEntity.ok(semestre);
    }

    /**
     * Obtener semestre por ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<SemestreDTO> obtenerSemestre(@PathVariable Long id) {
        SemestreDTO semestre = semestreServicio.obtenerSemestre(id);
        return ResponseEntity.ok(semestre);
    }

    /**
     * Crear un nuevo semestre
     */
    @PostMapping
    public ResponseEntity<SemestreDTO> crearSemestre(@Valid @RequestBody SemestreCrearDTO dto) {
        SemestreDTO creado = semestreServicio.crearSemestre(dto);
        return ResponseEntity.status(201).body(creado);
    }

    /**
     * Actualizar un semestre
     */
    @PutMapping("/{id}")
    public ResponseEntity<SemestreDTO> actualizarSemestre(
            @PathVariable Long id,
            @Valid @RequestBody SemestreCrearDTO dto) {
        SemestreDTO actualizado = semestreServicio.actualizarSemestre(id, dto);
        return ResponseEntity.ok(actualizado);
    }

    /**
     * Cambiar estado de un semestre
     */
    @PatchMapping("/{id}/estado")
    public ResponseEntity<SemestreDTO> cambiarEstado(
            @PathVariable Long id,
            @RequestParam boolean activo) {
        SemestreDTO semestre = semestreServicio.cambiarEstado(id, activo);
        return ResponseEntity.ok(semestre);
    }

    /**
     * Eliminar un semestre
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarSemestre(@PathVariable Long id) {
        semestreServicio.eliminarSemestre(id);
        return ResponseEntity.noContent().build();
    }
}