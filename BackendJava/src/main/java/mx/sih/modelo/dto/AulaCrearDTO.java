package mx.sih.modelo.dto;

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
public class AulaCrearDTO {

    @NotBlank(message = "El nombre del aula es obligatorio")
    @Size(max = 50)
    private String nombre;

    @Size(max = 50)
    private String edificio;

    @Size(max = 50)
    private String piso;

    @Size(max = 255)
    private String descripcion;

    private Boolean activo = true;

    /** true = el aula es un taller (sala de practica). */
    private Boolean taller = false;
    
    @NotNull(message = "El semestre es obligatorio")
    private Long semestreId;
    
    @NotNull(message = "El turno es obligatorio")
    private Long turnoId;
}