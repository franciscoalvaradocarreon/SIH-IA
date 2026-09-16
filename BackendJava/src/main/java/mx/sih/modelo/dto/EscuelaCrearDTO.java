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
public class EscuelaCrearDTO {

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 150)
    private String nombre;

    @Size(max = 150)
    private String nombreLargo;

    @Size(max = 255)
    private String direccion;

    @Size(max = 20)
    private String telefono;

    @Size(max = 30)
    private String clave;

    @Size(max = 255)
    private String logoUrl;
}