package mx.sih.modelo.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * Peticion para traer los bloques (la rejilla de horas) de un turno de OTRO semestre.
 *
 * <p>El turno de destino va en la ruta ({@code /api/turnos/{turnoId}/horarios/importar}) porque es el
 * que el usuario tiene abierto en la pantalla; aqui solo viaja de donde se traen.
 *
 * <p>{@code semestreDestinoId} se manda tambien (aunque el turno ya lo implica) como comprobacion:
 * si no coincide con el semestre del turno, la peticion se rechaza en lugar de copiar bloques al
 * semestre equivocado.
 */
@Getter
@Setter
public class ImportarTurnoHorarioDTO {

    @NotNull(message = "Falta el semestre de origen")
    private Long semestreOrigenId;

    @NotNull(message = "Falta el semestre de destino")
    private Long semestreDestinoId;
}
