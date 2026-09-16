package mx.sih.repositorio;

import mx.sih.modelo.entidad.Escuela;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;

@Repository
public interface EscuelaRepositorio extends JpaRepository<Escuela, Long> {

    @Query("SELECT e FROM Escuela e WHERE LOWER(e.nombre) LIKE LOWER(CONCAT('%', :busqueda, '%')) " +
           "OR LOWER(e.clave) LIKE LOWER(CONCAT('%', :busqueda, '%')) " +
           "ORDER BY e.nombre ASC")
    Page<Escuela> buscarPorNombre(@Param("busqueda") String busqueda, Pageable pageable);

    /**
     * Listado paginado restringido a un conjunto concreto de escuelas (las del usuario).
     * Con busqueda = "" devuelve todas las escuelas a las que el usuario tiene acceso.
     */
    @Query("SELECT e FROM Escuela e WHERE e.escuelaId IN :ids " +
           "AND (LOWER(e.nombre) LIKE LOWER(CONCAT('%', :busqueda, '%')) " +
           "OR LOWER(e.clave) LIKE LOWER(CONCAT('%', :busqueda, '%'))) " +
           "ORDER BY e.nombre ASC")
    Page<Escuela> buscarPorIds(@Param("ids") Collection<Long> ids,
                               @Param("busqueda") String busqueda,
                               Pageable pageable);

    boolean existsByClave(String clave);

    @Query("SELECT CASE WHEN COUNT(e) > 0 THEN true ELSE false END " +
           "FROM Escuela e WHERE e.clave = :clave AND e.escuelaId != :id")
    boolean existsByClaveAndIdNot(@Param("clave") String clave, @Param("id") Long id);
}
