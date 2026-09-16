package mx.sih.modelo.entidad;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "materias", schema = "sih")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Materia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "materia_id")
    private Long materiaId;

    @Column(length = 100, nullable = false)
    private String nombre;

    @Column(length = 50, nullable = false)
    private String clave;

    @Column(columnDefinition = "TEXT")
    private String descripcion;

    @Column(name = "creditos")
    private Integer creditos; 

    @Column(name = "color_hex", length = 7)
    private String colorHex = "#808080";

    private Boolean activo = true;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "horas_semana", nullable = false)
    private Integer horasSemana;

    @ManyToOne
    @JoinColumn(name = "escuela_id", nullable = false)
    private Escuela escuela;
    
    @ManyToOne
    @JoinColumn(name = "semestre_id", nullable = false)
    private Semestre semestre;
    
    @ManyToOne
    @JoinColumn(name = "turno_id", nullable = false)
    private Turno turno;
    
}