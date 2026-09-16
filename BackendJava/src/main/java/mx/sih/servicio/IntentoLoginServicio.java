package mx.sih.servicio;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import mx.sih.excepcion.NegocioExcepcion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Limitador de intentos para endpoints sensibles.
 *
 * Cubre dos flujos con políticas distintas:
 *
 *   1. LOGIN (por (correo+IP) y por IP):
 *      · 5 fallos por correo+IP → 15 min de bloqueo
 *      · 20 fallos por IP       → 15 min de bloqueo
 *      · Ventana deslizante de 15 min
 *
 *   2. RECUPERACIÓN DE CONTRASEÑA (por (correo+IP) y por IP):
 *      · 3 solicitudes por correo+IP → 1 hora de bloqueo
 *      · 10 solicitudes por IP       → 1 hora de bloqueo
 *      · Ventana deslizante de 1 hora
 *      Esto evita que un atacante use el endpoint para inundar el correo del
 *      usuario legítimo o para enumerar correos.
 *
 * Limitación conocida: los contadores viven en memoria del proceso. Con
 * varias instancias hay que moverlos a Redis (misma interfaz).
 */
@Service
public class IntentoLoginServicio {

    private static final Logger logger = LoggerFactory.getLogger(IntentoLoginServicio.class);

    // ----- LOGIN -----

    private static final int MAX_LOGIN_POR_USUARIO = 5;
    private static final int MAX_LOGIN_POR_IP = 20;
    private static final Duration VENTANA_LOGIN = Duration.ofMinutes(15);
    private static final Duration BLOQUEO_LOGIN = Duration.ofMinutes(15);

    // ----- RECUPERACIÓN DE CONTRASEÑA -----

    private static final int MAX_RECUPERACION_POR_USUARIO = 3;
    private static final int MAX_RECUPERACION_POR_IP = 10;
    private static final Duration VENTANA_RECUPERACION = Duration.ofHours(1);
    private static final Duration BLOQUEO_RECUPERACION = Duration.ofHours(1);

    private static final int MAX_ENTRADAS = 10_000;

    private static final class Registro {
        private final int fallos;
        private final Instant primerIntento;
        private final Instant bloqueadoHasta;

        private Registro(int fallos, Instant primerIntento, Instant bloqueadoHasta) {
            this.fallos = fallos;
            this.primerIntento = primerIntento;
            this.bloqueadoHasta = bloqueadoHasta;
        }
    }

    private final Map<String, Registro> registros = new ConcurrentHashMap<>();

    // ============================================================
    // LOGIN
    // ============================================================

    /** Lanza NegocioExcepcion si el par correo/IP está bloqueado por intentos de login. */
    public void verificarPermitido(String correo, String ip) {
        verificar(claveUsuario("login", correo, ip),
                  MAX_LOGIN_POR_USUARIO, VENTANA_LOGIN, BLOQUEO_LOGIN);
        if (ip != null && !ip.isBlank()) {
            verificar(claveIp("login", ip),
                      MAX_LOGIN_POR_IP, VENTANA_LOGIN, BLOQUEO_LOGIN);
        }
    }

    public void registrarFallo(String correo, String ip) {
        registrar(claveUsuario("login", correo, ip),
                  MAX_LOGIN_POR_USUARIO, VENTANA_LOGIN, BLOQUEO_LOGIN);
        if (ip != null && !ip.isBlank()) {
            registrar(claveIp("login", ip),
                      MAX_LOGIN_POR_IP, VENTANA_LOGIN, BLOQUEO_LOGIN);
        }
    }

    /** Un login correcto limpia el contador del usuario (no el de la IP). */
    public void registrarExito(String correo, String ip) {
        registros.remove(claveUsuario("login", correo, ip));
    }

    // ============================================================
    // RECUPERACIÓN DE CONTRASEÑA
    // ============================================================

    /** Lanza NegocioExcepcion si el par correo/IP está bloqueado por exceso de solicitudes. */
    public void verificarSolicitudRecuperacion(String correo, String ip) {
        verificar(claveUsuario("rec", correo, ip),
                  MAX_RECUPERACION_POR_USUARIO, VENTANA_RECUPERACION, BLOQUEO_RECUPERACION);
        if (ip != null && !ip.isBlank()) {
            verificar(claveIp("rec", ip),
                      MAX_RECUPERACION_POR_IP, VENTANA_RECUPERACION, BLOQUEO_RECUPERACION);
        }
    }

    public void registrarSolicitudRecuperacion(String correo, String ip) {
        registrar(claveUsuario("rec", correo, ip),
                  MAX_RECUPERACION_POR_USUARIO, VENTANA_RECUPERACION, BLOQUEO_RECUPERACION);
        if (ip != null && !ip.isBlank()) {
            registrar(claveIp("rec", ip),
                      MAX_RECUPERACION_POR_IP, VENTANA_RECUPERACION, BLOQUEO_RECUPERACION);
        }
    }

    // ============================================================
    // HELPERS INTERNOS
    // ============================================================

    private void verificar(String clave, int maximo, Duration ventana, Duration bloqueo) {
        Registro registro = registros.get(clave);
        if (registro == null) {
            return;
        }
        Instant ahora = Instant.now();

        if (registro.bloqueadoHasta != null && ahora.isBefore(registro.bloqueadoHasta)) {
            long minutos = Math.max(1, Duration.between(ahora, registro.bloqueadoHasta).toMinutes() + 1);
            throw new NegocioExcepcion("demasiados_intentos",
                    "Demasiados intentos. Vuelve a intentarlo en " + minutos + " minuto(s).");
        }

        if (registro.primerIntento != null
                && Duration.between(registro.primerIntento, ahora).compareTo(ventana) > 0) {
            registros.remove(clave, registro);
        }
    }

    private void registrar(String clave, int maximo, Duration ventana, Duration bloqueo) {
        Instant ahora = Instant.now();
        registros.compute(clave, (k, anterior) -> {
            if (anterior == null
                    || anterior.primerIntento == null
                    || Duration.between(anterior.primerIntento, ahora).compareTo(ventana) > 0) {
                return new Registro(1, ahora, null);
            }
            int fallos = anterior.fallos + 1;
            if (fallos >= maximo) {
                logger.warn("🚫 Bloqueado temporalmente ({} intentos) para: {}", fallos, k);
                return new Registro(fallos, anterior.primerIntento, ahora.plus(bloqueo));
            }
            return new Registro(fallos, anterior.primerIntento, null);
        });
        limpiar();
    }

    private void limpiar() {
        if (registros.size() < MAX_ENTRADAS) {
            return;
        }
        // Referencia conservadora: la ventana más larga entre login y recuperación.
        Instant limite = Instant.now()
                .minus(VENTANA_RECUPERACION)
                .minus(BLOQUEO_RECUPERACION);
        registros.entrySet().removeIf(entrada -> {
            Registro registro = entrada.getValue();
            Instant referencia = registro.bloqueadoHasta != null
                    ? registro.bloqueadoHasta
                    : registro.primerIntento;
            return referencia != null && referencia.isBefore(limite);
        });
    }

    private String claveUsuario(String prefijo, String correo, String ip) {
        return prefijo + ":u:" + (correo == null ? "" : correo)
                + "|" + (ip == null ? "" : ip);
    }

    private String claveIp(String prefijo, String ip) {
        return prefijo + ":ip:" + ip;
    }
    
    /*
    * Se usa para avisarle al usuario cuando está a punto de ser bloqueado.
    */
    public int intentosRestantes(String correo, String ip) {
       Registro registro = registros.get(claveUsuario("login", correo, ip));
       if (registro == null) {
           return MAX_LOGIN_POR_USUARIO;
       }

       Instant ahora = Instant.now();

       // Ya está bloqueado: no le quedan intentos en esta ventana.
       if (registro.bloqueadoHasta != null && ahora.isBefore(registro.bloqueadoHasta)) {
           return 0;
       }

       // La ventana expiró: el contador vuelve a cero.
       if (registro.primerIntento != null
               && Duration.between(registro.primerIntento, ahora).compareTo(VENTANA_LOGIN) > 0) {
           return MAX_LOGIN_POR_USUARIO;
       }

       return Math.max(0, MAX_LOGIN_POR_USUARIO - registro.fallos);
    }
}