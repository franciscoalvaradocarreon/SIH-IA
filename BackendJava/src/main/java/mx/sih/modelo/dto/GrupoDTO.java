package mx.sih.modelo.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GrupoDTO {
    private Long id;
    private String nombre;
    private Integer grado;
    private Long turnoId;
    private String turno;
    private String especialidad;
    private Integer capacidad;
    private Boolean activo;
    private Long semestreId;
    private String semestreNombre;
}