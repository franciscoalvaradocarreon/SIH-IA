package mx.sih.repositorio;

import java.util.List;
import mx.sih.modelo.entidad.Aula;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AulaRepositorio extends JpaRepository<Aula, Long> {

    @Query("SELECT a FROM Aula a WHERE a.escuela.escuelaId = :escuelaId " +
           "AND (LOWER(a.nombre) LIKE LOWER(CONCAT('%', :busqueda, '%')) " +
           "OR LOWER(a.edificio) LIKE LOWER(CONCAT('%', :busqueda, '%')) " +
           "OR LOWER(a.piso) LIKE LOWER(CONCAT('%', :busqueda, '%'))) " +
           "ORDER BY a.edificio ASC, a.piso ASC, a.nombre ASC")
    Page<Aula> buscarPorEscuelaYTexto(@Param("escuelaId") Long escuelaId,
                                      @Param("busqueda") String busqueda,
                                      Pageable pageable);

    @Query("SELECT a FROM Aula a " +
           "JOIN FETCH a.turno t " +
           "WHERE a.aulaId = :id AND a.escuela.escuelaId = :escuelaId")
    Optional<Aula> findByIdAndEscuelaId(@Param("id") Long id,
                                         @Param("escuelaId") Long escuelaId);
    
    @Query("SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END " +
           "FROM Aula a WHERE a.escuela.escuelaId = :escuelaId " +
           "AND LOWER(a.nombre) = LOWER(:nombre) AND a.aulaId != :id")
    boolean existsByEscuelaIdAndNombreIgnoreCaseAndIdNot(@Param("escuelaId") Long escuelaId,
                                                          @Param("nombre") String nombre,
                                                          @Param("id") Long id);

    @Query("SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END " +
           "FROM Aula a WHERE a.escuela.escuelaId = :escuelaId " +
           "AND LOWER(a.nombre) = LOWER(:nombre)")
    boolean existsByEscuelaIdAndNombreIgnoreCase(@Param("escuelaId") Long escuelaId,
                                                  @Param("nombre") String nombre);

    
    @Query("SELECT a FROM Aula a WHERE a.escuela.escuelaId = :escuelaId " +
           "AND (LOWER(a.nombre) LIKE LOWER(CONCAT('%', :busqueda, '%')) " +
           "OR LOWER(a.edificio) LIKE LOWER(CONCAT('%', :busqueda, '%')) " +
           "OR LOWER(a.piso) LIKE LOWER(CONCAT('%', :busqueda, '%'))) " +
           "AND a.semestre.semestreId = :semestreId " +
           "ORDER BY a.edificio ASC, a.piso ASC, a.nombre ASC")
    Page<Aula> buscarPorEscuelaYTextoYSemestre(@Param("escuelaId") Long escuelaId,
                                      @Param("busqueda") String busqueda,
                                      Pageable pageable,
                                      @Param("semestreId") Long semestreId);

    // Listar aulas activas de un semestre (para combos)
    @Query("SELECT a FROM Aula a " +
           "JOIN FETCH a.turno t " +
           "WHERE a.escuela.escuelaId = :escuelaId " +
           "AND a.activo = true " +
           "AND a.semestre.semestreId = :semestreId " +
           "ORDER BY a.nombre ASC")
    List<Aula> findByEscuelaIdAndActivoTrueAndSemestreId(@Param("escuelaId") Long escuelaId,
                                                          @Param("semestreId") Long semestreId);

        /**
     * Listado paginado con filtros opcionales (semestre + turno).
     * Hace JOIN FETCH a turno para evitar N+1.
     */
    @Query("SELECT a FROM Aula a " +
           "JOIN FETCH a.turno t " +
           "JOIN FETCH a.semestre s " +
           "WHERE a.escuela.escuelaId = :escuelaId " +
           "AND (LOWER(a.nombre) LIKE LOWER(CONCAT('%', :busqueda, '%')) " +
           "OR LOWER(a.edificio) LIKE LOWER(CONCAT('%', :busqueda, '%')) " +
           "OR LOWER(a.piso) LIKE LOWER(CONCAT('%', :busqueda, '%'))) " +
           "AND (:semestreId IS NULL OR s.semestreId = :semestreId) " +
           "AND (:turnoId IS NULL OR t.turnoId = :turnoId) " +
           "ORDER BY a.edificio ASC, a.piso ASC, a.nombre ASC")
    Page<Aula> buscarPorEscuelaYFiltros(@Param("escuelaId") Long escuelaId,
                                         @Param("busqueda") String busqueda,
                                         @Param("semestreId") Long semestreId,
                                         @Param("turnoId") Long turnoId,
                                         Pageable pageable);
    
    
    @Query("SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END " +
           "FROM Aula a " +
           "WHERE LOWER(a.nombre) = LOWER(:nombre) " +
           "AND a.semestre.semestreId = :semestreId " +
           "AND a.turno.turnoId = :turnoId")
    boolean existsByNombreAndSemestreIdAndTurnoId(@Param("nombre") String nombre,
                                                   @Param("semestreId") Long semestreId,
                                                   @Param("turnoId") Long turnoId);

    @Query("SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END " +
           "FROM Aula a " +
           "WHERE LOWER(a.nombre) = LOWER(:nombre) " +
           "AND a.semestre.semestreId = :semestreId " +
           "AND a.turno.turnoId = :turnoId " +
           "AND a.aulaId != :id")
    boolean existsByNombreAndSemestreIdAndTurnoIdAndIdNot(@Param("nombre") String nombre,
                                                           @Param("semestreId") Long semestreId,
                                                           @Param("turnoId") Long turnoId,
                                                           @Param("id") Long id);

    /** Cuenta aulas que referencian a un turno. */
    @Query("SELECT COUNT(a) FROM Aula a WHERE a.turno.turnoId = :turnoId")
    long countByTurnoId(@Param("turnoId") Long turnoId);
}