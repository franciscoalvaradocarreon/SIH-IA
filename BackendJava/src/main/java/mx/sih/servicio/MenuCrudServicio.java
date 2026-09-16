package mx.sih.servicio;

import mx.sih.excepcion.NegocioExcepcion;
import mx.sih.modelo.dto.MenuCrearDTO;
import mx.sih.modelo.dto.MenuListaDTO;
import mx.sih.modelo.entidad.Menu;
import mx.sih.repositorio.MenuRepositorio;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * CRUD de menús.
 *
 * Los menús son GLOBALES: no dependen de una escuela. Cualquier usuario con
 * rol ADMIN puede gestionar el árbol completo.
 */
@Service
public class MenuCrudServicio {

    private final MenuRepositorio menuRepositorio;

    public MenuCrudServicio(MenuRepositorio menuRepositorio) {
        this.menuRepositorio = menuRepositorio;
    }

    public Page<MenuListaDTO> listarMenus(Pageable pageable, String busqueda) {
        String busquedaNormalizada = (busqueda == null) ? "" : busqueda.trim();
        Page<Menu> pagina = menuRepositorio.buscarPorTexto(busquedaNormalizada, pageable);

        // Mapa id→label para resolver parienteLabel sin N+1
        List<Menu> todos = menuRepositorio.findAll();
        Map<Long, String> labelsPorId = todos.stream()
                .collect(Collectors.toMap(Menu::getMenuId, Menu::getLabel, (a, b) -> a));

        return pagina.map(m -> toListaDTO(m, labelsPorId));
    }

    public MenuCrearDTO obtenerMenu(Long id) {
        Menu menu = menuRepositorio.findByIdMenu(id)
                .orElseThrow(() -> new NegocioExcepcion("Menú no encontrado con ID: " + id));
        return toCrearDTO(menu);
    }

    @Transactional
    public MenuListaDTO crearMenu(MenuCrearDTO dto) {
        if (menuRepositorio.existsByLabelIgnoreCase(dto.getLabel())) {
            throw new NegocioExcepcion("Ya existe un menú con la etiqueta: " + dto.getLabel());
        }

        if (dto.getParienteId() != null && dto.getParienteId() > 0) {
            Menu padre = menuRepositorio.findByIdMenu(dto.getParienteId())
                    .orElseThrow(() -> new NegocioExcepcion("Menú padre no encontrado con ID: " + dto.getParienteId()));
            dto.setNivel(padre.getNivel() + 1);
        } else {
            dto.setParienteId(0L);
            dto.setNivel(1);
        }

        Menu menu = new Menu();
        menu.setLabel(dto.getLabel());
        menu.setPath(dto.getPath());
        menu.setIcono(dto.getIcono());
        menu.setParienteId(dto.getParienteId());
        menu.setNivel(dto.getNivel());
        menu.setMenuOrden(dto.getMenuOrden() != null ? dto.getMenuOrden() : 0);
        menu.setActivo(dto.getActivo() != null ? dto.getActivo() : true);

        Menu guardado = menuRepositorio.save(menu);
        return toListaDTO(guardado, Map.of());
    }

    @Transactional
    public MenuListaDTO actualizarMenu(Long id, MenuCrearDTO dto) {
        Menu menu = menuRepositorio.findByIdMenu(id)
                .orElseThrow(() -> new NegocioExcepcion("Menú no encontrado con ID: " + id));

        if (!menu.getLabel().equalsIgnoreCase(dto.getLabel()) &&
            menuRepositorio.existsByLabelIgnoreCaseAndIdNot(dto.getLabel(), id)) {
            throw new NegocioExcepcion("Ya existe otro menú con la etiqueta: " + dto.getLabel());
        }

        if (dto.getParienteId() != null && dto.getParienteId() > 0) {
            if (dto.getParienteId().equals(id)) {
                throw new NegocioExcepcion("Un menú no puede ser su propio padre");
            }
            Menu padre = menuRepositorio.findByIdMenu(dto.getParienteId())
                    .orElseThrow(() -> new NegocioExcepcion("Menú padre no encontrado con ID: " + dto.getParienteId()));
            dto.setNivel(padre.getNivel() + 1);
        } else {
            dto.setParienteId(0L);
            dto.setNivel(1);
        }

        menu.setLabel(dto.getLabel());
        menu.setPath(dto.getPath());
        menu.setIcono(dto.getIcono());
        menu.setParienteId(dto.getParienteId());
        menu.setNivel(dto.getNivel());
        menu.setMenuOrden(dto.getMenuOrden() != null ? dto.getMenuOrden() : 0);
        if (dto.getActivo() != null) {
            menu.setActivo(dto.getActivo());
        }

        Menu actualizado = menuRepositorio.save(menu);
        return toListaDTO(actualizado, Map.of());
    }

    @Transactional
    public void cambiarEstado(Long id, boolean activo) {
        Menu menu = menuRepositorio.findByIdMenu(id)
                .orElseThrow(() -> new NegocioExcepcion("Menú no encontrado con ID: " + id));
        menu.setActivo(activo);
        menuRepositorio.save(menu);
    }

    @Transactional
    public void eliminarMenu(Long id) {
        Menu menu = menuRepositorio.findByIdMenu(id)
                .orElseThrow(() -> new NegocioExcepcion("Menú no encontrado con ID: " + id));

        // Verificar que no tenga hijos
        List<Menu> todos = menuRepositorio.findActivos();
        boolean tieneHijos = todos.stream()
                .anyMatch(h -> h.getParienteId() != null && h.getParienteId().equals(id));
        if (tieneHijos) {
            throw new NegocioExcepcion("No se puede eliminar el menú porque tiene submenús asociados");
        }

        menuRepositorio.delete(menu);
    }

    /** Lista los menús activos (para combos y para el árbol público). */
    public List<MenuListaDTO> listarTodosActivos() {
        return menuRepositorio.findActivos().stream()
                .map(m -> toListaDTO(m, Map.of()))
                .collect(Collectors.toList());
    }

    private MenuListaDTO toListaDTO(Menu menu, Map<Long, String> labelsPorId) {
        String parienteLabel = null;
        if (menu.getParienteId() != null && menu.getParienteId() > 0) {
            parienteLabel = labelsPorId.get(menu.getParienteId());
        }
        return new MenuListaDTO(
                menu.getMenuId(),
                menu.getLabel(),
                menu.getPath(),
                menu.getIcono(),
                menu.getParienteId(),
                parienteLabel,
                menu.getNivel(),
                menu.getMenuOrden(),
                menu.getActivo()
        );
    }

    private MenuCrearDTO toCrearDTO(Menu menu) {
        return new MenuCrearDTO(
                menu.getLabel(),
                menu.getPath(),
                menu.getIcono(),
                menu.getParienteId(),
                menu.getNivel(),
                menu.getMenuOrden(),
                menu.getActivo()
        );
    }
}