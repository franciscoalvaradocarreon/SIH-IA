package mx.sih.servicio;

import mx.sih.excepcion.NegocioExcepcion;
import mx.sih.modelo.dto.RolMenuAsignacionDTO;
import mx.sih.modelo.dto.RolMenuCrearDTO;
import mx.sih.modelo.dto.RolMenuDTO;
import mx.sih.modelo.entidad.Menu;
import mx.sih.modelo.entidad.Rol;
import mx.sih.modelo.entidad.RolMenu;
import mx.sih.repositorio.MenuRepositorio;
import mx.sih.repositorio.RolMenuRepositorio;
import mx.sih.repositorio.RolRepositorio;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Relación rol-menu.
 *
 * Tanto los roles como los menús son globales. La combinación (rol, menu)
 * determina qué menús ve un usuario con ese rol.
 */
@Service
public class RolMenuServicio {

    private final RolMenuRepositorio rolMenuRepositorio;
    private final RolRepositorio rolRepositorio;
    private final MenuRepositorio menuRepositorio;

    public RolMenuServicio(RolMenuRepositorio rolMenuRepositorio,
                           RolRepositorio rolRepositorio,
                           MenuRepositorio menuRepositorio) {
        this.rolMenuRepositorio = rolMenuRepositorio;
        this.rolRepositorio = rolRepositorio;
        this.menuRepositorio = menuRepositorio;
    }

    public Page<RolMenuDTO> listarRolMenu(Pageable pageable) {
        Page<RolMenu> pagina = rolMenuRepositorio.findAllWithRolAndMenu(pageable);
        return pagina.map(this::toDTO);
    }

    public List<RolMenuDTO> listarPorRol(Long rolId) {
        List<RolMenu> lista = rolMenuRepositorio.findByRolId(rolId);
        return lista.stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Transactional
    public RolMenuDTO asignarMenu(RolMenuCrearDTO dto) {
        Rol rol = rolRepositorio.findById(dto.getRolId())
                .orElseThrow(() -> new NegocioExcepcion("Rol no encontrado con ID: " + dto.getRolId()));

        Menu menu = menuRepositorio.findByIdMenu(dto.getMenuId())
                .orElseThrow(() -> new NegocioExcepcion("Menú no encontrado con ID: " + dto.getMenuId()));

        if (rolMenuRepositorio.existsByRol_RolIdAndMenu_MenuId(dto.getRolId(), dto.getMenuId())) {
            throw new NegocioExcepcion("El menú ya está asignado a este rol");
        }

        RolMenu rolMenu = new RolMenu();
        rolMenu.setRol(rol);
        rolMenu.setMenu(menu);

        RolMenu guardado = rolMenuRepositorio.save(rolMenu);
        return toDTO(guardado);
    }

    @Transactional
    public void asignarMenus(RolMenuAsignacionDTO dto) {
        Rol rol = rolRepositorio.findById(dto.getRolId())
                .orElseThrow(() -> new NegocioExcepcion("Rol no encontrado con ID: " + dto.getRolId()));

        Set<Long> idsSolicitados = new HashSet<>(dto.getMenuIds());
        List<Menu> menus = menuRepositorio.findByIdIn(idsSolicitados);
        if (menus.size() != idsSolicitados.size()) {
            throw new NegocioExcepcion("menu_no_encontrado",
                    "Uno o más menús no existen.");
        }

        // Reemplazar el set completo del rol
        rolMenuRepositorio.deleteByRolId(dto.getRolId());
        rolMenuRepositorio.flush();

        for (Menu menu : menus) {
            RolMenu rolMenu = new RolMenu();
            rolMenu.setRol(rol);
            rolMenu.setMenu(menu);
            rolMenuRepositorio.save(rolMenu);
        }
    }

    @Transactional
    public void desasignarMenu(Long rolId, Long menuId) {
        rolMenuRepositorio.deleteByRolIdAndMenuIds(rolId, List.of(menuId));
    }

    @Transactional
    public void eliminarPorRol(Long rolId) {
        rolMenuRepositorio.deleteByRolId(rolId);
    }

    private RolMenuDTO toDTO(RolMenu rolMenu) {
        return new RolMenuDTO(
                rolMenu.getRol().getRolId(),
                rolMenu.getRol().getNombre(),
                rolMenu.getMenu().getMenuId(),
                rolMenu.getMenu().getLabel(),
                rolMenu.getMenu().getPath()
        );
    }
}