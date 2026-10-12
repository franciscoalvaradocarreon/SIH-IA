package mx.sih.seguridad;

import java.util.List;
import java.util.Locale;
import mx.sih.excepcion.NegocioExcepcion;

/**
 * POLITICA DE CONTRASENAS (hallazgo H-06 de QA).
 *
 * <p>Antes la unica regla era "al menos 8 caracteres", escrita DOS veces (UsuarioServicio y
 * RecuperacionPasswordServicio) y sin ninguna regla de composicion: {@code 12345678} era aceptada, y
 * combinada con el limite de intentos de login eso se agota por fuerza bruta en minutos.
 *
 * <p>Aqui vive la regla, en un solo sitio, y la usan el alta, la actualizacion y el restablecimiento.
 * Los DTO tambien declaran el minimo con las constantes de esta clase, para que la validacion de
 * entrada y la del servicio no se puedan desfasar.
 *
 * <p>Que exige:
 * <ul>
 *   <li>Entre 12 y 128 caracteres.</li>
 *   <li>Al menos una letra y al menos un digito (asi se cae "solo digitos").</li>
 *   <li>No ser el mismo caracter repetido.</li>
 *   <li>No estar en la lista de contrasenas obvias.</li>
 * </ul>
 *
 * <p>Lo que NO hace: comprobar si la contrasena aparece en filtraciones reales (eso pide un servicio
 * externo) ni caducidad. Si algun dia se quiere, este es el sitio.
 */
public final class PoliticaPassword {

    public static final int LONGITUD_MINIMA = 12;
    public static final int LONGITUD_MAXIMA = 128;

    /**
     * Contrasenas que hay que rechazar de entrada, en minusculas.
     *
     * <p>Es una lista corta y explicita a proposito: no pretende ser un diccionario, solo atajar lo
     * que la gente escribe cuando la regla le estorba. "123456789012" y "qwertyuiop12" cumplen la
     * longitud y la composicion, y siguen siendo adivinables.
     */
    private static final List<String> OBVIAS = List.of(
            "123456789012", "1234567890123", "0123456789012",
            "qwertyuiop12", "qwerty123456", "password1234", "contrasena12",
            "administrador", "administrador1", "abcdefghijkl", "aaaabbbbcccc");

    private PoliticaPassword() {
        // Clase de utilidades: no se instancia.
    }

    /**
     * Valida la contrasena y lanza el error de negocio con el motivo concreto.
     *
     * @throws NegocioExcepcion con codigo {@code password_debil} y un mensaje que el usuario puede
     *                          accionar (dice exactamente que falta).
     */
    public static void validar(String password) {
        if (password == null || password.isBlank()) {
            throw new NegocioExcepcion("password_debil", "La contraseña es obligatoria.");
        }
        if (password.length() < LONGITUD_MINIMA) {
            throw new NegocioExcepcion("password_debil",
                    "La contraseña debe tener al menos " + LONGITUD_MINIMA + " caracteres.");
        }
        if (password.length() > LONGITUD_MAXIMA) {
            throw new NegocioExcepcion("password_debil",
                    "La contraseña no puede exceder " + LONGITUD_MAXIMA + " caracteres.");
        }
        if (!tieneLetra(password) || !tieneDigito(password)) {
            throw new NegocioExcepcion("password_debil",
                    "La contraseña debe combinar letras y números (no solo dígitos ni solo letras).");
        }
        if (esMismoCaracterRepetido(password)) {
            throw new NegocioExcepcion("password_debil",
                    "La contraseña no puede ser el mismo carácter repetido.");
        }
        if (OBVIAS.contains(password.toLowerCase(Locale.ROOT))) {
            throw new NegocioExcepcion("password_debil",
                    "Esa contraseña es demasiado obvia. Elige una distinta.");
        }
    }

    /** Igual que {@link #validar(String)} pero sin lanzar: comodo para pruebas y vistas previas. */
    public static boolean esValida(String password) {
        try {
            validar(password);
            return true;
        } catch (NegocioExcepcion e) {
            return false;
        }
    }

    private static boolean tieneLetra(String password) {
        return password.chars().anyMatch(Character::isLetter);
    }

    private static boolean tieneDigito(String password) {
        return password.chars().anyMatch(Character::isDigit);
    }

    private static boolean esMismoCaracterRepetido(String password) {
        return password.chars().distinct().count() == 1;
    }
}
