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
public class MenuCrearDTO {

    @NotBlank(message = "La etiqueta es obligatoria")
    @Size(max = 100)
    private String label;

    @Size(max = 255)
    private String path;

    @Size(max = 50)
    private String icono;

    private Long parienteId = 0L;

    private Integer nivel = 1;

    private Integer menuOrden = 0;

    private Boolean activo = true;
    
}
