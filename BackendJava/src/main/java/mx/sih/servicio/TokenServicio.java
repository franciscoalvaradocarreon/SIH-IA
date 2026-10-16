/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package mx.sih.servicio;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import mx.sih.modelo.dto.ClaimsUsuario;
import mx.sih.repositorio.UsuarioRepositorio;

/**
 * Emite y valida los JWT, y comprueba que la sesion siga vigente.
 *
 * <p>El token lleva un contador ("tv") que se compara con el de la base en cada peticion. Cerrar
 * sesion o cambiar la contrasena lo incrementan, de modo que los tokens emitidos antes quedan
 * revocados al instante (H-07 de la validacion de ataques: antes, un token robado servia 24 horas
 * aunque el usuario cerrara sesion o cambiara su contrasena).
 */
@Service
public class TokenServicio {

    private final SecretKey llaveFirma;
    private final UsuarioRepositorio usuarioRepositorio;

    public TokenServicio(@Value("${jwt.secreto}") String secreto,
                         UsuarioRepositorio usuarioRepositorio) {
        if (secreto == null || secreto.length() < 32) {
            throw new IllegalArgumentException("La clave secreta JWT debe tener al menos 32 caracteres");
        }
        this.llaveFirma = Keys.hmacShaKeyFor(secreto.getBytes(StandardCharsets.UTF_8));
        this.usuarioRepositorio = usuarioRepositorio;
    }

    public String generarToken(ClaimsUsuario claims) {
        var ahora = new Date();
        var expiracion = new Date(ahora.getTime() + 86_400_000); // 24 horas

        return Jwts.builder()
            .subject(claims.correo())
            .claim("usuarioId", claims.usuarioId())
            .claim("roles", claims.roles())
            .claim("escuelaId", claims.escuelaId())
            .claim("escuelaIds", claims.escuelaIds())
            // Contador de sesiones: si no coincide con el de la base, el token esta revocado.
            .claim("tv", claims.versionToken())
            .issuedAt(ahora)
            .expiration(expiracion)
            .signWith(llaveFirma)
            .compact();
    }

    public ClaimsUsuario extraerClaims(String token) {
        var claims = Jwts.parser()
            .verifyWith(llaveFirma)
            .build()
            .parseSignedClaims(token)
            .getPayload();

        Long escuelaId = claims.get("escuelaId", Long.class);

        // "tv" puede llegar como Integer o como Long segun como se serialice: se lee como Number
        // para no depender de eso. Un token SIN el reclamo -emitido antes de este mecanismo- se
        // interpreta como version 0, que es el valor por defecto de la columna: asi el dia del
        // despliegue no se cierra la sesion a todo el mundo de golpe.
        Object tv = claims.get("tv");
        Integer tokenVersion = (tv instanceof Number numero) ? numero.intValue() : null;

        List<?> escuelaIdsRaw = claims.get("escuelaIds", List.class);
        List<Long> escuelaIds = escuelaIdsRaw == null
                ? (escuelaId != null ? List.of(escuelaId) : List.of())
                : escuelaIdsRaw.stream()
                    .map(o -> ((Number) o).longValue())
                    .toList();

        return new ClaimsUsuario(
            claims.get("usuarioId", Long.class),
            claims.getSubject(),
            claims.get("roles", List.class),
            escuelaId,
            escuelaIds,
            tokenVersion
        );
    }

    public boolean validarToken(String token) {
        try {
            Jwts.parser().verifyWith(llaveFirma).build().parseSignedClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Comprueba que la sesion del token siga vigente: el contador que trae el token debe coincidir
     * con el de la base.
     *
     * <p>Falla CERRADO. Si el usuario no existe, si esta inactivo o si la base no responde, el token
     * NO vale: nunca se deja pasar una sesion por no haber podido comprobarla. El filtro responde 401
     * y el usuario vuelve a iniciar sesion.
     */
    public boolean versionVigente(ClaimsUsuario claims) {
        if (claims == null || claims.usuarioId() == null) {
            return false;
        }
        return usuarioRepositorio.findById(claims.usuarioId())
                .filter(usuario -> Boolean.TRUE.equals(usuario.getActivo()))
                .map(usuario -> (usuario.getTokenVersion() == null ? 0 : usuario.getTokenVersion())
                        == claims.versionToken())
                .orElse(false);
    }
}
