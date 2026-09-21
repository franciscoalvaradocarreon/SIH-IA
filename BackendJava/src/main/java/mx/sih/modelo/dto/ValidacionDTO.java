package mx.sih.modelo.dto;

public record ValidacionDTO(
    String codigo,
    String titulo,
    String estado,     // "OK" | "ADVERTENCIA" | "ERROR"
    String mensaje
) {
    public static ValidacionDTO ok(String codigo, String titulo, String mensaje) {
        return new ValidacionDTO(codigo, titulo, "OK", mensaje);
    }
    public static ValidacionDTO advertencia(String codigo, String titulo, String mensaje) {
        return new ValidacionDTO(codigo, titulo, "ADVERTENCIA", mensaje);
    }
    public static ValidacionDTO error(String codigo, String titulo, String mensaje) {
        return new ValidacionDTO(codigo, titulo, "ERROR", mensaje);
    }
}