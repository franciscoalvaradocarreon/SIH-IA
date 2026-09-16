package mx.sih.servicio;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import mx.sih.modelo.dto.ClaimsUsuario;
import mx.sih.modelo.dto.MenuDTO;
import mx.sih.modelo.entidad.Menu;
import mx.sih.repositorio.MenuRepositorio;
import mx.sih.repositorio.UsuarioEscuelaRolRepositorio;
import mx.sih.seguridad.contexto.EscuelaContexto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * Construcción del árbol de menús del usuario.
 *
 * Los menús son GLOBALES: no dependen de la escuela activa.
 * El único filtro es por ROL, y los roles se resuelven vía la relación
 * usuario_escuela_rol (que sí es específica de cada escuela).
 */
@Service
public class MenuServicio {

    private static final Logger logger = LoggerFactory.getLogger(MenuServicio.class);

    private final MenuRepositorio menuRepositorio;
    private final UsuarioEscuelaRolRepositorio usuarioEscuelaRolRepositorio;

    public MenuServicio(MenuRepositorio menuRepositorio,
                        UsuarioEscuelaRolRepositorio usuarioEscuelaRolRepositorio) {
        this.menuRepositorio = menuRepositorio;
        this.usuarioEscuelaRolRepositorio = usuarioEscuelaRolRepositorio;
    }

    public List<MenuDTO> obtenerMenuPorUsuario() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        var claims = (ClaimsUsuario) authentication.getPrincipal();
        var usuarioId = claims.usuarioId();
        var escuelaId = EscuelaContexto.getEscuelaId();

        if (escuelaId == null) {
            logger.debug("Sin escuela activa: no se puede resolver el menú");
            return List.of();
        }

        // Los roles SÍ dependen de la escuela activa:
        // un usuario puede ser ADMIN en una escuela y MAESTRO en otra.
        var rolesIds = obtenerRolesDeUsuario(usuarioId, escuelaId);
        if (rolesIds.isEmpty()) {
            logger.debug("Usuario {} sin roles en escuela {}: menú vacío", usuarioId, escuelaId);
            return List.of();
        }

        var menus = menuRepositorio.findMenusByRoles(rolesIds);
        return construirArbol(menus);
    }

    private List<Long> obtenerRolesDeUsuario(Long usuarioId, Long escuelaId) {
        return usuarioEscuelaRolRepositorio
                .findByUsuarioIdAndEscuelaIdAndActivoTrue(usuarioId, escuelaId)
                .stream()
                .map(rel -> rel.getRol().getRolId())
                .toList();
    }

    private List<MenuDTO> construirArbol(List<Menu> menus) {
        // Normalizar parienteId: null -> 0
        menus.forEach(m -> {
            if (m.getParienteId() == null) {
                m.setParienteId(0L);
            }
        });

        Map<Long, List<Menu>> menuPorPadre = menus.stream()
                .collect(Collectors.groupingBy(Menu::getParienteId));

        List<Menu> raices = new ArrayList<>(menuPorPadre.getOrDefault(0L, Collections.emptyList()));
        raices.sort(Comparator.comparing(Menu::getMenuOrden));

        return raices.stream()
                .map(menu -> convertirAMenuDTO(menu, menuPorPadre))
                .toList();
    }

    private MenuDTO convertirAMenuDTO(Menu menu, Map<Long, List<Menu>> menuPorPadre) {
        List<Menu> hijos = new ArrayList<>(menuPorPadre.getOrDefault(menu.getMenuId(), Collections.emptyList()));
        hijos.sort(Comparator.comparing(Menu::getMenuOrden));

        List<MenuDTO> hijosDTO = hijos.stream()
                .map(hijo -> convertirAMenuDTO(hijo, menuPorPadre))
                .toList();

        return new MenuDTO(
                menu.getMenuId(),
                menu.getLabel(),
                menu.getPath(),
                menu.getIcono(),
                hijosDTO
        );
    }
}