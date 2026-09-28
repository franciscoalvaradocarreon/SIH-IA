package mx.sih.modelo.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Resultado de importar grupos desde otro semestre.
 *
 * `omitidos` son los grupos del semestre de origen que NO se copiaron, cada uno con el motivo entre
 * parentesis: ya existia, el destino no tiene el turno, o el turno del destino no tiene la
 * especialidad a la que pertenecia. La pantalla los muestra para que no parezca que se perdieron.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ResultadoImportacionGruposDTO {

    private int gruposCopiados;
    private List<String> omitidos;
    private String mensaje;
}
