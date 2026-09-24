package mx.sih.ia;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import mx.sih.modelo.entidad.Asignacion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * ASESOR LLM (opcional). Si hay {@code app.ia.api-key} configurada, se le pide al modelo un ORDEN de
 * colocación de materias (de la más difícil a la más fácil). El motor IA usa ese orden como
 * preferencia del fail-first, pero sigue comprobando todas las reglas duras por su cuenta: el LLM no
 * puede romper nada, solo aconsejar.
 *
 * <p>Se habla con una API compatible con OpenAI ({@code /chat/completions}) para no atarse a un
 * proveedor: sirve OpenAI, Azure OpenAI, Groq, Together, Ollama o cualquier proxy que respete ese
 * formato. Todo es configurable por propiedades:
 *
 * <pre>
 *   app.ia.api-key=            (vacío = sin LLM; se usa la heurística)
 *   app.ia.url=https://api.openai.com/v1/chat/completions
 *   app.ia.modelo=gpt-4o-mini
 *   app.ia.tiempo-limite-segundos=45
 * </pre>
 *
 * <p>Ante cualquier problema (timeout, HTTP de error, respuesta que no se entiende) se registra el
 * motivo en {@link #nota()} y se devuelve una lista vacía: la generación continúa con la heurística.
 */
public class AsesorLLM implements AsesorIA {

    private static final Logger logger = LoggerFactory.getLogger(AsesorLLM.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final String apiKey;
    private final String url;
    private final String modelo;
    private final long tiempoLimiteSegundos;

    // volatile porque en modo LLM este asesor lo COMPARTEN los intentos que corren en paralelo.
    // Solo alimentan a nota(), que es texto para la bitácora: la decisión del LLM se devuelve por el
    // 'return' de ordenSugerido(), así que esta carrera no puede alterar el horario. Con volatile,
    // la nota que se registra al terminar un intento es al menos una nota completa y reciente,
    // aunque puede describir a un intento hermano.
    private volatile String nota = "Asesor LLM listo.";
    private volatile String ultimoError;

    public AsesorLLM(String apiKey, String url, String modelo, long tiempoLimiteSegundos) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.url = url == null || url.isBlank() ? "https://api.openai.com/v1/chat/completions" : url.trim();
        this.modelo = modelo == null || modelo.isBlank() ? "gpt-4o-mini" : modelo.trim();
        this.tiempoLimiteSegundos = tiempoLimiteSegundos <= 0 ? 45 : tiempoLimiteSegundos;
    }

    /** true si hay credencial configurada (si no, el servicio ni lo instancia). */
    public static boolean configurado(String apiKey) {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public String nombre() {
        return "llm:" + modelo;
    }

    @Override
    public String nota() {
        return ultimoError == null ? nota : nota + " Último error: " + ultimoError;
    }

    @Override
    public List<Long> ordenSugerido(List<Asignacion> asignaciones, Map<Long, Integer> ventanasPorAsignacion) {
        if (asignaciones == null || asignaciones.isEmpty()) {
            return List.of();
        }
        try {
            String prompt = construirPrompt(asignaciones, ventanasPorAsignacion);
            String cuerpo = JSON.writeValueAsString(Map.of(
                    "model", modelo,
                    "temperature", 0,
                    "messages", List.of(
                            Map.of("role", "system", "content",
                                    "Eres un experto en horarios escolares. Te doy las asignaturas de un turno con "
                                            + "su numero de ventanas legales (donde grupo y maestro pueden coincidir), "
                                            + "horas y patron de distribucion. Tu orden sera EL orden en que se arma el "
                                            + "horario, asi que devuelve SOLO un array JSON con TODOS los ids, cada uno "
                                            + "exactamente una vez, ordenados de la MAS dificil de colocar a la mas "
                                            + "facil. Sin texto adicional."),
                            Map.of("role", "user", "content", prompt))));

            HttpClient cliente = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();
            HttpRequest peticion = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(tiempoLimiteSegundos))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(cuerpo))
                    .build();

            HttpResponse<String> respuesta = cliente.send(peticion, HttpResponse.BodyHandlers.ofString());
            if (respuesta.statusCode() / 100 != 2) {
                ultimoError = "HTTP " + respuesta.statusCode();
                logger.warn("Asesor LLM: {} · {}", ultimoError, recortar(respuesta.body()));
                return List.of();
            }

            JsonNode raiz = JSON.readTree(respuesta.body());
            String contenido = raiz.path("choices").path(0).path("message").path("content").asText("");
            List<Long> ids = extraerIds(contenido);
            if (ids.isEmpty()) {
                ultimoError = "respuesta sin ids utilizables";
                logger.warn("Asesor LLM: {} · {}", ultimoError, recortar(contenido));
                return List.of();
            }
            nota = "El LLM (" + modelo + ") sugirió el orden de " + ids.size() + " materias.";
            ultimoError = null;
            logger.info("Asesor LLM: orden sugerido para {} asignaciones", ids.size());
            return ids;

        } catch (Exception e) {
            ultimoError = e.getClass().getSimpleName() + ": " + e.getMessage();
            logger.warn("Asesor LLM no disponible: {}", ultimoError);
            return List.of();
        }
    }

    private String construirPrompt(List<Asignacion> asignaciones, Map<Long, Integer> ventanas) {
        StringBuilder sb = new StringBuilder("Asignaturas (id | materia | grupo | horas | patron | ventanasLegales):\n");
        for (Asignacion a : asignaciones) {
            sb.append(a.getAsignacionId()).append(" | ")
                    .append(a.getMateria() != null ? a.getMateria().getClave() : "?")
                    .append(" | ").append(a.getGrupo() != null ? a.getGrupo().getNombre() : "?")
                    .append(" | ").append(a.getHoras() == null ? 0 : a.getHoras())
                    .append(" | ").append(a.getDistribucion() == null ? "-" : a.getDistribucion())
                    .append(" | ").append(ventanas.getOrDefault(a.getAsignacionId(), 0))
                    .append('\n');
        }
        return sb.toString();
    }

    /** Acepta {@code [1,2,3]}, {@code {"orden":[1,2,3]}} o ids sueltos dentro de texto. */
    private List<Long> extraerIds(String contenido) {
        List<Long> ids = new ArrayList<>();
        if (contenido == null || contenido.isBlank()) {
            return ids;
        }
        String texto = contenido.trim();
        int inicio = texto.indexOf('[');
        int fin = texto.lastIndexOf(']');
        if (inicio >= 0 && fin > inicio) {
            texto = texto.substring(inicio + 1, fin);
        }
        for (String trozo : texto.split("[,\\s]+")) {
            String limpio = trozo.replaceAll("[^0-9]", "");
            if (!limpio.isEmpty()) {
                try {
                    ids.add(Long.parseLong(limpio));
                } catch (NumberFormatException ignorado) {
                    // trozo no numérico: se ignora
                }
            }
        }
        return ids;
    }

    private String recortar(String s) {
        if (s == null) {
            return "";
        }
        return s.length() <= 300 ? s : s.substring(0, 300) + "…";
    }
}
