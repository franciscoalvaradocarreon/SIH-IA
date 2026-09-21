package mx.sih.configuracion;

import java.util.List;
import mx.sih.seguridad.filtros.AutenticacionJwtFiltro;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Configuración de seguridad del SIH.
 *
 * CAMBIOS CRÍTICOS respecto a la versión anterior:
 *  1. @EnableMethodSecurity  → sin esta anotación los ~110 @PreAuthorize del proyecto
 *     se ignoran en silencio (Spring Security 6+ no los evalúa por defecto) y cualquier
 *     usuario autenticado podía ejecutar endpoints de ADMIN.
 *  2. /api/menu deja de ser permitAll (el diseño dice que requiere JWT válido).
 *  3. CORS por configuración exacta, sin comodines en dominios compartidos.
 *  4. 401 para "no autenticado" (antes 403, que confundía al frontend con "sin permiso").
 *  5. Cabeceras de seguridad: CSP, nosniff, frame-ancestors y HSTS.
 */
@Configuration
@EnableMethodSecurity(prePostEnabled = true)   // 🔥 IMPRESCINDIBLE
public class SeguridadConfiguracion {

    /** Orígenes exactos autorizados (lista separada por comas en application.properties). */
    @Value("${app.cors.allowed-origins}")
    private List<String> origenesPermitidos;

    /** Solo true si en el futuro se migra el JWT a cookie; con Bearer debe quedarse en false. */
    @Value("${app.cors.allow-credentials:false}")
    private boolean permitirCredenciales;

    @Bean
    public PasswordEncoder codificadorPassword() {
        // Coste 12: sigue verificando los hashes existentes (el coste va dentro del propio hash).
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public SecurityFilterChain cadenaFiltrosSeguridad(HttpSecurity http,
                                                      AutenticacionJwtFiltro filtroJwt) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            // CSRF desactivado es correcto MIENTRAS la autenticación viaje en la cabecera
            // Authorization (no hay cookies de sesión). Si se migra a cookie HttpOnly, hay
            // que volver a activarlo con CookieCsrfTokenRepository.
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .headers(headers -> headers
                .contentSecurityPolicy(csp -> csp.policyDirectives(
                    "default-src 'self'; " +
                    "script-src 'self'; " +
                    "style-src 'self' 'unsafe-inline'; " +
                    "img-src 'self' data:; " +
                    "font-src 'self' data:; " +
                    "connect-src 'self'; " +
                    "object-src 'none'; " +
                    "base-uri 'self'; " +
                    "frame-ancestors 'none'"))
                .frameOptions(frame -> frame.deny())
                .contentTypeOptions(Customizer.withDefaults())   // X-Content-Type-Options: nosniff
                .referrerPolicy(rp -> rp.policy(
                    ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                .httpStrictTransportSecurity(hsts -> hsts
                    .includeSubDomains(true)
                    .maxAgeInSeconds(31_536_000L))
            )
            .authorizeHttpRequests(auth -> auth
                // ---- Público: SOLO el login y el error ----
                .requestMatchers(HttpMethod.POST,
                    "/api/auth/login",
                    "/api/auth/recuperar-password",
                    "/api/auth/restablecer-password").permitAll()    
                .requestMatchers("/error").permitAll()

                // Preflight CORS
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                // Imágenes de maestros: siguen siendo públicas porque un <img src> del
                // navegador NO envía la cabecera Authorization. El XSS se cerró en
                // ArchivoServicio (re-codificación + lista blanca), no aquí.
                .requestMatchers("/uploads/**").permitAll()

                // Documentación: solo en desarrollo (en producción, springdoc.api-docs.enabled=false)
                .requestMatchers("/scalar/**", "/v3/api-docs/**").permitAll()

                // ---- Reglas de ruta (defensa en profundidad, además de @PreAuthorize) ----
                // OJO: /api/usuarios/escuelas lo usa CUALQUIER usuario en el selector de
                // escuela, por eso va ANTES de la regla de ADMIN.
                .requestMatchers("/api/usuarios/escuelas").authenticated()
                .requestMatchers("/api/usuarios/mis-roles").authenticated()   // 🔥 movida aquí
                .requestMatchers("/api/usuarios/**").hasRole("ADMIN")

                .requestMatchers("/api/roles/**", "/api/rol-menu/**").hasRole("ADMIN")
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/escuelas/**").hasRole("ADMIN")
                .requestMatchers("/api/escuelas/**").authenticated()

                // Todo el API exige autenticación (las reglas de arriba ya abrieron login, /uploads y docs).
                .requestMatchers("/api/**").authenticated()

                // EL SHELL DEL FRONT ES PÚBLICO: index.html, /assets/**, favicon y las rutas del SPA
                // (/reportes/..., /horarios/...). Esos archivos NO llevan datos; los datos salen del API,
                // que sigue protegido. Sin esta regla el navegador recibía 401 al abrir la aplicación y
                // el front servido desde el propio programa (-jar) nunca cargaba.
                .anyRequest().permitAll()
            )
            .addFilterBefore(filtroJwt, UsernamePasswordAuthenticationFilter.class)
            .exceptionHandling(ex -> ex
                // No autenticado (sin token o token inválido) => 401
                .authenticationEntryPoint((request, response, authException) ->
                    escribirJson(response, 401, "no_autenticado",
                        "Se requiere autenticación para acceder a este recurso"))
                // Autenticado pero sin permiso => 403
                .accessDeniedHandler((request, response, deniedException) ->
                    escribirJson(response, 403, "no_autorizado",
                        "No tienes permisos para realizar esta operación"))
            );
        return http.build();
    }

    private void escribirJson(jakarta.servlet.http.HttpServletResponse respuesta,
                              int status, String codigo, String mensaje) throws java.io.IOException {
        respuesta.setStatus(status);
        respuesta.setContentType(MediaType.APPLICATION_JSON_VALUE);
        respuesta.setCharacterEncoding("UTF-8");
        if (status == 401) {
            respuesta.setHeader("WWW-Authenticate", "Bearer");
        }
        respuesta.getWriter().write("""
                {"success":false,"error":"%s","message":"%s"}
                """.formatted(codigo, mensaje));
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Lista EXACTA de orígenes (sin comodines de dominio compartido como
        // *.devtunnels.ms o *.github.dev, que permitirían a terceros llamar a la API).
        configuration.setAllowedOrigins(origenesPermitidos);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "X-School-ID"));
        configuration.setExposedHeaders(List.of("Content-Disposition"));
        configuration.setAllowCredentials(permitirCredenciales);
        configuration.setMaxAge(1800L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
