package mx.sih.pruebas;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Las reglas de acceso, comprobadas de verdad y no de memoria.
 *
 * <p>Todas estas reglas ya nos mordieron durante el despliegue: el front que
 * devolvia 401, la documentacion de la API que quedaba publica, el endpoint de
 * version que tenia que ser publico para el monitor. Ahora estan escritas.
 *
 * <p>Se usa el {@link HttpClient} del propio Java y no {@code TestRestTemplate}
 * a proposito: en Spring Boot 4 esa clase cambio de paquete (dejo de estar en
 * {@code org.springframework.boot.test.web.client}), y no merece la pena atar
 * una prueba a una utilidad que se mueve entre versiones.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SeguridadTest extends BasePruebas {

    /** Puerto aleatorio que Spring Boot publica al arrancar con RANDOM_PORT. */
    @Value("${local.server.port}")
    private int puerto;

    private final HttpClient cliente = HttpClient.newHttpClient();

    private HttpResponse<String> pedir(String ruta) throws Exception {
        HttpRequest peticion = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + puerto + ruta))
                .GET()
                .build();
        return cliente.send(peticion, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    @DisplayName("El API exige token: /api/menu sin credenciales responde 401")
    void elApiExigeToken() throws Exception {
        assertThat(pedir("/api/menu").statusCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("/api/version es publico: lo consulta el monitor sin autenticarse")
    void versionEsPublica() throws Exception {
        HttpResponse<String> respuesta = pedir("/api/version");

        assertThat(respuesta.statusCode()).isEqualTo(200);
        assertThat(respuesta.body())
                .as("el monitor comprueba este campo para saber si el servicio esta vivo")
                .contains("\"estado\":\"ok\"");
    }

    @Test
    @DisplayName("La documentacion de la API esta CERRADA (no esta bajo /api/**, quedaria publica)")
    void laDocumentacionEstaCerrada() throws Exception {
        assertThat(pedir("/scalar").statusCode())
                .as("/scalar debe exigir autenticacion")
                .isEqualTo(401);
        assertThat(pedir("/v3/api-docs").statusCode())
                .as("/v3/api-docs debe exigir autenticacion")
                .isEqualTo(401);
    }

    @Test
    @DisplayName("El shell del front es publico: sin esto el navegador recibe 401 al abrir la aplicacion")
    void elShellDelFrontEsPublico() throws Exception {
        assertThat(pedir("/").statusCode()).isEqualTo(200);
    }
}
