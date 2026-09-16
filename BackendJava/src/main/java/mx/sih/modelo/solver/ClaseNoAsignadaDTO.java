// mx.sih.modelo.dto.ClaseNoAsignadaDTO.java
package mx.sih.modelo.solver;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClaseNoAsignadaDTO {
    private Long asignacionId;
    private Long grupoId;
    private String grupoNombre;
    private Long materiaId;
    private String materiaNombre;
    private String materiaClave;
    private Long maestroId;
    private String maestroNombre;
    private Long aulaId;
    private String aulaNombre;
    private String motivo;
}