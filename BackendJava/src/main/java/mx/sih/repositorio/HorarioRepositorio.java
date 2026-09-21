// mx.sih.repositorio.HorarioRepositorio.java
package mx.sih.repositorio;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import mx.sih.modelo.entidad.DisponibilidadGrupo;
import mx.sih.modelo.entidad.Grupo;
import mx.sih.modelo.entidad.Horario;

@Repository
public interface HorarioRepositorio extends JpaRepository<Horario, Long> {

    // ============================================================
    // 🔥 HORARIO DE UN GRUPO EN UN SEMESTRE
    // ============================================================
    @Query("SELECT h FROM Horario h " +
           "JOIN FETCH h.grupo g " +
           "JOIN FETCH h.asignacion a " +
           "JOIN FETCH a.materia m " +
           "JOIN FETCH a.maestro ma " +
           "JOIN FETCH h.turnoHorario th " +
           "JOIN FETCH h.aula au " +
           "JOIN FETCH h.semestre s " +
           "WHERE h.grupo.grupoId = :grupoId " +
           "AND h.escuela.escuelaId = :escuelaId " +
           "AND h.semestre.semestreId = :semestreId " +
           "ORDER BY th.diaSemana ASC, th.orden ASC")
    List<Horario> findByGrupoIdAndSemestreId(@Param("grupoId") Long grupoId,
                                              @Param("escuelaId") Long escuelaId,
                                              @Param("semestreId") Long semestreId);

    // ============================================================
    // 🔥 HORARIO DE UN GRUPO EN UN SEMESTRE Y VERSIÓN
    // ============================================================
    @Query("SELECT h FROM Horario h " +
           "JOIN FETCH h.grupo g " +
           "JOIN FETCH h.asignacion a " +
           "JOIN FETCH a.materia m " +
           "JOIN FETCH a.maestro ma " +
           "JOIN FETCH h.turnoHorario th " +
           "JOIN FETCH h.aula au " +
           "JOIN FETCH h.semestre s " +
           "WHERE h.grupo.grupoId = :grupoId " +
           "AND h.version = :version " +
           "AND h.escuela.escuelaId = :escuelaId " +
           "AND h.semestre.semestreId = :semestreId")
    List<Horario> findByGrupoIdAndVersionAndSemestreId(@Param("grupoId") Long grupoId,
                                                        @Param("version") Integer version,
                                                        @Param("escuelaId") Long escuelaId,
                                                        @Param("semestreId") Long semestreId);

    // ============================================================
    // 🔥 VERSIONES DISPONIBLES DE UN GRUPO EN UN SEMESTRE
    // ============================================================
    @Query("SELECT DISTINCT h.version FROM Horario h " +
           "WHERE h.grupo.grupoId = :grupoId " +
           "AND h.escuela.escuelaId = :escuelaId " +
           "AND h.semestre.semestreId = :semestreId " +
           "ORDER BY h.version DESC")
    List<Integer> findVersionsByGrupoIdAndSemestreId(@Param("grupoId") Long grupoId,
                                                      @Param("escuelaId") Long escuelaId,
                                                      @Param("semestreId") Long semestreId);

    // ============================================================
    // 🔥 ELIMINAR HORARIOS DE UN GRUPO EN UN SEMESTRE Y VERSIÓN
    // ============================================================
    @Modifying
    @Transactional
    @Query("DELETE FROM Horario h " +
           "WHERE h.grupo.grupoId = :grupoId " +
           "AND h.version = :version " +
           "AND h.escuela.escuelaId = :escuelaId " +
           "AND h.semestre.semestreId = :semestreId")
    void deleteByGrupoIdAndVersionAndSemestreId(@Param("grupoId") Long grupoId,
                                                 @Param("version") Integer version,
                                                 @Param("escuelaId") Long escuelaId,
                                                 @Param("semestreId") Long semestreId);

    // ============================================================
    // 🔥 HORARIO DE UN MAESTRO EN UN SEMESTRE
    // ============================================================
    @Query("SELECT h FROM Horario h " +
           "JOIN FETCH h.grupo g " +
           "JOIN FETCH h.asignacion a " +
           "JOIN FETCH a.materia m " +
           "JOIN FETCH a.maestro ma " +
           "JOIN FETCH h.turnoHorario th " +
           "JOIN FETCH h.aula au " +
           "JOIN FETCH h.semestre s " +
           "WHERE h.maestroId = :maestroId " +
           "AND h.escuela.escuelaId = :escuelaId " +
           "AND h.semestre.semestreId = :semestreId " +
           "ORDER BY th.diaSemana ASC, th.orden ASC")
    List<Horario> findByMaestroIdAndSemestreId(@Param("maestroId") Long maestroId,
                                                @Param("escuelaId") Long escuelaId,
                                                @Param("semestreId") Long semestreId);

    // ============================================================
    // 🔥 TODOS LOS HORARIOS DE UNA ESCUELA EN UN SEMESTRE
    // ============================================================
    @Query("SELECT h FROM Horario h " +
           "JOIN FETCH h.grupo g " +
           "JOIN FETCH h.asignacion a " +
           "JOIN FETCH a.materia m " +
           "JOIN FETCH a.maestro ma " +
           "JOIN FETCH h.turnoHorario th " +
           "JOIN FETCH h.aula au " +
           "JOIN FETCH h.semestre s " +
           "WHERE h.escuela.escuelaId = :escuelaId " +
           "AND h.semestre.semestreId = :semestreId " +
           "ORDER BY ma.apellidos ASC, ma.nombre ASC, th.diaSemana ASC, th.orden ASC")
    List<Horario> findByEscuelaIdAndSemestreId(@Param("escuelaId") Long escuelaId,
                                                @Param("semestreId") Long semestreId);

    // ============================================================
    // 🔥 ELIMINAR TODOS LOS HORARIOS DE UN SEMESTRE
    // ============================================================
    @Modifying
    @Transactional
    @Query("DELETE FROM Horario h " +
           "WHERE h.escuela.escuelaId = :escuelaId " +
           "AND h.semestre.semestreId = :semestreId")
    void deleteByEscuelaIdAndSemestreId(@Param("escuelaId") Long escuelaId,
                                         @Param("semestreId") Long semestreId);


    @Query("SELECT d FROM DisponibilidadGrupo d " +
           "JOIN FETCH d.turnoHorario th " +
           "JOIN FETCH d.grupo g " +
           "JOIN FETCH d.semestre s " +
           "WHERE d.grupo.grupoId = :grupoId " +
           "AND d.escuela.escuelaId = :escuelaId " +
           "AND d.semestre.semestreId = :semestreId " +
           "AND d.disponible = true " +
           "ORDER BY th.diaSemana ASC, th.orden ASC")
    List<DisponibilidadGrupo> findDisponiblesByGrupoIdAndSemestreId(
            @Param("grupoId") Long grupoId,
            @Param("escuelaId") Long escuelaId,
            @Param("semestreId") Long semestreId);

    @Query("SELECT h FROM Horario h " +
           "JOIN FETCH h.grupo g " +
           "JOIN FETCH h.asignacion a " +
           "JOIN FETCH a.materia m " +
           "JOIN FETCH a.maestro ma " +
           "JOIN FETCH h.turnoHorario th " +
           "JOIN FETCH h.aula au " +
           "JOIN FETCH h.semestre s " +
           "WHERE au.aulaId = :aulaId " +
           "AND h.escuela.escuelaId = :escuelaId " +
           "AND h.semestre.semestreId = :semestreId " +
           "ORDER BY th.diaSemana ASC, th.orden ASC")
    List<Horario> findByAulaIdAndSemestreId(@Param("aulaId") Long aulaId,
                                             @Param("escuelaId") Long escuelaId,
                                             @Param("semestreId") Long semestreId);

    @Query("SELECT CASE WHEN COUNT(h) > 0 THEN true ELSE false END " +
           "FROM Horario h WHERE h.turnoHorario.id = :turnoHorarioId")
    boolean existsByTurnoHorarioId(@Param("turnoHorarioId") Long turnoHorarioId);

    /**
     * Cuenta bloques de horario que referencian una materia
     * (a través de su asignación).
     */
    @Query("SELECT COUNT(h) FROM Horario h " +
           "WHERE h.asignacion.materia.materiaId = :materiaId")
    long countByMateriaId(@Param("materiaId") Long materiaId);

    /**
     * Verificación de existencia.
     */
    @Query("SELECT CASE WHEN COUNT(h) > 0 THEN true ELSE false END " +
           "FROM Horario h WHERE h.asignacion.materia.materiaId = :materiaId")
    boolean existsByMateriaId(@Param("materiaId") Long materiaId);

    /**
     * Cuenta bloques de horario que referencian directamente a un aula.
     */
    @Query("SELECT COUNT(h) FROM Horario h WHERE h.aula.aulaId = :aulaId")
    long countByAulaId(@Param("aulaId") Long aulaId);

    /**
     * Cuenta bloques de horario que referencian directamente a una asignación.
     * Se usa antes de eliminar una asignación para dar un mensaje contextual.
     */
    @Query("SELECT COUNT(h) FROM Horario h WHERE h.asignacion.asignacionId = :asignacionId")
    long countByAsignacionId(@Param("asignacionId") Long asignacionId);

    @Query("SELECT CASE WHEN COUNT(h) > 0 THEN true ELSE false END " +
           "FROM Horario h WHERE h.asignacion.asignacionId = :asignacionId")
    boolean existsByAsignacionId(@Param("asignacionId") Long asignacionId);

}