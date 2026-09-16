package mx.sih.excepcion;

import org.springframework.dao.DataIntegrityViolationException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MensajeErrorUtil {

    private static final Pattern P_TABLA_1 = Pattern.compile("referida desde la tabla «([^»]+)»");
    private static final Pattern P_TABLA_2 = Pattern.compile("en la tabla «([^»]+)»");
    private static final Pattern P_TABLA_3 = Pattern.compile("of relation \"([^\"]+)\"");

    /**
     * Extrae el nombre de la tabla del mensaje de PostgreSQL.
     */
    public static String extraerTablaDelMensaje(DataIntegrityViolationException e) {
        if (e.getMostSpecificCause() == null) return "desconocida";
        String msg = e.getMostSpecificCause().getMessage();
        if (msg == null) return "desconocida";

        Matcher m1 = P_TABLA_1.matcher(msg);
        if (m1.find()) return m1.group(1);

        Matcher m2 = P_TABLA_2.matcher(msg);
        if (m2.find()) return m2.group(1);

        Matcher m3 = P_TABLA_3.matcher(msg);
        if (m3.find()) return m3.group(1);

        return "desconocida";
    }

    /**
     * Genera una NegocioExcepcion con un mensaje amigable.
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
}