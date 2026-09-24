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
 * @param analisisViabilidad DIAGNÓSTICO de reparto: maestros en déficit, grupos apretados y bloques
 *                        donde no hay ningún maestro posible. Es solo información: no bloquea la
 *                        generación. Incluye además las dos revisiones finas
 *                        ({@link RevisionesViabilidad}) que buscan QUÉ IMPIDE COLOCAR CADA HORA.
 * @param aptoParaGenerar true si no hay errores: las revisiones finas son avisos, nunca errores
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
        int ventanasLegales,
        AnalisisViabilidad analisisViabilidad) {

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

    // ============================================================
    // ANÁLISIS DE VIABILIDAD (diagnóstico del reparto de maestros)
    // ============================================================

    /**
     * ANÁLISIS DE VIABILIDAD DEL REPARTO.
     *
     * <p>Responde a la pregunta "¿por qué no cabe todo?" antes de lanzar la generación. Mira cada
     * maestro y cada grupo por separado y busca imposibles matemáticos (un maestro con más horas
     * asignadas que huecos legales, un grupo con un bloque donde no hay ningún maestro posible) y
     * grupos que van justos, que son los candidatos a reacomodar maestros.
     *
     * <p>INFORMACIÓN, no error: nada de esto bloquea la generación.
     *
     * @param maestros       TODOS los maestros del alcance, con sus horas, sus huecos y su estado
     * @param grupos         TODOS los grupos del alcance, ordenados de más a menos ajustado
     *                      (equivale a {@code desbalance}: la peor holgura queda arriba)
     * @param desbalance     los mismos grupos, solo con lo necesario para verlos de un vistazo
     * @param resumen        totales del diagnóstico
     * @param revisiones     las dos revisiones FINAS (cupo por par maestro-grupo y bloques con un
     *                      solo maestro posible). Son avisos: nunca cambian {@code aptoParaGenerar}.
     */
    public record AnalisisViabilidad(List<MaestroViabilidad> maestros,
                                     List<GrupoViabilidad> grupos,
                                     List<GrupoDesbalance> desbalance,
                                     ResumenViabilidad resumen,
                                     RevisionesViabilidad revisiones) {
    }

    // ============================================================
    // LAS DOS REVISIONES FINAS DEL DIAGNÓSTICO
    // ============================================================

    /**
     * LAS DOS REVISIONES QUE BUSCAN QUÉ IMPIDE COLOCAR CADA HORA.
     *
     * <p>El reparto de maestros ya dice si algo "cabe o no cabe" a lo bruto, pero no POR QUÉ falla la
     * colocación hora a hora. Estas revisiones sí:
     *
     * <ol>
     *   <li>{@code cupo}: para cada par (maestro, grupo), horas que ese maestro debe dar en ese grupo
     *       contra bloques en los que los dos están libres a la vez. Es el déficit REAL.</li>
     *   <li>{@code bloquesApretados}: bloques donde el grupo solo tiene 1 o 2 maestros posibles (con 1
     *       es un punto único de fallo: si ese maestro se ocupa en otro grupo, el bloque queda libre
     *       garantizado).</li>
     * </ol>
     *
     * <p>TODO es diagnóstico: nada de esto bloquea ni cambia la generación.
     *
     * @param resumen totales de las dos revisiones ({@link ResumenRevisiones}), listos para asomar a
     *                los chequeos sin recorrer las listas
     */
    public record RevisionesViabilidad(List<CupoMaestroGrupo> cupo,
                                       List<GrupoBloquesApretados> bloquesApretados,
                                       ResumenRevisiones resumen) {
    }

    /**
     * REVISIÓN 1 · CUPO REAL DE UN PAR (MAESTRO, GRUPO).
     *
     * @param horasEnGrupo   suma de {@code asignacion.horas} de las materias de ese maestro en ese
     *                       grupo (varias filas de la misma materia y grupo SE SUMAN)
     * @param bloquesComunes bloques del turno del grupo en los que el maestro Y el grupo están
     *                       disponibles a la vez: los únicos donde esa clase puede caer
     * @param deficit        {@code horasEnGrupo - bloquesComunes} cuando es positivo: esas horas no
     *                       caben en ese grupo con ese maestro, por muchas vueltas que dé el motor
     * @param materias       las materias con las que ese maestro suma esas horas en ese grupo
     * @param severidad      {@code IMPOSIBLE} (déficit), {@code AJUSTADO} (0 o 1 bloque de sobra) o
     *                       {@code HOLGADO}
     */
    public record CupoMaestroGrupo(Long maestroId,
                                   String maestro,
                                   Long grupoId,
                                   String grupo,
                                   int horasEnGrupo,
                                   int bloquesComunes,
                                   int deficit,
                                   List<String> materias,
                                   String severidad) {
    }

    /**
     * REVISIÓN 2 · UN GRUPO Y SUS BLOQUES CON MENOS MAESTROS POSIBLES.
     *
     * @param bloquesConUno  bloques donde SOLO 1 maestro del grupo está disponible (punto único de
     *                       fallo)
     * @param bloquesConDos  bloques donde solo 2 maestros del grupo están disponibles
     * @param maestrosMinimo el menor número de maestros posibles en un bloque de ese grupo
     * @param detalle        esos bloques, con el día, la hora y quién es el único maestro posible
     */
    public record GrupoBloquesApretados(Long grupoId,
                                        String grupo,
                                        int bloquesDisponibles,
                                        int bloquesConUno,
                                        int bloquesConDos,
                                        int maestrosMinimo,
                                        List<BloquePocosMaestros> detalle) {
    }

    /**
     * REVISIÓN 2 (detalle) · UN BLOQUE CONCRETO CON POCOS MAESTROS POSIBLES.
     *
     * @param maestrosPosibles cuántos maestros del grupo pueden dar clase en ese bloque
     * @param unicoMaestro    nombre del único maestro posible (solo si {@code maestrosPosibles == 1})
     */
    public record BloquePocosMaestros(String bloque,
                                      int maestrosPosibles,
                                      String unicoMaestro) {
    }

    /**
     * Totales de las dos revisiones finas.
     *
     * @param paresConDeficit        pares (maestro, grupo) con más horas que bloques comunes
     * @param horasDeficit           suma de esos déficits: horas que no caben en su grupo con ese maestro
     * @param gruposConBloqueUnico   grupos con algún bloque donde solo hay 1 maestro posible
     * @param bloquesConUnMaestro    total de esos bloques (punto único de fallo)
     * @param bloquesConDosMaestros  total de bloques con solo 2 maestros posibles
     */
    public record ResumenRevisiones(int paresConDeficit,
                                    int horasDeficit,
                                    int gruposConBloqueUnico,
                                    int bloquesConUnMaestro,
                                    int bloquesConDosMaestros) {
    }

    /**
     * Un maestro del alcance visto desde su capacidad real.
     *
     * @param horasAsignadas  suma del campo {@code horas} de sus asignaciones (varias filas de la
     *                        misma materia y grupo SE SUMAN)
     * @param ventanasLegales bloques DISTINTOS en los que podría dar clase: bloques del turno de
     *                        alguno de sus grupos donde el maestro está disponible y al menos uno de
     *                        sus grupos también
     * @param deficit         {@code horasAsignadas - ventanasLegales} cuando es positivo: esas horas
     *                        no caben en ningún lado, por muchas vueltas que dé el motor
     * @param severidad       {@code IMPOSIBLE} (déficit), {@code AJUSTADO} (0 o 1 bloque de sobra) o
     *                        {@code HOLGADO}
     */
    public record MaestroViabilidad(Long maestroId,
                                    String maestro,
                                    int horasAsignadas,
                                    int ventanasLegales,
                                    int deficit,
                                    List<String> materias,
                                    int grupos,
                                    String severidad) {
    }

    /**
     * Un grupo del alcance visto desde su capacidad real.
     *
     * @param horasNecesarias   suma del campo {@code horas} de sus asignaciones
     * @param bloquesDisponibles bloques del turno en los que el grupo está disponible
     * @param maestrosPromedio  promedio de maestros disponibles por bloque disponible
     * @param bloquesConCero    bloques donde NINGÚN maestro del grupo está disponible: esa hora no la
     *                          puede dar nadie
     * @param bloquesSinMaestro detalle (día y hora) de esos bloques
     * @param indiceHolgura     cifra para comparar grupos entre sí (fórmula explicada en el servicio)
     * @param severidad         {@code IMPOSIBLE}, {@code AJUSTADO} o {@code HOLGADO}
     */
    public record GrupoViabilidad(Long grupoId,
                                  String grupo,
                                  int horasNecesarias,
                                  int bloquesDisponibles,
                                  double maestrosPromedio,
                                  int maestros,
                                  int bloquesConCero,
                                  List<String> bloquesSinMaestro,
                                  double indiceHolgura,
                                  String severidad) {
    }

    /** Una línea de la lista de desbalance: quién va más justo y por qué. */
    public record GrupoDesbalance(Long grupoId,
                                  String grupo,
                                  int horasNecesarias,
                                  int bloquesDisponibles,
                                  double indiceHolgura,
                                  String severidad) {
    }

    /**
     * Totales del diagnóstico.
     *
     * @param maestrosEnDeficit maestros con más horas que huecos legales
     * @param horasSinHueco     suma de los déficits: horas que matemáticamente no caben
     * @param gruposImposibles  grupos con un bloque sin ningún maestro posible
     * @param bloquesSinMaestro total de bloques donde no hay ningún maestro posible
     * @param gruposAjustados   grupos con holgura escasa (candidatos a reacomodar maestros)
     */
    public record ResumenViabilidad(int maestrosEnDeficit,
                                    int horasSinHueco,
                                    int gruposImposibles,
                                    int bloquesSinMaestro,
                                    int gruposAjustados) {
    }
}
