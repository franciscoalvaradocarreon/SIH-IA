package mx.sih.modelo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Estado de un trabajo de generación masiva de horarios.
 *
 * La generación dejó de ser síncrona: el solver tarda hasta 300 s, así que
 * {@code POST /api/horarios/generar-todos} responde {@code 202 Accepted} con uno de
 * estos trabajos y el cliente consulta su estado en
 * {@code GET /api/horarios/generar-todos/{id}}.
 *
 * Ventajas: la petición HTTP no se queda colgada minutos (con riesgo de timeout en
 * navegador, proxy o balanceador), el usuario ve progreso real, y el resultado no se
 * pierde aunque cierre la pestaña (queda consultable durante un rato).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TrabajoGeneracionDTO {

    private String id;

    /** EN_COLA | EN_PROCESO | COMPLETADO | ERROR */
    private String estado;

    /** Mensaje legible para mostrar en la interfaz. */
    private String mensaje;

    private LocalDateTime encoladoEn;
    private LocalDateTime iniciadoEn;
    private LocalDateTime finalizadoEn;

    /** Segundos desde que empezó (o desde que se encoló si aún no ha empezado). */
    private Long segundosTranscurridos;

    /**
     * Tiempo MÁXIMO configurado para la generación masiva, en segundos
     * ({@code app.solver.masiva.seconds-spent-limit}).
     *
     * <p>La interfaz lo usa para dibujar la barra de progreso real (antes estaba fija al 75 %).
     * Va aquí y no cableado en el front para que la barra siga al valor configurado del solver.
     */
    private Long limiteSegundos;

    private Long semestreId;
    private Long turnoId;

    /** Quién lanzó la generación (correo del usuario). Trazabilidad. */
    private String solicitadoPor;

    /** Mensaje de negocio cuando {@code estado = ERROR}. Null en el resto de estados. */
    private String error;

    /** Resultado completo. Solo llega cuando {@code estado = COMPLETADO}. */
    private HorarioSolucionMasivaDTO resultado;
}
