package mx.sih.modelo.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MateriaCrearDTO {

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 100)
    private String nombre;

    @NotBlank(message = "La clave es obligatoria")
    @Size(max = 50)
    private String clave;

    private String descripcion;

    @Min(value = 0, message = "Los créditos no pueden ser negativos")
    private Integer creditos = 0;

    @Size(max = 7, message = "El color debe tener formato hexadecimal (ej. #FF0000)")
    private String colorHex = "#808080";

    @NotNull(message = "Las horas por semana son obligatorias")
    @Min(value = 0, message = "Las horas por semana no pueden ser negativas")
    private Integer horasSemana;
    
    @NotNull(message = "El semestre es obligatorio")
    private Long semestreId;
    
    @NotNull(message = "El turno es obligatorio")
    private Long turnoId;
}