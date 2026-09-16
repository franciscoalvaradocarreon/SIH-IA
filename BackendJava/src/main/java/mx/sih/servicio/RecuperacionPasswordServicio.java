package mx.sih.servicio;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

import mx.sih.excepcion.NegocioExcepcion;
import mx.sih.modelo.entidad.PasswordResetToken;
import mx.sih.modelo.entidad.Usuario;
import mx.sih.repositorio.PasswordResetTokenRepositorio;
import mx.sih.repositorio.UsuarioRepositorio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Flujo de recuperación de contraseña por correo.
 *
 * DISEÑO DE SEGURIDAD:
 *  1. Respuesta uniforme: "solicitar" siempre termina igual (éxito silencioso)
 *     exista o no el correo. Evita que un atacante pueda enumerar qué correos
 *     están registrados.
 *  2. Token aleatorio de 32 bytes (SecureRandom) codificado en Base64 URL-safe.
 *     ~43 caracteres. Imposible de adivinar.
 *  3. En la BD se guarda SOLO el SHA-256 del token, nunca el token en claro.
 *     Si hay una fuga de BD, los tokens no son utilizables.
 *  4. Expiración corta: 30 minutos.
 *  5. Un solo uso: al validarse, se marca como usado y no puede reutilizarse.
 *  6. Al emitir un nuevo token, todos los anteriores del mismo usuario se
 *     invalidan. Solo hay un token activo por usuario.
 *  7. Rate limiting por correo + IP para evitar que alguien inunde de correos
 *     al usuario legítimo o use el endpoint para enumerar correos.
 *  8. La respuesta del correo electrónico es genérica: no revela si el correo
 *     existe ni si el usuario tiene roles asignados.
 */
@Service
public class RecuperacionPasswordServicio {

    private static final Logger logger = LoggerFactory.getLogger(RecuperacionPasswordServicio.class);

    /** Tamaño del token en bytes. 32 bytes → ~43 caracteres en Base64 URL-safe. */
    private static final int BYTES_TOKEN = 32;

    /** Ventana de validez del enlace enviado por correo. */
    private static final Duration EXPIRACION_TOKEN = Duration.ofMinutes(30);

    /** Mínimo de caracteres de la nueva contraseña (alineado con UsuarioServicio). */
    private static final int LONGITUD_MINIMA_PASSWORD = 8;

    private final UsuarioRepositorio usuarioRepositorio;
    private final PasswordResetTokenRepositorio tokenRepositorio;
    private final PasswordEncoder passwordEncoder;
    private final EmailServicio emailServicio;
    private final IntentoLoginServicio intentoLoginServicio;

    private final SecureRandom secureRandom = new SecureRandom();

    public RecuperacionPasswordServicio(UsuarioRepositorio usuarioRepositorio,
                                        PasswordResetTokenRepositorio tokenRepositorio,
                                        PasswordEncoder passwordEncoder,
                                        EmailServicio emailServicio,
                                        IntentoLoginServicio intentoLoginServicio) {
        this.usuarioRepositorio = usuarioRepositorio;
        this.tokenRepositorio = tokenRepositorio;
        this.passwordEncoder = passwordEncoder;
        this.emailServicio = emailServicio;
        this.intentoLoginServicio = intentoLoginServicio;
    }

    // ============================================================
    // Solicitud de recuperación
    // ============================================================

    /**
     * Solicita el envío de un enlace de recuperación.
     *
     * SIEMPRE responde sin excepción (a menos que el rate limit se supere):
     * si el correo no existe, no se envía nada pero el cliente ve el mismo
     * mensaje de éxito. Anti-enumeración.
     *
     * @param correo     correo del usuario.
     * @param ipCliente  IP del cliente (para rate limiting).
     */
    @Transactional
    public void solicitarRecuperacion(String correo, String ipCliente) {
        String correoNormalizado = correo == null ? "" : correo.trim().toLowerCase();

        // 1) Rate limit: bloquea si hay demasiadas solicitudes recientes.
        intentoLoginServicio.verificarSolicitudRecuperacion(correoNormalizado, ipCliente);

        // 2) Buscar usuario. Si no existe, se registra el intento y se sale
        //    silenciosamente para no filtrar la existencia del correo.
        var usuarioOpt = usuarioRepositorio.findByEmail(correoNormalizado);
        if (usuarioOpt.isEmpty()) {
            intentoLoginServicio.registrarSolicitudRecuperacion(correoNormalizado, ipCliente);
            logger.debug("Solicitud de recuperación para correo inexistente (silenciado)");
            return;
        }
        Usuario usuario = usuarioOpt.get();

        // 3) Si el usuario está inactivo, mismo silencio.
        if (!Boolean.TRUE.equals(usuario.getActivo())) {
            intentoLoginServicio.registrarSolicitudRecuperacion(correoNormalizado, ipCliente);
            logger.debug("Solicitud de recuperación para usuario inactivo (silenciado)");
            return;
        }

        // 4) Invalidar tokens anteriores del mismo usuario.
        tokenRepositorio.invalidarTodosDelUsuario(usuario.getUsuarioId());

        // 5) Generar token, guardar hash.
        String tokenPlano = generarTokenAleatorio();
        PasswordResetToken entidad = new PasswordResetToken();
        entidad.setUsuario(usuario);
        entidad.setTokenHash(hashear(tokenPlano));
        entidad.setExpiraEn(LocalDateTime.now().plus(EXPIRACION_TOKEN));
        entidad.setUsado(false);
        tokenRepositorio.save(entidad);

        // 6) Enviar email. Si falla, se loggea pero no se expone al cliente
        //    (el mensaje genérico ya se envió de todas formas).
        try {
            emailServicio.enviarRecuperacionPassword(
                    usuario.getEmail(),
                    usuario.getNombreCompleto(),
                    tokenPlano);
        } catch (Exception e) {
            logger.error("Fallo al enviar email de recuperación para usuario {}: {}",
                    usuario.getUsuarioId(), e.getMessage());
        }

        // 7) Registrar la solicitud exitosa (cuenta también para el rate limit).
        intentoLoginServicio.registrarSolicitudRecuperacion(correoNormalizado, ipCliente);
    }

    // ============================================================
    // Restablecimiento
    // ============================================================

    /**
     * Aplica la nueva contraseña si el token es válido y no expiró.
     *
     * Lanza NegocioExcepcion con mensaje genérico ante cualquier fallo
     * (token no existe, expirado, usado) para no dar pistas al atacante
     * sobre qué falló exactamente.
     */
    @Transactional
    public void restablecerPassword(String tokenPlano, String nuevaPassword) {
        if (tokenPlano == null || tokenPlano.isBlank()) {
            throw new NegocioExcepcion("token_invalido", "El enlace es inválido o expiró.");
        }
        validarPassword(nuevaPassword);

        String hash = hashear(tokenPlano);
        PasswordResetToken entidad = tokenRepositorio.findByTokenHash(hash)
                .orElseThrow(() -> new NegocioExcepcion("token_invalido",
                        "El enlace es inválido o expiró."));

        if (Boolean.TRUE.equals(entidad.getUsado())) {
            throw new NegocioExcepcion("token_invalido", "El enlace es inválido o expiró.");
        }
        if (entidad.getExpiraEn().isBefore(LocalDateTime.now())) {
            throw new NegocioExcepcion("token_invalido", "El enlace es inválido o expiró.");
        }

        // Actualizar contraseña
        Usuario usuario = entidad.getUsuario();
        usuario.setPasswordHash(passwordEncoder.encode(nuevaPassword));
        usuarioRepositorio.save(usuario);

        // Marcar el token como usado y limpiar cualquier otro token activo del usuario.
        entidad.setUsado(true);
        tokenRepositorio.save(entidad);
        tokenRepositorio.invalidarTodosDelUsuario(usuario.getUsuarioId());

        logger.info("Contraseña restablecida para usuario {}", usuario.getUsuarioId());
    }

    // ============================================================
    // Helpers
    // ============================================================

    /** Token aleatorio codificado en Base64 URL-safe sin padding. */
    private String generarTokenAleatorio() {
        byte[] bytes = new byte[BYTES_TOKEN];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** SHA-256 del token, codificado en hex lowercase (64 chars). */
    private String hashear(String valor) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(valor.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 está garantizado por la especificación de Java.
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    private void validarPassword(String password) {
        if (password == null || password.isBlank()) {
            throw new NegocioExcepcion("password_obligatoria", "La contraseña es obligatoria.");
        }
        if (password.length() < LONGITUD_MINIMA_PASSWORD) {
            throw new NegocioExcepcion("password_debil",
                    "La contraseña debe tener al menos " + LONGITUD_MINIMA_PASSWORD + " caracteres.");
        }
    }
}