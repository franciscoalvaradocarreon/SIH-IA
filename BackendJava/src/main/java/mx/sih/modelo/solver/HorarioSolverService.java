// mx.sih.modelo.solver.HorarioSolverService.java
package mx.sih.modelo.solver;

import ai.timefold.solver.core.api.solver.Solver;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.solver.SolverConfig;
import ai.timefold.solver.core.config.solver.termination.TerminationConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

/**
 * Wrapper de Timefold.
 *
 * CAMBIO DE RENDIMIENTO:
 *  Antes el SolverFactory se construía dentro de cada llamada a resolver().
 *  Eso obligaba a Timefold a parsear la configuración, instanciar el
 *  ConstraintProvider y escanear las clases cada vez (1-3s de overhead por
 *  request). Ahora el factory se construye UNA vez (@PostConstruct) y
 *  buildSolver() —que sí es barato— se llama por request.
 *
 * NOTA sobre transacciones:
 *  El llamante (HorarioServicio.generarTodos) debe sacar la llamada al solver
 *  FUERA de la transacción de base de datos. El solver puede tardar 60-180s
 *  y bloquear una conexión Hikari durante todo ese tiempo.
 */
@Service
public class HorarioSolverService {

    private static final Logger logger = LoggerFactory.getLogger(HorarioSolverService.class);

    private final long secondsSpentLimit;
    private final long unimprovedSecondsSpentLimit;

    private SolverFactory<HorarioSolution> solverFactory;

    public HorarioSolverService(
            @Value("${app.solver.seconds-spent-limit}") long secondsSpentLimit,
            @Value("${app.solver.unimproved-seconds-spent-limit}") long unimprovedSecondsSpentLimit) {
        this.secondsSpentLimit = secondsSpentLimit;
        this.unimprovedSecondsSpentLimit = unimprovedSecondsSpentLimit;
    }

    @PostConstruct
    void inicializar() {
        SolverConfig config = new SolverConfig()
                .withSolutionClass(HorarioSolution.class)
                .withEntityClasses(AsignacionHorario.class)
                .withConstraintProviderClass(HorarioConstraintProvider.class)
                .withTerminationConfig(new TerminationConfig()
                        .withSecondsSpentLimit(secondsSpentLimit)
                        .withUnimprovedSecondsSpentLimit(unimprovedSecondsSpentLimit));

        this.solverFactory = SolverFactory.create(config);
        logger.info("SolverFactory inicializado (secondsSpent={}, unimproved={})",
                secondsSpentLimit, unimprovedSecondsSpentLimit);
    }

    public HorarioSolution resolver(HorarioSolution problem) {
        logger.debug("Iniciando solver: asignaciones={}, bloques={}, disponibilidades={}, dispGrupo={}",
                problem.getAsignaciones().size(),
                problem.getBloquesDisponibles().size(),
                problem.getDisponibilidades() != null ? problem.getDisponibilidades().size() : 0,
                problem.getDisponibilidadesGrupo() != null ? problem.getDisponibilidadesGrupo().size() : 0);

        Solver<HorarioSolution> solver = solverFactory.buildSolver();   // barato
        HorarioSolution solution = solver.solve(problem);

        logger.info("Solver finalizado. Score: {}", solution.getScore());
        return solution;
    }
}