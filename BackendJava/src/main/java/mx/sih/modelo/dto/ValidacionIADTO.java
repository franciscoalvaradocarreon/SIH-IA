package mx.sih.modelo.dto;

import java.util.List;

/**
 * PRE-VALIDACIÓN DEL GENERADOR IA.
 *
 * <p>Antes de lanzar intentos, el motor IA comprueba que la información con la que va a trabajar no
 * tenga inconsistencias. Devuelve tres cosas:
 *
 * <ol>
 *   <li>{@code backend}: el resultado del validador que ya existe ({@code GET /api/horarios/validar}),
 *       para no tener dos verdades distintas.</li>
 *   <li>{@code chequeos}: comprobaciones PROPIAS del motor IA (ventanas legales por materia, tramos
 *       continuos del turno, aulas, maestros sin disponibilidad...).</li>
 *   <li>{@code imposibles}: las asignaciones que no tienen NINGUNA ventana legal con los datos
 *       actuales. Son las que nunca se podrán colocar, por muchas vueltas que dé el motor.</li>
 * </ol>
 *
 * @param backend         validaciones del backend de siempre
 * @param chequeos        chequeos del motor IA
 * @param imposibles      asignaciones sin ninguna ventana legal
 * @param aptoParaGenerar true si no hay errores (los imposibles NO bloquean: se generan y se reportan)
 * @param grupos          grupos del alcance
 * @param asignaciones    asignaciones del alcance
 * @param sesiones        sesiones en que se parten esas asignaciones (según su patrón)
 * @param bloques         bloques de clase de los turnos implicados
 * @param horasDemandadas horas totales pedidas
 * @param ventanasLegales ventanas legales totales de todas las sesiones
 */
public record ValidacionIADTO(
        ResultadoValidacionDTO backend,
        List<ChequeoIA> chequeos,
        List<MateriaImposible> imposibles,
        boolean aptoParaGenerar,
        int grupos,
        int asignaciones,
        int sesiones,
        int bloques,
        int horasDemandadas,
        int ventanasLegales) {

    /** Un chequeo con su estado: {@code OK}, {@code ADVERTENCIA} o {@code ERROR}. */
    public record ChequeoIA(String nombre, String estado, String detalle) {
    }

    /** Una asignatura que no cabe en ningún lado con los datos actuales. */
    public record MateriaImposible(Long asignacionId,
                                   String grupo,
                                   String materia,
                                   String maestro,
                                   int horas,
                                   int ventanas,
                                   String motivo) {
    }
}
