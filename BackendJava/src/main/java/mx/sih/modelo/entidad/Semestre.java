package mx.sih.modelo.entidad;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "semestre", schema = "sih")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Semestre {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "semestre_id")
    private Long semestreId;

    @ManyToOne
    @JoinColumn(name = "escuela_id", nullable = false)
    private Escuela escuela;

    @Column(name = "nombre", length = 100, nullable = false)
    private String nombre;

    @Column(name = "descripcion", columnDefinition = "TEXT")
    private String descripcion;

    @Column(name = "activo")
    private Boolean activo = true;

    @Column(name = "creado", updatable = false)
    private LocalDateTime creado;

    @PrePersist
    protected void onCreate() {
        creado = LocalDateTime.now();
    }
}