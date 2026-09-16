// TurnoHorarioRepositorio.java - Versión SIN campo activo
package mx.sih.repositorio;

import mx.sih.modelo.entidad.TurnoHorario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TurnoHorarioRepositorio extends JpaRepository<TurnoHorario, Long> {

    // 🔥 Métodos existentes (sin filtro de activo)
    @Query("SELECT th FROM TurnoHorario th " +
           "JOIN FETCH th.turno t " +
           "JOIN FETCH th.semestre s " +
           "WHERE t.turnoId = :turnoId " +
           "ORDER BY th.diaSemana ASC, th.orden ASC")
    List<TurnoHorario> findByTurnoId(@Param("turnoId") Long turnoId);

    @Query("SELECT th FROM TurnoHorario th WHERE th.turno.turnoId = :turnoId AND th.diaSemana = :diaSemana ORDER BY th.orden ASC")
    List<TurnoHorario> findByTurnoIdAndDiaSemana(@Param("turnoId") Long turnoId,
                                                  @Param("diaSemana") Integer diaSemana);

    @Query("SELECT th FROM TurnoHorario th WHERE th.turno.turnoId = :turnoId AND th.descanso = false ORDER BY th.diaSemana ASC, th.orden ASC")
    List<TurnoHorario> findClasesByTurnoId(@Param("turnoId") Long turnoId);

    @Query("SELECT th FROM TurnoHorario th WHERE th.turno.turnoId = :turnoId AND th.descanso = true ORDER BY th.diaSemana ASC, th.orden ASC")
    List<TurnoHorario> findDescansosByTurnoId(@Param("turnoId") Long turnoId);

    @Query("SELECT th FROM TurnoHorario th WHERE th.id = :id AND th.turno.escuela.escuelaId = :escuelaId")
    Optional<TurnoHorario> findByIdAndEscuelaId(@Param("id") Long id,
                                                @Param("escuelaId") Long escuelaId);

    @Query("SELECT th FROM TurnoHorario th " +
           "JOIN FETCH th.turno t " +
           "JOIN FETCH th.semestre s " +
           "WHERE t.turnoId = :turnoId " +
           "AND s.semestreId = :semestreId " +
           "AND th.descanso = false " +
           "ORDER BY th.diaSemana ASC, th.orden ASC")
    List<TurnoHorario> findClasesByTurnoIdAndSemestreId(@Param("turnoId") Long turnoId,
                                                         @Param("semestreId") Long semestreId);

    @Query("SELECT th FROM TurnoHorario th " +
           "JOIN FETCH th.turno t " +
           "JOIN FETCH th.semestre s " +
           "WHERE t.turnoId = :turnoId " +
           "AND s.semestreId = :semestreId " +
           "ORDER BY th.diaSemana ASC, th.orden ASC")
    List<TurnoHorario> findByTurnoIdAndSemestreId(@Param("turnoId") Long turnoId,
                                                   @Param("semestreId") Long semestreId);



    //Buscar horarios por turno, día y semestre
    @Query("SELECT th FROM TurnoHorario th " +
           "JOIN FETCH th.turno t " +
           "JOIN FETCH th.semestre s " +
           "WHERE t.turnoId = :turnoId " +
           "AND s.semestreId = :semestreId " +
           "ORDER BY th.orden ASC")
    List<TurnoHorario> findByTurnoIdAndDiaSemanaAndSemestreId(@Param("turnoId") Long turnoId,
                                                           @Param("semestreId") Long semestreId);
}