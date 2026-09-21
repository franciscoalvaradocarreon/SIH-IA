package mx.sih.modelo.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Un cambio del tablero manual de horarios.
 *
 * <p>Tres operaciones posibles, que son exactamente las que se pueden hacer con los pines:
 * <ul>
 *   <li>{@code COLOCAR}: tomar un pin de la caja y ponerlo en un bloque
 *       (requiere {@code asignacionId} + {@code turnoHorarioId}).</li>
 *   <li>{@code MOVER}: cambiar de bloque un pin ya colocado
 *       (requiere {@code horarioId} + {@code turnoHorarioId}).</li>
 *   <li>{@code QUITAR}: mandar un pin del tablero a la caja (requiere {@code horarioId}).</li>
 * </ul>
 *
 * <p>Para intercambiar dos clases de lugar se manda un QUITAR y un COLOCAR en la misma
 * tanda (el orden importa: el QUITAR libera el bloque). El aula no se manda: el backend
 * asigna una libre del turno del grupo.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CambioManualDTO {

    /** COLOCAR | MOVER | QUITAR (se acepta en cualquier combinación de mayúsculas). */
    private String tipo;

    /** Asignación (materia + grupo + maestro) cuya hora se coloca. Solo para COLOCAR. */
    private Long asignacionId;

    /** Horario existente que se mueve o se quita. Solo para MOVER y QUITAR. */
    private Long horarioId;

    /** Bloque destino (día + hora). Solo para COLOCAR y MOVER. */
    private Long turnoHorarioId;
}
