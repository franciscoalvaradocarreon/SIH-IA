package mx.sih.modelo.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import mx.sih.seguridad.PoliticaPassword;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
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

    // El minimo y el maximo salen de la politica (hallazgo H-06): asi la validacion de entrada y la
    // del servicio no se pueden desfasar. La regla de composicion (letras y numeros, nada de obvias)
    // la comprueba PoliticaPassword en el servicio, que es donde se puede explicar el motivo.
    @Size(min = PoliticaPassword.LONGITUD_MINIMA, max = PoliticaPassword.LONGITUD_MAXIMA,
            message = "La contraseña debe tener al menos 12 caracteres y no más de 128")
    private String password;

    private Boolean activo = true;

    /**
     * Ultimo dia de vigencia de la cuenta (formato ISO, {@code 2026-12-31}). Vacio = sin limite.
     *
     * <p>Es opcional y lo edita quien edita el usuario, como cualquier otro campo. No se aplica a
     * quien tiene el rol ADMIN.
     */
    private LocalDate vigenteHasta;

    private List<AsignacionEscuelaRolDTO> asignaciones;

    private MultipartFile fotoArchivo;
}