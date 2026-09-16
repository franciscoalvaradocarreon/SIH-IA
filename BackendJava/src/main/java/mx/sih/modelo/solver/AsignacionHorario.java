package mx.sih.modelo.solver;

import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.lookup.PlanningId;
import ai.timefold.solver.core.api.domain.variable.PlanningVariable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import mx.sih.modelo.entidad.TurnoHorario;

@PlanningEntity
@Getter
@Setter
@NoArgsConstructor
public class AsignacionHorario {

    @PlanningId
    private String id;

    // === DATOS DE LA ASIGNACIÓN (fijos) ===
    private Long asignacionId;
    private Long grupoId;
    private String grupoNombre;
    private Long materiaId;
    private String materiaNombre;
    private String materiaClave;
    private Long maestroId;
    private String maestroNombre;
    private Long aulaId;
    private String aulaNombre;
    private String colorHex;
    private String distribucion;
    private Long turnoId;
    private Integer numeroHora;

    // === VARIABLE DE PLANIFICACIÓN ===
    private TurnoHorario bloqueHorario;

    @PlanningVariable(valueRangeProviderRefs = "bloquesDisponibles")
    public TurnoHorario getBloqueHorario() {
        return bloqueHorario;
    }

    /**
     * ID determinista: `${asignacionId}-${numeroHora}`.
     * Antes se usaba UUID.randomUUID(), que es:
     *   - Más lento (SecureRandom).
     *   - Ilegible en logs ("id=f4e2..." vs "id=42-3").
     *   - No comparable entre ejecuciones.
     * Con asignacionId único y numeroHora ∈ [1..horas], el par es único.
     */
    public AsignacionHorario(Long asignacionId, Long grupoId, String grupoNombre,
                             Long materiaId, String materiaNombre, String materiaClave,
                             Long maestroId, String maestroNombre,
                             Long aulaId, String aulaNombre,
                             String colorHex, String distribucion, Integer numeroHora,
                             Long turnoId) {
        this.id = asignacionId + "-" + numeroHora;
        this.asignacionId = asignacionId;
        this.grupoId = grupoId;
        this.grupoNombre = grupoNombre;
        this.materiaId = materiaId;
        this.materiaNombre = materiaNombre;
        this.materiaClave = materiaClave;
        this.maestroId = maestroId;
        this.maestroNombre = maestroNombre;
        this.aulaId = aulaId;
        this.aulaNombre = aulaNombre;
        this.colorHex = colorHex;
        this.distribucion = distribucion;
        this.numeroHora = numeroHora;
        this.turnoId = turnoId;
    }

    @Override
    public String toString() {
        return "AsignacionHorario{" +
                "id=" + id +
                ", asignacionId=" + asignacionId +
                ", materia=" + materiaNombre +
                ", grupo=" + grupoNombre +
                ", bloque=" + (bloqueHorario != null
                    ? bloqueHorario.getDiaSemana() + "-" + bloqueHorario.getHoraInicio()
                    : "null") +
                '}';
    }
}