package mx.sih.modelo.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import mx.sih.ia.IntentoIA;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Estado de un trabajo del GENERADOR IA (sin Timefold).
 *
 * <p>Igual que la generación masiva, corre en segundo plano y el cliente consulta el estado: el
 * motor lanza varios intentos y cada uno tarda lo que diga {@code app.ia.segundos-por-intento}, así
 * que la petición HTTP no puede quedarse esperando.
 *
 * <p>Los intentos van en {@code intentos} (sin las filas del horario, que son cosa del servidor):
 * cada uno trae sus horas colocadas, su score y sus horas pendientes con el motivo.
 */
@Data
@NoArgsConstructor
public class TrabajoIADTO {

    private String id;

    /** EN_COLA | EN_PROCESO | COMPLETADO | TERMINADO | ERROR */
    private String estado;

    private String mensaje;

    /** heuristica | llm */
    private String modo;

    private LocalDateTime encoladoEn;
    private LocalDateTime iniciadoEn;
    private LocalDateTime finalizadoEn;
    private Long segundosTranscurridos;

    /** Tope de tiempo de CADA intento, en segundos. */
    private Long segundosPorIntento;

    private Long semestreId;
    private Long turnoId;
    private String solicitadoPor;

    /** Cuántos intentos se van a hacer y por cuál va. */
    private int intentosPlaneados;
    private int intentoActual;

    private int horasDemandadas;

    /** Número del intento con mejor resultado (null mientras no haya ninguno). */
    private Integer mejorNumero;

    /** Número del intento que se registró en el horario real (null si aún no se ha registrado). */
    private Integer registrado;
    private LocalDateTime registradoEn;
    private int filasRegistradas;
    private int horasRegistradas;

    /** true si el usuario pidió terminar antes de agotar los intentos. */
    private boolean terminadoPorUsuario;

    /** Mensaje de negocio cuando {@code estado = ERROR}. */
    private String error;

    /** Pre-validación que se hizo antes de generar (errores, avisos e imposibles). */
    private ValidacionIADTO validacion;

    private List<IntentoIA> intentos = new ArrayList<>();
}
