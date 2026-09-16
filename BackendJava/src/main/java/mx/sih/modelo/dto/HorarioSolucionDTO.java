// mx.sih.modelo.dto.HorarioSolucionDTO.java
package mx.sih.modelo.dto;

import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import mx.sih.modelo.solver.ClaseNoAsignadaDTO;

/**
 * Resultado de generar el horario de UN grupo.
 * NO es un ítem de horario (eso es HorarioDTO).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HorarioSolucionDTO {

    // Identificadores
    private Long grupoId;
    private String grupoNombre;
    private Long semestreId;
    private String semestreNombre;

    // Metadatos de la generación
    private LocalDateTime fechaGeneracion;
    private HardSoftScore score;
    private Integer version;

    // Resumen
    private Integer totalClasesAsignadas;
    private Integer totalClasesNoAsignadas;
    private List<ClaseNoAsignadaDTO> clasesNoAsignadas = new ArrayList<>();
}