package mx.sih.modelo.entidad;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;

@Entity
@Table(name = "turno_horario", schema = "sih")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TurnoHorario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "turno_horario_id")
    private Long id;

    @ManyToOne
    @JoinColumn(name = "turno_id", nullable = false)
    private Turno turno;

    @Column(name = "dia_semana", nullable = false)
    private Integer diaSemana;  // 1=Lunes, 2=Martes, ..., 5=Viernes

    @Column(name = "hora_inicio", nullable = false)
    private LocalTime horaInicio;

    @Column(name = "hora_fin", nullable = false)
    private LocalTime horaFin;

    @Column(name = "descanso")
    private Boolean descanso = false;

    @Column(name = "orden")
    private Integer orden = 0;

    @ManyToOne
    @JoinColumn(name = "semestre_id", nullable = false)
    private Semestre semestre;
}