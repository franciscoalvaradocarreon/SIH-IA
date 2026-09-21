// mx.sih.modelo.dto.HorarioSolucionMasivaDTO.java
package mx.sih.modelo.dto;

import ai.timefold.solver.core.api.score.buildin.hardmediumsoft.HardMediumSoftScore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class HorarioSolucionMasivaDTO {

    // ============================================================
    // Metadatos
    // ============================================================
    private Long semestreId;
    private String semestreNombre;
    private LocalDateTime fechaGeneracion;

    // ============================================================
    // Score
    // ============================================================
    private HardMediumSoftScore score;
    private Integer hardScore;
    private Integer mediumScore;
    private Integer softScore;

    // ============================================================
    // Factibilidad
    // ============================================================
    /** {@code true} si hardScore >= 0 y los horarios se persistieron. */
    private Boolean factible;

    /** Mensaje resumen cuando {@code factible = false}. Null si es factible. */
    private String motivoInfactibilidad;

    /** Desglose de violaciones hard por constraint. Vacío si es factible. */
    private List<ViolacionConstraintDTO> violacionesHard = new ArrayList<>();

    // ============================================================
    // Resumen de grupos
    // ============================================================
    private Integer totalGrupos;
    private Integer gruposConHorario;
    private Integer gruposSinAsignaciones;
    private Integer gruposSinDisponibilidad;

    // ============================================================
    // Resumen de clases
    // ============================================================
    private Integer totalClasesAsignadas;
    private Integer totalAsignaciones;

    // ============================================================
    // Tiempo
    // ============================================================
    private Long tiempoMs;
    private Long tiempoSegundos;

    // ============================================================
    // Filtros aplicados
    // ============================================================
    private Long turnoId;
    private String turnoNombre;

    // ============================================================
    // Detalles
    // ============================================================
    /** Detalle por grupo (compatibilidad con la tabla anterior). */
    private List<DetalleGrupoDTO> detalles = new ArrayList<>();

    /** Conflictos específicos detectados en la solución infactible. */
    private List<DetalleConflictoDTO> conflictosDetectados = new ArrayList<>();

    /**
     * Detalle por asignación: la tabla principal del frontend.
     * Cada fila representa una asignación (grupo + materia + maestro + aula)
     * con su estado: OK / PARCIAL / SIN_ASIGNAR.
     */
    private List<DetalleAsignacionDTO> detalleAsignaciones = new ArrayList<>();

    // ============================================================
    // DTO interno: detalle por grupo
    // ============================================================
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DetalleGrupoDTO {
        private Long grupoId;
        private String grupoNombre;
        private Integer grado;
        private String turno;
        private Integer clasesAsignadas;
        /** "OK" | "SIN_ASIGNACIONES" | "SIN_DISPONIBILIDAD" */
        private String estado;
        private String mensaje;
    }

    // ============================================================
    //  DTO interno: detalle por asignación
    // ============================================================
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DetalleAsignacionDTO {
        private Long asignacionId;
        private Long grupoId;
        private String grupoNombre;
        private Integer grupoGrado;
        private String especialidadNombre;
        private Long materiaId;
        private String materiaClave;
        private String materiaNombre;
        private Long maestroId;
        private String maestroNombre;
        private Long aulaId;
        private String aulaNombre;
        private Long turnoId;
        private String turnoNombre;
        private Integer horasEsperadas;
        private Integer clasesAsignadas;
        private Integer clasesSinAsignar;
        /** "OK" | "PARCIAL" | "SIN_ASIGNAR" */
        private String estado;
        /** Motivo si estado != OK. Null si OK. */
        private String motivo;
    }

    // ============================================================
    //  DTO interno: violación de constraint hard
    // ============================================================
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ViolacionConstraintDTO {
        /** Nombre legible de la constraint (ej. "Conflicto de grupo"). */
        private String constraint;
        /** Detalle con score y número de matches (ej. "-5hard (matches=5)"). */
        private String detalle;
    }
    
    // ============================================================
    // DTO interno: conflicto específico
    // ============================================================
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DetalleConflictoDTO {
        /** "GRUPO" | "MAESTRO" | "AULA" | "MAESTRO_NO_DISPONIBLE" | "GRUPO_NO_DISPONIBLE" | "BLOQUE_FUERA_TURNO" */
        private String tipo;
        private String titulo;
        private String bloqueTexto;          // "Lunes 07:00-07:50"
        private String grupoNombre;
        private String maestroNombre;
        private String aulaNombre;
        /** Lista de materias en conflicto (clave + nombre) */
        private List<String> materias;
        private String sugerencia;
    }
    
}