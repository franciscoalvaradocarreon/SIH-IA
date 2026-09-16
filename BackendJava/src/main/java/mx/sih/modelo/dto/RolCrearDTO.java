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
public class RolCrearDTO {

    @NotBlank(message = "El nombre del rol es obligatorio")
    @Size(max = 30)
    private String nombre;

    @Size(max = 150)
    private String descripcion;
}