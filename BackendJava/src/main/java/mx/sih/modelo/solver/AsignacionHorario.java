package mx.sih.modelo.solver;

import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.lookup.PlanningId;
import ai.timefold.solver.core.api.domain.valuerange.ValueRangeProvider;
import ai.timefold.solver.core.api.domain.variable.PlanningVariable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import mx.sih.modelo.entidad.TurnoHorario;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    /** true si la clase va en aula de taller: el generador la coloca antes que las demas. */
    private Boolean taller = false;
    private Long turnoId;
    /** Número de sesión dentro de la asignación (1..S). */
    private Integer sesion;

    /** Horas seguidas que ocupa la sesión (1 = hora suelta, 2 = par, 3 = taller largo). */
    private Integer duracion = 1;

    /**
     * Bloques que ocupa cada inicio válido: id del bloque de inicio -> sus {@code duracion}
     * bloques consecutivos. Lo precalcula HorarioServicio, y es lo que vuelve estructural el
     * patrón: el solver elige el inicio, nunca cómo se parte la sesión.
     */
    private Map<Long, List<TurnoHorario>> ventana = new HashMap<>();

    // 🔥 NUEVO: rango de bloques válidos SOLO para esta asignación.
    // Precalculado en HorarioServicio cuando se construye el problema.
    private List<TurnoHorario> bloquesValidos = new ArrayList<>();

    // === VARIABLE DE PLANIFICACIÓN ===
    private TurnoHorario bloqueHorario;

    // nullable = true es IMPRESCINDIBLE y antes faltaba.
    //
    // El valor por defecto de @PlanningVariable es nullable = false, así que el solver
    // tenía PROHIBIDO dejar una hora sin asignar. Consecuencia: cuando no hay bloques
    // suficientes para todos (maestro o aula compartidos), estaba OBLIGADO a empalmar
    // dos clases en el mismo bloque (violación HARD) en lugar de dejar una hora fuera,
    // y la solución terminaba infactible (y por tanto sin persistir nada).
    //
    // Además dejaba muerta la constraint "Horas sin asignar" (penaliza ONE_MEDIUM un
    // bloque null): null era un valor inalcanzable. Con nullable = true el solver
    // prefiere SIEMPRE un horario sin solapes con alguna hora pendiente antes que un
    // horario completo con empalmes, que es exactamente lo que documenta el provider.
    @PlanningVariable(valueRangeProviderRefs = "bloquesDisponibles", nullable = true)
    public TurnoHorario getBloqueHorario() {
        return bloqueHorario;
    }

    // 🔥 ValueRangeProvider por instancia: Timefold invoca este getter para
    // cada entidad y usa la lista devuelta como su rango de valores.
    @ValueRangeProvider(id = "bloquesDisponibles")
    public List<TurnoHorario> getBloquesValidos() {
        return bloquesValidos;
    }

    public AsignacionHorario(Long asignacionId, Long grupoId, String grupoNombre,
                             Long materiaId, String materiaNombre, String materiaClave,
                             Long maestroId, String maestroNombre,
                             Long aulaId, String aulaNombre, Boolean taller,
                             String colorHex, String distribucion, Integer sesion, Integer duracion,
                             Long turnoId) {
        this.id = asignacionId + "-s" + sesion;
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
        this.taller = taller;
        this.colorHex = colorHex;
        this.distribucion = distribucion;
        this.sesion = sesion;
        this.duracion = duracion;
        this.turnoId = turnoId;
    }

    /** Alias: el resto del código seguía llamando a numeroHora. */
    public Integer getNumeroHora() {
        return sesion;
    }

    /** Bloques que ocupa la sesión colocada (vacío si está sin colocar). */
    public List<TurnoHorario> getBloquesOcupados() {
        if (bloqueHorario == null) {
            return List.of();
        }
        List<TurnoHorario> bloques = ventana.get(bloqueHorario.getId());
        return bloques == null ? List.of(bloqueHorario) : bloques;
    }

    /** Último bloque de la sesión colocada: con él se calculan huecos y empalmes. */
    public TurnoHorario getFinVentana() {
        List<TurnoHorario> bloques = getBloquesOcupados();
        return bloques.isEmpty() ? null : bloques.get(bloques.size() - 1);
    }

    /** true si las dos sesiones se pisan: mismo día con los intervalos de bloques cruzados. */
    public boolean chocaCon(AsignacionHorario otra) {
        if (otra == null || bloqueHorario == null || otra.bloqueHorario == null) {
            return false;
        }
        Integer dia = bloqueHorario.getDiaSemana();
        Integer diaOtra = otra.bloqueHorario.getDiaSemana();
        if (dia == null || diaOtra == null || !dia.equals(diaOtra)) {
            return false;
        }
        Integer iniA = bloqueHorario.getOrden();
        Integer iniB = otra.bloqueHorario.getOrden();
        TurnoHorario finA = getFinVentana();
        TurnoHorario finB = otra.getFinVentana();
        Integer finOrdA = finA == null ? iniA : finA.getOrden();
        Integer finOrdB = finB == null ? iniB : finB.getOrden();
        if (iniA == null || iniB == null || finOrdA == null || finOrdB == null) {
            return false;
        }
        // Se comparan los extremos REALES de la ventana, no [inicio, inicio + duración - 1]:
        // cuando hay un descanso en medio los órdenes de los bloques NO son consecutivos, y esa
        // cuenta dejaba fuera el último bloque (así se colaba un solape que la base de datos cazó
        // con la restricción no_solape_maestro_bloque).
        return iniA <= finOrdB && iniB <= finOrdA;
    }

    /**
     * true si las dos sesiones quedan PEGADAS (gap de 5 min o menos) sin pisarse: la regla dura
     * de "el mismo maestro no cambia de materia con el mismo grupo de golpe".
     */
    public boolean pegadoCon(AsignacionHorario otra) {
        if (otra == null || chocaCon(otra)) {
            return false;
        }
        TurnoHorario finA = getFinVentana();
        TurnoHorario finB = otra.getFinVentana();
        if (finA == null || finB == null) {
            return false;
        }
        long gapAB = java.time.Duration
                .between(finA.getHoraFin(), otra.bloqueHorario.getHoraInicio()).toMinutes();
        long gapBA = java.time.Duration
                .between(finB.getHoraFin(), bloqueHorario.getHoraInicio()).toMinutes();
        return (gapAB >= 0 && gapAB <= 5) || (gapBA >= 0 && gapBA <= 5);
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