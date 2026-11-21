package mx.sih.modelo.entidad;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * Tipo de laboratorio o taller de un semestre.
 *
 * <p>Sustituye al booleano {@code aulas.taller}, que solo decia "es lab/taller" sin decir cual. Un
 * laboratorio de quimica y un taller de mecanica no son intercambiables en el horario, asi que el
 * tipo se declara en el aula y el motor lo respeta al reasignar.
 *
 * <p>El catalogo es por escuela y semestre, como el resto, y es editable: los cuatro tipos con los
 * que arranca son solo el punto de partida.
 */
@Entity
@Table(name = "tipos_espacio")
@Getter
@Setter
public class TipoEspacio {

    /** Valores admitidos en {@link #clase}. La base tiene la misma restriccion. */
    public static final String CLASE_LABORATORIO = "LABORATORIO";
    public static final String CLASE_TALLER = "TALLER";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "tipo_espacio_id")
    private Long tipoEspacioId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "escuela_id", nullable = false)
    private Escuela escuela;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "semestre_id", nullable = false)
    private Semestre semestre;

    /** {@code LABORATORIO} o {@code TALLER}. Sirve para agrupar en pantalla y en reportes. */
    @Column(name = "clase", length = 20, nullable = false)
    private String clase;

    /** "Laboratorio de Computo", "Taller de Mecanica"... */
    @Column(name = "nombre", length = 80, nullable = false)
    private String nombre;

    /**
     * Turno al que pertenece el tipo. El catalogo es por escuela, semestre y TURNO, como aulas,
     * grupos o materias: cada turno tiene su propia lista y puede tener tipos distintos.
     */
    @Column(name = "turno_id", nullable = false)
    private Long turnoId;

    @Column(name = "activo")
    private Boolean activo = true;

    @Column(name = "creado", updatable = false)
    private LocalDateTime creado = LocalDateTime.now();
}
