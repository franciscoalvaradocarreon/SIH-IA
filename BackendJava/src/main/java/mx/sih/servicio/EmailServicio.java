package mx.sih.servicio;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * Servicio de envío de correos.
 *
 * Hoy solo se usa para el flujo de recuperación de contraseña. La
 * configuración SMTP viene de application.properties (dev: MailHog,
 * prod: proveedor real) sin tocar código.
 */
@Service
public class EmailServicio {

    private static final Logger logger = LoggerFactory.getLogger(EmailServicio.class);

    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final String frontendUrl;

    public EmailServicio(JavaMailSender mailSender,
                         @Value("${app.mail.from}") String fromAddress,
                         @Value("${app.frontend.url}") String frontendUrl) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
        this.frontendUrl = frontendUrl;
    }

    /**
     * Envía el correo con el enlace de recuperación.
     *
     * El token se pasa en el query string del link. El enlace apunta al
     * frontend (no al backend) porque el usuario completa el formulario
     * en la SPA y esta hace el POST a /api/auth/restablecer-password.
     *
     * @param destinatario    correo del usuario.
     * @param nombreCompleto  se usa para personalizar el saludo.
     * @param token           token en claro (el único momento en que existe;
     *                        en BD se guarda solo su hash).
     */
    public void enviarRecuperacionPassword(String destinatario, String nombreCompleto, String token) {
        try {
            String link = frontendUrl + "/restablecer-password?token=" + token;

            MimeMessage mensaje = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mensaje, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(destinatario);
            helper.setSubject("Recuperación de contraseña - SIH");
            helper.setText(construirCuerpoHtml(nombreCompleto, link), true);

            mailSender.send(mensaje);
            logger.info("Email de recuperación enviado a {}", destinatario);
        } catch (Exception e) {
            logger.error("Error al enviar email de recuperación: {}", e.getMessage(), e);
            throw new RuntimeException("Error al enviar el correo", e);
        }
    }

    /**
     * Cuerpo del correo en HTML simple. Sin imágenes externas ni CSS complejo:
     * los clientes de correo son hostiles con ambos.
     */
    private String construirCuerpoHtml(String nombre, String link) {
        String nombreSeguro = escaparHtml(nombre);
        String linkSeguro = escaparHtml(link);

        return """
            <html><body style="font-family: sans-serif; max-width: 600px; margin: auto;">
              <h2>Recuperación de contraseña</h2>
              <p>Hola %s,</p>
              <p>Recibimos una solicitud para restablecer tu contraseña. Si no fuiste tú, ignora este mensaje.</p>
              <p><a href="%s" style="display:inline-block;padding:12px 24px;background:#2563eb;color:white;text-decoration:none;border-radius:6px;">Restablecer contraseña</a></p>
              <p style="color:#666;font-size:12px;">Este enlace expira en 30 minutos. Si no funciona, copia y pega esta URL: %s</p>
            </body></html>
            """.formatted(nombreSeguro, linkSeguro, linkSeguro);
    }

    /** Escapa los caracteres especiales de HTML para evitar inyección. */
    private String escaparHtml(String valor) {
        if (valor == null) return "";
        return valor
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}