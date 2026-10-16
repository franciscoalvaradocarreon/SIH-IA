package mx.sih.repositorio;

import mx.sih.modelo.entidad.Usuario;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
public interface UsuarioRepositorio extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByEmail(String email);

    Optional<Usuario> findByUsuario(String usuario);

    @Query("SELECT u FROM Usuario u WHERE " +
           "LOWER(u.nombreCompleto) LIKE LOWER(CONCAT('%', :busqueda, '%')) OR " +
           "LOWER(u.email) LIKE LOWER(CONCAT('%', :busqueda, '%')) OR " +
           "LOWER(u.usuario) LIKE LOWER(CONCAT('%', :busqueda, '%'))")
    Page<Usuario> buscarPorTexto(@Param("busqueda") String busqueda, Pageable pageable);

    /**
     * Listado paginado con visibilidad multi-tenant:
     *   · Usuarios con al menos una asignación en la escuela activa.
     *   · Usuarios SIN ninguna asignación (huérfanos).
     *
     * El segundo caso existe para que un ADMIN que crea un usuario por error
     * sin asignarle escuela/rol pueda verlo en el listado y corregirlo. Sin esa
     * condición, el usuario quedaba inaccesible desde la UI.
     *
     * Nota de seguridad: los huérfanos son visibles en TODAS las escuelas
     * (no tienen tenant asignado). Es aceptable como red de seguridad, pero
     * lo ideal es exigir asignaciones al crear (validación de servicio).
     */
    @Query("SELECT u FROM Usuario u WHERE (" +
           "  u.usuarioId IN (" +
           "    SELECT r.usuario.usuarioId FROM UsuarioEscuelaRol r " +
           "    WHERE r.escuela.escuelaId = :escuelaId" +
           "  ) " +
           "  OR NOT EXISTS (" +
           "    SELECT 1 FROM UsuarioEscuelaRol r2 " +
           "    WHERE r2.usuario.usuarioId = u.usuarioId" +
           "  )" +
           ") AND (" +
           "  LOWER(u.nombreCompleto) LIKE LOWER(CONCAT('%', :busqueda, '%')) OR " +
           "  LOWER(u.email) LIKE LOWER(CONCAT('%', :busqueda, '%')) OR " +
           "  LOWER(u.usuario) LIKE LOWER(CONCAT('%', :busqueda, '%'))" +
           ")")
    Page<Usuario> buscarPorEscuelaYTexto(@Param("escuelaId") Long escuelaId,
                                         @Param("busqueda") String busqueda,
                                         Pageable pageable);

    boolean existsByEmail(String email);

    boolean existsByUsuario(String usuario);

    @Query("SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END " +
           "FROM Usuario u WHERE u.email = :email AND u.usuarioId != :id")
    boolean existsByEmailAndIdNot(@Param("email") String email, @Param("id") Long id);

    @Query("SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END " +
           "FROM Usuario u WHERE u.usuario = :usuario AND u.usuarioId != :id")
    boolean existsByUsuarioAndIdNot(@Param("usuario") String usuario, @Param("id") Long id);

    @Query("SELECT DISTINCT u FROM Usuario u " +
           "LEFT JOIN FETCH u.relacionesEscuelaRol r " +
           "LEFT JOIN FETCH r.escuela " +
           "LEFT JOIN FETCH r.rol " +
           "WHERE u.email = :email")
    Optional<Usuario> findByEmailConRelaciones(@Param("email") String email);

    /**
     * Incrementa el contador de sesiones del usuario: TODOS los tokens emitidos antes dejan de valer
     * (al instante y en todos sus dispositivos). Es lo que se ejecuta al cerrar sesion y al cambiar o
     * restablecer la contrasena.
     *
     * <p>Se hace con un UPDATE atomico en la base y no leyendo-y-guardando, para que dos peticiones
     * simultaneas -cerrar sesion en dos pestañas, por ejemplo- no se pisen: cada una suma.
     *
     * <p>Va con {@code @Transactional} aqui porque una consulta de modificacion necesita una
     * transaccion activa, y asi no depende de que quien la llame se acuerde de abrirla.
     */
    @Modifying
    @Transactional
    @Query("UPDATE Usuario u SET u.tokenVersion = COALESCE(u.tokenVersion, 0) + 1 WHERE u.usuarioId = :id")
    int incrementarTokenVersion(@Param("id") Long id);
}