package mx.sih.modelo.entidad;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "aulas", schema = "sih")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Aula {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)  // ← BIGSERIAL = IDENTITY
    @Column(name = "aula_id")
    private Long aulaId;

    @ManyToOne
    @JoinColumn(name = "escuela_id", nullable = false)
    private Escuela escuela;

    @Column(length = 50, nullable = false)
    private String nombre;

    @Column(length = 50)
    private String edificio;

    @Column(length = 50)
    private String piso;

    @Column(columnDefinition = "TEXT")
    private String descripcion;

    private Boolean activo = true;

    /** true = taller (aula de practica: es la que deben usar las materias de taller). */
    @Column(name = "taller")
    private Boolean taller = false;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
    
    @ManyToOne
    @JoinColumn(name = "semestre_id", nullable = false)
    private Semestre semestre;
    
    @ManyToOne
    @JoinColumn(name = "turno_id", nullable = false)
    private Turno turno;
}