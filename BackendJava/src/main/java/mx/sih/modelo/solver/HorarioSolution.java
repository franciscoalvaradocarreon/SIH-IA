// mx.sih.modelo.solver.HorarioSolution.java
package mx.sih.modelo.solver;

import ai.timefold.solver.core.api.domain.solution.PlanningEntityCollectionProperty;
import ai.timefold.solver.core.api.domain.solution.PlanningScore;
import ai.timefold.solver.core.api.domain.solution.PlanningSolution;
import ai.timefold.solver.core.api.domain.solution.ProblemFactCollectionProperty;
import ai.timefold.solver.core.api.domain.valuerange.ValueRangeProvider;
import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import mx.sih.modelo.entidad.DisponibilidadGrupo;
import mx.sih.modelo.entidad.DisponibilidadMaestro;
import mx.sih.modelo.entidad.TurnoHorario;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@PlanningSolution
@Getter
@Setter
@NoArgsConstructor
public class HorarioSolution {

    @ValueRangeProvider(id = "bloquesDisponibles")
    @ProblemFactCollectionProperty
    private List<TurnoHorario> bloquesDisponibles = new ArrayList<>();

    @ProblemFactCollectionProperty
    private List<DisponibilidadMaestro> disponibilidades = new ArrayList<>();

    @ProblemFactCollectionProperty
    private List<DisponibilidadGrupo> disponibilidadesGrupo = new ArrayList<>();

    @PlanningEntityCollectionProperty
    private List<AsignacionHorario> asignaciones = new ArrayList<>();

    @PlanningScore
    private HardSoftScore score;

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