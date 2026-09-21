package mx.sih.modelo.solver;

/**
 * Bloque ocupado por una clase YA colocada de OTRO grupo.
 *
 * <h2>Para qué sirve</h2>
 * La generación masiva resuelve todos los grupos a la vez, así que no necesita nada de esto. Pero
 * la regeneración de UN grupo ({@code POST /api/horarios/generar/{grupoId}}) construye el problema
 * solo con las asignaciones de ese grupo: el solver no veía las clases de los demás, colocaba una
 * hora en un bloque donde el maestro (o el aula) ya estaba ocupado por otro grupo, terminaba en
 * 0hard y la base de datos rechazaba el guardado con {@code no_solape_maestro_bloque}.
 *
 * <p>Con estas ocupaciones como hechos del problema y las constraints HARD correspondientes, el
 * solver del grupo ya no puede usar esos bloques: regenerar un grupo deja de chocar con el resto
 * del horario.
 *
 * @param maestroId maestro que ya está dando clase en ese bloque (otro grupo)
 * @param aulaId    aula que ya está ocupada en ese bloque (puede ser {@code null})
 * @param bloqueId  bloque ocupado
 */
public record OcupacionExterna(Long maestroId, Long aulaId, Long bloqueId) {
}