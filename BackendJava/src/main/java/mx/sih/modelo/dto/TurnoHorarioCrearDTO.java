package mx.sih.modelo.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TurnoHorarioCrearDTO {

    @NotNull(message = "El día de la semana es obligatorio")
    @Min(value = 1, message = "El día debe ser entre 1 y 5")
    @Max(value = 5, message = "El día debe ser entre 1 y 5")
    private Integer diaSemana;

    @NotNull(message = "La hora de inicio es obligatoria")
    @JsonFormat(pattern = "HH:mm", shape = JsonFormat.Shape.STRING)
    private LocalTime horaInicio;

    @NotNull(message = "La hora de fin es obligatoria")
    @JsonFormat(pattern = "HH:mm", shape = JsonFormat.Shape.STRING)
    private LocalTime horaFin;

    private Boolean descanso = false;

    private Integer orden = 0;
    
    @NotNull(message = "El semestre es obligatorio")
    private Long semestreId;
}