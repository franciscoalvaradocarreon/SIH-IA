// mx.sih.modelo.entidad.DisponibilidadMaestro.java
package mx.sih.modelo.entidad;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalTime;

@Entity
@Table(name = "disponibilidad_maestro", schema = "sih")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DisponibilidadMaestro {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "disponibilidad_maestro_id")
    private Long disponibilidadMaestroId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "escuela_id", nullable = false)
    private Escuela escuela;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "maestro_id", nullable = false)
    private Maestro maestro;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "turno_horario_id", nullable = false)
    private TurnoHorario turnoHorario;

    @Column(name = "disponible", nullable = false)
    private Boolean disponible = false;

    @CreationTimestamp
    @Column(name = "creado", updatable = false)
    private LocalTime creado;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "semestre_id", nullable = false)
    private Semestre semestre;
    
}