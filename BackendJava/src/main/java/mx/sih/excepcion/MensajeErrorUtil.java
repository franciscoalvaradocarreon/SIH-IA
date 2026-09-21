package mx.sih.excepcion;

import org.springframework.dao.DataIntegrityViolationException;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utilidades para traducir mensajes técnicos de PostgreSQL a mensajes de
 * negocio entendibles por el usuario.
 *
 * <h2>Casos cubiertos</h2>
 * <ol>
 *   <li><b>Violaciones de FK (dependencias):</b> cuando se intenta eliminar un
 *       registro que está referenciado por otra tabla. Ej: eliminar un semestre
 *       con turnos asociados.</li>
 *   <li><b>Violaciones de constraints EXCLUDE de horario:</b> cuando el solver
 *       o cualquier proceso intenta persistir dos clases que se solapan en el
 *       mismo bloque. Las constraints se llaman {@code no_solape_grupo_bloque},
 *       {@code no_solape_aula_bloque} y {@code no_solape_maestro_bloque}.</li>
 * </ol>
 *
 * <h2>¿Por qué regex y no SQLState?</h2>
 * Postgres devuelve un {@code SQLState} estándar:
 * <ul>
 *   <li>{@code 23503} → foreign_key_violation</li>
 *   <li>{@code 23P01} → exclusion_violation</li>
 * </ul>
 * Pero Spring envuelve la excepción y, con {@code getMostSpecificCause()},
 * podemos leer el mensaje. En la práctica, el mensaje de Postgres SIEMPRE
 * incluye el nombre de la constraint entre comillas dobles y eso es lo más
 * estable entre versiones. Los regex cubren los formatos en español e inglés
 * de Postgres (según locale del servidor).
 */
public final class MensajeErrorUtil {

    private MensajeErrorUtil() {
        // Utilidad estática: no instanciar.
    }

    // ============================================================
    // REGEX PARA DEPENDENCIAS (FK)
    // ============================================================

    /** Ej: "sigue siendo referida desde la tabla «turno»" */
    private static final Pattern P_TABLA_1 =
            Pattern.compile("referida desde la tabla «([^»]+)»");

    /** Ej: "en la tabla «turno»" */
    private static final Pattern P_TABLA_2 =
            Pattern.compile("en la tabla «([^»]+)»");

    /** Ej: "on table \"turno\"" (locale en inglés) */
    private static final Pattern P_TABLA_3 =
            Pattern.compile("of relation \"([^\"]+)\"");

    // ============================================================
    // REGEX PARA CONSTRAINTS DE SOLAPAMIENTO (EXCLUDE)
    // ============================================================

    /**
     * Extrae el nombre de la constraint entre comillas dobles.
     * Ej: "conflicting key value violates exclusion constraint \"no_solape_grupo_bloque\""
     * Captura: {@code no_solape_grupo_bloque}
     *
     * El nombre puede venir con o sin comillas (según versión de Postgres),
     * y en algunos casos con un prefijo de schema (`sih.no_solape_grupo_bloque`).
     */
    private static final Pattern P_CONSTRAINT =
            Pattern.compile("constraint\\s+\"?([a-zA-Z0-9_.]+)\"?", Pattern.CASE_INSENSITIVE);

    /**
     * Nombre de una de NUESTRAS constraints de solapamiento, buscado directamente en el mensaje.
     *
     * <h2>Por qué hace falta además de {@link #P_CONSTRAINT}</h2>
     * Postgres en español NO dice "constraint": dice
     * {@code ERROR: llave en conflicto viola la restricción de exclusión «no_solape_maestro_bloque»}
     * — la palabra es "exclusión" y el nombre viene entre comillas angulares, así que
     * {@code P_CONSTRAINT} no lo encontraba y el usuario recibía el mensaje genérico
     * "no se puede completar la operación porque hay registros relacionados" en lugar de
     * "solapa dos clases del mismo maestro. Regenera el horario del grupo".
     */
    private static final Pattern P_NOMBRE_CONSTRAINT =
            Pattern.compile("(no_solape_[a-zA-Z_]+)", Pattern.CASE_INSENSITIVE);

    /**
     * Mapeo de nombre de constraint → mensaje de negocio.
     * El código asociado se resuelve aparte en {@link #codigoDesdeConstraint}.
     */
    private static final Map<String, String> MENSAJES_CONSTRAINT = Map.of(
            "no_solape_grupo_bloque",
            "El horario generado solapa dos clases del mismo grupo en el mismo bloque.",

            "no_solape_aula_bloque",
            "El horario generado solapa dos clases en la misma aula.",

            "no_solape_maestro_bloque",
            "El horario generado solapa dos clases del mismo maestro."
    );

    // ============================================================
    // API PÚBLICA — DEPENDENCIAS (FK)
    // ============================================================

    /**
     * Extrae el nombre de la tabla del mensaje de PostgreSQL a partir de los
     * patrones conocidos (español e inglés). Devuelve {@code "desconocida"} si
     * no puede identificarla.
     */
    public static String extraerTablaDelMensaje(DataIntegrityViolationException e) {
        if (e == null || e.getMostSpecificCause() == null) return "desconocida";
        String msg = e.getMostSpecificCause().getMessage();
        if (msg == null || msg.isBlank()) return "desconocida";

        Matcher m1 = P_TABLA_1.matcher(msg);
        if (m1.find()) return m1.group(1);

        Matcher m2 = P_TABLA_2.matcher(msg);
        if (m2.find()) return m2.group(1);

        Matcher m3 = P_TABLA_3.matcher(msg);
        if (m3.find()) return m3.group(1);

        return "desconocida";
    }

    /**
     * Genera una {@link NegocioExcepcion} con un mensaje amigable cuando un
     * registro no se puede eliminar porque tiene dependencias.
     *
     * @param entidad  nombre legible de la entidad (ej: "el semestre", "el turno").
     * @param e        excepción original de PostgreSQL.
     */
    public static NegocioExcepcion crearExcepcionDependencia(
            String entidad, DataIntegrityViolationException e) {
        String tabla = extraerTablaDelMensaje(e);
        return new NegocioExcepcion(
                "DEPENDENCIAS",
                "No se puede eliminar " + entidad + " porque está siendo usado en la tabla: "
                        + tabla + ". Elimina primero esos registros o desactívalo."
        );
    }

    // ============================================================
    // API PÚBLICA — CONSTRAINTS DE SOLAPAMIENTO (EXCLUDE)
    // ============================================================

    /**
     * Detecta si la violación viene de una constraint de solapamiento de
     * horario y devuelve el mensaje de negocio asociado.
     *
     * <p>Devuelve {@code null} si la violación no es de una de nuestras
     * constraints conocidas. En ese caso, el llamante debe tratar la excepción
     * como genérica.
     *
     * @param e excepción original.
     * @return mensaje de negocio en español, o {@code null} si no aplica.
     */
    public static String detectarConstraintSolape(DataIntegrityViolationException e) {
        String nombre = extraerNombreConstraint(e);
        if (nombre == null) return null;
        return MENSAJES_CONSTRAINT.get(nombre);
    }

    /**
     * Devuelve un código de error estable para el frontend a partir del
     * nombre de la constraint. Ej: {@code "no_solape_grupo_bloque"} →
     * {@code "horario_solape_grupo"}.
     *
     * <p>Si la violación no es de una constraint conocida, devuelve
     * {@code "integridad_datos"} para que el handler genérico la procese.
     */
    public static String codigoDesdeConstraint(DataIntegrityViolationException e) {
        String nombre = extraerNombreConstraint(e);
        if (nombre == null) return "integridad_datos";

        return switch (nombre) {
            case "no_solape_grupo_bloque"   -> "horario_solape_grupo";
            case "no_solape_aula_bloque"    -> "horario_solape_aula";
            case "no_solape_maestro_bloque" -> "horario_solape_maestro";
            default                          -> "integridad_datos";
        };
    }

    // ============================================================
    // HELPERS INTERNOS
    // ============================================================

    /**
     * Extrae el nombre de la constraint desde el mensaje de Postgres.
     * Devuelve {@code null} si no lo encuentra.
     *
     * <p>El regex captura nombres con letras, números, guiones bajos y puntos
     * (por si Postgres incluye el schema en el nombre).
     */
    private static String extraerNombreConstraint(DataIntegrityViolationException e) {
        if (e == null || e.getMostSpecificCause() == null) return null;
        String msg = e.getMostSpecificCause().getMessage();
        if (msg == null || msg.isBlank()) return null;

        // Primero por nombre conocido (funciona con el locale en español y con el inglés)...
        Matcher propio = P_NOMBRE_CONSTRAINT.matcher(msg);
        if (propio.find()) {
            return propio.group(1).toLowerCase();
        }

        // ...y si no, el formato inglés "constraint \"...\"".
        Matcher m = P_CONSTRAINT.matcher(msg);
        if (!m.find()) return null;

        String nombre = m.group(1).toLowerCase();

        // Si viene con schema (ej: "sih.no_solape_grupo_bloque"), quitar el prefijo.
        int idx = nombre.lastIndexOf('.');
        if (idx >= 0 && idx < nombre.length() - 1) {
            nombre = nombre.substring(idx + 1);
        }

        return nombre;
    }
}