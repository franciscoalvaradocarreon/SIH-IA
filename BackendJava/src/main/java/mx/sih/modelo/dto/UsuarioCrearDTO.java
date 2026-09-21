package mx.sih.modelo.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UsuarioCrearDTO {

    @NotBlank(message = "El nombre de usuario es obligatorio")
    @Size(max = 50)
    private String usuario;

    @NotBlank(message = "El nombre completo es obligatorio")
    @Size(max = 150)
    private String nombreCompleto;

    @NotBlank(message = "El email es obligatorio")
    @Email(message = "Formato de email inválido")
    @Size(max = 100)
    private String email;

    @Size(max = 255)
    private String fotoUrl;

    @Size(max = 128, message = "La contraseña no puede exceder 128 caracteres")
    private String password;

    private Boolean activo = true;

    private List<AsignacionEscuelaRolDTO> asignaciones;

    private MultipartFile fotoArchivo;
}