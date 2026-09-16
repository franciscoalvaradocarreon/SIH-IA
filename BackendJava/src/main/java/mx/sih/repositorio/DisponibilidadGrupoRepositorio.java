package mx.sih.repositorio;

import mx.sih.modelo.entidad.DisponibilidadGrupo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DisponibilidadGrupoRepositorio extends JpaRepository<DisponibilidadGrupo, Long> {

    /**
     *  Todas las disponibilidades de un grupo en un semestre
     */
    @Query("SELECT d FROM DisponibilidadGrupo d " +
           "JOIN FETCH d.turnoHorario th " +
           "JOIN FETCH d.grupo g " +
           "JOIN FETCH d.semestre s " +
           "WHERE d.grupo.grupoId = :grupoId " +
           "AND d.escuela.escuelaId = :escuelaId " +
           "AND d.semestre.semestreId = :semestreId " +
           "ORDER BY th.diaSemana ASC, th.orden ASC")
    List<DisponibilidadGrupo> findByGrupoIdAndSemestreId(@Param("grupoId") Long grupoId,
                                                          @Param("escuelaId") Long escuelaId,
                                                          @Param("semestreId") Long semestreId);

    /**
     *  Solo las disponibles (disponible = true) de un grupo en un semestre
     */
    @Query("SELECT d FROM DisponibilidadGrupo d " +
           "JOIN FETCH d.turnoHorario th " +
           "JOIN FETCH d.grupo g " +
           "JOIN FETCH d.semestre s " +
           "WHERE d.grupo.grupoId = :grupoId " +
           "AND d.escuela.escuelaId = :escuelaId " +
           "AND d.semestre.semestreId = :semestreId " +
           "AND d.disponible = true " +
           "ORDER BY th.diaSemana ASC, th.orden ASC")
    List<DisponibilidadGrupo> findDisponiblesByGrupoIdAndSemestreId(@Param("grupoId") Long grupoId,
                                                                     @Param("escuelaId") Long escuelaId,
                                                                     @Param("semestreId") Long semestreId);

    /**
     *  Buscar una disponibilidad específica (grupo + bloque + semestre)
     */
    @Query("SELECT d FROM DisponibilidadGrupo d " +
           "WHERE d.grupo.grupoId = :grupoId " +
           "AND d.turnoHorario.id = :turnoHorarioId " +
           "AND d.escuela.escuelaId = :escuelaId " +
           "AND d.semestre.semestreId = :semestreId")
    Optional<DisponibilidadGrupo> findByGrupoIdAndTurnoHorarioIdAndSemestreId(
            @Param("grupoId") Long grupoId,
            @Param("turnoHorarioId") Long turnoHorarioId,
            @Param("escuelaId") Long escuelaId,
            @Param("semestreId") Long semestreId);

    /**
     *  Eliminar todas las disponibilidades de un grupo en un semestre
     */
    @Query("DELETE FROM DisponibilidadGrupo d " +
           "WHERE d.grupo.grupoId = :grupoId " +
           "AND d.escuela.escuelaId = :escuelaId " +
           "AND d.semestre.semestreId = :semestreId")
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    void deleteByGrupoIdAndSemestreId(@Param("grupoId") Long grupoId,
                                       @Param("escuelaId") Long escuelaId,
                                       @Param("semestreId") Long semestreId);

    @Query("SELECT COUNT(d) FROM DisponibilidadGrupo d " +
           "WHERE d.grupo.grupoId = :grupoId " +
           "AND d.escuela.escuelaId = :escuelaId " +
           "AND d.semestre.semestreId = :semestreId " +
           "AND d.disponible = true")
    long countDisponiblesByGrupoIdAndSemestreId(@Param("grupoId") Long grupoId,
                                                 @Param("escuelaId") Long escuelaId,
                                                 @Param("semestreId") Long semestreId);

    @Query("SELECT CASE WHEN COUNT(d) > 0 THEN true ELSE false END " +
           "FROM DisponibilidadGrupo d WHERE d.turnoHorario.id = :turnoHorarioId")
    boolean existsByTurnoHorarioId(@Param("turnoHorarioId") Long turnoHorarioId);
    

    /**
     * Cuenta bloques de disponibilidad configurados para un grupo.
     */
    @Query("SELECT COUNT(d) FROM DisponibilidadGrupo d WHERE d.grupo.grupoId = :grupoId")
    long countByGrupoId(@Param("grupoId") Long grupoId);
}