package mx.sih.modelo.dto;

import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
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

    // Metadatos
    private Long semestreId;
    private String semestreNombre;
    private LocalDateTime fechaGeneracion;

    // Score del solver
    private HardSoftScore score;
    private Integer hardScore;
    private Integer softScore;

    // Resumen de grupos procesados
    private Integer totalGrupos;
    private Integer gruposConHorario;
    private Integer gruposSinAsignaciones;
    private Integer gruposSinDisponibilidad;

    // Resumen de clases
    private Integer totalClasesAsignadas;
    private Integer totalAsignaciones;

    // Tiempo
    private Long tiempoMs;
    private Long tiempoSegundos;

    private Long turnoId;
    private String turnoNombre;
    
    // Detalle por grupo (opcional)
    private List<DetalleGrupoDTO> detalles = new ArrayList<>();

    // ============================================================
    // DTO interno para el detalle por grupo
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
        private String estado;   // "OK", "SIN_ASIGNACIONES", "SIN_DISPONIBILIDAD"
        private String mensaje;
    }
}