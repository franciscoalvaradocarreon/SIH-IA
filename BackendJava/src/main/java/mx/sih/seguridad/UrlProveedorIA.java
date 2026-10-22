package mx.sih.seguridad;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import mx.sih.excepcion.NegocioExcepcion;

/**
 * LISTA BLANCA DE DESTINOS DEL ASESOR IA.
 *
 * <p>Nace de la validacion de ataques: la pantalla de Horario IA deja escribir la URL del proveedor
 * LLM (tiene presets de OpenAI y DeepSeek, pero el campo es libre). Esa URL llega hasta el cliente
 * HTTP del servidor, asi que sin control un usuario con permiso puede hacer que <b>el servidor</b>
 * abra una conexion a donde quiera:
 *
 * <ul>
 *   <li>a servicios internos que no estan expuestos a Internet;</li>
 *   <li>al servicio de metadatos de la instancia de Oracle (169.254.169.254), que entrega
 *       credenciales de la propia maquina;</li>
 *   <li>a cualquier servidor suyo, para provocar la negociacion TLS que ataca el fallo del JSSE
 *       descrito en CVE-2026-91403.</li>
 * </ul>
 *
 * <p>Que se exige a la direccion:
 * <ul>
 *   <li><b>https</b> obligatorio: nada de http, file, ftp ni otros esquemas;</li>
 *   <li>el <b>host exacto</b> tiene que estar en la lista, sin comodines ni subdominios
 *       ({@code api.openai.com.evil.com} no vale);</li>
 *   <li>sin credenciales embebidas: {@code https://api.openai.com@evil.com} se rechaza porque el
 *       host real que ve el {@link URI} es {@code evil.com};</li>
 *   <li>puerto 443 o el implicito; cualquier otro se rechaza.</li>
 * </ul>
 *
 * <p>Una direccion vacia o nula es valida y significa "usa la de la configuracion"
 * ({@code app.ia.url}), que es la que se escribe en {@code .env}.
 *
 * <p>No pretende ser un filtro de salvaguarda perfecto contra SSRF: es una lista de destinos
 * conocidos. Lo que impide es que el destino lo elija el cliente.
 */
public final class UrlProveedorIA {

    /** Destinos que se admiten si no se configura otra cosa. Son los presets de la pantalla. */
    public static final String HOSTS_POR_DEFECTO = "api.openai.com,api.deepseek.com";

    private UrlProveedorIA() {
        // Clase de utilidades: no se instancia.
    }

    /**
     * Devuelve la direccion que se debe usar, validada.
     *
     * @param solicitada  lo que manda la peticion; puede ser nula o vacia
     * @param configurada la de {@code app.ia.url}; se usa cuando no se pide otra
     * @param permitidas  lista separada por comas ({@code app.ia.url-permitidas})
     * @return la direccion validada (la configurada si no se pidio ninguna)
     * @throws NegocioExcepcion con codigo {@code url_no_permitida} si no cumple
     */
    public static String destino(String solicitada, String configurada, String permitidas) {
        String config = configurada == null ? "" : configurada.trim();
        if (solicitada == null || solicitada.isBlank()) {
            return config;
        }

        URI uri;
        try {
            uri = new URI(solicitada.trim());
        } catch (URISyntaxException e) {
            throw rechazo(permitidas, "no es una dirección válida");
        }

        String esquema = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!"https".equals(esquema)) {
            throw rechazo(permitidas, "solo se admite https");
        }

        // OJO con el orden: primero el userinfo. 'https://api.openai.com@evil.com' tiene como host
        // real evil.com, pero conviene decirlo con un mensaje propio en vez de "host no permitido".
        if (uri.getUserInfo() != null) {
            throw rechazo(permitidas, "no se admiten credenciales dentro de la dirección");
        }

        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (!hosts(permitidas).contains(host)) {
            throw rechazo(permitidas, "el destino no está en la lista");
        }

        String puerto = uri.getPort() == -1 ? "" : String.valueOf(uri.getPort());
        if (!puerto.isEmpty() && !"443".equals(puerto)) {
            throw rechazo(permitidas, "solo el puerto 443");
        }

        return uri.toString();
    }

    /** Hosts admitidos, en minusculas. Si la lista llega vacia se usan los de por defecto. */
    public static Set<String> hosts(String permitidas) {
        String csv = permitidas == null || permitidas.isBlank() ? HOSTS_POR_DEFECTO : permitidas;
        return Arrays.stream(csv.split(","))
                .map(h -> h.trim().toLowerCase(Locale.ROOT))
                .filter(h -> !h.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    private static NegocioExcepcion rechazo(String permitidas, String motivo) {
        return new NegocioExcepcion("url_no_permitida",
                "La dirección del asesor IA no se admite (" + motivo + "). "
                        + "Solo se permiten " + String.join(", ", hosts(permitidas)) + " por https, "
                        + "o dejar el campo vacío para usar el proveedor configurado.");
    }
}
