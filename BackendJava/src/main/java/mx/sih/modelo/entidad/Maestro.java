package mx.sih.modelo.entidad;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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

    /**
     * Apodo o sobrenombre con el que se conoce al maestro en la escuela
     * ("El Profe", "Chava"...). Es opcional: la columna admite null, así que
     * los maestros que ya existían simplemente quedan sin apodo.
     */
    @Column(name = "apodo", length = 15)
    private String apodo;

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

    public String getNombreCompleto() {
        return unirPartes(nombre, apellidos);
    }

    public String getTituloNombreCompleto() {
        return unirPartes(titulo, nombre, apellidos);
    }

    public String getApellidoNombre() {
        String ape = (apellidos != null) ? apellidos.trim() : "";
        String nom = (nombre != null) ? nombre.trim() : "";
        if (ape.isEmpty()) return nom;
        if (nom.isEmpty()) return ape;
        return ape + ", " + nom;
    }

    /**
     * Une las partes no nulas y no vacías con un espacio simple.
     * Si todas las partes son null/vacías, devuelve cadena vacía.
     */
    private static String unirPartes(String... partes) {
        return Stream.of(partes)
                .filter(p -> p != null && !p.isBlank())
                .map(String::trim)
                .collect(Collectors.joining(" "));
    }
}