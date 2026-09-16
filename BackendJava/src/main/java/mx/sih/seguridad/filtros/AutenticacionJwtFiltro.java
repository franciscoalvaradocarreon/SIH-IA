package mx.sih.seguridad.filtros;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import mx.sih.seguridad.contexto.EscuelaContexto;
import mx.sih.servicio.TokenServicio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

@Component
public class AutenticacionJwtFiltro extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(AutenticacionJwtFiltro.class);

    /** Header con la escuela activa que el frontend quiere usar. */
    public static final String HEADER_ESCUELA = "X-School-ID";

    private final TokenServicio servicioToken;

    public AutenticacionJwtFiltro(TokenServicio servicioToken) {
        this.servicioToken = servicioToken;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest peticion,
                                    HttpServletResponse respuesta,
                                    FilterChain cadena) throws ServletException, IOException {

        try {
            // 1. Extraer token del header Authorization
            String cabecera = peticion.getHeader("Authorization");
            if (cabecera == null || !cabecera.startsWith("Bearer ")) {
                // No hay token: seguir sin autenticar (SecurityConfig decidirá)
                cadena.doFilter(peticion, respuesta);
                return;
            }

            String token = cabecera.substring(7);

            // 2. Validar token. Si no es válido → 401 inmediato (no continuar)
            if (!servicioToken.validarToken(token)) {
                responderNoAutorizado(respuesta, "token_invalido", "Token inválido o expirado");
                return;
            }

            // 3. Extraer claims del usuario
            var claims = servicioToken.extraerClaims(token);

            // 4. Resolver la escuela activa (validación multi-tenant)
            Optional<Long> escuelaActiva = resolverEscuelaActiva(peticion, claims, respuesta);
            if (escuelaActiva.isEmpty()) {
                // Ya se escribió la respuesta de error dentro de resolverEscuelaActiva
                return;
            }

            // 5. Establecer autenticación en Spring Security
            var autoridades = claims.roles().stream()
                    .map(rol -> new SimpleGrantedAuthority("ROLE_" + rol))
                    .toList();

            var autenticacion = new UsernamePasswordAuthenticationToken(claims, null, autoridades);
            SecurityContextHolder.getContext().setAuthentication(autenticacion);

            // 6. Guardar escuela activa en el contexto para los servicios
            EscuelaContexto.setEscuelaId(escuelaActiva.get());
            logger.debug("Usuario {} accede a escuela {}", claims.usuarioId(), escuelaActiva.get());

            cadena.doFilter(peticion, respuesta);

        } catch (Exception e) {
            logger.error("Error inesperado en filtro JWT", e);
            responderNoAutorizado(respuesta, "error_interno", "Error procesando autenticación");
        } finally {
            // CRÍTICO: limpiar ThreadLocal SIEMPRE para no filtrar entre requests
            EscuelaContexto.limpiar();
        }
    }

    /**
     * Resuelve la escuela activa a partir del header X-School-ID,
     * validando que el usuario tenga acceso a ella según el JWT.
     *
     * Reglas:
     *  - Sin header: se usa la escuela del JWT (retrocompatibilidad).
     *  - Con header: debe coincidir EXACTAMENTE con la escuela del JWT.
     *    Si no coincide → 403 y se corta la request.
     *
     * @return Optional con la escuelaId resuelta, o empty si hubo error (ya se respondió).
     */
    private Optional<Long> resolverEscuelaActiva(HttpServletRequest peticion,
                                                 mx.sih.modelo.dto.ClaimsUsuario claims,
                                                 HttpServletResponse respuesta) throws IOException {

        String headerValor = peticion.getHeader(HEADER_ESCUELA);
        Long escuelaDelToken = claims.escuelaId();

        // Caso A: no viene header → usar la del token
        if (headerValor == null || headerValor.isBlank()) {
            if (escuelaDelToken == null) {
                responderProhibido(respuesta, "escuela_no_definida",
                        "El token no tiene escuela activa y no se envió " + HEADER_ESCUELA);
                return Optional.empty();
            }
            return Optional.of(escuelaDelToken);
        }

        // Caso B: viene header → parsear y validar
        Long escuelaDelHeader;
        try {
            escuelaDelHeader = Long.parseLong(headerValor.trim());
        } catch (NumberFormatException e) {
            responderBadRequest(respuesta, "escuela_id_invalido",
                    HEADER_ESCUELA + " debe ser un número entero");
            return Optional.empty();
        }

        if (escuelaDelToken == null) {
            responderProhibido(respuesta, "escuela_no_definida",
                    "El token no tiene escuela y se intentó forzar una por header");
            return Optional.empty();
        }

        // 🔒 VALIDACIÓN DE AISLAMIENTO
        if (!claims.escuelaIds().contains(escuelaDelHeader)) {
            logger.warn("🚨 Cross-tenant: usuarioId={}, escuela solicitada={}, permitidas={}",
                    claims.usuarioId(), escuelaDelHeader, claims.escuelaIds());
            responderProhibido(respuesta, "escuela_no_autorizada",
                "No tienes acceso a la escuela solicitada");
            return Optional.empty();
        }

        return Optional.of(escuelaDelHeader);
    }

    // ============================================================
    // Helpers de respuesta JSON
    // ============================================================

    private void responderNoAutorizado(HttpServletResponse respuesta, String codigo, String mensaje) throws IOException {
        escribirError(respuesta, HttpServletResponse.SC_UNAUTHORIZED, codigo, mensaje);
    }

    private void responderProhibido(HttpServletResponse respuesta, String codigo, String mensaje) throws IOException {
        escribirError(respuesta, HttpServletResponse.SC_FORBIDDEN, codigo, mensaje);
    }

    private void responderBadRequest(HttpServletResponse respuesta, String codigo, String mensaje) throws IOException {
        escribirError(respuesta, HttpServletResponse.SC_BAD_REQUEST, codigo, mensaje);
    }

    private void escribirError(HttpServletResponse respuesta, int status, String codigo, String mensaje) throws IOException {
        respuesta.setStatus(status);
        respuesta.setContentType(MediaType.APPLICATION_JSON_VALUE);
        respuesta.setCharacterEncoding("UTF-8");
        respuesta.getWriter().write("""
                {"success":false,"error":"%s","message":"%s"}
                """.formatted(codigo, mensaje));
    }
}