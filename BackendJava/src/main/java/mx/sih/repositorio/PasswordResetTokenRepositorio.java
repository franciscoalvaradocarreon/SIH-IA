package mx.sih.repositorio;

import mx.sih.modelo.entidad.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PasswordResetTokenRepositorio extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("UPDATE PasswordResetToken t SET t.usado = true " +
           "WHERE t.usuario.usuarioId = :usuarioId AND t.usado = false")
    void invalidarTodosDelUsuario(@Param("usuarioId") Long usuarioId);
}