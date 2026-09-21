package mx.sih.modelo.solver;

import ai.timefold.solver.core.api.score.buildin.hardmediumsoft.HardMediumSoftScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ai.timefold.solver.core.api.score.stream.ConstraintProvider;
import ai.timefold.solver.core.api.score.stream.ConstraintCollectors;
import ai.timefold.solver.core.api.score.stream.Joiners;
import mx.sih.modelo.entidad.DisponibilidadGrupo;
import mx.sih.modelo.entidad.DisponibilidadMaestro;
import mx.sih.modelo.entidad.TurnoHorario;

import java.time.Duration;
import java.time.LocalTime;
import java.util.*;

/**
 * ConstraintProvider del solver de horarios.
 *
 * <h2>Jerarquía de score (HardMediumSoftScore)</h2>
 * <ul>
 *   <li><b>HARD</b>: violaciones intocables (conflictos de maestro/aula/grupo,
 *       disponibilidad, bloque fuera de turno, solapamientos).</li>
 *   <li><b>MEDIUM</b>: deseables pero sacrificables antes que un HARD.
 *       Incluye "clase sin asignar", "respetar distribución" y "sesiones del patrón".
 *       Esto garantiza que el solver prefiera <em>dejar una clase sin acomodar</em>
 *       antes que <em>solapar dos clases del mismo grupo</em>.</li>
 *   <li><b>SOFT</b>: cosmético (evitar huecos).</li>
 * </ul>
 *
 * <h2>¿Por qué MEDIUM en "clase sin asignar"?</h2>
 * Con {@code HardSoftScore} anterior, solapar (1H) y no-asignar (1H) costaban
 * lo mismo. Ante infactibilidad, el solver elegía cualquiera de las dos. Con
 * tres niveles, solapar (-1H) es infinitamente peor que no asignar (-1M), así
 * que el solver siempre prioriza la solución factible.
 *
 * <h2>Joiners por ID, no por referencia</h2>
 * Las entidades JPA ({@code TurnoHorario}, {@code DisponibilidadGrupo}) NO
 * sobrescriben {@code equals()}/{@code hashCode()} de forma segura con
 * Hibernate (proxies, lazy loading). Comparar por referencia rompía
 * silenciosamente las constraints de conflicto. Ahora se compara por {@code id}.
 */
public class HorarioConstraintProvider implements ConstraintProvider {

    /**
     * Máximo gap (minutos) entre el FIN de una clase y el INICIO de la
     * siguiente para considerarlas "continuas". Debe ser >= duración del descanso real.
     */
    private static final long TOLERANCIA_CONTINUIDAD_MINUTOS = 50;
    private static final long TOLERANCIA_ADYACENCIA_MINUTOS = 5;

    /**
     * Formato válido de `asignacion.distribucion`: números separados por comas ("2,1,1").
     *
     * Se comprueba AQUÍ además de en el DTO porque en la base puede haber datos
     * antiguos mal formateados: sin esta validación, Integer::parseInt lanzaba
     * NumberFormatException dentro del motor de constraints y el solver abortaba
     * a mitad de la búsqueda local (sin horario y con un error poco claro).
     */
    private static final java.util.regex.Pattern DISTRIBUCION_VALIDA =
            java.util.regex.Pattern.compile("^\\s*\\d+(\\s*,\\s*\\d+)*\\s*$");

    /** Duración esperada de un bloque de clase (minutos). */
    private static final long DURACION_BLOQUE_MINUTOS = 50;

    /**
     * Peso de "Iniciar a primera hora" (MEDIUM, por cada grupo-día que no arranca en
     * el bloque de apertura).
     *
     * <h2>Por qué 4 y por qué es binario</h2>
     * El orden de prioridades del plantel queda así (ver {@link #PESO_SESION} y
     * {@link #PESO_DISTRIBUCION}):
     * <pre>
     *   sesiones del patrón (7 por hora mal armada) &gt; colocar todas las horas (6) &gt;
     *   iniciar a 1ª hora (4) &gt; distribución (3) &gt; sin huecos (2)
     * </pre>
     * Es <b>binario</b> (un grupo-día arranca o no arranca a 1ª hora; no importa cuántos
     * bloques de retraso lleve) y vale 4 a propósito:
     * <ul>
     *   <li>&lt; 6 (peso de "Horas sin asignar") para que NUNCA le convenga dejar una hora
     *       pendiente con tal de arrancar puntual. Si el peso por retraso creciera con los
     *       bloques (p.ej. 3 × 5 bloques = 15), colocar la única clase del día al final de
     *       la jornada costaría más que dejarla sin asignar y el solver sacrificaría horas.</li>
     *   <li>&gt; 3 (distribución) y &gt; 2 (hueco), para que prefiera arrancar puntual aunque
     *       eso le obligue a reacomodar el reparto o a dejar un hueco en medio. Queda por debajo
     *       de las sesiones del patrón (7): un taller mal armado se deja pendiente antes que
     *       retrasar la entrada de un grupo.</li>
     * </ul>
     */
    private static final int PESO_ENTRADA_PRIMERA_HORA = 4;

    /**
     * Peso de "Sin clases continuas del mismo maestro en el mismo grupo" (MEDIUM, por cada par de
     * clases pegadas de materias distintas).
     *
     * <h2>Por qué 3 y no 5 (ni HARD)</h2>
     * Esta regla tiene que quedar POR DEBAJO de las dos reglas de forma, o el solver las cambia por
     * ella. Medido en el semestre 11, dos corridas idénticas con la adyacencia en 5 y el arranque
     * cobrando solo 2 por bloque: 41 grupos-día arrancando tarde (39 evitables, con la apertura libre
     * para el grupo y su maestro) y cobertura 685 h, frente a 699 h del modelo con la regla HARD.
     * Con 3 el arranque puntual (4 + huecos) gana el trueque y la regla se sigue respetando cuando
     * se puede: en esa misma medición solo quedaban 2 pares pegados.
     * <p>
     * Y tampoco puede volver a HARD: como HARD obligaba a dejar huecos por construcción (si un maestro
     * da 4 materias al mismo grupo, esas horas no pueden ir consecutivas).
     */
    private static final int PESO_ADYACENCIA_MAESTRO_GRUPO = 3;

    /**
     * Peso de "Evitar huecos" (MEDIUM, por cada bloque libre EN MEDIO de la jornada de un grupo).
     *
     * <h2>Por qué 4, con tope de 5 por grupo-día</h2>
     * El hueco es lo que el requerimiento prohíbe, así que tiene que ganarle a la preferencia de
     * adyacencia ({@link #PESO_ADYACENCIA_MAESTRO_GRUPO}, 3): si un hueco costara menos que romper
     * una adyacencia, al AMPLIAR la disponibilidad el solver gastaría la holgura nueva abriendo
     * huecos para separar clases del mismo maestro, que es justo lo que se midió (hueco 2 contra
     * adyacencia 5: 41 grupos-día arrancando tarde y huecos repartidos).
     * <p>
     * El tope de {@link #TOPE_HUECOS_POR_DIA} existe para que NUNCA salga rentable dejar una hora
     * sin colocar con tal de quitar huecos: colocar una hora vale 6, y un día con muchos huecos no
     * puede costar más de 5. Sin tope, un día con 4 huecos costaría 16 y al solver le saldría más
     * barato tirar la clase aislada (6) que dejarla con sus huecos, y la cobertura se caería —
     * exactamente el mismo error que con el arranque plano en 6.
     */
    private static final int PESO_HUECO = 4;

    /** Tope del castigo por huecos de un mismo grupo-día. Ver {@link #PESO_HUECO}. */
    private static final int TOPE_HUECOS_POR_DIA = 5;

    /**
     * Desempate a favor de las SESIONES LARGAS (MEDIUM, por cada sesión de 2+ horas que queda sin
     * colocar).
     *
     * <h2>Qué problema resuelve</h2>
     * Una sesión de 2 horas vale lo mismo que dos de una hora (12 unidades de "Horas sin asignar"),
     * así que cuando compiten por las mismas ventanas el solver empata y puede dejar fuera la
     * materia larga. Medido sobre el semestre 11: {@code Deportes} de 1°I (patrón "2") quedaba SIN
     * ASIGNAR en las 15 ventanas que tenía, ocupadas por clases de una hora.
     *
     * <p>Con este extra, colocar la sesión larga (12 + 3) le gana a dejar que la ocupen dos sesiones
     * de una hora (12). Las horas totales no cambian: se cambia UNA materia incompleta por dos a
     * medias, que es lo que el plantel prefiere (y lo que ya insinúa el orden fail-first, que coloca
     * las sesiones largas primero).
     *
     * <h2>Por qué 3 y no más</h2>
     * Menos que una hora de clase (6). Con 7 el solver preferiría conservar la sesión larga aunque
     * eso le costara dejar una hora sin colocar y la cobertura bajaría; con 3 solo rompe empates
     * ajustados (2 horas largas contra 2 horas cortas) y nunca compensa perder una hora entera.
     */
    private static final int PESO_SESION_LARGA_PENDIENTE = 3;

    /**
     * Peso de "Respetar distribución", por cada unidad de desvío (una hora que habría que
     * cambiar de día para cumplir el patrón).
     *
     * <h2>Por qué 3</h2>
     * Es el orden de prioridades acordado con el plantel:
     * <pre>
     *   colocar todas las horas (6) &gt; iniciar a 1ª hora (4) &gt; distribución (3) &gt; huecos (2)
     * </pre>
     * <ul>
     *   <li>&gt; 2 (un hueco): corregir un desvío de una hora compensa abrir un hueco.</li>
     *   <li>&lt; 4 (arranque a 1ª hora) y &lt; 6 (hora sin colocar): nunca le conviene retrasar la
     *       entrada de un grupo ni dejar una hora pendiente con tal de cuadrar el patrón. Ese
     *       margen es el que impide que la búsqueda "arregle" el reparto dejando horas fuera.</li>
     * </ul>
     * El desvío se acota solo: una materia con "1,1,1,1,1" y las cinco horas el mismo día desvía
     * 8 (24 puntos), y a partir de ahí al solver le sale más barato seguir con otras materias que
     * desarmar medias jornadas.
     */
    private static final int PESO_DISTRIBUCION = 3;

    /**
     * Peso de "Sesiones del patrón", por cada hora de una sesión mal armada.
     *
     * <h2>Por qué 7: esto es lo que vuelve obligatoria la distribución</h2>
     * Partir una sesión de 2 horas cuesta 14 y una de 3 cuesta 21, mientras que colocar esas horas
     * vale 6 cada una (peso de "Horas sin asignar"). O sea: al solver le sale MÁS BARATO dejar la
     * sesión entera sin colocar —y que aparezca como fallida en la tabla— que entregarla partida.
     * Eso es lo que faltaba: con el peso 5 anterior sólo se rompían menos sesiones (25 de 98 en la
     * última medición), no se dejaban de romper.
     *
     * <h2>Por qué por encima de todo lo demás</h2>
     * Un taller de 3 horas partido en 1+2 no es un horario válido para el plantel: antes que
     * entregarlo mal armado, el plantel prefiere verlo como pendiente y arreglar los datos de
     * disponibilidad que lo impiden.
     */
    private static final int PESO_SESION = 7;

    /**
     * Clave de agrupación para la regla de entrada: (día, hora de inicio de la apertura).
     *
     * Se usa una clave propia en lugar de la entidad {@code TurnoHorario} porque la misma
     * hora de inicio existe en varios días (14:00 de lunes a viernes): agrupar solo por hora
     * mezclaría los días y la regla se evaluaría mal.
     */
    private record AperturaDia(int diaSemana, LocalTime horaInicio) {
    }

    public HorarioConstraintProvider() {
        // El provider se instancia una vez por SolverFactory. Sin estado mutable.
    }

    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
        return new Constraint[] {
                // ─────────────── HARD ───────────────
                conflictoMaestro(factory),
                conflictoAula(factory),
                conflictoGrupo(factory),
                grupoSolapamientoParcial(factory),   // defensivo (bloques distintos con overlap real)
                disponibilidadMaestro(factory),
                disponibilidadGrupo(factory),
                bloqueDelTurnoDelGrupo(factory),
                bloqueDeMaestroOcupadoPorOtroGrupo(factory),
                bloqueDeAulaOcupadoPorOtroGrupo(factory),
                sesionesEnDiasDistintos(factory),

                // ─────────────── MEDIUM ─────────────
                horasAsignadas(factory),
                sesionLargaPendiente(factory),   // desempate: completar la materia antes que trocearla


                iniciarAPrimeraHora(factory),   // entrada a primera hora (regla del plantel)
                evitarHuecos(factory),   // antes SOFT: ver nota en el método
                sinClasesContinuasMismoMaestroGrupo(factory),   // blanda: ver javadoc del peso

                // ─────────────── SOFT ───────────────
        };
    }

    // ═══════════════════════════════════════════════════════
    // HARD — violaciones intocables
    // ═══════════════════════════════════════════════════════

    /**
     * Dos asignaciones del mismo maestro en el mismo bloque → HARD.
     * Compara por ID de bloque (no por referencia de entidad).
     */
    private Constraint conflictoMaestro(ConstraintFactory factory) {
        return factory
                .forEachUniquePair(AsignacionHorario.class,
                        Joiners.equal(AsignacionHorario::getMaestroId),
                        Joiners.filtering(AsignacionHorario::chocaCon))
                .penalize(HardMediumSoftScore.ONE_HARD)
                .asConstraint("Conflicto de maestro");
    }

    /** Dos asignaciones en la misma aula en el mismo bloque → HARD. */
    private Constraint conflictoAula(ConstraintFactory factory) {
        return factory
                .forEachUniquePair(AsignacionHorario.class,
                        Joiners.equal(AsignacionHorario::getAulaId),
                        Joiners.filtering(AsignacionHorario::chocaCon))
                .penalize(HardMediumSoftScore.ONE_HARD)
                .asConstraint("Conflicto de aula");
    }

    /**
     * Dos asignaciones del mismo grupo en el mismo bloque → HARD.
     * <b>Esta es la constraint que estaba fallando</b>: comparaba por
     * referencia de entidad, y con Hibernate los proxies hacían que
     * {@code equals()} devolviera {@code false} para el mismo bloque.
     */
    private Constraint conflictoGrupo(ConstraintFactory factory) {
        return factory
                .forEachUniquePair(AsignacionHorario.class,
                        Joiners.equal(AsignacionHorario::getGrupoId),
                        Joiners.filtering(AsignacionHorario::chocaCon))
                .penalize(HardMediumSoftScore.ONE_HARD)
                .asConstraint("Conflicto de grupo");
    }

    /**
     * Solapamiento PARCIAL: dos asignaciones del mismo grupo el mismo día
     * cuyos intervalos [horaInicio, horaFin) se cruzan pero NO son el mismo
     * bloque. Se ignora el caso exacto (ya lo cubre {@link #conflictoGrupo}).
     *
     * <p>Esta constraint es <b>defensiva</b>: si tu modelo garantiza que los
     * bloques de un turno nunca se solapan entre sí, esta constraint no
     * dispara nunca. Si algún día se relaja esa garantía, aquí queda cubierto.
     */
    private Constraint grupoSolapamientoParcial(ConstraintFactory factory) {
        return factory
                .forEach(AsignacionHorario.class)
                .filter(a -> a.getBloqueHorario() != null)
                .join(AsignacionHorario.class,
                        Joiners.equal(AsignacionHorario::getGrupoId),
                        Joiners.lessThan(AsignacionHorario::getId),
                        Joiners.equal(
                                (AsignacionHorario a) -> a.getBloqueHorario().getDiaSemana(),
                                (AsignacionHorario b) -> b.getBloqueHorario().getDiaSemana()))
                .filter((a, b) -> b.getBloqueHorario() != null)
                .filter((a, b) -> {
                    TurnoHorario bhA = a.getBloqueHorario();
                    TurnoHorario bhB = b.getBloqueHorario();
                    // Ignorar bloques idénticos: ya penalizado por conflictoGrupo
                    if (bhA.getId().equals(bhB.getId())) return false;
                    // Solapamiento real de intervalos [ini, fin)
                    return bhA.getHoraInicio().isBefore(bhB.getHoraFin())
                            && bhB.getHoraInicio().isBefore(bhA.getHoraFin());
                })
                .penalize(HardMediumSoftScore.ONE_HARD)
                .asConstraint("Grupo con solapamiento parcial");
    }

    private Constraint disponibilidadMaestro(ConstraintFactory factory) {
        return factory
                .forEach(AsignacionHorario.class)
                .filter(a -> a.getBloqueHorario() != null)
                // OJO: la disponibilidad solo cuenta si disponible=true. La tabla guarda filas con
                // disponible=false (en DisponibilidadMaestro el default es false) y un ifNotExists
                // que no mira el flag las tomaba por disponibles: la red de seguridad estaba abierta.
                .ifNotExists(DisponibilidadMaestro.class,
                        Joiners.equal(
                                (AsignacionHorario a) -> a.getMaestroId(),
                                (DisponibilidadMaestro d) -> d.getMaestro().getMaestroId()),
                        Joiners.equal(
                                (AsignacionHorario a) -> a.getBloqueHorario().getId(),
                                (DisponibilidadMaestro d) -> d.getTurnoHorario().getId()),
                        Joiners.filtering((AsignacionHorario a, DisponibilidadMaestro d) ->
                                Boolean.TRUE.equals(d.getDisponible())))
                .penalize(HardMediumSoftScore.ONE_HARD)
                .asConstraint("Maestro no disponible");
    }

    private Constraint disponibilidadGrupo(ConstraintFactory factory) {
        return factory
                .forEach(AsignacionHorario.class)
                .filter(a -> a.getBloqueHorario() != null)
                // Igual que en el maestro: las filas con disponible=false NO son disponibilidad.
                .ifNotExists(DisponibilidadGrupo.class,
                        Joiners.equal(
                                (AsignacionHorario a) -> a.getGrupoId(),
                                (DisponibilidadGrupo d) -> d.getGrupo().getGrupoId()),
                        Joiners.equal(
                                (AsignacionHorario a) -> a.getBloqueHorario().getId(),
                                (DisponibilidadGrupo d) -> d.getTurnoHorario().getId()),
                        Joiners.filtering((AsignacionHorario a, DisponibilidadGrupo d) ->
                                Boolean.TRUE.equals(d.getDisponible())))
                .penalize(HardMediumSoftScore.ONE_HARD)
                .asConstraint("Grupo no disponible en este bloque");
    }

    private Constraint bloqueDelTurnoDelGrupo(ConstraintFactory factory) {
        return factory
                .forEach(AsignacionHorario.class)
                .filter(a -> a.getBloqueHorario() != null)
                .filter(a -> {
                    Long turnoBloque = a.getBloqueHorario().getTurno() != null
                            ? a.getBloqueHorario().getTurno().getTurnoId()
                            : null;
                    return turnoBloque != null && !turnoBloque.equals(a.getTurnoId());
                })
                .penalize(HardMediumSoftScore.ONE_HARD)
                .asConstraint("Bloque no pertenece al turno del grupo");
    }
   
    private Constraint sinClasesContinuasMismoMaestroGrupo(ConstraintFactory factory) {
        return factory
                .forEachUniquePair(AsignacionHorario.class,
                        Joiners.equal(AsignacionHorario::getMaestroId),
                        Joiners.equal(AsignacionHorario::getGrupoId),
                        Joiners.equal(a -> a.getBloqueHorario() != null
                                ? a.getBloqueHorario().getDiaSemana() : null))
                .filter((a, b) -> !a.getAsignacionId().equals(b.getAsignacionId())   // distintas materias
                        && a.pegadoCon(b))
                .penalize(HardMediumSoftScore.ONE_MEDIUM,
                        (a, b) -> PESO_ADYACENCIA_MAESTRO_GRUPO)
                .asConstraint("Sin clases continuas del mismo maestro en el mismo grupo");
    }

    /**
     * Determina si dos bloques del mismo día están "pegados" en el tiempo.
     */
    private static boolean bloquesPegados(TurnoHorario a, TurnoHorario b) {
        if (a.getDiaSemana() == null || b.getDiaSemana() == null) return false;
        if (!a.getDiaSemana().equals(b.getDiaSemana())) return false;

        long gapAB = Duration.between(a.getHoraFin(), b.getHoraInicio()).toMinutes();
        long gapBA = Duration.between(b.getHoraFin(), a.getHoraInicio()).toMinutes();

        return (gapAB >= 0 && gapAB <= TOLERANCIA_ADYACENCIA_MINUTOS)
            || (gapBA >= 0 && gapBA <= TOLERANCIA_ADYACENCIA_MINUTOS);
    }
    
    // ═══════════════════════════════════════════════════════
    // MEDIUM — preferible, pero sacrificable antes que un HARD
    // ═══════════════════════════════════════════════════════

    /**
     * Cada asignación sin bloque asignado cuesta 1 MEDIUM (no 1 HARD).
     * Gracias a esto, ante infactibilidad el solver prefiere dejar una clase
     * como "no asignada" (que el front ya muestra en su tabla roja) antes
     * que meter un solapamiento HARD.
     */
    /**
     * Dos sesiones de la MISMA materia no pueden caer el mismo día → HARD.
     *
     * Es la pieza que cierra el patrón. Con las sesiones ya enteras (el rango de valores sólo
     * admite ventanas completas), si además cada sesión va en un día distinto, el reparto de horas
     * por día es exactamente la distribución pedida. Sin esta regla las sesiones eran correctas
     * pero se amontonaban el mismo día: medido, 52 de 180 materias respetaban el patrón.
     *
     * Va como HARD porque es el requisito del plantel: "1,1,1,1" son cuatro días, no dos días con
     * dos horas. Si alguna vez no cupiera, la pantalla lo dirá como infactible en vez de entregar
     * un reparto silenciosamente equivocado.
     */
    private Constraint sesionesEnDiasDistintos(ConstraintFactory factory) {
        return factory
                .forEachUniquePair(AsignacionHorario.class,
                        Joiners.equal(AsignacionHorario::getAsignacionId),
                        Joiners.equal(a -> a.getBloqueHorario() != null
                                ? a.getBloqueHorario().getDiaSemana() : null))
                .penalize(HardMediumSoftScore.ONE_HARD)
                .asConstraint("Sesiones de la misma materia en días distintos");
    }

    private Constraint horasAsignadas(ConstraintFactory factory) {
        return factory
                // ⚠️ forEachIncludingNullVars es IMPRESCINDIBLE aquí.
                //
                // forEach(Class) EXCLUYE las entidades cuyo planning variable es null,
                // así que el filtro "bloqueHorario == null" NUNCA se cumplía: la
                // constraint era código muerto y jamás penalizaba una hora sin asignar.
                //
                // Con nullable = true (el escape que permite dejar una hora fuera antes
                // que empalmar) esto sería fatal: el solver podría obtener 0hard/0medium
                // dejando TODAS las horas sin asignar y nada lo penalizaría. Con
                // forEachIncludingNullVars la penalización sí se aplica y el solver
                // prefiere colocar horas mientras no viole un HARD.
                .forEachIncludingNullVars(AsignacionHorario.class)
                .filter(a -> a.getBloqueHorario() == null)
                // PESO 6. El orden de prioridades del modelo es:
                //    1) colocar todas las horas      -> peso 6
                //    2) no dejar huecos en medio     -> peso 2 por hueco
                //    3) respetar los patrones        -> peso 1
                //
                // El 6 no es arbitrario: colocar una hora puede AÑADIR hasta DOS huecos
                // (parte un hueco existente o crea un grupo aislado), y cada hueco cuesta 2,
                // así que el coste máximo de colocar es 4. Con peso 3 el solver prefería
                // DEJAR LA CLASE SIN ASIGNAR (3) antes que colocarla creando dos huecos (4),
                // y por eso aparecían asignaciones a medias. Con 6 colocar siempre compensa.
                // Por HORA de la sesión: una de 3 horas sin colocar pesa 18, no 6.
                .penalize(HardMediumSoftScore.ONE_MEDIUM,
                        a -> 6 * (a.getDuracion() == null ? 1 : a.getDuracion()))
                .asConstraint("Horas sin asignar");
    }

    /**
     * SESIÓN LARGA SIN COLOCAR: cada sesión de 2+ horas que queda pendiente cuesta
     * {@link #PESO_SESION_LARGA_PENDIENTE} extra además de sus horas.
     *
     * <p>Es un desempate, no una regla dura: sirve para que, cuando dos combinaciones cubren las
     * mismas horas, gane la que deja la materia completa. Ver el javadoc del peso.
     */
    private Constraint sesionLargaPendiente(ConstraintFactory factory) {
        return factory
                // Mismo motivo que en "Horas sin asignar": forEach(Class) excluye las entidades sin
                // variable asignada, que son justo las que hay que penalizar aquí.
                .forEachIncludingNullVars(AsignacionHorario.class)
                .filter(a -> a.getBloqueHorario() == null)
                .filter(a -> a.getDuracion() != null && a.getDuracion() >= 2)
                .penalize(HardMediumSoftScore.ONE_MEDIUM,
                        a -> PESO_SESION_LARGA_PENDIENTE)
                .asConstraint("Sesión larga sin colocar");
    }

    /**
     * Distribución de las horas de una materia entre los días.
     *
     * <h2>Historia: por qué no es HARD</h2>
     * Estaba declarada HARD aunque el javadoc de esta clase la incluía en MEDIUM. Eso la
     * convertía en la constraint más destructiva del modelo: si una materia no cabía con su
     * patrón exacto, la solución quedaba INFACTIBLE (hardScore &lt; 0) y HorarioServicio no
     * persistía absolutamente nada. Con horas sueltas como variable de planificación, exigir el
     * patrón exacto es pedirle al solver un puzle que puede no tener solución: por eso se queda
     * en MEDIUM con peso alto y no en HARD.
     *
     * <h2>Por qué gradual y no binaria</h2>
     * Antes penalizaba 1 punto por materia incumplida, diese igual estar a una hora del patrón
     * que a ocho. Eso deja a la búsqueda local sin pendiente: la constraint se comporta como
     * "todo o nada" y no hay ninguna pista de hacia dónde moverse. Ahora se penaliza la
     * DISTANCIA al patrón ({@link #desvioDistribucion}), así que mover una hora al día que falta
     * ya mejora la puntuación aunque el patrón todavía no se cumpla.
     *
     * <h2>Qué se evalúa</h2>
     * Se incluyen las horas sin asignar ({@code forEachIncludingNullVars}) porque una hora
     * pendiente es una hora que falta en su día y debe contar como desvío: así el solver reparte
     * a medida que coloca, en vez de amontonar y decidir el reparto al final. Las materias sin
     * ninguna hora colocada quedan fuera: su desvío sería el patrón entero y no significaría
     * nada (de colocarlas se encarga "Horas sin asignar").
     */
    private Constraint respetarDistribucion(ConstraintFactory factory) {
        return factory
                .forEachIncludingNullVars(AsignacionHorario.class)
                .groupBy(AsignacionHorario::getAsignacionId,
                        AsignacionHorario::getDistribucion,
                        ConstraintCollectors.toList())
                .filter((id, dist, lista) -> distribucionValida(dist)
                        && lista.stream().anyMatch(a -> a.getBloqueHorario() != null))
                .penalize(HardMediumSoftScore.ONE_MEDIUM,
                        (id, dist, lista) -> PESO_DISTRIBUCION * desvioDistribucion(dist, lista))
                .asConstraint("Respetar distribución");
    }

    /**
     * Las sesiones del patrón: cada tramo de la distribución tiene que existir tal cual.
     *
     * <h2>Qué exige</h2>
     * Para cada materia con distribución válida se parte el patrón en sesiones ("2,1,1" son tres:
     * una de 2 horas y dos de 1). Cada sesión debe estar:
     * <ul>
     *   <li><b>completa y pegada</b>: sus horas el mismo día y contiguas (con la tolerancia de
     *       descanso de {@link #TOLERANCIA_CONTINUIDAD_MINUTOS});</li>
     *   <li><b>en un día distinto</b> al de las otras sesiones de la misma materia.</li>
     * </ul>
     * Entre las dos reglas, el reparto de horas por día queda igual al patrón en cuanto la
     * materia está completa: eso es "respetar la distribución". Las sesiones a medias NO se
     * penalizan aquí (de las horas que faltan ya se encarga "Horas sin asignar": cobrarlas dos
     * veces castigaría al solver por algo que ya está pagando).
     */
    private Constraint sesionesDelPatron(ConstraintFactory factory) {
        return factory
                .forEachIncludingNullVars(AsignacionHorario.class)
                .groupBy(AsignacionHorario::getAsignacionId,
                        AsignacionHorario::getDistribucion,
                        ConstraintCollectors.toList())
                .filter((id, dist, lista) -> distribucionValida(dist)
                        && lista.stream().anyMatch(a -> a.getBloqueHorario() != null))
                .penalize(HardMediumSoftScore.ONE_MEDIUM,
                        (id, dist, lista) -> PESO_SESION * sesionesRojas(dist, lista))
                .asConstraint("Sesiones del patrón");
    }
    
    // ═══════════════════════════════════════════════════════
    // SOFT — cosmético
    // ═══════════════════════════════════════════════════════

    private Constraint evitarHuecos(ConstraintFactory factory) {
        return factory
                .forEach(AsignacionHorario.class)
                .filter(a -> a.getBloqueHorario() != null)
                // Se agrupa por (grupo, DÍA) y no solo por grupo: el tope del castigo es por jornada,
                // así que cada día se penaliza por separado.
                .groupBy(AsignacionHorario::getGrupoId,
                        (AsignacionHorario a) -> a.getBloqueHorario().getDiaSemana(),
                        ConstraintCollectors.toList())
                .filter((id, dia, lista) -> lista.size() > 1 && tieneHuecos(lista))
                // Estaba como SOFT, y en una puntuación lexicográfica (HardMediumSoftScore)
                // CUALQUIER penalización MEDIUM pesa más que todos los SOFT juntos: el solver
                // sacrificaba huecos (horas libres EN MEDIO de la jornada, que el requerimiento
                // prohíbe) por preferencias que también son MEDIUM.
                //
                // Peso y tope: ver {@link #PESO_HUECO} y {@link #TOPE_HUECOS_POR_DIA}.
                .penalize(HardMediumSoftScore.ONE_MEDIUM,
                        (Long id, Integer dia, List<AsignacionHorario> lista) ->
                                Math.min(PESO_HUECO * contarHuecos(lista), TOPE_HUECOS_POR_DIA))
                .asConstraint("Evitar huecos");
    }

    /**
     * ENTRADA A PRIMERA HORA (regla del plantel): si un grupo tiene clases un día, su
     * primera clase de ese día debe ser el bloque de apertura (el primero de la jornada
     * de su turno).
     *
     * <h2>Por qué hacía falta una constraint nueva</h2>
     * {@code Evitar huecos} solo cuenta huecos <em>entre</em> clases consecutivas
     * (además, ignora los días con una sola clase), así que un día que arranca a 2ª o 3ª
     * hora no se penalizaba de ninguna forma: el solver no tenía ningún incentivo para
     * subir esa primera clase al bloque de apertura.
     *
     * <h2>Cómo se identifica el bloque de apertura</h2>
     * {@code bloquesDisponibles} ya es un problem fact con TODOS los bloques de clase del
     * turno, así que la apertura de (turno, día) es el bloque para el que NO existe otro
     * bloque del mismo turno y día con hora de inicio anterior ({@code ifNotExists}). Esto
     * evita agregar un problem fact nuevo o tocar el modelo/servicio.
     *
     * <h2>Semántica</h2>
     * Se agrupa por (apertura, grupo) sobre las clases realmente colocadas de ese día:
     * <ul>
     *   <li>Si el grupo no tiene ninguna clase ese día, no hay fila y NO se penaliza
     *       (no se exige asistir un día en el que no hay clases).</li>
     *   <li>Si tiene clases pero la más temprana no es la apertura, se penaliza
     *       {@link #PESO_ENTRADA_PRIMERA_HORA} una sola vez por grupo-día.</li>
     * </ul>
     */
    private Constraint iniciarAPrimeraHora(ConstraintFactory factory) {
        return factory
                .forEach(TurnoHorario.class)
                .filter(b -> b.getDiaSemana() != null && b.getTurno() != null && b.getHoraInicio() != null)
                // Bloque de apertura: ninguno del mismo turno y día empieza antes.
                //
                // Se usa Joiners.filtering con el predicado explícito en lugar de
                // Joiners.lessThan(mapper, mapper): con dos mappers del MISMO tipo
                // (TurnoHorario) y en un ifNotExists, la dirección de la comparación
                // queda ambigua y la implementación se quedaba con el ÚLTIMO bloque del
                // día (arranque tardío nunca penalizado). Con filtering el orden de los
                // parámetros es explícito: (elemento del stream, hecho).
                .ifNotExists(TurnoHorario.class,
                        Joiners.equal(
                                (TurnoHorario b) -> b.getTurno().getTurnoId(),
                                (TurnoHorario c) -> c.getTurno().getTurnoId()),
                        Joiners.equal(TurnoHorario::getDiaSemana, TurnoHorario::getDiaSemana),
                        Joiners.filtering((TurnoHorario b, TurnoHorario c) ->
                                c.getHoraInicio() != null
                                        && b.getHoraInicio() != null
                                        && c.getHoraInicio().isBefore(b.getHoraInicio())))
                // Clases realmente colocadas en ese turno...
                .join(AsignacionHorario.class,
                        Joiners.equal(
                                (TurnoHorario apertura) -> apertura.getTurno().getTurnoId(),
                                AsignacionHorario::getTurnoId))
                // ...y de ese mismo día.
                .filter((TurnoHorario apertura, AsignacionHorario a) -> a.getBloqueHorario() != null
                        && apertura.getDiaSemana().equals(a.getBloqueHorario().getDiaSemana()))
                .groupBy((TurnoHorario apertura, AsignacionHorario a) ->
                                new AperturaDia(apertura.getDiaSemana(), apertura.getHoraInicio()),
                        (TurnoHorario apertura, AsignacionHorario a) -> a.getGrupoId(),
                        // El stream es Bi (viene de un join), así que el colector tiene que ser
                        // Bi: ConstraintCollectors.toList() sin argumentos es Uni y Timefold
                        // rechaza la constraint al compilarla ("Uncompilable code"). El argumento
                        // le dice qué elemento de la pareja se guarda en la lista.
                        ConstraintCollectors.toList((TurnoHorario apertura, AsignacionHorario a) -> a))
                .filter((AperturaDia apertura, Long grupoId, List<AsignacionHorario> lista) ->
                        !iniciaEnApertura(apertura, lista))
                .penalize(HardMediumSoftScore.ONE_MEDIUM,
                        (AperturaDia apertura, Long grupoId, List<AsignacionHorario> lista) ->
                                PESO_ENTRADA_PRIMERA_HORA)
                .asConstraint("Iniciar a primera hora");
    }

    /**
     * true si la clase más temprana del día es el bloque de apertura.
     * La lista solo contiene clases del día de esa apertura.
     */
    private static boolean iniciaEnApertura(AperturaDia apertura, List<AsignacionHorario> lista) {
        LocalTime primera = null;
        for (AsignacionHorario a : lista) {
            TurnoHorario b = a.getBloqueHorario();
            if (b == null || b.getHoraInicio() == null) {
                continue;
            }
            if (primera == null || b.getHoraInicio().isBefore(primera)) {
                primera = b.getHoraInicio();
            }
        }
        return primera != null && !primera.isAfter(apertura.horaInicio());
    }

    // ═══════════════════════════════════════════════════════
    // HARD — OCUPACIÓN DE OTROS GRUPOS (regenerar un grupo)
    // ═══════════════════════════════════════════════════════

    /**
     * El maestro de esta sesión ya está dando clase en ese bloque a OTRO grupo → HARD.
     *
     * <p>Solo tiene datos cuando se regenera un grupo suelto
     * ({@code HorarioSolution.ocupacionesExternas}); en la generación masiva la lista va vacía y
     * estas constraints no producen ninguna fila.
     */
    private Constraint bloqueDeMaestroOcupadoPorOtroGrupo(ConstraintFactory factory) {
        return factory
                .forEach(OcupacionExterna.class)
                .filter(o -> o.maestroId() != null && o.bloqueId() != null)
                .join(AsignacionHorario.class,
                        Joiners.equal(OcupacionExterna::maestroId, AsignacionHorario::getMaestroId))
                .filter((o, a) -> a.getBloqueHorario() != null && ocupaBloque(a, o.bloqueId()))
                .penalize(HardMediumSoftScore.ONE_HARD)
                .asConstraint("Maestro ya ocupado por otro grupo");
    }

    /**
     * El aula de esta sesión ya está ocupada en ese bloque por OTRO grupo → HARD.
     *
     * <p>El aula es fija por asignación, pero dos grupos pueden compartir aula en los datos, así que
     * la comprobación es la misma que hace la constraint {@code no_solape_aula_bloque} de Postgres.
     */
    private Constraint bloqueDeAulaOcupadoPorOtroGrupo(ConstraintFactory factory) {
        return factory
                .forEach(OcupacionExterna.class)
                .filter(o -> o.aulaId() != null && o.bloqueId() != null)
                .join(AsignacionHorario.class,
                        Joiners.equal(OcupacionExterna::aulaId, AsignacionHorario::getAulaId))
                .filter((o, a) -> a.getBloqueHorario() != null && ocupaBloque(a, o.bloqueId()))
                .penalize(HardMediumSoftScore.ONE_HARD)
                .asConstraint("Aula ya ocupada por otro grupo");
    }

    /** true si la sesión ocupa ese bloque EN CUALQUIERA de sus horas (no solo la de inicio). */
    private static boolean ocupaBloque(AsignacionHorario a, Long bloqueId) {
        for (TurnoHorario b : a.getBloquesOcupados()) {
            if (bloqueId.equals(b.getId())) {
                return true;
            }
        }
        return false;
    }

    // ═══════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════

    /**
     * Extrae el ID del bloque de una asignación de forma null-safe.
     *
     * <p>Necesario porque {@code Joiners.equal} invoca el mapper directamente
     * sobre las entidades, y si alguna tiene {@code bloqueHorario == null}
     * (clase aún no asignada), un {@code a.getBloqueHorario().getId()} crudo
     * lanzaría NPE dentro del motor de constraints.
     */
    private static Long idDeBloque(AsignacionHorario a) {
        TurnoHorario bh = a.getBloqueHorario();
        return bh != null ? bh.getId() : null;
    }

    // ─── Distribución ──────────────────────────────────────

    /**
     * Cuántas horas habría que cambiar de día para acercarse al patrón, contando SOLO las horas ya
     * colocadas (0 = el reparto de lo colocado ya es el correcto).
     *
     * <h2>Por qué el patrón se recorta a las horas colocadas</h2>
     * Comparar siempre contra el patrón COMPLETO crea un acantilado. Una materia de 9 horas con
     * patrón "3,3,3" que coloca su PRIMERA hora desviaría 8 unidades de golpe (2 + 3 + 3), o sea 24
     * puntos, mientras que dejarla sin ninguna hora sólo cuesta 6 por "Horas sin asignar": al solver
     * le sale más barato NO EMPEZAR a colocar esa materia. Por eso el objetivo se construye
     * rellenando el patrón (de la sesión más larga a la más corta) hasta las horas ya colocadas:
     * con "3,3,3" y 1 hora el objetivo es [1]; con 4 horas, [3,1].
     *
     * Con eso empezar a colocar no cuesta nada, concentrar horas sí (4 horas el mismo día contra un
     * objetivo [1,3] desvía 6), y cuando la materia está completa el objetivo es el patrón entero,
     * así que sigue valiendo 0 sólo si el reparto es exactamente el pedido.
     *
     * Ante una distribución ilegible, o sin ninguna hora colocada, devuelve 0.
     */
    private int desvioDistribucion(String distribucion, List<AsignacionHorario> bloques) {
        int[] patron = parsearDistribucion(distribucion);
        if (patron.length == 0) {
            return 0;
        }
        int[] real = contarHorasPorDia(bloques).values().stream()
                .mapToInt(Integer::intValue)
                .sorted()
                .toArray();
        if (real.length == 0) {
            return 0;
        }

        int colocadas = 0;
        for (int r : real) {
            colocadas += r;
        }
        int[] objetivo = recortarPatron(patron, colocadas);

        int n = Math.max(real.length, objetivo.length);
        int desvio = 0;
        for (int i = 0; i < n; i++) {
            int r = i < real.length ? real[i] : 0;
            int e = i < objetivo.length ? objetivo[i] : 0;
            desvio += Math.abs(r - e);
        }
        return desvio;
    }

    /**
     * Las primeras {@code horas} horas del patrón, tomando antes las sesiones más largas, y
     * devueltas ordenadas de menor a mayor (igual que la lista real, para poder compararlas
     * posición a posición).
     */
    private static int[] recortarPatron(int[] patron, int horas) {
        int[] ordenado = Arrays.copyOf(patron, patron.length);
        Arrays.sort(ordenado);

        List<Integer> trozos = new ArrayList<>();
        int restantes = horas;
        for (int i = ordenado.length - 1; i >= 0 && restantes > 0; i--) {
            int trozo = Math.min(ordenado[i], restantes);
            trozos.add(trozo);
            restantes -= trozo;
        }

        int[] objetivo = new int[trozos.size()];
        for (int i = 0; i < objetivo.length; i++) {
            objetivo[i] = trozos.get(i);
        }
        Arrays.sort(objetivo);
        return objetivo;
    }

    /**
     * Parsea la distribución de forma defensiva: ante un valor inesperado devuelve
     * un array vacío en lugar de lanzar NumberFormatException (que abortaría el
     * solver). Los filtros llaman antes a distribucionValida(), así que en la
     * práctica solo llega aquí texto ya validado.
     */
    private int[] parsearDistribucion(String d) {
        if (!distribucionValida(d)) {
            return new int[0];
        }
        try {
            return Arrays.stream(d.split(","))
                    .map(String::trim)
                    .mapToInt(Integer::parseInt)
                    .toArray();
        } catch (NumberFormatException e) {
            return new int[0];
        }
    }

    /** true si el texto es una lista de números separados por comas. */
    private boolean distribucionValida(String d) {
        return d != null && !d.isBlank() && DISTRIBUCION_VALIDA.matcher(d.trim()).matches();
    }

    private Map<Integer, Integer> contarHorasPorDia(List<AsignacionHorario> bloques) {
        Map<Integer, Integer> mapa = new HashMap<>();
        for (AsignacionHorario a : bloques) {
            if (a.getBloqueHorario() != null) {
                int dia = a.getBloqueHorario().getDiaSemana();
                mapa.merge(dia, 1, Integer::sum);
            }
        }
        return mapa;
    }

    // ─── Sesiones del patrón ───────────────────────────────

    /**
     * Unidades de penalización de una materia: una por cada hora de una sesión completa mal
     * armada (partida o en el mismo día que otra), más una por cada sesión completa de más que
     * comparta día con otra.
     *
     * Devuelve 0 si la distribución es ilegible o si el patrón no cuadra con el número de horas
     * de la materia: es un dato sucio y no se exige nada, igual que en {@link #desvioDistribucion}.
     */
    private int sesionesRojas(String distribucion, List<AsignacionHorario> lista) {
        int[] patron = parsearDistribucion(distribucion);
        if (patron.length == 0) {
            return 0;
        }
        int suma = 0;
        for (int k : patron) {
            suma += k;
        }
        if (suma != lista.size()) {
            return 0;
        }

        Map<Integer, TurnoHorario> porNumero = new HashMap<>();
        for (AsignacionHorario a : lista) {
            if (a.getBloqueHorario() != null) {
                porNumero.put(a.getNumeroHora(), a.getBloqueHorario());
            }
        }

        Map<Integer, Integer> sesionesPorDia = new HashMap<>();
        int rojas = 0;
        int numero = 1;
        for (int k : patron) {
            List<TurnoHorario> bloques = new ArrayList<>();
            boolean completa = true;
            for (int h = numero; h < numero + k; h++) {
                TurnoHorario b = porNumero.get(h);
                if (b == null) {
                    completa = false;
                    break;
                }
                bloques.add(b);
            }
            numero += k;

            if (!completa) {
                continue;   // sesión a medias: eso lo paga "Horas sin asignar"
            }
            if (!esSesionPegada(bloques)) {
                rojas += k;
                continue;
            }
            sesionesPorDia.merge(bloques.get(0).getDiaSemana(), 1, Integer::sum);
        }

        for (Integer cuantas : sesionesPorDia.values()) {
            if (cuantas > 1) {
                rojas += cuantas - 1;
            }
        }
        return rojas;
    }

    /** true si las horas están todas el mismo día y pegadas (con la tolerancia de descanso). */
    private static boolean esSesionPegada(List<TurnoHorario> bloques) {
        if (bloques.size() < 2) {
            return true;
        }
        List<TurnoHorario> orden = new ArrayList<>(bloques);
        orden.sort(Comparator.comparing(TurnoHorario::getHoraInicio));
        Integer dia = orden.get(0).getDiaSemana();
        for (int i = 1; i < orden.size(); i++) {
            if (!java.util.Objects.equals(orden.get(i).getDiaSemana(), dia)) {
                return false;
            }
            long gap = Duration.between(orden.get(i - 1).getHoraFin(), orden.get(i).getHoraInicio())
                    .toMinutes();
            if (gap > TOLERANCIA_CONTINUIDAD_MINUTOS) {
                return false;
            }
        }
        return true;
    }

    // ─── Huecos ────────────────────────────────────────────

    private int contarHuecos(List<AsignacionHorario> lista) {
        // Con sesiones ya no se cuentan horas sueltas: se comparan TRAMOS. El hueco es el tiempo
        // libre entre el final de una sesión y el inicio de la siguiente del mismo día.
        Map<Integer, List<AsignacionHorario>> porDia = new HashMap<>();
        for (AsignacionHorario a : lista) {
            if (a.getBloqueHorario() != null) {
                porDia.computeIfAbsent(a.getBloqueHorario().getDiaSemana(), k -> new ArrayList<>())
                        .add(a);
            }
        }

        int huecos = 0;
        for (List<AsignacionHorario> sesiones : porDia.values()) {
            if (sesiones.size() < 2) continue;
            sesiones.sort(Comparator.comparing(s -> s.getBloqueHorario().getHoraInicio()));

            for (int i = 1; i < sesiones.size(); i++) {
                TurnoHorario finAnterior = sesiones.get(i - 1).getFinVentana();
                if (finAnterior == null) {
                    finAnterior = sesiones.get(i - 1).getBloqueHorario();
                }
                LocalTime inicioSiguiente = sesiones.get(i).getBloqueHorario().getHoraInicio();
                long gapMin = Duration.between(finAnterior.getHoraFin(), inicioSiguiente).toMinutes();

                // División entera entre la duración del bloque: un hueco de 50-60 min cuenta 1,
                // uno de 110 cuenta 2, y un descanso de 20-30 min entre bloques cuenta 0.
                huecos += (int) Math.max(0, gapMin / DURACION_BLOQUE_MINUTOS);
            }
        }
        return huecos;
    }

    private boolean tieneHuecos(List<AsignacionHorario> lista) {
        return contarHuecos(lista) > 0;
    }
}