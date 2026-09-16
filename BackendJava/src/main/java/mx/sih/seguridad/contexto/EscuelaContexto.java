package mx.sih.seguridad.contexto;

public class EscuelaContexto {
    private static final ThreadLocal<Long> ESCUELA_ACTUAL = new ThreadLocal<>();

    public static void setEscuelaId(Long escuelaId) {
        ESCUELA_ACTUAL.set(escuelaId);
    }

    public static Long getEscuelaId() {
        return ESCUELA_ACTUAL.get();
    }

    public static void limpiar() {
        ESCUELA_ACTUAL.remove();
    }
}