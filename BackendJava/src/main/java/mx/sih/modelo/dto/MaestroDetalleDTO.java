package mx.sih.modelo.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MaestroDetalleDTO {
    private Long id;
    private String nombre;
    private String apellidos;
    private String email;
    private String telefono;
    private String fotoUrl;
    private String titulo;
    private Boolean activo;
    private Long semestreId;
    private Long turnoId;
    private String turnoNombre;
}