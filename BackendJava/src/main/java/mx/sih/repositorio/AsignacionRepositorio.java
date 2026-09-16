package mx.sih.repositorio;

import mx.sih.modelo.entidad.Asignacion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AsignacionRepositorio extends JpaRepository<Asignacion, Long> {

        @Query("SELECT a FROM Asignacion a " +
           "JOIN FETCH a.grupo g " +
           "JOIN FETCH a.materia m " +
           "JOIN FETCH a.maestro ma " +
           "JOIN FETCH a.aula au " +
           "WHERE a.escuela.escuelaId = :escuelaId " +
           "AND (LOWER(g.nombre) LIKE LOWER(CONCAT('%', :busqueda, '%')) " +
           "OR LOWER(m.nombre) LIKE LOWER(CONCAT('%', :busqueda, '%')) " +
           "OR LOWER(m.clave) LIKE LOWER(CONCAT('%', :busqueda, '%')) " +
           "OR LOWER(ma.nombre) LIKE LOWER(CONCAT('%', :busqueda, '%')) " +
           "OR LOWER(ma.apellidos) LIKE LOWER(CONCAT('%', :busqueda, '%')) " +
           "OR LOWER(au.nombre) LIKE LOWER(CONCAT('%', :busqueda, '%'))) " +
           "ORDER BY g.nombre ASC, m.nombre ASC")
    Page<Asignacion> buscarPorEscuelaYTexto(@Param("escuelaId") Long escuelaId,
                                             @Param("busqueda") String busqueda,
                                             Pageable pageable);

   
    // Filtrar por especialidad
    @Query("SELECT a FROM Asignacion a " +
           "JOIN FETCH a.grupo g " +
           "JOIN FETCH a.materia m " +
           "JOIN FETCH a.maestro ma " +
           "JOIN FETCH a.aula au " +
           "WHERE a.escuela.escuelaId = :escuelaId " +
           "AND g.especialidad.especialidadId = :especialidadId " +
           "AND (LOWER(g.nombre) LIKE LOWER(CONCAT('%', :busqueda, '%')) " +
           "OR LOWER(m.nombre) LIKE LOWER(CONCAT('%', :busqueda, '%')) " +
           "OR LOWER(m.clave) LIKE LOWER(CONCAT('%', :busqueda, '%')) " +
           "OR LOWER(ma.nombre) LIKE LOWER(CONCAT('%', :busqueda, '%')) " +
           "OR LOWER(ma.apellidos) LIKE LOWER(CONCAT('%', :busqueda, '%')) " +
           "OR LOWER(au.nombre) LIKE LOWER(CONCAT('%', :busqueda, '%'))) " +
           "ORDER BY g.nombre ASC, m.nombre ASC")
    Page<Asignacion> buscarPorEscuelaYEspecialidad(@Param("escuelaId") Long escuelaId,
                                                    @Param("busqueda") String busqueda,
                                                    @Param("especialidadId") Long especialidadId,
                                                    Pageable pageable);
    
    
    @Query("SELECT a FROM Asignacion a " +
           "JOIN FETCH a.grupo g " +
           "JOIN FETCH a.materia m " +
           "JOIN FETCH a.maestro ma " +
           "JOIN FETCH a.aula au " +
           "WHERE a.asignacionId = :id " +
           "AND a.escuela.escuelaId = :escuelaId")
    Optional<Asignacion> findByIdAndEscuelaId(@Param("id") Long id,
                                               @Param("escuelaId") Long escuelaId);

    @Query("SELECT a FROM Asignacion a " +
           "WHERE a.grupo.grupoId = :grupoId " +
           "AND a.activo = true")
    List<Asignacion> findByGrupoId(@Param("grupoId") Long grupoId);

    @Query("SELECT a FROM Asignacion a " +
           "WHERE a.maestro.maestroId = :maestroId " +
           "AND a.activo = true")
    List<Asignacion> findByMaestroId(@Param("maestroId") Long maestroId);

    @Query("SELECT a FROM Asignacion a " +
           "WHERE a.aula.aulaId = :aulaId " +
           "AND a.activo = true")
    List<Asignacion> findByAulaId(@Param("aulaId") Long aulaId);

    @Query("SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END " +
           "FROM Asignacion a " +
           "WHERE a.grupo.grupoId = :grupoId " +
           "AND a.materia.materiaId = :materiaId")
    boolean existsByGrupoIdAndMateriaId(@Param("grupoId") Long grupoId,
                                         @Param("materiaId") Long materiaId);

    @Query("SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END " +
           "FROM Asignacion a " +
           "WHERE a.grupo.grupoId = :grupoId " +
           "AND a.materia.materiaId = :materiaId " +
           "AND a.asignacionId != :id")
    boolean existsByGrupoIdAndMateriaIdAndIdNot(@Param("grupoId") Long grupoId,
                                                 @Param("materiaId") Long materiaId,
                                                 @Param("id") Long id);

   // Filtrar por especialidad y semestre
    @Query("SELECT a FROM Asignacion a " +
           "JOIN FETCH a.grupo g " +
           "JOIN FETCH a.materia m " +
           "JOIN FETCH a.maestro ma " +
           "JOIN FETCH a.aula au " +
           "WHERE a.escuela.escuelaId = :escuelaId " +
           "AND a.semestre.semestreId = :semestreId " +
           "AND g.especialidad.especialidadId = :especialidadId " +
           "AND (LOWER(g.nombre) LIKE LOWER(CONCAT('%', :busqueda, '%')) " +
           "OR LOWER(m.nombre) LIKE LOWER(CONCAT('%', :busqueda, '%')) " +
           "OR LOWER(m.clave) LIKE LOWER(CONCAT('%', :busqueda, '%')) " +
           "OR LOWER(ma.nombre) LIKE LOWER(CONCAT('%', :busqueda, '%')) " +
           "OR LOWER(ma.apellidos) LIKE LOWER(CONCAT('%', :busqueda, '%')) " +
           "OR LOWER(au.nombre) LIKE LOWER(CONCAT('%', :busqueda, '%'))) " +
           "ORDER BY g.nombre ASC, m.nombre ASC")
    Page<Asignacion> buscarPorEscuelaYSemestreYEspecialidad(
            @Param("escuelaId") Long escuelaId,
            @Param("semestreId") Long semestreId,
            @Param("especialidadId") Long especialidadId,
            @Param("busqueda") String busqueda,
            Pageable pageable);

    @Query("SELECT a FROM Asignacion a " +
           "JOIN FETCH a.grupo g " +
           "JOIN FETCH a.materia m " +
           "JOIN FETCH a.maestro ma " +
           "JOIN FETCH a.aula au " +
           "WHERE a.escuela.escuelaId = :escuelaId " +
           "AND a.semestre.semestreId = :semestreId " +
           "AND (LOWER(g.nombre) LIKE LOWER(CONCAT('%', :busqueda, '%')) " +
           "OR LOWER(m.nombre) LIKE LOWER(CONCAT('%', :busqueda, '%')) " +
           "OR LOWER(m.clave) LIKE LOWER(CONCAT('%', :busqueda, '%')) " +
           "OR LOWER(ma.nombre) LIKE LOWER(CONCAT('%', :busqueda, '%')) " +
           "OR LOWER(ma.apellidos) LIKE LOWER(CONCAT('%', :busqueda, '%')) " +
           "OR LOWER(au.nombre) LIKE LOWER(CONCAT('%', :busqueda, '%'))) " +
           "ORDER BY g.nombre ASC, m.nombre ASC")
    Page<Asignacion> buscarPorEscuelaYSemestreYTexto(
            @Param("escuelaId") Long escuelaId,
            @Param("semestreId") Long semestreId,
            @Param("busqueda") String busqueda,
            Pageable pageable);

    // Verificar duplicado (grupo + materia) en un semestre
    @Query("SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END " +
           "FROM Asignacion a " +
           "WHERE a.grupo.grupoId = :grupoId " +
           "AND a.materia.materiaId = :materiaId " +
           "AND a.semestre.semestreId = :semestreId")
    boolean existsByGrupoIdAndMateriaIdAndSemestreId(@Param("grupoId") Long grupoId,
                                                     @Param("materiaId") Long materiaId,
                                                     @Param("semestreId") Long semestreId);


    /**
     * 🔥 Obtener todas las asignaciones ACTIVAS de un grupo en un semestre específico.
     * Carga las relaciones necesarias (grupo, materia, maestro, aula, semestre)
     * para evitar LazyInitializationException al construir el HorarioSolution.
     *
     * @param grupoId ID del grupo
     * @param semestreId ID del semestre
     * @return Lista de asignaciones activas del grupo en ese semestre
     */
    @Query("SELECT a FROM Asignacion a " +
           "JOIN FETCH a.grupo g " +
           "JOIN FETCH a.materia m " +
           "JOIN FETCH a.maestro ma " +
           "JOIN FETCH a.aula au " +
           "JOIN FETCH a.semestre s " +
           "WHERE a.grupo.grupoId = :grupoId " +
           "AND a.semestre.semestreId = :semestreId " +
           "AND a.activo = true " +
           "ORDER BY m.nombre ASC")
    List<Asignacion> findByGrupoIdAndSemestreId(@Param("grupoId") Long grupoId,
                                                 @Param("semestreId") Long semestreId);

    /**
     * Cuenta asignaciones activas que referencian una materia.
     * Usado antes de eliminar para dar mensaje contextual.
     */
    @Query("SELECT COUNT(a) FROM Asignacion a WHERE a.materia.materiaId = :materiaId")
    long countByMateriaId(@Param("materiaId") Long materiaId);

    /**
     * Verifica rápida de existencia (más eficiente que count si solo
     * necesitas saber si hay al menos una).
     */
    @Query("SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END " +
           "FROM Asignacion a WHERE a.materia.materiaId = :materiaId")
    boolean existsByMateriaId(@Param("materiaId") Long materiaId);

    /**
     * Cuenta asignaciones que referencian a un maestro.
     */
    @Query("SELECT COUNT(a) FROM Asignacion a WHERE a.maestro.maestroId = :maestroId")
    long countByMaestroId(@Param("maestroId") Long maestroId);

    /**
     * Cuenta asignaciones que referencian a un grupo.
     */
    @Query("SELECT COUNT(a) FROM Asignacion a WHERE a.grupo.grupoId = :grupoId")
    long countByGrupoId(@Param("grupoId") Long grupoId);

    /**
     * Cuenta asignaciones que referencian a un aula.
     */
    @Query("SELECT COUNT(a) FROM Asignacion a WHERE a.aula.aulaId = :aulaId")
    long countByAulaId(@Param("aulaId") Long aulaId);

    /**
     * Búsqueda paginada de asignaciones con filtros opcionales:
     * - especialidadId (por grupo)
     * - turnoId (por grupo)
     * - semestreId
     */
    @Query("SELECT a FROM Asignacion a " +
           "JOIN FETCH a.grupo g " +
           "LEFT JOIN FETCH g.especialidad ge " +
           "JOIN FETCH a.materia m " +
           "JOIN FETCH a.maestro ma " +
           "JOIN FETCH a.aula au " +
           "JOIN FETCH a.semestre s " +
           "JOIN FETCH a.turno at " +
           "WHERE a.escuela.escuelaId = :escuelaId " +
           "AND s.semestreId = :semestreId " +
           "AND (:grupoId IS NULL OR g.grupoId = :grupoId) " +
           "AND (:especialidadId IS NULL OR ge.especialidadId = :especialidadId) " +
           "AND (:turnoId IS NULL OR at.turnoId = :turnoId) " +
           "AND (LOWER(g.nombre) LIKE LOWER(CONCAT('%', :busqueda, '%')) " +
           "OR LOWER(m.nombre) LIKE LOWER(CONCAT('%', :busqueda, '%')) " +
           "OR LOWER(m.clave) LIKE LOWER(CONCAT('%', :busqueda, '%')) " +
           "OR LOWER(ma.nombre) LIKE LOWER(CONCAT('%', :busqueda, '%')) " +
           "OR LOWER(ma.apellidos) LIKE LOWER(CONCAT('%', :busqueda, '%')) " +
           "OR LOWER(au.nombre) LIKE LOWER(CONCAT('%', :busqueda, '%'))) " +
           "ORDER BY g.grado ASC, g.nombre ASC, m.nombre ASC")
    Page<Asignacion> buscarPorEscuelaYSemestreYFiltros(
            @Param("escuelaId") Long escuelaId,
            @Param("semestreId") Long semestreId,
            @Param("grupoId") Long grupoId,
            @Param("especialidadId") Long especialidadId,
            @Param("turnoId") Long turnoId,
            @Param("busqueda") String busqueda,
            Pageable pageable);

}