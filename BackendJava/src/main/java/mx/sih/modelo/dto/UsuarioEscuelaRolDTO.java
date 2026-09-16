package mx.sih.modelo.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UsuarioEscuelaRolDTO {
    private Long id;
    private Long escuelaId;
    private String escuelaNombre;
    private Long rolId;
    private String rolNombre;
    private Boolean activo;
}