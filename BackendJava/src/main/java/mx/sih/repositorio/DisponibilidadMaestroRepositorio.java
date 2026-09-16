// mx.sih.repositorio.DisponibilidadMaestroRepositorio.java
package mx.sih.repositorio;

import mx.sih.modelo.entidad.DisponibilidadMaestro;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DisponibilidadMaestroRepositorio extends JpaRepository<DisponibilidadMaestro, Long> {

    /**
     * Buscar disponibilidades por maestro
     */
    @Query("SELECT d FROM DisponibilidadMaestro d " +
           "JOIN FETCH d.maestro m " +
           "JOIN FETCH d.turnoHorario th " +
           "WHERE d.maestro.maestroId = :maestroId " +
           "AND d.escuela.escuelaId = :escuelaId")
    List<DisponibilidadMaestro> findByMaestroId(@Param("maestroId") Long maestroId,
                                                 @Param("escuelaId") Long escuelaId);

    /**
     * Buscar disponibilidad por maestro y turno_horario
     */
    @Query("SELECT d FROM DisponibilidadMaestro d " +
           "WHERE d.maestro.maestroId = :maestroId " +
           "AND d.turnoHorario.id = :turnoHorarioId " +
           "AND d.escuela.escuelaId = :escuelaId")
    Optional<DisponibilidadMaestro> findByMaestroIdAndTurnoHorarioId(@Param("maestroId") Long maestroId,
                                                                      @Param("turnoHorarioId") Long turnoHorarioId,
                                                                      @Param("escuelaId") Long escuelaId);

    /**
     * Buscar por turno_horario (para saber qué maestros están disponibles en un bloque)
     */
    @Query("SELECT d FROM DisponibilidadMaestro d " +
           "JOIN FETCH d.maestro m " +
           "WHERE d.turnoHorario.id = :turnoHorarioId " +
           "AND d.disponible = true " +
           "AND d.escuela.escuelaId = :escuelaId")
    List<DisponibilidadMaestro> findByTurnoHorarioIdAndDisponibleTrue(@Param("turnoHorarioId") Long turnoHorarioId,
                                                                      @Param("escuelaId") Long escuelaId);

    /**
     * Paginado con búsqueda por nombre de maestro
     */
    @Query("SELECT d FROM DisponibilidadMaestro d " +
           "JOIN d.maestro m " +
           "JOIN d.turnoHorario th " +
           "WHERE d.escuela.escuelaId = :escuelaId " +
           "AND (LOWER(m.nombre) LIKE LOWER(CONCAT('%', :busqueda, '%')) " +
           "OR LOWER(m.apellidos) LIKE LOWER(CONCAT('%', :busqueda, '%')))")
    Page<DisponibilidadMaestro> buscarPorEscuelaYMaestro(@Param("escuelaId") Long escuelaId,
                                                          @Param("busqueda") String busqueda,
                                                          Pageable pageable);


    @Query("SELECT CASE WHEN COUNT(d) > 0 THEN true ELSE false END " +
           "FROM DisponibilidadMaestro d " +
           "WHERE d.maestro.maestroId = :maestroId " +
           "AND d.turnoHorario.id = :turnoHorarioId " +
           "AND d.escuela.escuelaId = :escuelaId " +
           "AND d.disponible = true")
    boolean existsByMaestroIdAndTurnoHorarioIdAndDisponibleTrue(@Param("maestroId") Long maestroId,
                                                                 @Param("turnoHorarioId") Long turnoHorarioId,
                                                                 @Param("escuelaId") Long escuelaId);

    
    @Query("SELECT d FROM DisponibilidadMaestro d " +
           "JOIN FETCH d.turnoHorario th " +
           "WHERE d.maestro.maestroId = :maestroId " +
           "AND d.escuela.escuelaId = :escuelaId " +
           "AND d.disponible = true " +
           "ORDER BY th.diaSemana ASC, th.orden ASC")
    List<DisponibilidadMaestro> findDisponiblesByMaestroId(@Param("maestroId") Long maestroId,
                                                            @Param("escuelaId") Long escuelaId);

    /**
     * 🔥 Obtener todos los bloques horarios donde un maestro NO está disponible.
     * 
     * @param maestroId ID del maestro
     * @param escuelaId ID de la escuela
     * @return Lista de disponibilidades con disponible = false
     */
    @Query("SELECT d FROM DisponibilidadMaestro d " +
           "JOIN FETCH d.turnoHorario th " +
           "WHERE d.maestro.maestroId = :maestroId " +
           "AND d.escuela.escuelaId = :escuelaId " +
           "AND d.disponible = false " +
           "ORDER BY th.diaSemana ASC, th.orden ASC")
    List<DisponibilidadMaestro> findNoDisponiblesByMaestroId(@Param("maestroId") Long maestroId,
                                                              @Param("escuelaId") Long escuelaId);

    /**
     * 🔥 Obtener todos los maestros disponibles en un bloque horario específico.
     * Útil para encontrar maestros que pueden tomar una clase en ese bloque.
     * 
     * @param turnoHorarioId ID del bloque horario
     * @param escuelaId ID de la escuela
     * @return Lista de disponibilidades de maestros disponibles
     */
    @Query("SELECT d FROM DisponibilidadMaestro d " +
           "JOIN FETCH d.maestro m " +
           "WHERE d.turnoHorario.id = :turnoHorarioId " +
           "AND d.escuela.escuelaId = :escuelaId " +
           "AND d.disponible = true")
    List<DisponibilidadMaestro> findMaestrosDisponiblesEnBloque(@Param("turnoHorarioId") Long turnoHorarioId,
                                                                 @Param("escuelaId") Long escuelaId);

    /**
     * 🔥 Contar cuántas horas de disponibilidad tiene un maestro en un día específico.
     * Útil para limitar horas por día.
     * 
     * @param maestroId ID del maestro
     * @param diaSemana Día de la semana (1=Lunes, 5=Viernes)
     * @param escuelaId ID de la escuela
     * @return Número de bloques disponibles en ese día
     */
    @Query("SELECT COUNT(d) FROM DisponibilidadMaestro d " +
           "JOIN d.turnoHorario th " +
           "WHERE d.maestro.maestroId = :maestroId " +
           "AND th.diaSemana = :diaSemana " +
           "AND d.escuela.escuelaId = :escuelaId " +
           "AND d.disponible = true")
    long countDisponiblesPorMaestroYDia(@Param("maestroId") Long maestroId,
                                         @Param("diaSemana") Integer diaSemana,
                                         @Param("escuelaId") Long escuelaId);

    /**
     * 🔥 Contar cuántas horas de disponibilidad tiene un maestro en total.
     * Útil para limitar horas por semana.
     * 
     * @param maestroId ID del maestro
     * @param escuelaId ID de la escuela
     * @return Número total de bloques disponibles
     */
    @Query("SELECT COUNT(d) FROM DisponibilidadMaestro d " +
           "WHERE d.maestro.maestroId = :maestroId " +
           "AND d.escuela.escuelaId = :escuelaId " +
           "AND d.disponible = true")
    long countDisponiblesPorMaestro(@Param("maestroId") Long maestroId,
                                     @Param("escuelaId") Long escuelaId);

    /**
     * 🔥 Verificar si un maestro tiene al menos una disponibilidad registrada.
     * 
     * @param maestroId ID del maestro
     * @param escuelaId ID de la escuela
     * @return true si tiene al menos un registro
     */
    @Query("SELECT CASE WHEN COUNT(d) > 0 THEN true ELSE false END " +
           "FROM DisponibilidadMaestro d " +
           "WHERE d.maestro.maestroId = :maestroId " +
           "AND d.escuela.escuelaId = :escuelaId")
    boolean existsByMaestroId(@Param("maestroId") Long maestroId,
                               @Param("escuelaId") Long escuelaId);

    /**
     * 🔥 Obtener todos los bloques disponibles para un maestro, con información del turno_horario.
     * Incluye los campos necesarios para el solver.
     * 
     * @param maestroId ID del maestro
     * @param escuelaId ID de la escuela
     * @return Lista de disponibilidades con turno_horario cargado
     */
    @Query("SELECT d FROM DisponibilidadMaestro d " +
           "JOIN FETCH d.turnoHorario th " +
           "JOIN FETCH th.turno t " +
           "WHERE d.maestro.maestroId = :maestroId " +
           "AND d.escuela.escuelaId = :escuelaId " +
           "AND d.disponible = true")
    List<DisponibilidadMaestro> findDisponiblesConTurnoHorarioByMaestroId(@Param("maestroId") Long maestroId,
                                                                           @Param("escuelaId") Long escuelaId);

    // ============================================================
    // 🔥 MÉTODOS PARA ELIMINACIÓN MASIVA (útiles para regenerar disponibilidad)
    // ============================================================

    /**
     * 🔥 Eliminar todas las disponibilidades de un maestro.
     * 
     * @param maestroId ID del maestro
     * @param escuelaId ID de la escuela
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM DisponibilidadMaestro d " +
           "WHERE d.maestro.maestroId = :maestroId " +
           "AND d.escuela.escuelaId = :escuelaId")
    void deleteByMaestroIdAndEscuelaId(@Param("maestroId") Long maestroId,
                                        @Param("escuelaId") Long escuelaId);

    /**
     * 🔥 Eliminar todas las disponibilidades de un maestro en un turno específico.
     * 
     * @param maestroId ID del maestro
     * @param turnoId ID del turno
     * @param escuelaId ID de la escuela
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM DisponibilidadMaestro d " +
           "WHERE d.maestro.maestroId = :maestroId " +
           "AND d.turnoHorario.turno.turnoId = :turnoId " +
           "AND d.escuela.escuelaId = :escuelaId")
    void deleteByMaestroIdAndTurnoId(@Param("maestroId") Long maestroId,
                                      @Param("turnoId") Long turnoId,
                                      @Param("escuelaId") Long escuelaId);

    // 🔥 Paginado con búsqueda y semestre
    @Query("SELECT d FROM DisponibilidadMaestro d " +
           "JOIN d.maestro m " +
           "JOIN d.turnoHorario th " +
           "WHERE d.escuela.escuelaId = :escuelaId " +
           "AND d.semestre.semestreId = :semestreId " +
           "AND (LOWER(m.nombre) LIKE LOWER(CONCAT('%', :busqueda, '%')) " +
           "OR LOWER(m.apellidos) LIKE LOWER(CONCAT('%', :busqueda, '%')))")
    Page<DisponibilidadMaestro> buscarPorEscuelaYMaestroYSemestre(
            @Param("escuelaId") Long escuelaId,
            @Param("semestreId") Long semestreId,
            @Param("busqueda") String busqueda,
            Pageable pageable);

    @Query("SELECT d FROM DisponibilidadMaestro d " +
           "JOIN FETCH d.maestro m " +
           "JOIN FETCH d.turnoHorario th " +
           "JOIN FETCH th.turno t " +
           "JOIN FETCH d.semestre s " +
           "WHERE d.maestro.maestroId = :maestroId " +
           "AND d.escuela.escuelaId = :escuelaId " +
           "AND d.semestre.semestreId = :semestreId " +
           "AND d.disponible = true " +
           "ORDER BY th.diaSemana ASC, th.orden ASC")
    List<DisponibilidadMaestro> findDisponiblesByMaestroIdAndSemestreId(
            @Param("maestroId") Long maestroId,
            @Param("escuelaId") Long escuelaId,
            @Param("semestreId") Long semestreId);

    @Query("SELECT CASE WHEN COUNT(d) > 0 THEN true ELSE false END " +
           "FROM DisponibilidadMaestro d WHERE d.turnoHorario.id = :turnoHorarioId")
    boolean existsByTurnoHorarioId(@Param("turnoHorarioId") Long turnoHorarioId);
    
    /**
     * Cuenta registros de disponibilidad de un maestro.
     */
    @Query("SELECT COUNT(d) FROM DisponibilidadMaestro d " +
           "WHERE d.maestro.maestroId = :maestroId")
    long countByMaestroId(@Param("maestroId") Long maestroId);
}