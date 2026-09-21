// mx.sih.modelo.solver.HorarioSolverService.java
package mx.sih.modelo.solver;

import ai.timefold.solver.core.api.score.analysis.ScoreAnalysis;
import ai.timefold.solver.core.api.score.buildin.hardmediumsoft.HardMediumSoftScore;
import ai.timefold.solver.core.api.solver.Solver;
import ai.timefold.solver.core.api.solver.SolutionManager;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig;
import ai.timefold.solver.core.config.localsearch.LocalSearchPhaseConfig;
import ai.timefold.solver.core.config.localsearch.decider.acceptor.LocalSearchAcceptorConfig;
import ai.timefold.solver.core.config.localsearch.decider.forager.LocalSearchForagerConfig;
import ai.timefold.solver.core.config.solver.SolverConfig;
import ai.timefold.solver.core.config.solver.termination.TerminationConfig;
import mx.sih.modelo.entidad.TurnoHorario;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * Wrapper de Timefold.
 *
 * <h2>Dos perfiles de solver (uno por operación)</h2>
 * El tiempo que necesita el solver depende del TAMAÑO del problema, y las dos
 * operaciones del sistema no se parecen en nada:
 * <ul>
 *   <li>{@code POST /api/horarios/generar/{grupoId}} → un grupo, decenas de variables.
 *       Respuesta rápida ({@code app.solver.grupo.*}).</li>
 *   <li>{@code POST /api/horarios/generar-todos} → todos los grupos del semestre/turno,
 *       cientos de variables (729 horas en un caso real medido). Necesita más margen
 *       ({@code app.solver.masiva.*}).</li>
 * </ul>
 * Antes había un único límite para las dos, así que o se quedaba corto para la
 * generación masiva (devolvía soluciones incompletas) o hacía esperar de más al
 * usuario que solo quería un grupo.
 *
 * <h2>Rendimiento</h2>
 * Los dos {@link SolverFactory} se construyen UNA vez ({@code @PostConstruct});
 * {@code buildSolver()} —que sí es barato— se llama por request. Antes el factory se
 * construía dentro de cada llamada (1-3 s de overhead por petición).
 *
 * <h2>Configuración de Local Search</h2>
 * Se usa Late Acceptance (LAHC) con historia de 400 scores en lugar del Tabu Search
 * por defecto: acepta movimientos que no mejoran el score actual pero superan el de
 * hace N pasos, y escapa mejor de óptimos locales en problemas muy restringidos.
 *
 * <h2>⚠️ Transacciones (pendiente)</h2>
 * El llamante ({@code HorarioServicio}) todavía ejecuta el solver DENTRO de la
 * transacción de base de datos, así que una conexión Hikari queda retenida todo el
 * tiempo del solve. Con el límite masivo actual son hasta 180 s por petición. Lo
 * correcto es cargar → resolver fuera de la transacción → persistir en una
 * transacción corta (o ejecutarlo asíncrono con un id de trabajo).
 */
@Service
public class HorarioSolverService {

    private static final Logger logger = LoggerFactory.getLogger(HorarioSolverService.class);

    /** Tamaño de la historia de Late Acceptance. */
    private static final int HISTORIA_LATE_ACCEPTANCE = 400;

    /**
     * Movimientos aceptados antes de que el forager aplique el mejor.
     *
     * <h2>Por qué 400 y no 4</h2>
     * Estaba en 4, que con Late Acceptance es casi escalada pura: el forager se queda con el
     * mejor de cada 4 movimientos aceptados, así que no puede encadenar los varios movimientos
     * que hacen falta para armar una sesión partida (sacar dos horas de donde están y meterlas
     * juntas en la misma ventana), porque el camino pasa por estados peores.
     *
     * Con 150 (medido sobre el semestre 11) la cobertura subió de 681 a 684 horas, las materias
     * completas de 144 a 152 y los huecos bajaron de 21 a 5, con la distribución intacta al
     * 100 %. Como seguía mejorando y en Late Acceptance lo habitual es igualar el tamaño de la
     * historia ({@link #HISTORIA_LATE_ACCEPTANCE}), se sube a 400.
     */
    private static final int MOVIMIENTOS_ACEPTADOS = 400;

    // ── Límites para UN grupo ──
    private final long segundosGrupo;
    private final long segundosSinMejoraGrupo;

    // ── Límites para la generación MASIVA ──
    private final long segundosMasiva;
    private final long segundosSinMejoraMasiva;

    // ── Palanca de PRUEBAS: terminar por PASOS en vez de por reloj (0 = por reloj) ──
    private final long pasosGrupo;
    private final long pasosMasiva;

    // ── Varias corridas de la generación masiva (best-of-N) ──
    private final int intentosMasiva;
    private final long segundosPorIntentoMasiva;

    /**
     * Tope de reloj que acompaña al límite de pasos. No es el límite real: es la red de seguridad
     * para que una prueba mal configurada no se quede corriendo horas.
     */
    private static final long SEGUNDOS_TOPE_PRUEBA = 3600;

    private SolverFactory<HorarioSolution> solverFactoryGrupo;
    private SolverFactory<HorarioSolution> solverFactoryMasiva;

    /** Fábrica para las corridas por intentos (límite corto por intento). */
    private SolverFactory<HorarioSolution> solverFactoryMasivaIntentos;

    /** Corrida en curso, para poder terminarla desde el hilo HTTP. */
    private volatile Solver<HorarioSolution> solverEnCurso;

    /** Lo pone el hilo HTTP al pulsar "Terminar"; el bucle de intentos lo lee. */
    private volatile boolean cancelado;

    /** Compartido: ScoreManager/SolutionManager de Timefold son thread-safe. */
    private SolutionManager<HorarioSolution, HardMediumSoftScore> solutionManager;

    public HorarioSolverService(
            @Value("${app.solver.grupo.seconds-spent-limit}") long segundosGrupo,
            @Value("${app.solver.grupo.unimproved-seconds-spent-limit}") long segundosSinMejoraGrupo,
            @Value("${app.solver.masiva.seconds-spent-limit}") long segundosMasiva,
            @Value("${app.solver.masiva.unimproved-seconds-spent-limit}") long segundosSinMejoraMasiva,
            @Value("${app.solver.grupo.step-count-limit:0}") long pasosGrupo,
            @Value("${app.solver.masiva.step-count-limit:0}") long pasosMasiva,
            @Value("${app.solver.masiva.intentos:1}") int intentosMasiva,
            @Value("${app.solver.masiva.segundos-por-intento:60}") long segundosPorIntentoMasiva) {
        this.segundosGrupo = segundosGrupo;
        this.segundosSinMejoraGrupo = segundosSinMejoraGrupo;
        this.segundosMasiva = segundosMasiva;
        this.segundosSinMejoraMasiva = segundosSinMejoraMasiva;
        this.pasosGrupo = pasosGrupo;
        this.pasosMasiva = pasosMasiva;
        this.intentosMasiva = intentosMasiva;
        this.segundosPorIntentoMasiva = segundosPorIntentoMasiva;
    }

    @PostConstruct
    void inicializar() {
        this.solverFactoryGrupo = crearSolverFactory(segundosGrupo, segundosSinMejoraGrupo, pasosGrupo);
        this.solverFactoryMasiva = crearSolverFactory(segundosMasiva, segundosSinMejoraMasiva, pasosMasiva);
        // Cada intento con su propio límite y SIN límite de "sin mejora": un intento corto debe
        // aprovecharse entero en vez de cortarse esperando una mejora que quizá no llegue.
        this.solverFactoryMasivaIntentos = crearSolverFactory(
                segundosPorIntentoMasiva, segundosPorIntentoMasiva, 0);
        this.solutionManager = SolutionManager.create(solverFactoryMasiva);

        logger.info("Solver listo · GRUPO: {}s (sin mejora {}s) · MASIVA: {}s (sin mejora {}s) · "
                        + "acceptor=LateAcceptance(size={}), forager=acceptedCountLimit={}",
                segundosGrupo, segundosSinMejoraGrupo,
                segundosMasiva, segundosSinMejoraMasiva,
                HISTORIA_LATE_ACCEPTANCE, MOVIMIENTOS_ACEPTADOS);

        if (intentosMasiva > 1) {
            logger.info("Generación masiva POR INTENTOS: {} corridas de {}s (reinicio y orden de "
                            + "entidades distinto), se guarda la mejor · total {}s",
                    intentosMasiva, segundosPorIntentoMasiva, getLimiteSegundosMasiva());
        }

        if (pasosGrupo > 0 || pasosMasiva > 0) {
            logger.warn("MODO PRUEBA activo: terminación por PASOS · grupo={} · masiva={} "
                            + "(0 = por reloj). Dos corridas del mismo código deben dar el MISMO horario.",
                    pasosGrupo, pasosMasiva);
        }
    }

    /**
     * Terminación de la búsqueda.
     *
     * En producción manda el reloj: interesa aprovechar todo el tiempo disponible. Para MEDIR no
     * sirve, porque el número de pasos que da ese tiempo depende de la máquina y dos corridas del
     * mismo código producen horarios distintos. Con {@code pasos > 0} se termina por PASOS y la
     * corrida se vuelve repetible.
     */
    private TerminationConfig terminacion(long segundos, long segundosSinMejora, long pasos) {
        if (pasos > 0) {
            return new TerminationConfig()
                    .withStepCountLimit((int) pasos)
                    .withSecondsSpentLimit(Math.max(segundos, SEGUNDOS_TOPE_PRUEBA));
        }
        return new TerminationConfig()
                .withSecondsSpentLimit(segundos)
                .withUnimprovedSecondsSpentLimit(segundosSinMejora);
    }

    private SolverFactory<HorarioSolution> crearSolverFactory(long segundos, long segundosSinMejora,
                                                             long pasos) {
        var acceptorConfig = new LocalSearchAcceptorConfig()
                .withLateAcceptanceSize(HISTORIA_LATE_ACCEPTANCE);

        var foragerConfig = new LocalSearchForagerConfig()
                .withAcceptedCountLimit(MOVIMIENTOS_ACEPTADOS);

        var localSearch = new LocalSearchPhaseConfig()
                .withAcceptorConfig(acceptorConfig)
                .withForagerConfig(foragerConfig);

        SolverConfig config = new SolverConfig()
                .withSolutionClass(HorarioSolution.class)
                .withEntityClasses(AsignacionHorario.class)
                .withConstraintProviderClass(HorarioConstraintProvider.class)
                .withPhases(
                        new ConstructionHeuristicPhaseConfig(),
                        localSearch
                )
                .withTerminationConfig(terminacion(segundos, segundosSinMejora, pasos));

        return SolverFactory.create(config);
    }

    /** Resuelve un problema de UN grupo (perfil rápido). */
    public HorarioSolution resolverGrupo(HorarioSolution problem) {
        return resolverCon(solverFactoryGrupo, "grupo", problem, segundosGrupo);
    }

    /**
     * Resuelve un problema con TODOS los grupos del semestre/turno (perfil masivo).
     *
     * <p>Con {@code app.solver.masiva.intentos > 1} hace VARIAS corridas del mismo problema y se
     * queda con la mejor. Motivo medido: con terminación por reloj la búsqueda no es reproducible y
     * el MISMO código dio entre 675 y 714 horas colocadas en corridas distintas; repetir y quedarse
     * con la mejor convierte esa dispersión en una ventaja.
     */
    public HorarioSolution resolverMasiva(HorarioSolution problem) {
        if (intentosMasiva <= 1) {
            return resolverCon(solverFactoryMasiva, "masiva", problem, segundosMasiva);
        }
        return resolverPorIntentos(problem);
    }

    /**
     * Corridas sucesivas del MISMO problema, cada una desde cero, quedándose con la mejor.
     *
     * <h2>Cómo se consigue que las corridas sean distintas</h2>
     * La semilla del solver es fija (0, repetible), así que la diversidad no puede venir de ahí:
     * antes de cada intento se <b>borran</b> las variables de planificación (reinicio real, no
     * continuación) y se <b>baraja el orden de las entidades</b>, que es lo que decide el orden en
     * que la heurística de construcción las coloca. Con el mismo problema, un orden distinto lleva a
     * otra solución. El barajado usa {@code new Random(intento)}, así que la corrida entera sigue
     * siendo reproducible.
     *
     * <h2>Cuándo para</h2>
     * Al acabar los intentos, si el usuario pulsa "Terminar", o antes si la solución ya es perfecta
     * (0hard/0medium: todas las horas colocadas, sin huecos, sin arranques tarde y sin adyacencias).
     */
    private HorarioSolution resolverPorIntentos(HorarioSolution problem) {
        cancelado = false;
        HardMediumSoftScore mejorScore = null;
        HorarioSolution mejor = null;

        for (int intento = 0; intento < intentosMasiva; intento++) {
            if (intento > 0) {
                // Reinicio real: sin esto el solver continuaría desde la solución anterior.
                for (AsignacionHorario a : problem.getAsignaciones()) {
                    a.setBloqueHorario(null);
                }
                Collections.shuffle(problem.getAsignaciones(), new Random(intento));
            }

            Solver<HorarioSolution> solver = solverFactoryMasivaIntentos.buildSolver();
            solverEnCurso = solver;
            long inicio = System.currentTimeMillis();
            HorarioSolution solucion = solver.solve(problem);
            solverEnCurso = null;

            HardMediumSoftScore score = (HardMediumSoftScore) solucion.getScore();
            logger.info("Intento {}/{}: score {} en {} ms{}",
                    intento + 1, intentosMasiva, score, System.currentTimeMillis() - inicio,
                    cancelado ? " (terminado por el usuario)" : "");

            if (mejorScore == null || (score != null && score.compareTo(mejorScore) > 0)) {
                mejorScore = score;
                mejor = solucion;
            }

            if (cancelado) {
                break;
            }
            if (mejorScore != null && mejorScore.hardScore() == 0 && mejorScore.mediumScore() == 0) {
                logger.info("Solución perfecta (0hard/0medium) en el intento {}; no hace falta seguir",
                        intento + 1);
                break;
            }
        }

        // OJO (bug real que vació un horario): solve() NO modifica la instancia que recibe, devuelve
        // una COPIA con las variables puestas. Devolver `problem` (como se hacía antes) entregaba el
        // problema original: todas las variables en null, score 0hard/-6·horas, y como "factible"
        // solo mira las violaciones HARD, el backend borraba el horario guardado e insertaba 0 filas.
        // Por eso se devuelve tal cual el objeto de la mejor corrida, sin copiar valores.
        if (mejor == null) {
            // No debería pasar (intentosMasiva >= 1), pero devolver el problema vacío sería el bug.
            throw new IllegalStateException("La generación por intentos no produjo ninguna solución");
        }
        solutionManager.update(mejor);
        HardMediumSoftScore recalculado = (HardMediumSoftScore) mejor.getScore();
        if (mejorScore != null && !mejorScore.equals(recalculado)) {
            logger.error("Score inconsistente al devolver la mejor corrida: registrado {} y "
                    + "recalculado {}", mejorScore, recalculado);
        }
        logger.info("Generación por intentos: {} intento(s) · mejor score {}", intentosMasiva, recalculado);
        return mejor;
    }

    /**
     * Termina la corrida en curso y hace que el bucle de intentos no lance más.
     *
     * <p>El intento que se está resolviendo devuelve lo que tenga en ese momento, así que la
     * generación guarda la MEJOR solución encontrada hasta ahora en lugar de perderlo todo.
     *
     * @return true si había una corrida en curso.
     */
    public boolean cancelarGeneracionEnCurso() {
        cancelado = true;
        Solver<HorarioSolution> solver = solverEnCurso;
        if (solver == null) {
            return false;
        }
        logger.info("Terminando la generación en curso a petición del usuario");
        solver.terminateEarly();
        return true;
    }

    /**
     * Presupuesto total de la generación masiva, en segundos: los intentos por su límite, o el
     * límite único. Es lo que publica la interfaz para dibujar la barra de progreso.
     */
    public long getLimiteSegundosMasiva() {
        return intentosMasiva > 1 ? intentosMasiva * segundosPorIntentoMasiva : segundosMasiva;
    }

    /** Compatibilidad: sin contexto, se asume el perfil de un grupo. */
    public HorarioSolution resolver(HorarioSolution problem) {
        return resolverGrupo(problem);
    }

    private HorarioSolution resolverCon(SolverFactory<HorarioSolution> factory,
                                        String etiqueta,
                                        HorarioSolution problem,
                                        long limiteSegundos) {
        logger.debug("Iniciando solver [{}]: asignaciones={}, bloques={}, disponibilidades={}, dispGrupo={}",
                etiqueta,
                problem.getAsignaciones().size(),
                problem.getBloquesDisponibles().size(),
                problem.getDisponibilidades() != null ? problem.getDisponibilidades().size() : 0,
                problem.getDisponibilidadesGrupo() != null ? problem.getDisponibilidadesGrupo().size() : 0);

        Solver<HorarioSolution> solver = factory.buildSolver();   // barato
        long inicio = System.currentTimeMillis();
        HorarioSolution solution = solver.solve(problem);
        long transcurridoMs = System.currentTimeMillis() - inicio;

        logger.info("Solver [{}] finalizado en {} ms (límite {}s, sin mejora {}s). Score: {}",
                etiqueta, transcurridoMs, limiteSegundos,
                "grupo".equals(etiqueta) ? segundosSinMejoraGrupo : segundosSinMejoraMasiva,
                solution.getScore());
        return solution;
    }

    /**
     * Analiza una solución ya resuelta y devuelve el desglose de violaciones HARD
     * por constraint. Útil para diagnosticar por qué el solver no llega a 0 hard.
     *
     * <p>Uso típico: si {@code solution.getScore().hardScore() < 0}, llamar a este
     * método para saber EXACTAMENTE qué constraints están fallando y cuántas veces.
     *
     * @return Map con nombre de constraint → total de penalización + número de matches.
     */
    public Map<String, String> analizarViolaciones(HorarioSolution solution) {
        ScoreAnalysis<HardMediumSoftScore> scoreAnalysis = solutionManager.analyze(solution);

        return scoreAnalysis.constraintMap().entrySet().stream()
                .filter(e -> e.getValue().score().hardScore() < 0)
                .sorted((a, b) -> Integer.compare(
                        a.getValue().score().hardScore(),
                        b.getValue().score().hardScore()))
                .collect(Collectors.toMap(
                        e -> e.getKey().constraintName(),
                        e -> e.getValue().score() + " (matches=" + e.getValue().matchCount() + ")",
                        (a, b) -> a,
                        java.util.LinkedHashMap::new
                ));
    }
}
