package mx.sih.modelo.dto;

import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Estadisticas de un horario ya guardado (el que muestra el tablero manual).
 *
 * <p>Los numeros de "forma" (huecos, arranques, adyacencias, desvio y medium) usan las MISMAS
 * formulas y pesos que el motor IA, asi que se pueden comparar de tu a tu con un intento de la
 * pantalla de Horario IA: sirven para saber si una edicion manual mejoro o empeoro lo que habia.
 *
 * <p>Las reglas DURAS van aparte y todas deben valer 0: si alguna sube, el horario tiene un
 * problema real (choques o la misma materia dos veces el mismo dia).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EstadisticasHorarioDTO {

    private Long turnoId;
    private String turnoNombre;
    private Long semestreId;
    private String semestreNombre;

    /** Grupos del turno que tienen clases colocadas. */
    private int grupos;

    // ── Cobertura ──
    private int horasColocadas;
    private int horasDemandadas;
    private int coberturaPorcentaje;
    private int materiasCompletas;
    private int materiasTotales;
    private int horasPendientes;

    // ── Forma (mismas formulas y pesos que el motor IA) ──
    /** Bloques libres con clase despues, sumados por grupo y dia. */
    private int huecos;
    /** Castigo de huecos que entra en el medium: min(4 x huecos, 5) por grupo-dia. */
    private int castigoHuecos;
    /** Grupo-dias que no arrancan en el primer bloque de clase del turno. */
    private int arranquesTarde;
    /** Pares pegados (<= 5 min) del mismo maestro y grupo con materias distintas. */
    private int adyacencias;
    /** Suma del desvio de cada materia respecto a su patron de distribucion. */
    private int desvioDistribucion;
    /** Sesiones de 2+ h que quedaron sin colocar. */
    private int sesionesLargasPendientes;
    /** Sesiones de 2+ h colocadas. */
    private int sesionesLargas;
    /** Score MEDIUM (negativo): mas cerca de 0 = mejor. Comparable con los intentos de la IA. */
    private int medium;

    // ── Reglas duras (todo debe ser 0) ──
    private int choquesGrupo;
    private int choquesMaestro;
    private int choquesAula;
    private int materiasRepetidasDia;
    private int clasesEnDescanso;
    private int excedeHoras;
    /** Suma de las seis reglas duras de arriba. */
    private int problemas;

    /** Materias a las que les faltan horas (las que mas faltan primero). */
    private List<Pendiente> pendientes = new ArrayList<>();

    /** Frase lista para el recuadro. */
    private String mensaje;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Pendiente {
        private Long asignacionId;
        private String grupo;
        private String materia;
        private String maestro;
        private int colocadas;
        private int contratadas;
        private int faltan;
    }
}
