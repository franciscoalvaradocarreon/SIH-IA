package mx.sih.modelo.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DisponibilidadGrupoDTO {
    private Long id;
    private Long grupoId;
    private String grupoNombre;
    private Long turnoHorarioId;
    private Integer diaSemana;
    private String horaInicio;
    private String horaFin;
    private Boolean disponible;
    private Long semestreId;
    private String semestreNombre;
}