package mx.sih.modelo.entidad;

import jakarta.persistence.*;
import lombok.*;

/**
 * Una fila de la solucion de una corrida guardada: un bloque ocupado.
 *
 * <p>Es la copia exacta de {@code mx.sih.ia.IntentoIA.Fila}, que es lo unico que necesita
 * {@code HorarioIAServicio.registrar} para reescribir el horario vigente.
 *
 * <p>Los identificadores se guardan como numeros sueltos y no como asociaciones: una corrida tiene
 * cientos de filas y al aplicarla solo hacen falta los numeros, asi que traer cinco asociaciones
 * perezosas por fila seria trabajo inutil. Las claves foraneas de la base siguen ahi, que es donde
 * importan.
 */
@Entity
@Table(name = "corrida_ia_detalle", schema = "sih")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CorridaIaDetalle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "corrida_ia_detalle_id")
    private Long corridaIaDetalleId;

    /** La corrida a la que pertenece esta fila. La base borra el detalle en cascada. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "corrida_ia_id", nullable = false)
    private CorridaIa corrida;

    @Column(name = "asignacion_id", nullable = false)
    private Long asignacionId;

    @Column(name = "turno_horario_id", nullable = false)
    private Long turnoHorarioId;

    /**
     * Maestro elegido para esa sesion. En modo stock puede diferir del de la asignacion. Nullable: si
     * el maestro desaparece del catalogo la fila queda en null y {@code registrar} vuelve a usar el
     * de la asignacion en lugar de romperse.
     */
    @Column(name = "maestro_id")
    private Long maestroId;

    /** Taller (aula) elegido para esa sesion. Mismo criterio que {@link #maestroId}. */
    @Column(name = "aula_id")
    private Long aulaId;
}
