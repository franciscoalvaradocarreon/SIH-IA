package mx.sih.modelo.solver;

import ai.timefold.solver.core.api.domain.solution.PlanningEntityCollectionProperty;
import ai.timefold.solver.core.api.domain.solution.PlanningScore;
import ai.timefold.solver.core.api.domain.solution.PlanningSolution;
import ai.timefold.solver.core.api.domain.solution.ProblemFactCollectionProperty;
import ai.timefold.solver.core.api.domain.valuerange.ValueRangeProvider;
import ai.timefold.solver.core.api.score.buildin.hardmediumsoft.HardMediumSoftScore;   // 🔥 CAMBIO
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import mx.sih.modelo.entidad.DisponibilidadGrupo;
import mx.sih.modelo.entidad.DisponibilidadMaestro;
import mx.sih.modelo.entidad.TurnoHorario;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Solución de planificación de horarios.
 *
 * <h2>Cambio de score</h2>
 * Migrado de {@code HardSoftScore} a {@code HardMediumSoftScore} para que el
 * solver pueda distinguir 3 niveles de penalización:
 * <ul>
 *   <li><b>HARD</b>: violaciones intocables (conflictos de grupo/aula/maestro,
 *       disponibilidad, bloque fuera de turno).</li>
 *   <li><b>MEDIUM</b>: deseables pero sacrificables antes que un HARD
 *       (clase sin asignar, distribución, horas continuas). Gracias a este
 *       nivel, el solver PREFIERE dejar una clase como {@code noAsignada} antes
 *       que generar un solapamiento HARD.</li>
 *   <li><b>SOFT</b>: cosmético (evitar huecos).</li>
 * </ul>
 *
 * <p><b>El tipo del score DEBE coincidir con el que devuelve el
 * {@code HorarioConstraintProvider}</b>. Si aquí queda {@code HardSoftScore}
 * pero el provider penaliza con {@code HardMediumSoftScore.ONE_HARD}, el
 * solver no arranca o los cast fallan en tiempo de ejecución.
 */
@PlanningSolution
@Getter
@Setter
@NoArgsConstructor
public class HorarioSolution {

    @ProblemFactCollectionProperty
    private List<TurnoHorario> bloquesDisponibles = new ArrayList<>();

    @ProblemFactCollectionProperty
    private List<DisponibilidadMaestro> disponibilidades = new ArrayList<>();

    @ProblemFactCollectionProperty
    private List<DisponibilidadGrupo> disponibilidadesGrupo = new ArrayList<>();

    /**
     * Bloques ya ocupados por clases de OTROS grupos (maestro y aula), para poder regenerar un grupo
     * suelto sin chocar con el horario que ya existe. En la generación masiva va vacía.
     */
    @ProblemFactCollectionProperty
    private List<OcupacionExterna> ocupacionesExternas = new ArrayList<>();

    @PlanningEntityCollectionProperty
    private List<AsignacionHorario> asignaciones = new ArrayList<>();

    // 🔥 CAMBIO: tipo del score migrado a HardMediumSoftScore
    @PlanningScore
    private HardMediumSoftScore score;

    private Long grupoId;
    private Long semestreId;
    private LocalDateTime fechaGeneracion;

    public HorarioSolution(List<TurnoHorario> bloquesDisponibles,
                           List<DisponibilidadMaestro> disponibilidades,
                           List<DisponibilidadGrupo> disponibilidadesGrupo,
                           List<AsignacionHorario> asignaciones) {
        this.bloquesDisponibles = bloquesDisponibles != null ? bloquesDisponibles : new ArrayList<>();
        this.disponibilidades = disponibilidades != null ? disponibilidades : new ArrayList<>();
        this.disponibilidadesGrupo = disponibilidadesGrupo != null ? disponibilidadesGrupo : new ArrayList<>();
        this.asignaciones = asignaciones != null ? asignaciones : new ArrayList<>();
        this.fechaGeneracion = LocalDateTime.now();
    }
}