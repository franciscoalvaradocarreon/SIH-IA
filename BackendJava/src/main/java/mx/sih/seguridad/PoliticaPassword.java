package mx.sih.seguridad;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import mx.sih.excepcion.NegocioExcepcion;

/**
 * POLITICA DE CONTRASENAS (hallazgo H-06 de QA, reforzada tras H-07).
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
 *   <li>No estar en la lista corta de contrasenas obvias.</li>
 *   <li><b>No llevar secuencias</b> de 4 o mas caracteres consecutivos (1234, abcd, 4321...).</li>
 *   <li><b>No contener palabras del sistema</b> (admin, escuela, maestro, horario...).</li>
 *   <li><b>No aparecer en la lista de contrasenas filtradas</b> del archivo
 *       {@code recursos/seguridad/contrasenas-filtradas.txt}, ni tal cual ni quitandole los digitos
 *       y simbolos (asi se atrapa "admin" escondido dentro de "Admin1234567").</li>
 * </ul>
 *
 * <p><b>Por que existe la ultima regla (H-07, 16/10/2026):</b> una auditoria con ataque de diccionario
 * contra los hashes bcrypt encontro que la cuenta {@code admin} usaba una contrasena del tipo
 * "palabra + secuencia de numeros": cumplia longitud, composicion y la lista de obviedades, y aun asi
 * cayo al primer intento. Es el patron mas probado del mundo. Las tres reglas nuevas lo cierran, y
 * atacan la causa: no la contrasena de una persona, sino lo que la politica permite elegir.
 *
 * <p>Lo que NO hace: consultar servicios externos de filtraciones (Have I Been Pwned cubriria miles de
 * millones mas, a cambio de una llamada de red) ni caducidad. Si algun dia se quiere, este es el sitio.
 */
public final class PoliticaPassword {

    public static final int LONGITUD_MINIMA = 12;
    public static final int LONGITUD_MAXIMA = 128;

    private static final Logger log = LoggerFactory.getLogger(PoliticaPassword.class);

    /** Lista de contrasenas mas usadas del mundo. Se carga una sola vez, al arrancar. */
    private static final String RECURSO_FILTRADAS = "/seguridad/contrasenas-filtradas.txt";

    /**
     * Contrasenas que hay que rechazar de entrada, en minusculas.
     *
     * <p>Es una lista corta y explicita a proposito: no pretende ser un diccionario (para eso esta
     * {@link #FILTRADAS}), solo atajar lo que la gente escribe cuando la regla le estorba.
     */
    private static final List<String> OBVIAS = List.of(
            "123456789012", "1234567890123", "0123456789012",
            "qwertyuiop12", "qwerty123456", "password1234", "contrasena12",
            "administrador", "administrador1", "abcdefghijkl", "aaaabbbbcccc");

    /**
     * Palabras que no deben aparecer dentro de una contrasena: son las del propio sistema, las que
     * cualquiera probaria primero sabiendo de que va la aplicacion. Se comparan en minusculas y sin
     * acentos, buscando la palabra EN CUALQUIER POSICION (asi "MiEscuela2026" tambien cae).
     *
     * <p>"sih" es corta a proposito: son las siglas del sistema y hay que atajarlas.
     */
    private static final List<String> TERMINOS_DEL_SISTEMA = List.of(
            "sih", "admin", "administrador", "escuela", "escuelas", "colegio", "preparatoria",
            "secundaria", "bachillerato", "instituto", "maestro", "maestros", "docente", "docentes",
            "alumno", "alumnos", "grupo", "grupos", "materia", "materias", "horario", "horarios",
            "semestre", "semestres", "turno", "turnos", "calendario", "direccion", "sistema",
            "usuario", "usuarios", "contrasena", "password", "mexico", "sihipa");

    /** Secuencias que se prueban antes que cualquier otra cosa. */
    private static final List<String> SECUENCIAS_TECLADO = List.of(
            "qwert", "asdfg", "zxcvb", "qazws", "wsxed", "poiuy", "lkjhg", "mnbvc");

    /**
     * Contrasenas filtradas, cargadas del archivo de recursos.
     *
     * <p>Si el archivo no estuviera o viniera truncado, esto LANZA al arrancar la aplicacion. Es
     * deliberado: una regla de seguridad que desaparece en silencio es peor que no tenerla, porque
     * nadie se enteraria. Mejor que no arranque.
     */
    private static final Set<String> FILTRADAS = cargarFiltradas();

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
        String normalizada = normalizar(password);
        if (OBVIAS.contains(normalizada)) {
            throw new NegocioExcepcion("password_debil",
                    "Esa contraseña es demasiado obvia. Elige una distinta.");
        }
        if (tieneSecuencia(normalizada)) {
            throw new NegocioExcepcion("password_debil",
                    "La contraseña no puede llevar secuencias de teclado ni de números seguidos "
                            + "(1234, abcd, qwerty...).");
        }
        if (contieneTerminoDelSistema(normalizada)) {
            throw new NegocioExcepcion("password_debil",
                    "La contraseña no puede contener palabras del sistema (admin, escuela, maestro, "
                            + "horario...). Elige algo que no se pueda adivinar sabiendo dónde trabajas.");
        }
        if (apareceEnFiltradas(normalizada)) {
            throw new NegocioExcepcion("password_debil",
                    "Esa contraseña aparece en listas públicas de contraseñas filtradas, así que es de "
                            + "las primeras que se prueban. Elige una distinta.");
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

    /** Cuantas contrasenas filtradas se cargaron. Sirve para comprobar el empaquetado en pruebas. */
    public static int totalFiltradas() {
        return FILTRADAS.size();
    }

    /** Minusculas y sin acentos, para que "Contraseña" y "contrasena" se comparen igual. */
    private static String normalizar(String password) {
        return password.toLowerCase(Locale.ROOT)
                .replace('á', 'a').replace('à', 'a').replace('ä', 'a').replace('â', 'a')
                .replace('é', 'e').replace('è', 'e').replace('ë', 'e').replace('ê', 'e')
                .replace('í', 'i').replace('ì', 'i').replace('ï', 'i').replace('î', 'i')
                .replace('ó', 'o').replace('ò', 'o').replace('ö', 'o').replace('ô', 'o')
                .replace('ú', 'u').replace('ù', 'u').replace('ü', 'u').replace('û', 'u')
                .replace('ñ', 'n');
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

    /**
     * Secuencias de 4 o mas caracteres consecutivos en cualquiera de los dos sentidos: 1234, 4321,
     * abcd, dcba. Ademas, las filas de teclado (qwerty, asdf...), que no son consecutivas en el
     * alfabeto pero se teclean de un tirón.
     */
    private static boolean tieneSecuencia(String normalizada) {
        int ascendente = 1;
        int descendente = 1;
        for (int i = 1; i < normalizada.length(); i++) {
            int diferencia = normalizada.charAt(i) - normalizada.charAt(i - 1);
            ascendente = (diferencia == 1) ? ascendente + 1 : 1;
            descendente = (diferencia == -1) ? descendente + 1 : 1;
            if (ascendente >= 4 || descendente >= 4) {
                return true;
            }
        }
        for (String fila : SECUENCIAS_TECLADO) {
            if (normalizada.contains(fila)) {
                return true;
            }
        }
        return false;
    }

    private static boolean contieneTerminoDelSistema(String normalizada) {
        for (String termino : TERMINOS_DEL_SISTEMA) {
            if (normalizada.contains(termino)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Comprueba la contrasena contra la lista de filtradas de dos formas:
     * <ol>
     *   <li>tal cual, en minusculas; y</li>
     *   <li>quitandole digitos y simbolos, para atrapar el caso real de H-07: una palabra comun con
     *       numeros pegados ("Admin1234567" -> "admin", que si esta en la lista).</li>
     * </ol>
     * El nucleo debe tener 4 letras o mas: con menos, "abc" o "uno" darian falsos positivos.
     */
    private static boolean apareceEnFiltradas(String normalizada) {
        if (FILTRADAS.contains(normalizada)) {
            return true;
        }
        String nucleo = normalizada.replaceAll("[^a-z]", "");
        return nucleo.length() >= 4 && FILTRADAS.contains(nucleo);
    }

    private static Set<String> cargarFiltradas() {
        try (InputStream entrada = PoliticaPassword.class.getResourceAsStream(RECURSO_FILTRADAS)) {
            if (entrada == null) {
                throw new IllegalStateException("No encuentro el archivo de recursos " + RECURSO_FILTRADAS);
            }
            Set<String> conjunto = new HashSet<>(20_000);
            try (BufferedReader lector = new BufferedReader(new InputStreamReader(entrada, StandardCharsets.UTF_8))) {
                String linea;
                while ((linea = lector.readLine()) != null) {
                    String limpia = linea.trim().toLowerCase(Locale.ROOT);
                    if (!limpia.isEmpty() && !limpia.startsWith("#")) {
                        conjunto.add(limpia);
                    }
                }
            }
            // Si el archivo viniera vacio o cortado, la regla no serviria de nada y nadie lo notaria.
            if (conjunto.size() < 1000) {
                throw new IllegalStateException(
                        "La lista de contrasenas filtradas parece incompleta: " + conjunto.size() + " entradas");
            }
            log.info("Politica de contrasenas: {} contrasenas filtradas cargadas", conjunto.size());
            return Set.copyOf(conjunto);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "No se pudo cargar la lista de contrasenas filtradas de " + RECURSO_FILTRADAS
                            + ". La politica de contrasenas no puede funcionar a medias.", e);
        }
    }
}
