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
    
    private Boolean activo = true;

    private Long semestreId;
}