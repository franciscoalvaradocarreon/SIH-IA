package mx.sih.modelo.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Resultado de importar maestros desde otro semestre.
 *
 * `omitidos` son los maestros del semestre de origen que NO se copiaron, cada uno con el motivo
 * entre parentesis: ya existia en el destino, o el destino no tiene el turno al que pertenecia.
 * La pantalla los muestra para que no parezca que se perdieron.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ResultadoImportacionMaestrosDTO {

    private int maestrosCopiados;
    private List<String> omitidos;
    private String mensaje;
}
