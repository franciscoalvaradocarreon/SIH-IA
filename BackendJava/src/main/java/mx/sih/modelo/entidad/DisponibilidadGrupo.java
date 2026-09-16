package mx.sih.modelo.entidad;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "disponibilidad_grupo", schema = "sih",
       uniqueConstraints = @UniqueConstraint(
           name = "grupo_turno_semestre_unique",
           columnNames = {"grupo_id", "turno_horario_id", "semestre_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DisponibilidadGrupo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "disponibilidad_grupo_id")
    private Long disponibilidadGrupoId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "escuela_id", nullable = false)
    private Escuela escuela;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grupo_id", nullable = false)
    private Grupo grupo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "turno_horario_id", nullable = false)
    private TurnoHorario turnoHorario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "semestre_id", nullable = false)
    private Semestre semestre;

    @Column(name = "disponible", nullable = false)
    private Boolean disponible = true;

    @CreationTimestamp
    @Column(name = "creado", updatable = false)
    private LocalDateTime creado;
}