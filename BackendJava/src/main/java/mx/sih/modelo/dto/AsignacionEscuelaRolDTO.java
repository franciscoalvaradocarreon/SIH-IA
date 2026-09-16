package mx.sih.modelo.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AsignacionEscuelaRolDTO {
    private Long escuelaId;
    private Long rolId;
    private Boolean activo = true;
}