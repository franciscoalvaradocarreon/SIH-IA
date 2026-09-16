package mx.sih.modelo.entidad;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "maestros", schema = "sih")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Maestro {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "maestro_id")
    private Long maestroId;

    @Column(name = "nombre", length = 80, nullable = false)
    private String nombre;

    @Column(length = 100, nullable = false)
    private String apellidos;

    @Column(length = 100)
    private String email;

    @Column(length = 40)
    private String telefono;

    @Column(name = "foto_url", length = 100)
    private String fotoUrl;
    
    @Column(name = "titulo", length = 20)
    private String titulo;

    @ManyToOne
    @JoinColumn(name = "escuela_id", nullable = false)
    private Escuela escuela;

    private Boolean activo = true;

    @Column(name = "creado")
    private LocalDateTime creado = LocalDateTime.now();
    
    @ManyToOne
    @JoinColumn(name = "semestre_id", nullable = false)
    private Semestre semestre;
    
    @ManyToOne
    @JoinColumn(name = "turno_id", nullable = false)
    private Turno turno;

    // Método auxiliar para obtener nombre completo
    public String getNombreCompleto() {
        return ((nombre != null ? nombre : "")+" "+(apellidos != null ? apellidos : ""));
    }
    
    public String getTituloNombreCompleto() {
        return ((titulo != null ? titulo : "")+" "+(nombre != null ? nombre : "")+" "+(apellidos != null ? apellidos : ""));
    }

    public String getApellidoNombre() {
        return ((apellidos != null ? apellidos : "")+ ", " + (nombre != null ? nombre : ""));
    }

    
}