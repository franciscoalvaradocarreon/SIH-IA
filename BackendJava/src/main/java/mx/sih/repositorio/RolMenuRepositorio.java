package mx.sih.repositorio;

import mx.sih.modelo.entidad.RolMenu;
import mx.sih.modelo.entidad.RolMenuId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RolMenuRepositorio extends JpaRepository<RolMenu, RolMenuId> {

    @Query("SELECT rm FROM RolMenu rm " +
           "JOIN FETCH rm.rol r " +
           "JOIN FETCH rm.menu m " +
           "ORDER BY r.nombre ASC, m.menuOrden ASC")
    Page<RolMenu> findAllWithRolAndMenu(Pageable pageable);

    @Query("SELECT rm FROM RolMenu rm " +
           "JOIN FETCH rm.rol r " +
           "JOIN FETCH rm.menu m " +
           "WHERE r.rolId = :rolId")
    List<RolMenu> findByRolId(@Param("rolId") Long rolId);

    @Modifying
    @Query("DELETE FROM RolMenu rm WHERE rm.rol.rolId = :rolId")
    void deleteByRolId(@Param("rolId") Long rolId);

    @Modifying
    @Query("DELETE FROM RolMenu rm WHERE rm.rol.rolId = :rolId AND rm.menu.menuId IN :menuIds")
    void deleteByRolIdAndMenuIds(@Param("rolId") Long rolId,
                                 @Param("menuIds") List<Long> menuIds);

    boolean existsByRol_RolIdAndMenu_MenuId(Long rolId, Long menuId);
}