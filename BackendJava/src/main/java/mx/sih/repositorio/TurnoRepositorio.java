package mx.sih.repositorio;

import mx.sih.modelo.entidad.Turno;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TurnoRepositorio extends JpaRepository<Turno, Long> {

    @Query("SELECT t FROM Turno t WHERE t.escuela.escuelaId = :escuelaId " +
           "AND LOWER(t.nombre) LIKE LOWER(CONCAT('%', :busqueda, '%')) " +
           "ORDER BY t.nombre ASC")
    Page<Turno> buscarPorEscuelaYNombre(@Param("escuelaId") Long escuelaId,
                                        @Param("busqueda") String busqueda,
                                        Pageable pageable);

    @Query("SELECT t FROM Turno t WHERE t.escuela.escuelaId = :escuelaId " +
           "AND LOWER(t.nombre) = LOWER(:nombre)")
    Optional<Turno> findByEscuelaIdAndNombreIgnoreCase(@Param("escuelaId") Long escuelaId,
                                                        @Param("nombre") String nombre);

    @Query("SELECT CASE WHEN COUNT(t) > 0 THEN true ELSE false END " +
           "FROM Turno t WHERE t.escuela.escuelaId = :escuelaId " +
           "AND LOWER(t.nombre) = LOWER(:nombre) AND t.turnoId != :id")
    boolean existsByEscuelaIdAndNombreIgnoreCaseAndIdNot(@Param("escuelaId") Long escuelaId,
                                                          @Param("nombre") String nombre,
                                                          @Param("id") Long id);
    @Query("SELECT t FROM Turno t " +
           "JOIN FETCH t.escuela e " +
           "LEFT JOIN FETCH t.semestre s " +
           "WHERE e.escuelaId = :escuelaId " +
           "AND (LOWER(t.nombre) LIKE LOWER(CONCAT('%', :busqueda, '%')) " +
           "OR LOWER(t.descripcion) LIKE LOWER(CONCAT('%', :busqueda, '%'))) " +
           "AND (s.semestreId = :semestreId OR :semestreId IS NULL) " +  // 🔥 Filtro por semestre
           "ORDER BY t.turnoId DESC")
    Page<Turno> findByEscuelaIdAndSemestreIdAndBusqueda(@Param("escuelaId") Long escuelaId,
                                                         @Param("semestreId") Long semestreId,
                                                         @Param("busqueda") String busqueda,
                                                         Pageable pageable);

    // Buscar por escuela con búsqueda (sin filtro de semestre)
    @Query("SELECT t FROM Turno t " +
           "JOIN FETCH t.escuela e " +
           "LEFT JOIN FETCH t.semestre s " +
           "WHERE e.escuelaId = :escuelaId " +
           "AND (LOWER(t.nombre) LIKE LOWER(CONCAT('%', :busqueda, '%')) " +
           "OR LOWER(t.descripcion) LIKE LOWER(CONCAT('%', :busqueda, '%'))) " +
           "ORDER BY t.turnoId DESC")
    Page<Turno> findByEscuelaIdAndBusqueda(@Param("escuelaId") Long escuelaId,
                                            @Param("busqueda") String busqueda,
                                            Pageable pageable);


    // * Buscar turno por ID y escuela*
    @Query("SELECT t FROM Turno t " +
           "JOIN FETCH t.escuela e " +
           "LEFT JOIN FETCH t.semestre s " +
           "WHERE t.turnoId = :id AND e.escuelaId = :escuelaId")
    Optional<Turno> findByIdAndEscuelaId(@Param("id") Long id,
                                          @Param("escuelaId") Long escuelaId);

}