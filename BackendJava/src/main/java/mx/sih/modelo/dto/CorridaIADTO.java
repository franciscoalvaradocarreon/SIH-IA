package mx.sih.modelo.dto;

import java.time.LocalDateTime;

/**
 * Una corrida guardada del generador IA, tal como la ve la pantalla.
 *
 * <p>Es un DTO y no la entidad por dos motivos:
 * <ul>
 *   <li>La entidad tiene asociaciones perezosas (escuela, semestre) que Jackson intentaria serializar
 *       fuera de la transaccion.</li>
 *   <li>La pantalla necesita dos datos que NO estan en la tabla: cuantas filas hay guardadas de
 *       verdad ahora mismo, y si la corrida se puede aplicar.</li>
 * </ul>
 *
 * <p>El nombre del turno no viaja aqui: la pantalla ya tiene la lista de turnos cargada y lo resuelve
 * con {@code turnoId}, que es una consulta menos en el servidor.
 */
public record CorridaIADTO(
        Long id,
        String nombre,
        String notas,

        Long turnoId,
        String asesor,
        LocalDateTime generadoEn,
        LocalDateTime creado,
        String creadoPor,

        // ── Con qué banderas se generó. null = corrida guardada antes de que se registrara ──
        Boolean asignarMaestros,
        Boolean asignarAulas,

        // ── Metricas del intento: es lo que se compara entre corridas ──
        Long milisegundos,
        Integer horas,
        Integer horasDemandadas,
        Integer sesionesLargas,
        Integer sesionesLargasPendientes,
        Integer arranquesTarde,
        Integer castigoHuecos,
        Integer adyacencias,
        Integer materiasCompletas,
        Integer materiasTotales,
        Integer medium,

        // ── Idoneidad para aplicarse ──
        Integer totalFilas,
        Integer totalPendientes,
        Integer totalProblemas,

        /**
         * Bloques que hay guardados AHORA. Si no cuadra con {@link #totalFilas}, la corrida esta
         * incompleta: alguna asignacion o bloque del catalogo se borro despues de guardarla.
         */
        long filasGuardadas,

        /** false si tiene problemas duros o si el detalle quedo incompleto. */
        boolean aplicable,

        /** Motivo por el que no se puede aplicar, listo para mostrar. null si si se puede. */
        String motivoNoAplicable
) {
}
