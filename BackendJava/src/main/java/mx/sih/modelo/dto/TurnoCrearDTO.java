package mx.sih.modelo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TurnoCrearDTO {

    @NotBlank(message = "El nombre del turno es obligatorio")
    @Size(max = 40)
    private String nombre;

    private String descripcion;
    
    /**
     * SIN valor por defecto a propósito.
     *
     * Si se inicializa a true, un PUT que no envíe el campo llega como true y
     * REACTIVA el turno al editarlo (un turno inactivo volvía a estar activo solo
     * por cambiarle la descripción). Al dejarlo nulo:
     *   · crear  -> nulo significa "activo por defecto" (lo resuelve TurnoServicio)
     *   · editar -> nulo significa "no cambiar el estado" (hay PATCH /{id}/estado)
     */
    private Boolean activo;

    private Long semestreId;
}