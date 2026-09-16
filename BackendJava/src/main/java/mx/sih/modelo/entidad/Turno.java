package mx.sih.modelo.entidad;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "turno", schema = "sih")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Turno {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "turno_id")
    private Long turnoId;

    @ManyToOne
    @JoinColumn(name = "escuela_id", nullable = false)
    private Escuela escuela;

    @Column(name = "nombre", length = 40, nullable = false)
    private String nombre;

    @Column(name = "description")
    private String descripcion;
    
    @Column(name = "activo")
    private Boolean activo = true;
    
    @ManyToOne
    @JoinColumn(name = "semestre_id")
    private Semestre semestre;

}