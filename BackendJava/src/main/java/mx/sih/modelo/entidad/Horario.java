package mx.sih.modelo.entidad;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "horario", schema = "sih")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Horario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "horario_id")
    private Long horarioId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "escuela_id", nullable = false)
    private Escuela escuela;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grupo_id", nullable = false)
    private Grupo grupo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asignacion_id", nullable = false)
    private Asignacion asignacion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "turno_horario_id", nullable = false)
    private TurnoHorario turnoHorario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "aula_id", nullable = false)
    private Aula aula;

    @Column(name = "maestro_id", nullable = false)
    private Long maestroId;

    @CreationTimestamp
    @Column(name = "creado", updatable = false)
    private LocalDateTime creado;

    @Column(name = "version")
    private Integer version = 1;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "semestre_id", nullable = false)
    private Semestre semestre;

    /**
     * Copias del grupo al que pertenece la clase: turno, especialidad y grado. Viven en la propia
     * fila para poder leer el horario y armar reportes sin ir a grupos en cada consulta
     * (db/11_horario_turno_especialidad.sql). El QA del esquema comprueba que sigan coincidiendo
     * con el grupo, porque son datos repetidos.
     *
     * Van como columnas sueltas y no como relaciones, igual que maestroId: el horario se lee en
     * bloque (cientos de filas) y no queremos que cada fila dispare consultas por detras.
     *
     * especialidadId puede ser null: hay grupos sin especialidad.
     */
    @Column(name = "turno_id")
    private Long turnoId;

    @Column(name = "especialidad_id")
    private Long especialidadId;

    @Column(name = "grado")
    private Integer grado;

    /**
     * Rellena las tres copias a partir del grupo ya asignado. Existe para que la derivacion este
     * en UN solo sitio: los dos puntos que crean filas de horario (el motor IA y el tablero manual)
     * la llaman despues de setGrupo, y asi no pueden rellenarlas de forma distinta.
     */
    public void copiarDelGrupo() {
        if (grupo == null) {
            return;
        }
        this.turnoId = (grupo.getTurno() != null) ? grupo.getTurno().getTurnoId() : null;
        this.especialidadId = (grupo.getEspecialidad() != null)
                ? grupo.getEspecialidad().getEspecialidadId()
                : null;
        this.grado = grupo.getGrado();
    }
}