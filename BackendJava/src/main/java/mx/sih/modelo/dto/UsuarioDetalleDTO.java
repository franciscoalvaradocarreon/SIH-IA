package mx.sih.modelo.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UsuarioDetalleDTO {
    private Long id;
    private String usuario;
    private String nombreCompleto;
    private String email;
    private String fotoUrl;
    private Boolean activo;
    private LocalDateTime ultimoAcceso;

    /**
     * Ultimo dia de vigencia; {@code null} = sin limite.
     *
     * <p>Tiene que estar aqui Y en {@link UsuarioDTO}: son dos salidas distintas del mismo usuario (el
     * listado y el detalle), y una se olvido en la primera version del cambio, con el efecto de que el
     * formulario de edicion abria el campo vacio aunque la fecha estuviera guardada.
     */
    private LocalDate vigenteHasta;

    private List<UsuarioEscuelaRolDTO> asignaciones;
}