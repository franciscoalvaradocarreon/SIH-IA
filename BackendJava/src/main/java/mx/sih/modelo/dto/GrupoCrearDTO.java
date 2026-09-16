package mx.sih.modelo.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GrupoCrearDTO {

    @NotBlank(message = "El nombre del grupo es obligatorio")
    @Size(max = 20)
    private String nombre;

    @NotNull(message = "El grado es obligatorio")
    @Min(value = 1, message = "El grado debe ser entre 1 y 6")
    @Max(value = 6, message = "El grado debe ser entre 1 y 6")
    private Integer grado;

    @NotNull(message = "El turno es obligatorio")
    private Long turnoId;

    private Long especialidadId;

    @Min(value = 0, message = "La capacidad no puede ser negativa")
    private Integer capacidad = 0;

    private Boolean activo = true;
    
    @NotNull(message = "El semestre es obligatorio")
    private Long semestreId;
}