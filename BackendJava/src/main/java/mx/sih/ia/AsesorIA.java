package mx.sih.ia;

import mx.sih.modelo.entidad.Asignacion;

import java.util.List;
import java.util.Map;

/**
 * ASESOR DEL MOTOR IA (gancho enchufable).
 *
 * <p>El motor funciona siempre con su propia heurística. Este interfaz permite que un asesor externo
 * (por ejemplo un LLM) decida <b>el orden COMPLETO en que se intentan colocar las materias</b>: si
 * devuelve una lista con ids, ese orden es el que manda en la construcción (las materias omitidas
 * quedan al final con el criterio de siempre); si devuelve lista vacía, el motor usa su propio
 * fail-first (lo más restringido primero). Es el punto donde una IA puede aportar sin poder romper
 * nada, porque el motor sigue comprobando todas las reglas duras.
 *
 * <p>Si no hay asesor configurado, se usa {@link AsesorHeuristico} y todo sigue igual.
 */
public interface AsesorIA {

    /** Nombre corto para la interfaz: {@code heuristica} o {@code llm:modelo}. */
    String nombre();

    /**
     * Orden sugerido de asignaciones, de la más difícil de colocar a la más fácil.
     *
     * @param asignaciones        asignaciones a ordenar (todas las del turno)
     * @param ventanasPorAsignacion cuántas ventanas legales tiene cada una (dato objetivo)
     * @return lista de ids; vacía significa "sin sugerencia, usa el orden del motor"
     */
    List<Long> ordenSugerido(List<Asignacion> asignaciones, Map<Long, Integer> ventanasPorAsignacion);

    /** Explicación breve de lo que sugirió (para mostrarla en la pantalla). */
    String nota();
}
