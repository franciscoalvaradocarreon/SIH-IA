package mx.sih.controlador;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Identidad de la version desplegada: {@code GET /api/version}.
 *
 * <p>Para que sirve:
 * <ul>
 *   <li><b>Monitorizacion</b>: un vigilante externo (UptimeRobot, un cron con
 *       curl, el healthcheck de Docker) comprueba que el servicio responde y, de
 *       paso, sabe que version esta viva sin entrar al servidor.</li>
 *   <li><b>Despliegues</b>: tras publicar, una peticion a este endpoint confirma
 *       que la version nueva es la que esta corriendo. Sin esto, la unica forma
 *       de saberlo era entrar por SSH.</li>
 *   <li><b>Soporte</b>: cuando alguien reporta un fallo, el commit exacto evita
 *       adivinar que codigo tenia desplegado.</li>
 * </ul>
 *
 * <p>Es un endpoint PUBLICO a proposito (ver SeguridadConfiguracion): un monitor
 * no puede autenticarse. Por eso NO expone nada de negocio, ni la version de
 * Java, ni las dependencias. Solo el identificador del build; el commit se
 * recorta a 7 caracteres para no facilitar la Busqueda de fallos conocidos de un
 * commit concreto.
 *
 * <p>Los valores llegan del build (Dockerfile) o del pipeline:
 * {@code APP_VERSION}, {@code GIT_COMMIT} y {@code APP_CONSTRUIDO}. En local, sin
 * esos datos, responde {@code dev/desconocido} en lugar de fallar.
 */
@RestController
@RequestMapping("/api/version")
public class VersionControlador {

    @Value("${app.version:dev}")
    private String version;

    @Value("${app.commit:desconocido}")
    private String commit;

    @Value("${app.construido:desconocido}")
    private String construido;

    @GetMapping
    public Map<String, Object> version() {
        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("aplicacion", "SIH-IA");
        respuesta.put("version", version);
        respuesta.put("commit", recortar(commit));
        respuesta.put("construido", construido);
        respuesta.put("estado", "ok");
        return respuesta;
    }

    /**
     * Recorta un SHA de commit a 7 caracteres, pero deja intacto cualquier valor
     * que no sea un SHA (como el literal "desconocido" de una ejecucion local):
     * cortarlo a ciegas daba "descono".
     */
    private String recortar(String valor) {
        if (valor == null || valor.isBlank()) return "desconocido";
        if (!valor.matches("[0-9a-fA-F]{7,}")) return valor;
        return valor.substring(0, 7);
    }
}
