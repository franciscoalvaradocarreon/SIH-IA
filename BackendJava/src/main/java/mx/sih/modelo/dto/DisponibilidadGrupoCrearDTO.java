package mx.sih.modelo.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DisponibilidadGrupoCrearDTO {

    @NotNull(message = "El grupo es obligatorio")
    private Long grupoId;

    @NotNull(message = "El horario es obligatorio")
    private Long turnoHorarioId;

    @NotNull(message = "La disponibilidad es obligatoria")
    private Boolean disponible;

    @NotNull(message = "El semestre es obligatorio")
    private Long semestreId;
}