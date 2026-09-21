package mx.sih.modelo.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/** Resultado de aplicar (o validar) una tanda de cambios manuales del tablero. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ResultadoManualDTO {

    /** true solo si los cambios se escribieron en la base. */
    private Boolean aplicado;

    /** true si la petición era de solo validación. */
    private Boolean soloValidacion;

    private Integer colocados;
    private Integer movidos;
    private Integer quitados;

    /** Mensaje de resumen para mostrar al usuario. */
    private String mensaje;

    /**
     * Problemas encontrados, numerados según la posición del cambio en la tanda
     * ("Cambio 3: el grupo 1°A ya tiene clase en lunes 14:00 (MATE)"). Si hay alguno,
     * no se aplicó nada.
     */
    private List<String> errores = new ArrayList<>();
}
