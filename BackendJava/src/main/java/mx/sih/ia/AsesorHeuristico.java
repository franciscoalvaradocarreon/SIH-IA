package mx.sih.ia;

import mx.sih.modelo.entidad.Asignacion;

import java.util.List;
import java.util.Map;

/**
 * Asesor por defecto: no sugiere nada. El motor IA usa su propio fail-first (menos ventanas legales
 * primero y, a igualdad, las sesiones largas). Es el modo que funciona sin configurar nada.
 */
public class AsesorHeuristico implements AsesorIA {

    @Override
    public String nombre() {
        return "heuristica";
    }

    @Override
    public List<Long> ordenSugerido(List<Asignacion> asignaciones, Map<Long, Integer> ventanasPorAsignacion) {
        return List.of();
    }

    @Override
    public String nota() {
        return "Sin asesor LLM (no hay app.ia.api-key): el motor usa su fail-first propio.";
    }
}
