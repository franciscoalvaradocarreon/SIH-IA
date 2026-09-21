package mx.sih.repositorio;

import mx.sih.modelo.entidad.Maestro;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MaestroRepositorio extends JpaRepository<Maestro, Long> {

    // Búsqueda paginada con filtro por escuela y texto
@Query("SELECT m FROM Maestro m WHERE m.escuela.escuelaId = :escuelaId " +
       "AND (LOWER(m.nombre) LIKE LOWER(CONCAT('%', :busqueda, '%')) " +
       "OR LOWER(m.apellidos) LIKE LOWER(CONCAT('%', :busqueda, '%')) " +
       "OR LOWER(m.email) LIKE LOWER(CONCAT('%', :busqueda, '%')) " +
       "OR LOWER(m.apodo) LIKE LOWER(CONCAT('%', :busqueda, '%'))) " +
       "ORDER BY m.apellidos ASC, m.nombre ASC")
Page<Maestro> buscarPorEscuelaYTexto(@Param("escuelaId") Long escuelaId,
                                      @Param("busqueda") String busqueda,
                                      Pageable pageable);

    // Verificar email único por escuela (usando @Query explícita)
    @Query("SELECT CASE WHEN COUNT(m) > 0 THEN true ELSE false END " +
           "FROM Maestro m WHERE m.email = :email AND m.escuela.escuelaId = :escuelaId")
    boolean existsByEmailAndEscuelaId(@Param("email") String email,
                                      @Param("escuelaId") Long escuelaId);

    // Verificar email único excluyendo un ID (para actualización)
    @Query("SELECT CASE WHEN COUNT(m) > 0 THEN true ELSE false END " +
           "FROM Maestro m WHERE m.email = :email AND m.escuela.escuelaId = :escuelaId AND m.maestroId != :id")
    boolean existsByEmailAndEscuelaIdAndIdNot(@Param("email") String email,
                                              @Param("escuelaId") Long escuelaId,
                                              @Param("id") Long id);

    // Obtener un maestro por ID y escuela (multi-tenant)
    @Query("SELECT m FROM Maestro m " +
           "JOIN FETCH m.semestre s " +
           "JOIN FETCH m.turno t " +
           "WHERE m.maestroId = :id AND m.escuela.escuelaId = :escuelaId")
    Optional<Maestro> findByIdAndEscuelaId(@Param("id") Long id,
                                            @Param("escuelaId") Long escuelaId);
    
    
    @Query("SELECT m FROM Maestro m " +
           "JOIN FETCH m.escuela e " +
           "JOIN FETCH m.semestre s " +
           "JOIN FETCH m.turno t " +
           "WHERE e.escuelaId = :escuelaId " +
           "AND (LOWER(m.nombre) LIKE LOWER(CONCAT('%', :busqueda, '%')) " +
           "OR LOWER(m.apellidos) LIKE LOWER(CONCAT('%', :busqueda, '%')) " +
           "OR LOWER(m.email) LIKE LOWER(CONCAT('%', :busqueda, '%')) " +
           "OR LOWER(m.titulo) LIKE LOWER(CONCAT('%', :busqueda, '%')) " +
           "OR LOWER(m.apodo) LIKE LOWER(CONCAT('%', :busqueda, '%'))) " +
           "AND (:semestreId IS NULL OR s.semestreId = :semestreId) " +
           "AND (:turnoId IS NULL OR t.turnoId = :turnoId) " +
           "ORDER BY m.apellidos ASC, m.nombre ASC")
    Page<Maestro> findByEscuelaIdAndBusquedaAndSemestreId(
            @Param("escuelaId") Long escuelaId,
            @Param("busqueda") String busqueda,
            @Param("semestreId") Long semestreId,
            @Param("turnoId") Long turnoId,
            Pageable pageable);
  
    @Query("SELECT CASE WHEN COUNT(m) > 0 THEN true ELSE false END "
            + "FROM Maestro m "
            + "WHERE LOWER(m.nombre) = LOWER(:nombre) "
            + "AND LOWER(m.apellidos) = LOWER(:apellidos) "
            + "AND m.semestre.semestreId = :semestreId")
    boolean existsByNombreApellidosAndSemestreId(
            @Param("nombre") String nombre,
            @Param("apellidos") String apellidos,
            @Param("semestreId") Long semestreId);
    
    @Query("SELECT CASE WHEN COUNT(m) > 0 THEN true ELSE false END " +
           "FROM Maestro m " +
           "WHERE LOWER(m.nombre) = LOWER(:nombre) " +
           "AND LOWER(m.apellidos) = LOWER(:apellidos) " +
           "AND m.semestre.semestreId = :semestreId " +
           "AND m.maestroId != :id")
    boolean existsByNombreApellidosAndSemestreIdAndIdNot(
            @Param("nombre") String nombre,
            @Param("apellidos") String apellidos,
            @Param("semestreId") Long semestreId,
            @Param("id") Long id);
    
    @Query("SELECT CASE WHEN COUNT(m) > 0 THEN true ELSE false END " +
           "FROM Maestro m " +
           "WHERE LOWER(m.nombre) = LOWER(:nombre) " +
           "AND LOWER(m.apellidos) = LOWER(:apellidos) " +
           "AND m.semestre.semestreId = :semestreId " +
           "AND m.turno.turnoId = :turnoId")
    boolean existsByNombreApellidosAndSemestreIdAndTurnoId(
            @Param("nombre") String nombre,
            @Param("apellidos") String apellidos,
            @Param("semestreId") Long semestreId,
            @Param("turnoId") Long turnoId);

    @Query("SELECT CASE WHEN COUNT(m) > 0 THEN true ELSE false END " +
           "FROM Maestro m " +
           "WHERE LOWER(m.nombre) = LOWER(:nombre) " +
           "AND LOWER(m.apellidos) = LOWER(:apellidos) " +
           "AND m.semestre.semestreId = :semestreId " +
           "AND m.turno.turnoId = :turnoId " +
           "AND m.maestroId != :id")
    boolean existsByNombreApellidosAndSemestreIdAndTurnoIdAndIdNot(
            @Param("nombre") String nombre,
            @Param("apellidos") String apellidos,
            @Param("semestreId") Long semestreId,
            @Param("turnoId") Long turnoId,
            @Param("id") Long id);
 
    /** Cuenta maestros que referencian a un turno. */
    @Query("SELECT COUNT(m) FROM Maestro m WHERE m.turno.turnoId = :turnoId")
    long countByTurnoId(@Param("turnoId") Long turnoId);
}