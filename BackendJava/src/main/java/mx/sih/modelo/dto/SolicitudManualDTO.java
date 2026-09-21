package mx.sih.modelo.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Tanda de cambios del tablero manual (el "Guardar" del borrador local).
 *
 * <p>Se manda todo junto y se valida como un conjunto: si algún cambio tiene un problema
 * (choque de grupo, de maestro o de aula, indisponibilidad, bloque de otro turno, o la
 * materia ya con todas sus horas colocadas) <b>no se aplica ninguno</b> y se devuelven
 * todos los problemas para que el tablero los marque.
 *
 * <p>Con {@code validarSolo = true} solo se comprueba (útil para avisar antes de guardar
 * sin tocar la base).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SolicitudManualDTO {

    private Long semestreId;

    /** Si es true, no se escribe nada: solo se responde si la tanda es válida. */
    private Boolean validarSolo;

    private List<CambioManualDTO> cambios;
}
