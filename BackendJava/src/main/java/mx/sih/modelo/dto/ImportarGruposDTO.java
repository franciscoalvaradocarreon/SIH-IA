package mx.sih.modelo.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * Peticion para traer los grupos de otro semestre al semestre de destino.
 *
 * Los dos semestres son de la escuela activa: la escuela sale del contexto de seguridad, no del
 * cuerpo de la peticion.
 *
 * `turnoId` es el turno de DESTINO que el usuario tiene seleccionado en la pantalla y acota la
 * importacion: solo se traen los grupos del turno del origen que se llame igual. Como cada turno lo
 * trabaja gente distinta, traer todo el semestre haria aparecer datos que nadie pidio.
 */
@Getter
@Setter
public class ImportarGruposDTO {

    @NotNull(message = "Falta el semestre de origen")
    private Long semestreOrigenId;

    @NotNull(message = "Falta el semestre de destino")
    private Long semestreDestinoId;

    @NotNull(message = "Falta el turno")
    private Long turnoId;
}
