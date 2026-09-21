package mx.sih.ia;

/**
 * REGLAS DEL GENERADOR IA — una sola fuente de verdad.
 *
 * <p>Este fichero existe para que las reglas del motor de IA no se separen de las que ya están
 * definidas en el solver de Timefold ({@code mx.sih.modelo.solver.HorarioConstraintProvider}). Cada
 * constante documenta de qué constraint del provider sale y con qué valor, así que si allí cambia un
 * peso, aquí se cambia igual y los dos motores siguen siendo comparables.
 *
 * <h2>Reglas DURAS (nunca se puede violar)</h2>
 * <ol>
 *   <li><b>Sin choques</b>: un grupo, un maestro o un aula no pueden tener dos clases en el mismo
 *       bloque ({@code Conflicto de grupo/aula/maestro}).</li>
 *   <li><b>Maestro no disponible</b>: la clase tiene que caer en un bloque con fila
 *       {@code disponible = true} de {@code disponibilidad_maestro}.</li>
 *   <li><b>Grupo no disponible</b>: ídem con {@code disponibilidad_grupo}.</li>
 *   <li><b>Bloque del turno del grupo</b>: el bloque tiene que pertenecer al turno del grupo.</li>
 *   <li><b>Sesión entera</b>: una sesión de 2 horas ocupa 2 bloques <em>contiguos</em> (hueco máximo
 *       {@link #TOLERANCIA_CONTIGUIDAD_MIN} minutos, para permitir un descanso en medio).</li>
 *   <li><b>Misma materia en días distintos</b>: dos sesiones de una materia no pueden caer el mismo
 *       día. Es lo que garantiza que el reparto por días sea el del patrón.</li>
 * </ol>
 *
 * <h2>Reglas BLANDAS (pesos, menor es mejor)</h2>
 * <pre>
 *   PESO_HORA        = 6   por cada hora sin colocar          (Horas sin asignar, 6 x duración)
 *   PESO_SESION_LARGA= 3   por sesión de 2+ h sin colocar      (Sesión larga sin colocar)
 *   PESO_ARRANQUE    = 4   por grupo-día que no arranca a 1ª   (Iniciar a primera hora, plano)
 *   PESO_HUECO       = 4   por bloque libre con clase después  (Evitar huecos)
 *   TOPE_HUECOS_DIA  = 5   tope del castigo de huecos por día  (Evitar huecos, tope)
 *   PESO_ADYACENCIA  = 3   por par pegado del mismo maestro+grupo y materias distintas
 *                                                              (Sin clases continuas..., blanda)
 * </pre>
 *
 * <p><b>Por qué 6 manda sobre todo</b>: colocar una hora vale 6 y ningún castigo de forma puede
 * superar ese valor por unidad, así que el motor nunca sacrifica cobertura por estética. Es la misma
 * lección que se midió en el solver (con el arranque en 6 el solver cambiaba cobertura por forma).
 */
public final class ReglasIA {

    private ReglasIA() {
        // Constantes: no instanciar.
    }

    // ───────── reglas blandas: pesos ─────────
    /** Por cada hora de clase sin colocar. */
    public static final int PESO_HORA = 6;
    /** Por cada sesión de 2 o más horas que queda sin colocar. */
    public static final int PESO_SESION_LARGA = 3;
    /** Por cada grupo-día con clases que no arranca en el bloque de apertura. */
    public static final int PESO_ARRANQUE = 4;
    /** Por cada bloque libre que tiene clase después (hueco o arranque tardío). */
    public static final int PESO_HUECO = 4;
    /** Tope del castigo por huecos de un mismo grupo-día. */
    public static final int TOPE_HUECOS_DIA = 5;
    /** Por cada par de bloques pegados del mismo maestro y grupo con materias distintas. */
    public static final int PESO_ADYACENCIA = 3;

    // ───────── reglas duras: tolerancias ─────────
    /** Hueco máximo entre dos bloques para considerarlos contiguos (igual que el solver). */
    public static final long TOLERANCIA_CONTIGUIDAD_MIN = 50;
    /** Gap máximo entre dos clases para considerarlas "pegadas" (regla de adyacencia). */
    public static final long TOLERANCIA_ADYACENCIA_MIN = 5;
    /** Duración nominal de un bloque de clase, en minutos. */
    public static final long DURACION_BLOQUE_MINUTOS = 50;

    /**
     * Score MEDIUM que el motor IA intenta minimizar. Devuelve el mismo número que daría el solver
     * de Timefold para ese horario (signo negativo incluido), para poder comparar los dos motores.
     *
     * @param horasColocadas   horas de clase efectivamente colocadas
     * @param horasDemandadas  horas totales pedidas por las asignaciones
     * @param sesionesLargasPendientes sesiones de 2+ h sin colocar
     * @param arranquesTarde   grupo-días que no arrancan en la apertura
     * @param castigoHuecos    suma del castigo de huecos (ya con el tope por día)
     * @param adyacencias      pares pegados del mismo maestro y grupo
     */
    public static int medium(int horasColocadas, int horasDemandadas, int sesionesLargasPendientes,
                             int arranquesTarde, int castigoHuecos, int adyacencias) {
        return -(PESO_HORA * (horasDemandadas - horasColocadas)
                + PESO_SESION_LARGA * sesionesLargasPendientes
                + PESO_ARRANQUE * arranquesTarde
                + castigoHuecos
                + PESO_ADYACENCIA * adyacencias);
    }
}
