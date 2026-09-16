package mx.sih.modelo.entidad;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "escuelas", schema = "sih")
@Getter
@Setter
public class Escuela {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "escuela_id")
    private Long escuelaId;

    @Column(length = 150, nullable = false)
    private String nombre;

    @Column(name = "nombre_largo", length = 150)
    private String nombreLargo;

    @Column(length = 255)
    private String direccion;

    @Column(length = 20)
    private String telefono;

    private Boolean activo = true;

    @Column(name = "creado")
    private LocalDateTime creado = LocalDateTime.now();

    @Column(name = "logo_url", length = 255)
    private String logoUrl;

    @Column(length = 30)
    private String clave;
}