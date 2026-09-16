package mx.sih.modelo.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EscuelaDTO {
    private Long id;
    private String nombre;
    private String nombreLargo;
    private String direccion;
    private String telefono;
    private String logoUrl;
    private String clave;
    private Boolean activo;
}