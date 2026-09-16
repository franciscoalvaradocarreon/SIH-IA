package mx.sih.modelo.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MenuSimpleDTO {
    private Long menuId;
    private String label;
    private String path;
    private String icono;
}