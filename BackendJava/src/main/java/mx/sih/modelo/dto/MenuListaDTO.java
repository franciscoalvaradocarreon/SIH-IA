package mx.sih.modelo.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MenuListaDTO {
    private Long menuId;
    private String label;
    private String path;
    private String icono;
    private Long parienteId;
    private String parienteLabel;
    private Integer nivel;
    private Integer menuOrden;
    private Boolean activo;
}