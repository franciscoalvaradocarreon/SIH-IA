package mx.sih.repositorio;

import java.util.List;
import mx.sih.modelo.entidad.Especialidad;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EspecialidadRepositorio extends JpaRepository<Especialidad, Long> {

    @Query("SELECT e FROM Especialidad e WHERE LOWER(e.nombre) LIKE LOWER(CONCAT('%', :busqueda, '%')) ORDER BY e.nombre ASC")
    Page<Especialidad> buscarPorNombre(@Param("busqueda") String busqueda, Pageable pageable);

    Optional<Especialidad> findByNombreIgnoreCase(String nombre);
    
        @Query("SELECT e FROM Especialidad e " +
           "JOIN FETCH e.semestre s " +
           "WHERE s.escuela.escuelaId = :escuelaId " +
           "AND (LOWER(e.nombre) LIKE LOWER(CONCAT('%', :busqueda, '%')) " +
           "OR LOWER(s.nombre) LIKE LOWER(CONCAT('%', :busqueda, '%'))) " +
           "AND (:semestreId IS NULL OR s.semestreId = :semestreId) " +
           "ORDER BY e.especialidadId DESC")
    Page<Especialidad> findByEscuelaIdAndBusquedaAndSemestreId(@Param("escuelaId") Long escuelaId,
                                                                @Param("busqueda") String busqueda,
                                                                @Param("semestreId") Long semestreId,
                                                                Pageable pageable);

    @Query("SELECT e FROM Especialidad e " +
           "JOIN FETCH e.semestre s " +
           "WHERE e.especialidadId = :id AND s.escuela.escuelaId = :escuelaId")
    Optional<Especialidad> findByIdAndEscuelaId(@Param("id") Long id,
                                                 @Param("escuelaId") Long escuelaId);

    @Query("SELECT e FROM Especialidad e " +
           "JOIN FETCH e.semestre s " +
           "WHERE s.escuela.escuelaId = :escuelaId " +
           "ORDER BY e.nombre ASC")
    List<Especialidad> findByEscuelaId(@Param("escuelaId") Long escuelaId);
    
    /**
     * Listado paginado filtrando por escuela (vía semestre) y opcionalmente por semestre y turno.
     * Hace JOIN FETCH a semestre y turno para evitar N+1 al mapear el DTO.
     */
    @Query("SELECT e FROM Especialidad e " +
           "JOIN FETCH e.semestre s " +
           "JOIN FETCH e.turno t " +
           "WHERE s.escuela.escuelaId = :escuelaId " +
           "AND (LOWER(e.nombre) LIKE LOWER(CONCAT('%', :busqueda, '%')) " +
           "     OR LOWER(s.nombre) LIKE LOWER(CONCAT('%', :busqueda, '%')) " +
           "     OR LOWER(t.nombre) LIKE LOWER(CONCAT('%', :busqueda, '%'))) " +
           "AND (:semestreId IS NULL OR s.semestreId = :semestreId) " +
           "AND (:turnoId IS NULL OR t.turnoId = :turnoId) " +
           "ORDER BY e.especialidadId DESC")
    Page<Especialidad> buscarPorEscuelaYFiltros(
            @Param("escuelaId") Long escuelaId,
            @Param("busqueda") String busqueda,
            @Param("semestreId") Long semestreId,
            @Param("turnoId") Long turnoId,
            Pageable pageable);

    /**
     * Verificar duplicado por (semestre_id, turno_id, nombre) al crear.
     * Refleja el constraint unique "Semestre_turno_nombre".
     */
    @Query("SELECT CASE WHEN COUNT(e) > 0 THEN true ELSE false END " +
           "FROM Especialidad e " +
           "WHERE e.semestre.semestreId = :semestreId " +
           "AND e.turno.turnoId = :turnoId " +
           "AND LOWER(e.nombre) = LOWER(:nombre)")
    boolean existsBySemestreTurnoNombre(@Param("semestreId") Long semestreId,
                                         @Param("turnoId") Long turnoId,
                                         @Param("nombre") String nombre);

    /**
     * Verificar duplicado excluyendo el propio registro (para edición).
     */
    @Query("SELECT CASE WHEN COUNT(e) > 0 THEN true ELSE false END " +
           "FROM Especialidad e " +
           "WHERE e.semestre.semestreId = :semestreId " +
           "AND e.turno.turnoId = :turnoId " +
           "AND LOWER(e.nombre) = LOWER(:nombre) " +
           "AND e.especialidadId != :id")
    boolean existsBySemestreTurnoNombreAndIdNot(@Param("semestreId") Long semestreId,
                                                 @Param("turnoId") Long turnoId,
                                                 @Param("nombre") String nombre,
                                                 @Param("id") Long id);

    /**
     * Especialidades de un turno específico (útil para combos filtrados por turno).
     */
    @Query("SELECT e FROM Especialidad e " +
           "JOIN FETCH e.semestre s " +
           "JOIN FETCH e.turno t " +
           "WHERE s.escuela.escuelaId = :escuelaId " +
           "AND t.turnoId = :turnoId " +
           "ORDER BY e.nombre ASC")
    List<Especialidad> findByEscuelaIdAndTurnoId(@Param("escuelaId") Long escuelaId,
                                                  @Param("turnoId") Long turnoId);

}