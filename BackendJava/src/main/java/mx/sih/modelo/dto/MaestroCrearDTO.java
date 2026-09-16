package mx.sih.modelo.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MaestroCrearDTO {

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 80)
    private String nombre;

    @NotBlank(message = "Los apellidos son obligatorios")
    @Size(max = 100)
    private String apellidos;

    @Email(message = "Formato de email inválido")
    @Size(max = 100)
    private String email;

    @Size(max = 40)
    private String telefono;

    @Size(max = 100)
    private String fotoUrl;
    
    @Size(max = 20)
    private String titulo;
    
    private MultipartFile fotoArchivo;
    
    @NotNull(message = "El semestre es obligatorio")
    private Long semestreId;
    
    @NotNull(message = "El turno es obligatorio")
    private Long turnoId;
}