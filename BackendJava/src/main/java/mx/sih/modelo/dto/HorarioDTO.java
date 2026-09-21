// mx.sih.modelo.dto.HorarioDTO.java
package mx.sih.modelo.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class HorarioDTO {
    private Long id;
    private Long grupoId;
    private String grupoNombre;
    private Long asignacionId;
    private String materiaNombre;
    private String materiaClave;
    private Long maestroId;
    private String maestroNombre;
    private Long turnoHorarioId;
    private Integer diaSemana;
    private String horaInicio;
    private String horaFin;
    private Long aulaId;
    private String aulaNombre;
    private String colorHex;
    private Integer version;
    private Long semestreId;
    private String semestreNombre;
}