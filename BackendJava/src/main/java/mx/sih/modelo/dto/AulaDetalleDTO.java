package mx.sih.modelo.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AulaDetalleDTO {
    private Long id;
    private String nombre;
    private String edificio;
    private String piso;
    private String descripcion;
    private Boolean activo;
    private Boolean taller;
    private Long semestreId;
    private String semestreNombre;
    private Long turnoId;
    private String turnoNombre;
}