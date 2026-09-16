package mx.sih.controlador;

import mx.sih.modelo.dto.MenuDTO;
import mx.sih.modelo.dto.MenuListaDTO;
import mx.sih.servicio.MenuCrudServicio;
import mx.sih.servicio.MenuServicio;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/menu")
public class MenuControlador {

    private final MenuServicio menuServicio;
    private final MenuCrudServicio menuCrudServicio;

    public MenuControlador(MenuServicio menuServicio,
                           MenuCrudServicio menuCrudServicio) {
        this.menuServicio = menuServicio;
        this.menuCrudServicio = menuCrudServicio;
    }

    /** Árbol de menús para el usuario autenticado (filtrado por roles). */
    @GetMapping
    public List<MenuDTO> obtenerMenu() {
        return menuServicio.obtenerMenuPorUsuario();
    }

    /** Lista plana de todos los menús activos (para el CRUD). */
    @GetMapping("/todos")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<MenuListaDTO>> listarTodosLosMenus() {
        return ResponseEntity.ok(menuCrudServicio.listarTodosActivos());
    }
}