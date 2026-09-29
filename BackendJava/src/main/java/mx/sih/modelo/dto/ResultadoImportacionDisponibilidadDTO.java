package mx.sih.modelo.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Resultado de traer la disponibilidad desde otro semestre.
 *
 * <p>Aqui `omitidos` NO es una lista por fila (una importacion puede tener mas de 1.500 filas), sino
 * un resumen por motivo, con ejemplos: cuantas filas se saltaron porque el maestro no esta en este
 * turno, porque el bloque no existe en este turno, o porque ya estaban configuradas.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ResultadoImportacionDisponibilidadDTO {

    /** Filas de disponibilidad creadas. */
    private int filasCopiadas;

    /** Cuantos maestros (o grupos) distintos recibieron al menos una fila. */
    private int duenosCopiados;

    private List<String> omitidos;
    private String mensaje;
}
