package mx.sih.modelo.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Resultado de importar turnos desde otro semestre.
 *
 * `omitidos` son los turnos del semestre de origen cuyo nombre ya existia en el destino: no se
 * tocaron. La pantalla los muestra para que no parezca que se perdieron.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ResultadoImportacionTurnosDTO {

    private int turnosCopiados;
    private List<String> omitidos;
    private String mensaje;
}
