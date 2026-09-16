// mx.sih.repositorio.SemestreRepositorio.java
package mx.sih.repositorio;

import mx.sih.modelo.entidad.Semestre;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SemestreRepositorio extends JpaRepository<Semestre, Long> {

    // Buscar por escuela con paginación
    @Query("SELECT s FROM Semestre s " +
           "JOIN FETCH s.escuela e " +
           "WHERE e.escuelaId = :escuelaId " +
           "AND (LOWER(s.nombre) LIKE LOWER(CONCAT('%', :busqueda, '%')) " +
           "OR LOWER(s.descripcion) LIKE LOWER(CONCAT('%', :busqueda, '%'))) " +
           "ORDER BY s.semestreId DESC")
    Page<Semestre> findByEscuelaIdAndBusqueda(@Param("escuelaId") Long escuelaId,
                                               @Param("busqueda") String busqueda,
                                               Pageable pageable);

    // Obtener todos los semestres de una escuela
    @Query("SELECT s FROM Semestre s " +
           "JOIN FETCH s.escuela e " +
           "WHERE e.escuelaId = :escuelaId " +
           "ORDER BY s.semestreId DESC")
    List<Semestre> findByEscuelaId(@Param("escuelaId") Long escuelaId);

    // Obtener semestres activos
    @Query("SELECT s FROM Semestre s " +
           "JOIN FETCH s.escuela e " +
           "WHERE e.escuelaId = :escuelaId " +
           "AND s.activo = true " +
           "ORDER BY s.semestreId DESC")
    List<Semestre> findActivosByEscuelaId(@Param("escuelaId") Long escuelaId);

    // Obtener semestre actual (activo)
    @Query("SELECT s FROM Semestre s " +
           "JOIN FETCH s.escuela e " +
           "WHERE e.escuelaId = :escuelaId " +
           "AND s.activo = true " +
           "ORDER BY s.semestreId")
    Optional<Semestre> findSemestreActual(@Param("escuelaId") Long escuelaId);

    // Buscar por ID y escuela
    @Query("SELECT s FROM Semestre s " +
           "JOIN FETCH s.escuela e " +
           "WHERE s.semestreId = :id " +
           "AND e.escuelaId = :escuelaId")
    Optional<Semestre> findByIdAndEscuelaId(@Param("id") Long id,
                                             @Param("escuelaId") Long escuelaId);

    // Verificar si existe un semestre activo
    @Query("SELECT CASE WHEN COUNT(s) > 0 THEN true ELSE false END " +
           "FROM Semestre s " +
           "WHERE s.escuela.escuelaId = :escuelaId " +
           "AND s.activo = true")
    boolean existsSemestreActivo(@Param("escuelaId") Long escuelaId);

    // Verificar duplicado por nombre
    @Query("SELECT CASE WHEN COUNT(s) > 0 THEN true ELSE false END " +
           "FROM Semestre s " +
           "WHERE s.escuela.escuelaId = :escuelaId " +
           "AND LOWER(s.nombre) = LOWER(:nombre) " +
           "AND s.semestreId != :id")
    boolean existsByNombreAndEscuelaIdAndIdNot(@Param("nombre") String nombre,
                                                @Param("escuelaId") Long escuelaId,
                                                @Param("id") Long id);

    @Query("SELECT s FROM Semestre s " +
           "WHERE s.escuela.escuelaId = :escuelaId " +
           "AND s.activo = true " +
           "ORDER BY s.semestreId DESC")
    List<Semestre> findActivosOrderBySemestreIdDesc(@Param("escuelaId") Long escuelaId);

        /**
     * 🔥 Obtiene el semestre activo más reciente de una escuela.
     * Usa el nombre derivado de Spring Data JPA (findFirst + OrderBy + Desc)
     * para evitar el error NonUniqueResultException cuando hay más de un
     * semestre activo (devuelve el más reciente por semestreId).
     *
     * @param escuelaId ID de la escuela
     * @return Optional con el semestre activo más reciente
     */
    Optional<Semestre> findFirstByEscuela_EscuelaIdAndActivoTrueOrderBySemestreIdDesc(Long escuelaId);

}