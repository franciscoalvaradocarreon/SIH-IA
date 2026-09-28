package mx.sih.repositorio;

import java.util.List;
import mx.sih.modelo.entidad.Grupo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GrupoRepositorio extends JpaRepository<Grupo, Long> {

    @Query("SELECT g FROM Grupo g " +
           "LEFT JOIN FETCH g.turno t " +
           "LEFT JOIN FETCH g.especialidad e " +
           "WHERE g.escuela.escuelaId = :escuelaId " +
           "AND (LOWER(g.nombre) LIKE LOWER(CONCAT('%', :busqueda, '%')) " +
           "OR CAST(g.grado AS string) LIKE :busqueda " +
           "OR LOWER(t.nombre) LIKE LOWER(CONCAT('%', :busqueda, '%'))) " +
           "ORDER BY g.grado ASC, g.nombre ASC")
    Page<Grupo> buscarPorEscuelaYTexto(@Param("escuelaId") Long escuelaId,
                                       @Param("busqueda") String busqueda,
                                       Pageable pageable);

    @Query("SELECT g FROM Grupo g " +
           "LEFT JOIN FETCH g.turno t " +
           "LEFT JOIN FETCH g.especialidad e " +
           "WHERE g.escuela.escuelaId = :escuelaId " +
           "AND (:especialidadId = 0 OR g.especialidad.especialidadId = :especialidadId) " +
           "AND (LOWER(g.nombre) LIKE LOWER(CONCAT('%', :busqueda, '%')) " +
           "OR CAST(g.grado AS string) LIKE :busqueda " +
           "OR LOWER(t.nombre) LIKE LOWER(CONCAT('%', :busqueda, '%'))) " +
           "ORDER BY g.grado ASC, g.nombre ASC")
    Page<Grupo> buscarPorEscuelaYTextoYEspecialidad(@Param("escuelaId") Long escuelaId,
                                                     @Param("busqueda") String busqueda,
                                                     @Param("especialidadId") Long especialidadId,
                                                     Pageable pageable);

    @Query("SELECT g FROM Grupo g " +
           "LEFT JOIN FETCH g.turno t " +
           "LEFT JOIN FETCH g.especialidad e " +
           "WHERE g.grupoId = :id AND g.escuela.escuelaId = :escuelaId")
    Optional<Grupo> findByIdAndEscuelaId(@Param("id") Long id,
                                         @Param("escuelaId") Long escuelaId);

    @Query("SELECT CASE WHEN COUNT(g) > 0 THEN true ELSE false END " +
           "FROM Grupo g " +
           "WHERE g.escuela.escuelaId = :escuelaId " +
           "AND LOWER(g.nombre) = LOWER(:nombre) " +
           "AND (g.especialidad.especialidadId = :especialidadId OR " +
           "    (:especialidadId IS NULL AND g.especialidad IS NULL)) " +
           "AND g.grupoId != :id")
    boolean existsByEscuelaIdAndNombreAndEspecialidadAndIdNot(
            @Param("escuelaId") Long escuelaId,
            @Param("nombre") String nombre,
            @Param("especialidadId") Long especialidadId,
            @Param("id") Long id);

    @Query("SELECT CASE WHEN COUNT(g) > 0 THEN true ELSE false END " +
           "FROM Grupo g " +
           "WHERE g.escuela.escuelaId = :escuelaId " +
           "AND LOWER(g.nombre) = LOWER(:nombre) " +
           "AND (g.especialidad.especialidadId = :especialidadId OR " +
           "    (:especialidadId IS NULL AND g.especialidad IS NULL))")
    boolean existsByEscuelaIdAndNombreAndEspecialidad(
            @Param("escuelaId") Long escuelaId,
            @Param("nombre") String nombre,
            @Param("especialidadId") Long especialidadId);

   @Query("SELECT g FROM Grupo g " +
           "JOIN FETCH g.turno t " +
           "LEFT JOIN FETCH g.especialidad e " +
           "WHERE g.escuela.escuelaId = :escuelaId " +
           "AND g.activo = true " +
           "ORDER BY g.nombre ASC")
    List<Grupo> findByEscuelaIdAndActivoTrue(@Param("escuelaId") Long escuelaId);

    @Query("SELECT g FROM Grupo g " +
           "JOIN FETCH g.escuela e " +
           "JOIN FETCH g.turno t " +
           "LEFT JOIN FETCH g.especialidad esp " +
           "JOIN FETCH g.semestre s " +
           "WHERE e.escuelaId = :escuelaId " +
           "AND (LOWER(g.nombre) LIKE LOWER(CONCAT('%', :busqueda, '%')) " +
           "OR CAST(g.grado AS string) LIKE LOWER(CONCAT('%', :busqueda, '%')) " +
           "OR LOWER(t.nombre) LIKE LOWER(CONCAT('%', :busqueda, '%')) " +
           "OR LOWER(esp.nombre) LIKE LOWER(CONCAT('%', :busqueda, '%'))) " +
           "AND (:especialidadId IS NULL OR esp.especialidadId = :especialidadId) " +
           "AND (:semestreId IS NULL OR s.semestreId = :semestreId) " +
           "ORDER BY g.grado ASC, g.nombre ASC")
    Page<Grupo> findByEscuelaIdAndBusquedaAndEspecialidadIdAndSemestreId(
            @Param("escuelaId") Long escuelaId,
            @Param("busqueda") String busqueda,
            @Param("especialidadId") Long especialidadId,
            @Param("semestreId") Long semestreId,
            Pageable pageable);
    
    @Query("SELECT g FROM Grupo g " +
            "LEFT JOIN FETCH g.turno t " +
            "LEFT JOIN FETCH g.especialidad e " +
            "WHERE g.escuela.escuelaId = :escuelaId " +
            "AND g.semestre.semestreId = :semestreId " +
            "AND (LOWER(g.nombre) LIKE LOWER(CONCAT('%', :busqueda, '%')) " +
            "OR CAST(g.grado AS string) LIKE :busqueda " +
            "OR LOWER(t.nombre) LIKE LOWER(CONCAT('%', :busqueda, '%'))) " +
            "ORDER BY g.grado ASC, g.nombre ASC")
    Page<Grupo> findByEscuelaIdAndBusquedaAndSemestreId(
        @Param("escuelaId") Long escuelaId,
        @Param("busqueda") String busqueda,
        @Param("semestreId") Long semestreId,
        Pageable pageable);

        // mx.sih.repositorio.GrupoRepositorio
    @Query("SELECT g FROM Grupo g " +
           "JOIN FETCH g.turno t " +
           "LEFT JOIN FETCH g.especialidad e " +
           "JOIN FETCH g.semestre s " +
           "WHERE g.escuela.escuelaId = :escuelaId " +
           "AND s.semestreId = :semestreId " +
           "AND g.activo = true " +
           "ORDER BY g.nombre ASC")
    List<Grupo> findByEscuelaIdAndSemestreIdAndActivoTrue(
            @Param("escuelaId") Long escuelaId,
            @Param("semestreId") Long semestreId);


    /**
     * Búsqueda paginada con filtros opcionales (especialidad, turno, semestre).
     * Un solo método para todos los casos: pasar null en los filtros que no apliquen.
     */
    @Query("SELECT g FROM Grupo g " +
           "JOIN FETCH g.escuela e " +
           "JOIN FETCH g.turno t " +
           "LEFT JOIN FETCH g.especialidad esp " +
           "JOIN FETCH g.semestre s " +
           "WHERE e.escuelaId = :escuelaId " +
           "AND (LOWER(g.nombre) LIKE LOWER(CONCAT('%', :busqueda, '%')) " +
           "OR CAST(g.grado AS string) LIKE LOWER(CONCAT('%', :busqueda, '%')) " +
           "OR LOWER(t.nombre) LIKE LOWER(CONCAT('%', :busqueda, '%')) " +
           "OR LOWER(esp.nombre) LIKE LOWER(CONCAT('%', :busqueda, '%'))) " +
           "AND (:especialidadId IS NULL OR esp.especialidadId = :especialidadId) " +
           "AND (:turnoId IS NULL OR t.turnoId = :turnoId) " +
           "AND (:semestreId IS NULL OR s.semestreId = :semestreId) " +
           "ORDER BY g.grado ASC, g.nombre ASC")
    Page<Grupo> findByEscuelaYFiltros(
            @Param("escuelaId") Long escuelaId,
            @Param("busqueda") String busqueda,
            @Param("especialidadId") Long especialidadId,
            @Param("turnoId") Long turnoId,
            @Param("semestreId") Long semestreId,
            Pageable pageable);

    /**
    * Grupos activos de un semestre, con filtro opcional por turno.
    * Hace JOIN FETCH a turno para evitar N+1 al construir el solver.
    */
   @Query("SELECT g FROM Grupo g " +
          "JOIN FETCH g.turno t " +
          "LEFT JOIN FETCH g.especialidad e " +
          "JOIN FETCH g.semestre s " +
          "WHERE g.escuela.escuelaId = :escuelaId " +
          "AND s.semestreId = :semestreId " +
          "AND g.activo = true " +
          "AND (:turnoId IS NULL OR t.turnoId = :turnoId) " +
          "ORDER BY g.grado ASC, g.nombre ASC")
   List<Grupo> findActivosByEscuelaYSemestreYTurno(
           @Param("escuelaId") Long escuelaId,
           @Param("semestreId") Long semestreId,
           @Param("turnoId") Long turnoId);

    /** Cuenta grupos que referencian a un turno. */
    @Query("SELECT COUNT(g) FROM Grupo g WHERE g.turno.turnoId = :turnoId")
    long countByTurnoId(@Param("turnoId") Long turnoId);

    /**
     * TODOS los grupos de una escuela en un semestre, SIN paginar y sin filtrar por estado.
     *
     * Lo usa la importacion desde otro semestre: ahi hacen falta todos (tambien los inactivos, para
     * copiarlos tal cual). El listado de la pantalla sigue usando la version paginada
     * (findByEscuelaYFiltros / findByEscuelaIdAndBusquedaAndSemestreId).
     *
     * Se hace JOIN FETCH a turno y semestre porque el servicio los necesita para reencontrar el
     * turno equivalente en el destino, y LEFT JOIN FETCH a especialidad porque es opcional.
     */
    @Query("SELECT g FROM Grupo g " +
           "JOIN FETCH g.escuela e " +
           "JOIN FETCH g.semestre s " +
           "JOIN FETCH g.turno t " +
           "LEFT JOIN FETCH g.especialidad esp " +
           "WHERE e.escuelaId = :escuelaId " +
           "AND s.semestreId = :semestreId " +
           "ORDER BY g.grado ASC, g.nombre ASC")
    List<Grupo> findByEscuelaIdAndSemestreId(@Param("escuelaId") Long escuelaId,
                                             @Param("semestreId") Long semestreId);

    /**
     * ¿Existe YA un grupo con ese nombre en ese (semestre, turno, especialidad)?
     *
     * Es EXACTAMENTE el alcance del indice unico de la tabla:
     * (escuela_id, semestre_id, especialidad_id, turno_id, nombre). La especialidad puede ser null
     * (un grupo sin especialidad es valido), y en SQL dos NULL no son iguales, asi que el caso
     * "sin especialidad" se comprueba aparte con IS NULL.
     *
     * OJO: no sirve existsByEscuelaIdAndNombreAndEspecialidad, que es el que usa crearGrupo: ese
     * NO mira semestre ni turno, asi que al importar encontraria los grupos del semestre de ORIGEN
     * y diria que ya existen. Aqui la pregunta es solo por el semestre de destino.
     */
    @Query("SELECT CASE WHEN COUNT(g) > 0 THEN true ELSE false END " +
           "FROM Grupo g " +
           "WHERE g.escuela.escuelaId = :escuelaId " +
           "AND g.semestre.semestreId = :semestreId " +
           "AND g.turno.turnoId = :turnoId " +
           "AND LOWER(g.nombre) = LOWER(:nombre) " +
           "AND ((:especialidadId IS NULL AND g.especialidad IS NULL) " +
           "     OR g.especialidad.especialidadId = :especialidadId)")
    boolean existeGrupoEnSemestreTurnoYEspecialidad(@Param("escuelaId") Long escuelaId,
                                                    @Param("semestreId") Long semestreId,
                                                    @Param("turnoId") Long turnoId,
                                                    @Param("nombre") String nombre,
                                                    @Param("especialidadId") Long especialidadId);

}