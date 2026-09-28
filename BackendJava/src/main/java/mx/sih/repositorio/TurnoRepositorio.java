package mx.sih.repositorio;

import mx.sih.modelo.entidad.Turno;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
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

    /**
     * ¿Existe ya un turno con ese nombre en la escuela Y EN ESE SEMESTRE?
     *
     * Dos decisiones importantes:
     *
     *  1. La unicidad es por (escuela, semestre, nombre), NO por escuela: cada semestre
     *     tiene su propio "MATUTINO" (en la base conviven uno del semestre 11 y otro del
     *     14, y eso es correcto). La lista de turnos se consulta filtrando por semestre.
     *
     *  2. Devuelve boolean y NO Optional: un Optional con dos filas lanza
     *     IncorrectResultSizeDataAccessException, es decir, un 500 en lugar del mensaje
     *     de negocio (era lo que ocurría con findByEscuelaIdAndNombreIgnoreCase).
     */
    @Query("SELECT CASE WHEN COUNT(t) > 0 THEN true ELSE false END " +
           "FROM Turno t WHERE t.escuela.escuelaId = :escuelaId " +
           "AND LOWER(t.nombre) = LOWER(:nombre) " +
           "AND ((:semestreId IS NULL AND t.semestre IS NULL) " +
           "     OR (t.semestre IS NOT NULL AND t.semestre.semestreId = :semestreId))")
    boolean existsByEscuelaIdAndSemestreIdAndNombreIgnoreCase(
            @Param("escuelaId") Long escuelaId,
            @Param("semestreId") Long semestreId,
            @Param("nombre") String nombre);

    /**
     * La misma comprobacion, pero al ACTUALIZAR: excluye el propio turno (si no, se encontraria a si
     * mismo y no dejaria guardar ningun cambio).
     *
     * Es la que usa actualizarTurno. Antes esa validacion miraba TODA la escuela, sin semestre, asi
     * que renombrar un turno a un nombre que existia en OTRO semestre se rechazaba con "ya existe
     * otro turno con el nombre ... en esta escuela", aunque en su semestre no hubiera ninguno.
     */
    @Query("SELECT CASE WHEN COUNT(t) > 0 THEN true ELSE false END " +
           "FROM Turno t WHERE t.escuela.escuelaId = :escuelaId " +
           "AND LOWER(t.nombre) = LOWER(:nombre) " +
           "AND ((:semestreId IS NULL AND t.semestre IS NULL) " +
           "     OR (t.semestre IS NOT NULL AND t.semestre.semestreId = :semestreId)) " +
           "AND t.turnoId != :id")
    boolean existsByEscuelaIdAndSemestreIdAndNombreIgnoreCaseAndIdNot(
            @Param("escuelaId") Long escuelaId,
            @Param("semestreId") Long semestreId,
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

    /**
     * Todos los turnos de una escuela en un semestre, SIN paginar.
     *
     * Lo usa la importacion desde otro semestre: ahi hacen falta todos, porque se copian enteros
     * (con sus bloques de horario). El listado de la pantalla sigue usando la version paginada.
     */
    @Query("SELECT t FROM Turno t " +
           "JOIN FETCH t.escuela e " +
           "LEFT JOIN FETCH t.semestre s " +
           "WHERE e.escuelaId = :escuelaId AND s.semestreId = :semestreId " +
           "ORDER BY t.nombre ASC")
    List<Turno> findByEscuelaIdAndSemestreId(@Param("escuelaId") Long escuelaId,
                                             @Param("semestreId") Long semestreId);

}