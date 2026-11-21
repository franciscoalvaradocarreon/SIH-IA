package mx.sih.modelo.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Un tipo de laboratorio o taller tal como lo devuelve la API. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TipoEspacioDTO {

    private Long id;
    private Long semestreId;
    private Long turnoId;
    private String clase;
    private String nombre;
    private Boolean activo;
}
