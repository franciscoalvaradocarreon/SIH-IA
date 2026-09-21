package mx.sih.modelo.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AsignacionCrearDTO {

    @NotNull(message = "El grupo es obligatorio")
    private Long grupoId;

    @NotNull(message = "La materia es obligatoria")
    private Long materiaId;

    @NotNull(message = "El maestro es obligatorio")
    private Long maestroId;

    @NotNull(message = "El aula es obligatoria")  // 🔥 NUEVO
    private Long aulaId;

    @NotNull(message = "Las horas son obligatorias")
    @Min(value = 1, message = "Las horas deben ser al menos 1")
    @Max(value = 35, message = "Las horas no pueden exceder 35")
    private Integer horas;

    private String colorHex = "#808080";

    /**
     * Distribución de las horas por día, p. ej. "2,1,1" (dos horas un día y una en
     * otros dos). El FORMATO se valida aquí porque el solver lo parsea con
     * Integer::parseInt dentro del motor de constraints: un valor como "2,,3"
     * lanzaba NumberFormatException en plena búsqueda local y abortaba la
     * generación del horario completo.
     */
    @Pattern(regexp = "^$|^\\s*\\d+(\\s*,\\s*\\d+)*\\s*$",
             message = "La distribución debe ser números separados por comas (ej. 2,1,1)")
    private String distribucion;
    
    private Boolean activo = true;
    
    @NotNull(message = "El semestre es obligatorio")
    private Long semestreId;
    
    @NotNull(message = "El turno es obligatorio")
    private Long turnoId;
}