
package mx.sih.modelo.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 *
 * @author USER
 */
public record SolicitudRecuperacionDTO (
    @NotBlank @Email @Size(max = 100) String correo

) {}
