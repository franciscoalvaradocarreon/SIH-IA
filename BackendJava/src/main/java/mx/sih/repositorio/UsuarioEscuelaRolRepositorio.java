package mx.sih.repositorio;

import mx.sih.modelo.entidad.Escuela;
import mx.sih.modelo.entidad.UsuarioEscuelaRol;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UsuarioEscuelaRolRepositorio extends JpaRepository<UsuarioEscuelaRol, Long> {

    /**
     * Obtiene todas las asignaciones de un usuario
     */
    @Query("SELECT uer FROM UsuarioEscuelaRol uer " +
           "WHERE uer.usuario.usuarioId = :usuarioId")
    List<UsuarioEscuelaRol> findByUsuarioId(@Param("usuarioId") Long usuarioId);

    /**
     * Obtiene todas las asignaciones de una escuela
     */
    @Query("SELECT uer FROM UsuarioEscuelaRol uer " +
           "WHERE uer.escuela.escuelaId = :escuelaId")
    List<UsuarioEscuelaRol> findByEscuelaId(@Param("escuelaId") Long escuelaId);

    /**
     * Obtiene las escuelas de un usuario (para el selector)
     */
    @Query("SELECT uer.escuela FROM UsuarioEscuelaRol uer " +
           "WHERE uer.usuario.usuarioId = :usuarioId " +
           "AND uer.activo = true")
    List<Escuela> findEscuelasByUsuarioId(@Param("usuarioId") Long usuarioId);

    /**
     * Obtiene una asignación específica por usuario, escuela y rol
     */
    @Query("SELECT uer FROM UsuarioEscuelaRol uer " +
           "WHERE uer.usuario.usuarioId = :usuarioId " +
           "AND uer.escuela.escuelaId = :escuelaId " +
           "AND uer.rol.rolId = :rolId")
    Optional<UsuarioEscuelaRol> findByUsuarioIdAndEscuelaIdAndRolId(
            @Param("usuarioId") Long usuarioId,
            @Param("escuelaId") Long escuelaId,
            @Param("rolId") Long rolId
    );

    /**
     * Obtiene asignaciones activas de un usuario en una escuela
     */
    @Query("SELECT uer FROM UsuarioEscuelaRol uer " +
           "WHERE uer.usuario.usuarioId = :usuarioId " +
           "AND uer.escuela.escuelaId = :escuelaId " +
           "AND uer.activo = true")
    List<UsuarioEscuelaRol> findByUsuarioIdAndEscuelaIdAndActivoTrue(
            @Param("usuarioId") Long usuarioId,
            @Param("escuelaId") Long escuelaId
    );

    // ============================================================
    // VERIFICACIONES
    // ============================================================

    /**
     * Verifica si un usuario tiene un rol específico en una escuela
     */
    @Query("SELECT CASE WHEN COUNT(uer) > 0 THEN true ELSE false END " +
           "FROM UsuarioEscuelaRol uer " +
           "WHERE uer.usuario.usuarioId = :usuarioId " +
           "AND uer.escuela.escuelaId = :escuelaId " +
           "AND uer.rol.rolId = :rolId " +
           "AND uer.activo = true")
    boolean existsByUsuarioIdAndEscuelaIdAndRolId(
            @Param("usuarioId") Long usuarioId,
            @Param("escuelaId") Long escuelaId,
            @Param("rolId") Long rolId
    );

    /**
     * Verifica si un usuario tiene asignaciones activas en una escuela
     */
    @Query("SELECT CASE WHEN COUNT(uer) > 0 THEN true ELSE false END " +
           "FROM UsuarioEscuelaRol uer " +
           "WHERE uer.usuario.usuarioId = :usuarioId " +
           "AND uer.escuela.escuelaId = :escuelaId " +
           "AND uer.activo = true")
    boolean existsByUsuarioIdAndEscuelaIdAndActivoTrue(
            @Param("usuarioId") Long usuarioId,
            @Param("escuelaId") Long escuelaId
    );

    // ============================================================
    // OPERACIONES DE ELIMINACIÓN
    // ============================================================

    /**
     * Elimina todas las asignaciones de un usuario
     */
    @Modifying
    @Query("DELETE FROM UsuarioEscuelaRol uer " +
           "WHERE uer.usuario.usuarioId = :usuarioId")
    void deleteByUsuarioId(@Param("usuarioId") Long usuarioId);

    /**
     * Elimina todas las asignaciones de un usuario en una escuela
     */
    @Modifying
    @Query("DELETE FROM UsuarioEscuelaRol uer " +
           "WHERE uer.usuario.usuarioId = :usuarioId " +
           "AND uer.escuela.escuelaId = :escuelaId")
    void deleteByUsuarioIdAndEscuelaId(
            @Param("usuarioId") Long usuarioId,
            @Param("escuelaId") Long escuelaId
    );
    
    @Query("SELECT CASE WHEN COUNT(uer) > 0 THEN true ELSE false END " +
            "FROM UsuarioEscuelaRol uer " +
            "WHERE uer.usuario.usuarioId = :usuarioId")
     boolean existsByUsuarioId(@Param("usuarioId") Long usuarioId);
}