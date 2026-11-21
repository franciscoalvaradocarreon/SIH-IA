package mx.sih.modelo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Lo que manda la pantalla para crear o editar un tipo de laboratorio o taller. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TipoEspacioCrearDTO {

    /** El semestre se deduce del turno; la pantalla solo manda el turno. */
    private Long turnoId;

    /** {@code LABORATORIO} o {@code TALLER}. */
    @NotBlank(message = "Indica si es laboratorio o taller")
    @Size(max = 20)
    private String clase;

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 80)
    private String nombre;

    private Boolean activo = true;
}
