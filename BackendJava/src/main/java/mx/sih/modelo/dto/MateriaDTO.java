package mx.sih.modelo.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MateriaDTO {
    private Long id;
    private String nombre;
    private String clave;
    private String descripcion;
    private Integer creditos;
    private Integer horasSemana;
    private String colorHex;
    private Boolean activo;
    private Long semestreId;
    private String semestreNombre;
    private Long turnoId;
    private String turnoNombre;
}