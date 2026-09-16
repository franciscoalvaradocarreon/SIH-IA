// mx.sih.modelo.dto.GrupoMateriaMaestroDTO.java
package mx.sih.modelo.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AsignacionDTO {
    private Long id;
    private Long grupoId;
    private String grupoNombre;
    private Long materiaId;
    private String materiaNombre;
    private String materiaClave;
    private Long maestroId;
    private String maestroNombre;
    private Long aulaId;
    private String aulaNombre;
    private Integer horas;
    private String colorHex;
    private String distribucion;
    private Boolean activo;
    private Long semestreId;
    private String semestreNombre;
    private Long turnoId;
    private String turnoNombre;
}