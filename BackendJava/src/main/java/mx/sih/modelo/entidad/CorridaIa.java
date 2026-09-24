package mx.sih.modelo.entidad;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Una corrida del generador IA GUARDADA por el usuario, con sus metricas.
 *
 * <p>Para que sirve: el generador lanza varios intentos y se queda con el mejor, pero hasta ahora
 * ese resultado solo vivia en memoria y en la pantalla. Cada generacion nueva pisaba la anterior, asi
 * que no habia forma de comparar dos opciones antes de decidir cual aplicar al horario real.
 *
 * <p>Por que una tabla aparte y no {@code horario} con otra version: las consultas de lectura del
 * horario (grupo, maestro, aula y escuela) NO filtran por version. Mientras existiera una opcion
 * guardada, esas pantallas mostrarian la union de todas las opciones. Ademas {@code horario} no tiene
 * donde guardar el score ni las horas pendientes, que es justo lo que se compara al elegir.
 *
 * <p>Las filas de la solucion viven en {@link CorridaIaDetalle}: son las que necesita
 * {@code HorarioIAServicio.registrar} para reescribir el horario vigente.
 */
@Entity
@Table(name = "corrida_ia", schema = "sih")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CorridaIa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "corrida_ia_id")
    private Long corridaIaId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "escuela_id", nullable = false)
    private Escuela escuela;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "semestre_id", nullable = false)
    private Semestre semestre;

    /** Turno sobre el que se genero. Puede quedar null si el turno se borra del catalogo. */
    @Column(name = "turno_id")
    private Long turnoId;

    /** Nombre que le pone el usuario para reconocerla en la lista. Unico por escuela y semestre. */
    @Column(name = "nombre", nullable = false, length = 120)
    private String nombre;

    @Column(name = "notas", length = 500)
    private String notas;

    /** "heuristica" o "llm:&lt;modelo&gt;" segun el asesor que participo. */
    @Column(name = "asesor", length = 80)
    private String asesor;

    /** Numero de intento dentro de su trabajo. */
    @Column(name = "numero_intento")
    private Integer numeroIntento;

    /** Cuando lo genero el motor (no cuando se guardo). */
    @Column(name = "generado_en")
    private LocalDateTime generadoEn;

    @CreationTimestamp
    @Column(name = "creado", updatable = false)
    private LocalDateTime creado;

    /** Correo de quien la guardo. Mismo criterio que el "solicitadoPor" del modulo IA. */
    @Column(name = "creado_por", length = 120)
    private String creadoPor;

    // ============================================================
    // METRICAS DEL INTENTO: es lo que se compara entre corridas
    // ============================================================

    @Column(name = "milisegundos")
    private Long milisegundos;

    @Column(name = "horas")
    private Integer horas;

    @Column(name = "horas_demandadas")
    private Integer horasDemandadas;

    @Column(name = "sesiones_largas")
    private Integer sesionesLargas;

    @Column(name = "sesiones_largas_pendientes")
    private Integer sesionesLargasPendientes;

    @Column(name = "arranques_tarde")
    private Integer arranquesTarde;

    @Column(name = "castigo_huecos")
    private Integer castigoHuecos;

    @Column(name = "adyacencias")
    private Integer adyacencias;

    @Column(name = "materias_completas")
    private Integer materiasCompletas;

    @Column(name = "materias_totales")
    private Integer materiasTotales;

    @Column(name = "medium")
    private Integer medium;

    // ============================================================
    // RECUENTOS ESPERADOS DEL DETALLE
    // ============================================================

    /**
     * Cuantas filas tenia el intento al guardarse. Sirve para detectar una corrida incompleta: si no
     * cuadra con las filas que hay en {@link CorridaIaDetalle}, alguna asignacion del catalogo se
     * borro despues y la opcion ya no representa lo que era.
     */
    @Column(name = "total_filas")
    private Integer totalFilas;

    @Column(name = "total_pendientes")
    private Integer totalPendientes;

    /**
     * Si es mayor que 0, la corrida NO se puede aplicar: {@code registrar} rechaza los intentos con
     * problemas duros. Se guarda el motivo para poder explicarlo en la pantalla.
     */
    @Column(name = "total_problemas")
    private Integer totalProblemas;

    /** Motivos de los problemas duros, uno por linea. */
    @Column(name = "problemas")
    private String problemas;
}
