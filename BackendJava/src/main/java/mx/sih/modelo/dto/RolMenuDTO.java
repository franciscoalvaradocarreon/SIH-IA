package mx.sih.modelo.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RolMenuDTO {
    private Long rolId;
    private String rolNombre;
    private Long menuId;
    private String menuLabel;
    private String menuPath;
}