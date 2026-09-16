package mx.sih.repositorio;

import mx.sih.modelo.entidad.Rol;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface RolRepositorio extends JpaRepository<Rol, Long> {

    @Query("SELECT r FROM Rol r WHERE LOWER(r.nombre) LIKE LOWER(CONCAT('%', :busqueda, '%')) ORDER BY r.nombre ASC")
    Page<Rol> buscarPorNombre(@Param("busqueda") String busqueda, Pageable pageable);

    boolean existsByNombreIgnoreCase(String nombre);

    @Query("SELECT CASE WHEN COUNT(r) > 0 THEN true ELSE false END " +
           "FROM Rol r WHERE LOWER(r.nombre) = LOWER(:nombre) AND r.rolId != :id")
    boolean existsByNombreIgnoreCaseAndIdNot(@Param("nombre") String nombre, @Param("id") Long id);

    @Query("SELECT COUNT(uer) FROM UsuarioEscuelaRol uer WHERE uer.rol.rolId = :rolId")
    long tieneUsuariosAsignados(@Param("rolId") Long rolId);
}