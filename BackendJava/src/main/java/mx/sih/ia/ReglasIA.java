package mx.sih.ia;

import java.util.Arrays;
import java.util.regex.Pattern;

/**
 * REGLAS DEL GENERADOR IA — una sola fuente de verdad.
 *
 * <p>Este fichero es la UNICA fuente de verdad de las reglas del motor: el solver de Timefold se
 * retiro, asi que ya no hay dos motores que mantener en sincronia. Cada peso documenta de donde sale
 * (muchos vienen del modelo de constraints que usaba el solver, y su valor se conservo a proposito
 * para que las puntuaciones sigan siendo comparables con las corridas anteriores).
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
 *   <li><b>Una SESIÓN por día y materia</b>: dentro de un grupo, dos <em>sesiones</em> de la misma
 *       materia no pueden caer el mismo día (una sesión por cada día del patrón). La regla se mide
 *       por SESIONES, nunca por horas, así que una sesión de 2 h —que ocupa dos bloques del
 *       <em>mismo</em> día— no la viola: es justo lo que pide un patrón "1,1,1,1,2". Las sesiones se
 *       crean ya partidas con {@link #parsearDistribucion}, así que esta regla más el reparto de
 *       sesiones es lo que deja el horario con la forma pedida.</li>
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
 *   PESO_DISTRIBUCION= 3   por hora de desvío del patrón       (Respetar distribución)
 * </pre>
 *
 * <p><b>Por qué 6 manda sobre todo</b>: colocar una hora vale 6 y ningún castigo de forma puede
 * superar ese valor por unidad, así que el motor nunca sacrifica cobertura por estética. Es la misma
 * lección que se midió en el solver (con el arranque en 6 el solver cambiaba cobertura por forma).
 * La regla de distribución cae en el mismo saco: con 3, dejar una hora sin colocar (6) siempre sale
 * más caro que desviarse del patrón, que es como debe ser.
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

    /**
     * Por cada unidad de desvío respecto al patrón de distribución de una asignación (una hora que
     * habría que cambiar de día para cumplir el patrón). Sale del constraint {@code Respetar
     * distribución} del solver ({@code PESO_DISTRIBUCION = 3} allí).
     *
     * <h2>Por qué 3</h2>
     * Es el orden de prioridades acordado con el plantel:
     * <pre>
     *   colocar todas las horas (6) &gt; arranque/huecos (4) &gt; distribución (3) ≥ adyacencia (3)
     * </pre>
     * <ul>
     *   <li><b>&lt; 6 (hora sin colocar)</b>: nunca compensa dejar una hora pendiente con tal de
     *       cuadrar el patrón. Ese margen es el que impide que la búsqueda "arregle" el reparto
     *       dejando horas fuera.</li>
     *   <li><b>= 3 (adyacencia) y por debajo de la forma</b>: es un desempate: decide entre
     *       colocaciones que cuestan lo mismo, pero no compra cobertura ni huecos con ella.</li>
     *   <li><b>El mismo valor que tenia el solver</b> (3), conservado al retirarlo para que las
     *       puntuaciones de las corridas anteriores sigan siendo comparables.</li>
     * </ul>
     * El desvío se acota solo: una materia con "1,1,1,1,1" y las cinco horas el mismo día desvía 8
     * (24 puntos), y a partir de ahí sale más barato seguir con otras materias que desarmar medias
     * jornadas.
     */
    public static final int PESO_DISTRIBUCION = 3;

    // ───────── reglas duras: tolerancias ─────────
    /** Hueco máximo entre dos bloques para considerarlos contiguos (igual que el solver). */
    public static final long TOLERANCIA_CONTIGUIDAD_MIN = 50;
    /** Gap máximo entre dos clases para considerarlas "pegadas" (regla de adyacencia). */
    public static final long TOLERANCIA_ADYACENCIA_MIN = 5;
    /** Duración nominal de un bloque de clase, en minutos. */
    public static final long DURACION_BLOQUE_MINUTOS = 50;

    // ───────── patrón de distribución: dato sucio y medida del desvío ─────────

    /**
     * Formato válido de {@code asignacion.distribucion}: números separados por comas ("2,1,1").
     *
     * <p>Es EXACTAMENTE el mismo criterio (y el mismo motivo) que en el solver: en la base puede
     * haber datos antiguos mal formateados y sin esta comprobación {@code Integer::parseInt} reventaba
     * dentro del motor. Una distribución nula, vacía o con formato inválido es "dato sucio": no se
     * exige nada por ella (ver {@link #desvioDistribucion}).
     */
    private static final Pattern DISTRIBUCION_VALIDA =
            Pattern.compile("^\\s*\\d+(\\s*,\\s*\\d+)*\\s*$");

    /** true si el texto es una lista de números separados por comas (criterio del solver). */
    public static boolean distribucionValida(String distribucion) {
        return distribucion != null && !distribucion.isBlank()
                && DISTRIBUCION_VALIDA.matcher(distribucion.trim()).matches();
    }

    /**
     * Parsea la distribución de forma defensiva: ante un valor inesperado devuelve un array vacío en
     * lugar de lanzar NumberFormatException (que abortaría la búsqueda).
     */
    public static int[] parsearDistribucion(String distribucion) {
        if (!distribucionValida(distribucion)) {
            return new int[0];
        }
        try {
            return Arrays.stream(distribucion.split(","))
                    .map(String::trim)
                    .mapToInt(Integer::parseInt)
                    .toArray();
        } catch (NumberFormatException e) {
            return new int[0];
        }
    }

    /**
     * Cuántas horas habría que cambiar de día para acercarse al patrón, contando SOLO las horas ya
     * colocadas (0 = el reparto de lo colocado ya es el correcto).
     *
     * <h2>El patrón no tiene orden y se recorta a las horas colocadas</h2>
     * El patrón es un CONJUNTO de tamaños de sesión ("1,1,1,1,2" = cuatro sesiones de 1 h y una de
     * 2 h); dice cuántas horas van cada día, no en qué día. Por eso lo que se compara es el
     * <em>multiset ordenado</em> de horas por día que hay colocado contra el multiset ordenado del
     * patrón: mientras los dos multiset coincidan, el reparto es el pedido, caiga cada sesión en el
     * día que caiga.
     *
     * <p>Comparar siempre contra el patrón COMPLETO crea un acantilado: una materia de 9 horas con
     * patrón "3,3,3" que coloca su PRIMERA hora desviaría 8 unidades de golpe (2 + 3 + 3), o sea 24
     * puntos, mientras que dejarla sin ninguna hora sólo cuesta 6 por "Horas sin asignar": saldría más
     * barato NO EMPEZAR a colocar esa materia. Por eso el objetivo se construye rellenando el patrón
     * (de la sesión más larga a la más corta) hasta las horas ya colocadas: con "3,3,3" y 1 hora el
     * objetivo es [1]; con 4 horas, [3,1]. Así empezar a colocar no cuesta nada, concentrar horas sí,
     * y cuando la materia está completa el objetivo es el patrón entero, que sólo vale 0 si el reparto
     * es exactamente el pedido.
     *
     * <p>El motor cuenta HORAS por día (cada sesión conoce su duración) y como la regla dura sólo
     * admite una sesión por materia y día, "horas por día" es exactamente el tamaño de la sesión de
     * ese día: es la lectura literal del patrón.
     *
     * @param distribucion patrón de la asignación (se ignora si es dato sucio)
     * @param horasPorDia  horas colocadas en cada día con clase (una entrada por día, sin orden)
     * @return unidades de desvío; 0 si no hay patrón válido o no hay ninguna hora colocada
     */
    public static int desvioDistribucion(String distribucion, int[] horasPorDia) {
        return desvioDistribucion(parsearDistribucion(distribucion), horasPorDia);
    }

    /**
     * Igual que {@link #desvioDistribucion(String, int[])} pero con el patrón ya parseado: es la
     * variante que usa el motor en sus bucles internos, donde el patrón de cada asignación se cachea
     * una sola vez (parsear el texto en cada paso sería tirar el presupuesto de tiempo).
     *
     * @param patron       patrón ya parseado (vacío = dato sucio, no se exige nada)
     * @param horasPorDia  horas colocadas en cada día con clase (una entrada por día, sin orden)
     */
    public static int desvioDistribucion(int[] patron, int[] horasPorDia) {
        if (patron == null || patron.length == 0 || horasPorDia == null || horasPorDia.length == 0) {
            return 0;
        }
        int[] real = Arrays.copyOf(horasPorDia, horasPorDia.length);
        Arrays.sort(real);

        int colocadas = 0;
        for (int r : real) {
            colocadas += r;
        }
        int[] objetivo = recortarPatron(patron, colocadas);

        int n = Math.max(real.length, objetivo.length);
        int desvio = 0;
        for (int i = 0; i < n; i++) {
            int r = i < real.length ? real[i] : 0;
            int e = i < objetivo.length ? objetivo[i] : 0;
            desvio += Math.abs(r - e);
        }
        return desvio;
    }

    /**
     * Las primeras {@code horas} horas del patrón, tomando antes las sesiones más largas, y devueltas
     * ordenadas de menor a mayor (igual que la lista real, para poder compararlas posición a
     * posición).
     */
    public static int[] recortarPatron(int[] patron, int horas) {
        int[] ordenado = Arrays.copyOf(patron, patron.length);
        Arrays.sort(ordenado);

        int[] trozos = new int[ordenado.length];
        int cuantos = 0;
        int restantes = horas;
        for (int i = ordenado.length - 1; i >= 0 && restantes > 0; i--) {
            int trozo = Math.min(ordenado[i], restantes);
            trozos[cuantos++] = trozo;
            restantes -= trozo;
        }

        int[] objetivo = Arrays.copyOf(trozos, cuantos);
        Arrays.sort(objetivo);
        return objetivo;
    }

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
     * @param desvioDistribucion suma de {@link #desvioDistribucion} de todas las asignaciones
     *                         (0 = todas las que tienen patrón lo cumplen)
     */
    public static int medium(int horasColocadas, int horasDemandadas, int sesionesLargasPendientes,
                             int arranquesTarde, int castigoHuecos, int adyacencias,
                             int desvioDistribucion) {
        return -(PESO_HORA * (horasDemandadas - horasColocadas)
                + PESO_SESION_LARGA * sesionesLargasPendientes
                + PESO_ARRANQUE * arranquesTarde
                + castigoHuecos
                + PESO_ADYACENCIA * adyacencias
                + PESO_DISTRIBUCION * desvioDistribucion);
    }
}
