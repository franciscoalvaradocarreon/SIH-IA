package mx.sih.modelo.entidad;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "grupos", schema = "sih")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Grupo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "grupo_id")
    private Long grupoId;

    @ManyToOne
    @JoinColumn(name = "escuela_id", nullable = false)
    private Escuela escuela;

    @Column(length = 20, nullable = false)
    private String nombre;

    @Column(nullable = false)
    private Integer grado;

    @ManyToOne
    @JoinColumn(name = "turno_id", nullable = false)
    private Turno turno;

    @ManyToOne
    @JoinColumn(name = "especialidad_id")
    private Especialidad especialidad;

    private Integer capacidad = 0;

    private Boolean activo = true;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
    
    @ManyToOne
    @JoinColumn(name = "semestre_id", nullable = false)
    private Semestre semestre;
}