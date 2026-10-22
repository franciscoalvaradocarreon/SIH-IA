package mx.sih.pruebas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import mx.sih.excepcion.NegocioExcepcion;
import mx.sih.seguridad.UrlProveedorIA;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * PRUEBAS DE LA LISTA BLANCA DEL ASESOR IA.
 *
 * <p>Unitarias a proposito: no extienden {@link BasePruebas} y no necesitan Docker, porque son una
 * regla pura. Cada caso de aqui es una forma real de saltarse una comprobacion ingenua de "la URL
 * empieza por https".
 */
class UrlProveedorIATest {

    private static final String CONFIGURADA = "https://api.openai.com/v1/chat/completions";

    @Test
    @DisplayName("sin URL se usa la de la configuracion")
    void sinUrlUsaLaConfigurada() {
        assertEquals(CONFIGURADA, UrlProveedorIA.destino(null, CONFIGURADA, null));
        assertEquals(CONFIGURADA, UrlProveedorIA.destino("   ", CONFIGURADA, null));
    }

    @Test
    @DisplayName("admite los proveedores de la lista, por https")
    void admiteLosDeLaLista() {
        assertEquals("https://api.openai.com/v1/chat/completions",
                UrlProveedorIA.destino("https://api.openai.com/v1/chat/completions", CONFIGURADA, null));
        assertEquals("https://api.deepseek.com/chat/completions",
                UrlProveedorIA.destino("https://api.deepseek.com/chat/completions", CONFIGURADA, null));
        // Puerto 443 explicito: tambien vale.
        assertEquals("https://api.openai.com:443/x",
                UrlProveedorIA.destino("https://api.openai.com:443/x", CONFIGURADA, null));
    }

    @Test
    @DisplayName("rechaza http y cualquier otro esquema")
    void rechazaEsquemasNoSeguros() {
        assertRechaza("http://api.openai.com/v1/chat/completions");
        assertRechaza("file:///etc/passwd");
        assertRechaza("ftp://api.openai.com/x");
        assertRechaza("gopher://api.openai.com/x");
    }

    @Test
    @DisplayName("rechaza el servicio de metadatos de la instancia (el caso SSRF clasico)")
    void rechazaMetadatosDeLaNube() {
        assertRechaza("https://169.254.169.254/opc/v2/identity/");
        assertRechaza("http://169.254.169.254/latest/meta-data/");
        assertRechaza("https://127.0.0.1:8080/api/usuarios");
        assertRechaza("https://localhost/x");
    }

    @Test
    @DisplayName("rechaza el truco de credenciales en la direccion")
    void rechazaCredencialesEmbebidas() {
        // El host real de esta direccion es evil.com, no api.openai.com.
        assertRechaza("https://api.openai.com@evil.com/v1/chat/completions");
        assertRechaza("https://usuario:clave@api.openai.com/x");
    }

    @Test
    @DisplayName("rechaza dominios que solo CONTIENEN el nombre permitido")
    void rechazaDominiosParecidos() {
        assertRechaza("https://api.openai.com.evil.com/x");
        assertRechaza("https://evilapi.openai.com/x");
        assertRechaza("https://api.openai.com.mx/x");
        assertRechaza("https://notopenai.com/x");
    }

    @Test
    @DisplayName("rechaza puertos distintos del 443")
    void rechazaOtrosPuertos() {
        assertRechaza("https://api.openai.com:8443/x");
        assertRechaza("https://api.openai.com:22/x");
        assertRechaza("https://api.openai.com:5432/x");
    }

    @Test
    @DisplayName("la lista se puede ampliar por configuracion, y sigue sin admitir el resto")
    void laListaEsConfigurable() {
        assertEquals("https://mi-proxy.interno/x",
                UrlProveedorIA.destino("https://mi-proxy.interno/x", CONFIGURADA, "mi-proxy.interno"));
        // Con esa lista, el proveedor por defecto deja de valer: la lista manda.
        assertRechaza("https://api.openai.com/x", "mi-proxy.interno");
        // Sin lista configurada valen los de por defecto.
        assertTrue(UrlProveedorIA.hosts(null).contains("api.openai.com"));
        assertTrue(UrlProveedorIA.hosts("").contains("api.deepseek.com"));
    }

    private void assertRechaza(String url) {
        assertRechaza(url, null);
    }

    private void assertRechaza(String url, String permitidas) {
        NegocioExcepcion e = assertThrows(NegocioExcepcion.class,
                () -> UrlProveedorIA.destino(url, CONFIGURADA, permitidas),
                "deberia rechazar: " + url);
        assertEquals("url_no_permitida", e.getCodigoError());
    }
}
