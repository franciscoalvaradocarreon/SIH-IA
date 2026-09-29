package mx.sih.modelo.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Resultado de traer los bloques de un turno de otro semestre.
 *
 * <p>`omitidos` son los bloques del origen que NO se copiaron, cada uno con el motivo entre
 * parentesis: en el destino ya habia un bloque en esa hora (o que se solapa con ella). La pantalla
 * los muestra para que no parezca que se perdieron.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ResultadoImportacionTurnoHorarioDTO {

    private int bloquesCopiados;
    private List<String> omitidos;
    private String mensaje;
}
