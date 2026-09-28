package mx.sih.modelo.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Resultado de importar aulas desde otro semestre.
 *
 * `omitidos` son las aulas del semestre de origen que NO se copiaron, cada una con el motivo entre
 * parentesis: ya existia ese nombre en el destino, o el destino no tiene el turno al que
 * pertenecia. La pantalla las muestra para que no parezca que se perdieron.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ResultadoImportacionAulasDTO {

    private int aulasCopiadas;
    private List<String> omitidos;
    private String mensaje;
}
