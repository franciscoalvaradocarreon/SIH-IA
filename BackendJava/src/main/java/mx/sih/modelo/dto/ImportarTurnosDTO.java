package mx.sih.modelo.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * Peticion para traer los turnos de otro semestre (con sus bloques de horario) al semestre de
 * destino. Los dos semestres son de la escuela activa: la escuela sale del contexto, no del cuerpo.
 */
@Getter
@Setter
public class ImportarTurnosDTO {

    @NotNull(message = "Falta el semestre de origen")
    private Long semestreOrigenId;

    @NotNull(message = "Falta el semestre de destino")
    private Long semestreDestinoId;
}
