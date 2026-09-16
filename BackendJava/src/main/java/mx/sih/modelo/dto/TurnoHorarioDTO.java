package mx.sih.modelo.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;
import lombok.Builder;
import lombok.Data;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TurnoHorarioDTO {
    private Long id;
    private Long turnoId;
    private String turnoNombre;
    private Integer diaSemana;
    private String diaNombre;
    private String horaInicio;
    private String horaFin;
    private Boolean descanso;
    private Integer orden;
    private Long semestreId;
    private String semestreNombre;
}