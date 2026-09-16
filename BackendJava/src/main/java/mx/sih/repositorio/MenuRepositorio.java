package mx.sih.repositorio;

import mx.sih.modelo.entidad.Menu;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface MenuRepositorio extends JpaRepository<Menu, Long> {

    /**
     * Menús que corresponden a alguno de los roles del usuario.
     * Los menús son globales; el filtro real es por rol vía rol_menu.
     */
    @Query("SELECT DISTINCT m FROM Menu m " +
           "JOIN RolMenu rm ON rm.menu = m " +
           "WHERE rm.rol.rolId IN :rolesIds " +
           "AND m.activo = true " +
           "ORDER BY m.menuOrden ASC")
    List<Menu> findMenusByRoles(@Param("rolesIds") List<Long> rolesIds);

    /** Menús activos, sin filtro por escuela (globales). */
    @Query("SELECT m FROM Menu m " +
           "WHERE m.activo = true " +
           "ORDER BY m.nivel ASC, m.menuOrden ASC")
    List<Menu> findActivos();

    @Query("SELECT m FROM Menu m " +
           "WHERE (LOWER(m.label) LIKE LOWER(CONCAT('%', :busqueda, '%')) " +
           "OR LOWER(m.path) LIKE LOWER(CONCAT('%', :busqueda, '%'))) " +
           "ORDER BY m.nivel ASC, m.menuOrden ASC")
    Page<Menu> buscarPorTexto(@Param("busqueda") String busqueda,
                              Pageable pageable);

    @Query("SELECT m FROM Menu m WHERE m.menuId = :id")
    Optional<Menu> findByIdMenu(@Param("id") Long id);

    @Query("SELECT m FROM Menu m WHERE m.menuId IN :ids")
    List<Menu> findByIdIn(@Param("ids") Collection<Long> ids);

    @Query("SELECT CASE WHEN COUNT(m) > 0 THEN true ELSE false END " +
           "FROM Menu m WHERE LOWER(m.label) = LOWER(:label)")
    boolean existsByLabelIgnoreCase(@Param("label") String label);

    @Query("SELECT CASE WHEN COUNT(m) > 0 THEN true ELSE false END " +
           "FROM Menu m WHERE LOWER(m.label) = LOWER(:label) AND m.menuId != :id")
    boolean existsByLabelIgnoreCaseAndIdNot(@Param("label") String label,
                                            @Param("id") Long id);
}