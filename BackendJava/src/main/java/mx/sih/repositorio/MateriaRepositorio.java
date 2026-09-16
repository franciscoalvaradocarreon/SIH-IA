package mx.sih.repositorio;

import mx.sih.modelo.entidad.Materia;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MateriaRepositorio extends JpaRepository<Materia, Long> {

    @Query("SELECT m FROM Materia m WHERE m.escuela.escuelaId = :escuelaId " +
           "AND (LOWER(m.nombre) LIKE LOWER(CONCAT('%', :busqueda, '%')) " +
           "OR LOWER(m.clave) LIKE LOWER(CONCAT('%', :busqueda, '%'))) " +
           "ORDER BY m.nombre ASC")
    Page<Materia> buscarPorEscuelaYTexto(@Param("escuelaId") Long escuelaId,
                                         @Param("busqueda") String busqueda,
                                         Pageable pageable);

    @Query("SELECT CASE WHEN COUNT(m) > 0 THEN true ELSE false END " +
           "FROM Materia m WHERE m.clave = :clave AND m.escuela.escuelaId = :escuelaId")
    boolean existsByClaveAndEscuelaId(@Param("clave") String clave,
                                      @Param("escuelaId") Long escuelaId);

    @Query("SELECT CASE WHEN COUNT(m) > 0 THEN true ELSE false END " +
           "FROM Materia m WHERE m.clave = :clave AND m.escuela.escuelaId = :escuelaId AND m.materiaId != :id")
    boolean existsByClaveAndEscuelaIdAndIdNot(@Param("clave") String clave,
                                              @Param("escuelaId") Long escuelaId,
                                              @Param("id") Long id);

    @Query("SELECT m FROM Materia m " +
           "JOIN FETCH m.semestre s " +
           "JOIN FETCH m.turno t " +
           "WHERE m.materiaId = :id AND m.escuela.escuelaId = :escuelaId")
    Optional<Materia> findByIdAndEscuelaId(@Param("id") Long id,
                                            @Param("escuelaId") Long escuelaId);
    
    @Query("SELECT m FROM Materia m " +
           "JOIN FETCH m.escuela e " +
           "JOIN FETCH m.semestre s " +
           "WHERE e.escuelaId = :escuelaId " +
           "AND (LOWER(m.nombre) LIKE LOWER(CONCAT('%', :busqueda, '%')) " +
           "OR LOWER(m.clave) LIKE LOWER(CONCAT('%', :busqueda, '%'))) " +
           "AND (:semestreId IS NULL OR s.semestreId = :semestreId) " +
           "ORDER BY m.nombre ASC")
    Page<Materia> buscarPorEscuelaYTextoYSemestreId(@Param("escuelaId") Long escuelaId,
                                                     @Param("busqueda") String busqueda,
                                                     @Param("semestreId") Long semestreId,
                                                     Pageable pageable);

    @Query("SELECT m FROM Materia m " +
           "JOIN FETCH m.semestre s " +
           "JOIN FETCH m.turno t " +
           "WHERE m.escuela.escuelaId = :escuelaId " +
           "AND (LOWER(m.nombre) LIKE LOWER(CONCAT('%', :busqueda, '%')) " +
           "OR LOWER(m.clave) LIKE LOWER(CONCAT('%', :busqueda, '%'))) " +
           "AND (:semestreId IS NULL OR s.semestreId = :semestreId) " +
           "AND (:turnoId IS NULL OR t.turnoId = :turnoId) " +
           "ORDER BY m.nombre ASC")
    Page<Materia> buscarPorEscuelaYFiltros(@Param("escuelaId") Long escuelaId,
                                            @Param("busqueda") String busqueda,
                                            @Param("semestreId") Long semestreId,
                                            @Param("turnoId") Long turnoId,
                                            Pageable pageable);


    @Query("SELECT CASE WHEN COUNT(m) > 0 THEN true ELSE false END " +
           "FROM Materia m " +
           "WHERE LOWER(m.clave) = LOWER(:clave) " +
           "AND m.semestre.semestreId = :semestreId " +
           "AND m.turno.turnoId = :turnoId")
    boolean existsByClaveAndSemestreIdAndTurnoId(@Param("clave") String clave,
                                                  @Param("semestreId") Long semestreId,
                                                  @Param("turnoId") Long turnoId);

    @Query("SELECT CASE WHEN COUNT(m) > 0 THEN true ELSE false END " +
           "FROM Materia m " +
           "WHERE LOWER(m.clave) = LOWER(:clave) " +
           "AND m.semestre.semestreId = :semestreId " +
           "AND m.turno.turnoId = :turnoId " +
           "AND m.materiaId != :id")
    boolean existsByClaveAndSemestreIdAndTurnoIdAndIdNot(@Param("clave") String clave,
                                                          @Param("semestreId") Long semestreId,
                                                          @Param("turnoId") Long turnoId,
                                                          @Param("id") Long id);
}