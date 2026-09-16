package mx.sih.modelo.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TurnoDTO {
    private Long id;
    private Long escuelaId;
    private String nombre;
    private String descripcion;
    private Boolean activo;
    private Long semestreId;
    private String semestreNombre;
}   