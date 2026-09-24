package mx.sih.ia;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Resultado de UN intento del generador IA: qué colocó, qué quedó pendiente y con qué score.
 *
 * <p>Se expone tal cual por la API (Jackson serializa los getters de Lombok), así que la pantalla de
 * "Horario IA" puede listar los intentos, mostrar las horas pendientes de cada uno y elegir cuál
 * registrar en el horario real.
 */
@Data
@NoArgsConstructor
public class IntentoIA {

    private int numero;
    /** "heuristica" o "llm:&lt;modelo&gt;" según el asesor que participó. */
    private String asesor = "heuristica";
    private LocalDateTime generadoEn = LocalDateTime.now();
    private long milisegundos;

    private int horas;
    private int horasDemandadas;
    private int sesionesLargas;
    private int sesionesLargasPendientes;
    private int arranquesTarde;
    private int castigoHuecos;
    private int adyacencias;
    private int materiasCompletas;
    private int materiasTotales;
    private int medium;

    /** Filas listas para escribir en la tabla `horario` (una por bloque ocupado). */
    @JsonIgnore
    private List<Fila> filas = new ArrayList<>();
    /** Sesiones que no se pudieron colocar, con el motivo. */
    private List<Pendiente> pendientes = new ArrayList<>();
    /** Violaciones DURAS detectadas por la comprobación independiente (0 = horario válido). */
    private List<String> problemas = new ArrayList<>();

    /**
     * Bitácora del intento: qué hizo cada fase y cuántas violaciones duras había al terminarla.
     * Sirve para auditar un intento sin depender del log del servidor.
     */
    private List<String> bitacora = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Fila {
        private Long asignacionId;
        private Long turnoHorarioId;
        /** Maestro elegido para esa sesión (en modo maestros puede diferir del de la asignación). */
        private Long maestroId;
        /** Taller (aula) elegido para esa sesión (en modo aulas puede diferir del de la asignación). */
        private Long aulaId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Pendiente {
        private Long asignacionId;
        private Long grupoId;
        private String grupoNombre;
        private String materiaClave;
        private String materiaNombre;
        private Long maestroId;
        private String maestroNombre;
        private int duracion;
        private String motivo;
        /** Bloques donde la sesión cabría por disponibilidad, con quién los ocupa. */
        private List<String> ventanas = new ArrayList<>();
    }
}
