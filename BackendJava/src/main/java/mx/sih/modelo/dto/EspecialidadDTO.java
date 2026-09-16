package mx.sih.modelo.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EspecialidadDTO {
    private Long id;
    private String nombre;
    private Long semestreId;
    private String semestreNombre;
    private Long turnoId;
    private String turnoNombre;
}