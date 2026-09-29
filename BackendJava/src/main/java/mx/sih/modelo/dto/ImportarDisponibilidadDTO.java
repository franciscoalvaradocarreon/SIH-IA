package mx.sih.modelo.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * Peticion para traer la disponibilidad (de maestros o de grupos) desde OTRO semestre.
 *
 * <p>Se comparte entre las dos pantallas porque piden exactamente lo mismo.
 *
 * <p>El TURNO es la pieza clave: la disponibilidad es "quien puede dar clase en que bloque", y los
 * bloques pertenecen a un turno. Por eso se trae turno por turno y no el semestre entero de una vez:
 * asi el usuario ve el resultado sobre la rejilla que tiene delante, y no se mezclan bloques de
 * MATUTINO con maestros de VESPERTINO.
 */
@Getter
@Setter
public class ImportarDisponibilidadDTO {

    @NotNull(message = "Falta el semestre de origen")
    private Long semestreOrigenId;

    @NotNull(message = "Falta el semestre de destino")
    private Long semestreDestinoId;

    /** Turno de DESTINO (el que esta seleccionado en la pantalla). */
    @NotNull(message = "Falta el turno")
    private Long turnoId;
}
