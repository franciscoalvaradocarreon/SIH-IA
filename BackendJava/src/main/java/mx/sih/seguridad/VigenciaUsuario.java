package mx.sih.seguridad;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * VIGENCIA DE UN USUARIO.
 *
 * <p>Permite dar acceso por periodos (un maestro de un semestre, una suplencia, una cuenta de
 * prueba) sin depender de acordarse de desactivarla a mano.
 *
 * <p>Reglas, todas deliberadas:
 * <ul>
 *   <li><b>null = sin limite</b>. Es el estado de todos los usuarios que ya existian, asi que
 *       aplicar el cambio no deja a nadie fuera.</li>
 *   <li><b>Se compara por dia</b>: si {@code vigente_hasta} es el 31/12, la cuenta vale todo el 31.
 *       Vence al empezar el 1/1.</li>
 *   <li><b>La fecha de "hoy" la pone quien llama</b> (el servicio), no esta clase: asi la regla es
 *       pura, se puede probar sin relojes y la zona horaria se decide en un solo sitio.</li>
 * </ul>
 *
 * <p>Lo que esta clase NO decide: que el rol ADMIN no esta restringido. Eso es una regla de negocio
 * del inicio de sesion y vive en {@code AutenticacionServicio}, que es quien conoce los roles.
 */
public final class VigenciaUsuario {

    /** Formato de la fecha que se muestra al usuario: 31/12/2026. */
    private static final DateTimeFormatter FORMATO =
            DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT);

    private VigenciaUsuario() {
        // Clase de utilidades: no se instancia.
    }

    /**
     * Indica si la vigencia esta vencida.
     *
     * @param vigenteHasta ultimo dia valido, o {@code null} si no hay limite
     * @param hoy          fecha actual en la zona horaria de la aplicacion
     * @return {@code true} solo si hay fecha y ya paso
     */
    public static boolean vencida(LocalDate vigenteHasta, LocalDate hoy) {
        return vigenteHasta != null && hoy != null && hoy.isAfter(vigenteHasta);
    }

    /** Texto que ve el usuario al intentar entrar con la vigencia vencida. */
    public static String mensaje(LocalDate vigenteHasta) {
        return "La vigencia de tu usuario terminó el " + FORMATO.format(vigenteHasta)
                + ". Contacta al administrador.";
    }

    /** Fecha en el formato que se muestra (utilidad para la interfaz y las pruebas). */
    public static String formatear(LocalDate fecha) {
        return fecha == null ? "" : FORMATO.format(fecha);
    }
}
