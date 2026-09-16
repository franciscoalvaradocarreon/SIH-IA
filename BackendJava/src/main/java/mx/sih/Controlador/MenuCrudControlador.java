package mx.sih.controlador;

import jakarta.validation.Valid;
import mx.sih.modelo.dto.MenuCrearDTO;
import mx.sih.modelo.dto.MenuListaDTO;
import mx.sih.servicio.MenuCrudServicio;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/admin/menu")
@PreAuthorize("hasRole('ADMIN')")
public class MenuCrudControlador {

    private final MenuCrudServicio menuCrudServicio;

    public MenuCrudControlador(MenuCrudServicio menuCrudServicio) {
        this.menuCrudServicio = menuCrudServicio;
    }

    /**
     * Listar menús con paginación y búsqueda
     */
    @GetMapping
    public ResponseEntity<Page<MenuListaDTO>> listarMenus(
            @PageableDefault(size = 10, sort = "nivel", direction = Sort.Direction.ASC) Pageable pageable,
            @RequestParam(required = false) String busqueda) {
        Page<MenuListaDTO> pagina = menuCrudServicio.listarMenus(pageable, busqueda);
        return ResponseEntity.ok(pagina);
    }

    /**
     * Obtener un menú por ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<MenuCrearDTO> obtenerMenu(@PathVariable Long id) {
        MenuCrearDTO dto = menuCrudServicio.obtenerMenu(id);
        return ResponseEntity.ok(dto);
    }

    /**
     * Crear un nuevo menú
     */
    @PostMapping
    public ResponseEntity<MenuListaDTO> crearMenu(@Valid @RequestBody MenuCrearDTO dto) {
        MenuListaDTO creado = menuCrudServicio.crearMenu(dto);
        return ResponseEntity.status(201).body(creado);
    }

    /**
     * Actualizar un menú existente
     */
    @PutMapping("/{id}")
    public ResponseEntity<MenuListaDTO> actualizarMenu(
            @PathVariable Long id,
            @Valid @RequestBody MenuCrearDTO dto) {
        MenuListaDTO actualizado = menuCrudServicio.actualizarMenu(id, dto);
        return ResponseEntity.ok(actualizado);
    }

    /**
     * Cambiar estado (activo/inactivo)
     */
    @PatchMapping("/{id}/estado")
    public ResponseEntity<Void> cambiarEstado(
            @PathVariable Long id,
            @RequestParam boolean activo) {
        menuCrudServicio.cambiarEstado(id, activo);
        return ResponseEntity.noContent().build();
    }

    /**
     * Eliminar un menú (solo si no tiene hijos)
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarMenu(@PathVariable Long id) {
        menuCrudServicio.eliminarMenu(id);
        return ResponseEntity.noContent().build();
    }
}