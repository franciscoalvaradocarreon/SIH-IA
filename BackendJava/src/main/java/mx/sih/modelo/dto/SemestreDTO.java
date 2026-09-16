package mx.sih.modelo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SemestreDTO {

    private Long semestreId;
    private Long escuelaId;
    private String escuelaNombre;
    private String nombre;
    private String descripcion;
    private Boolean activo;
    private LocalDateTime creado;
}