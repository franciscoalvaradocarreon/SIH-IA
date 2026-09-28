package mx.sih.modelo.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Resultado de importar materias desde otro semestre.
 *
 * `omitidos` son las materias del semestre de origen que NO se copiaron, cada una con el motivo
 * entre parentesis: ya existia la clave en el destino, o el destino no tiene el turno al que
 * pertenecia. La pantalla las muestra para que no parezca que se perdieron.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ResultadoImportacionMateriasDTO {

    private int materiasCopiadas;
    private List<String> omitidos;
    private String mensaje;
}
