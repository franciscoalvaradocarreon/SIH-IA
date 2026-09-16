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

@Service
public class TokenServicio {

    private final SecretKey llaveFirma;

    public TokenServicio(@Value("${jwt.secreto}") String secreto) {
        if (secreto == null || secreto.length() < 32) {
            throw new IllegalArgumentException("La clave secreta JWT debe tener al menos 32 caracteres");
        }
        this.llaveFirma = Keys.hmacShaKeyFor(secreto.getBytes(StandardCharsets.UTF_8));
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
            escuelaIds
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

}