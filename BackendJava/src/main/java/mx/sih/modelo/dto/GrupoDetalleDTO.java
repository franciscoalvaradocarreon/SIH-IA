package mx.sih.modelo.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GrupoDetalleDTO {
    private Long id;
    private String nombre;
    private Integer grado;
    private Long turnoId;
    private String turnoNombre;
    private Long especialidadId;
    private String especialidadNombre;
    private Integer capacidad;
    private Boolean activo;
    private Long semestreId;
}