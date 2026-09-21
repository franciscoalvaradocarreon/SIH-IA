// mx.sih.modelo.dto.AnalisisCuelloBotellaDTO.java
package mx.sih.modelo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AnalisisCuelloBotellaDTO {

    private Long semestreId;
    private String semestreNombre;
    private Long turnoId;
    private String turnoNombre;

    // Resumen global
    private Integer totalBloques;
    private Integer totalGrupos;
    private Integer totalParesGrupoBloque;      // grupos × bloques evaluados
    private Integer paresCriticos;              // 0-1 maestros disponibles
    private Integer paresAdvertencia;           // exactamente 2 maestros
    private Integer paresOk;                    // 3+ maestros

    // Detalle ordenado: primero los más críticos
    private List<ParGrupoBloqueDTO> pares = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ParGrupoBloqueDTO {
        private Long grupoId;
        private String grupoNombre;
        private Integer grupoGrado;
        private String grupoTurno;

        private Long turnoHorarioId;
        private Integer diaSemana;
        private String diaNombre;
        private String horaInicio;
        private String horaFin;

        /** Cuántos maestros del grupo están disponibles en este bloque. */
        private Integer maestrosDisponibles;

        /** Cuántas asignaciones tiene el grupo en total (contexto). */
        private Integer totalAsignacionesGrupo;

        /** Nombres de los maestros disponibles (ayuda a la acción correctiva). */
        private List<String> maestrosNombres = new ArrayList<>();

        /** "CRITICO" (0-1) | "ADVERTENCIA" (2) | "OK" (3+) */
        private String severidad;
    }
}