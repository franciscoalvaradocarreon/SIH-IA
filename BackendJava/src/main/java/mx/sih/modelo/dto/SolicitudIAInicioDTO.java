package mx.sih.modelo.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Lo que pide la pantalla de Horario IA para lanzar una generación.
 *
 * <p>Todos los números son opcionales: si vienen vacíos se usan los valores configurados
 * ({@code app.ia.*}), que son los que también enseña la interfaz.
 */
@Data
@NoArgsConstructor
public class SolicitudIAInicioDTO {

    private Long semestreId;
    private Long turnoId;

    /** {@code heuristica} (motor propio) o {@code llm} (motor propio + asesor LLM si hay clave). */
    private String modo;

    /** Cuántos intentos completos se hacen. Cada uno parte de cero. */
    private Integer intentos;

    /** Tope de tiempo de cada intento, en segundos. */
    private Integer segundosPorIntento;

    /** Pasos de búsqueda local por intento. */
    private Integer maxPasos;

    /**
     * MODO "ASIGNAR MAESTROS DESDE EL STOCK" (sin tablas nuevas): si true, el motor elige el maestro
     * de cada materia entre los que ya la imparten en las asignaciones (stock), por disponibilidad y
     * carga, con la regla de Jóvenes (maestro con otra clase en el grupo y un Jóvenes por maestro).
     * Al registrar se escribe el maestro elegido en el horario.
     */
    private Boolean asignarMaestros;

    /**
     * Clave de la API del asesor LLM, SOLO para esta generación.
     *
     * <p>Viaja en la petición, se usa para crear el asesor de este trabajo y se descarta al terminar:
     * no se guarda en ningún fichero, no se escribe en el log y no se devuelve nunca en las
     * respuestas. Si viene vacía se usa la configurada en {@code app.ia.api-key}.
     */
    private String apiKey;

    /** Modelo a usar en esta generación (opcional; si viene vacío, el configurado). */
    private String modelo;

    /** URL del endpoint compatible con OpenAI (opcional; si viene vacía, la configurada). */
    private String url;
}
