package mx.sih.pruebas;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import mx.sih.modelo.dto.ClaimsUsuario;
import mx.sih.modelo.entidad.Usuario;
import mx.sih.repositorio.UsuarioRepositorio;
import mx.sih.servicio.TokenServicio;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * CERRAR SESION INVALIDA EL TOKEN DE VERDAD.
 *
 * <p>Nace de la validacion de ataques (H-07): un JWT no tiene estado, asi que hasta ahora cerrar
 * sesion solo borraba el token del navegador. El token seguia siendo valido hasta 24 horas, y
 * cambiar la contrasena tampoco lo invalidaba. Con el contador de sesiones (reclamo "tv" contra la
 * columna {@code usuarios.token_version}), cerrar sesion lo revoca al instante en todos los
 * dispositivos del usuario.
 *
 * <p>Se prueba contra el endpoint de cerrar sesion a proposito: no depende de escuelas, roles ni
 * datos de negocio, solo del mecanismo. La primera llamada tiene que funcionar (204) y la segunda
 * con EL MISMO token tiene que fallar (401): eso demuestra que el token dejo de valer.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RevocacionTokenTest extends BasePruebas {

    @Value("${local.server.port}")
    private int puerto;

    @Autowired
    private TokenServicio tokenServicio;

    @Autowired
    private UsuarioRepositorio usuarioRepositorio;

    private final HttpClient cliente = HttpClient.newHttpClient();

    /** Crea un usuario real en la base (la contrasena no se usa: el token se emite directamente). */
    private Usuario crearUsuario() {
        long marca = System.nanoTime();
        Usuario usuario = new Usuario();
        usuario.setUsuario("revocacion-" + marca);
        usuario.setPasswordHash("no-se-usa-en-esta-prueba");
        usuario.setNombreCompleto("Prueba Revocacion");
        usuario.setEmail("revocacion-" + marca + "@prueba.local");
        usuario.setActivo(true);
        usuario.setTokenVersion(0);
        return usuarioRepositorio.save(usuario);
    }

    /** Emite un token valido para ese usuario con la version indicada. */
    private String tokenPara(Usuario usuario, int version) {
        return tokenServicio.generarToken(new ClaimsUsuario(
                usuario.getUsuarioId(),
                usuario.getEmail(),
                List.of("ADMIN"),
                999L,
                List.of(999L),
                version));
    }

    private HttpResponse<String> cerrarSesion(String token) throws Exception {
        HttpRequest.Builder constructor = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + puerto + "/api/auth/logout"))
                .POST(HttpRequest.BodyPublishers.noBody());
        if (token != null) {
            constructor.header("Authorization", "Bearer " + token);
        }
        return cliente.send(constructor.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    @DisplayName("El token vale, se cierra sesion, y el MISMO token ya no vale")
    void cerrarSesionRevocaElToken() throws Exception {
        Usuario usuario = crearUsuario();
        String token = tokenPara(usuario, 0);

        assertThat(cerrarSesion(token).statusCode())
                .as("con el token vigente, cerrar sesion tiene que funcionar")
                .isEqualTo(204);

        assertThat(cerrarSesion(token).statusCode())
                .as("el mismo token, despues de cerrar sesion, debe quedar revocado")
                .isEqualTo(401);

        // Un token emitido despues -el contador ya subio- si vale: cerrar sesion no deja fuera al
        // usuario que vuelve a entrar.
        Usuario recargado = usuarioRepositorio.findById(usuario.getUsuarioId()).orElseThrow();
        assertThat(recargado.getTokenVersion())
                .as("cerrar sesion debe incrementar el contador")
                .isEqualTo(1);
        assertThat(cerrarSesion(tokenPara(recargado, recargado.getTokenVersion())).statusCode())
                .isEqualTo(204);
    }

    @Test
    @DisplayName("Cerrar sesion en un dispositivo revoca tambien los de los demas")
    void cerrarSesionEnUnDispositivoRevocaLosOtros() throws Exception {
        Usuario usuario = crearUsuario();
        String dispositivoA = tokenPara(usuario, 0);
        String dispositivoB = tokenPara(usuario, 0);

        assertThat(cerrarSesion(dispositivoA).statusCode()).isEqualTo(204);

        assertThat(cerrarSesion(dispositivoB).statusCode())
                .as("el token del otro dispositivo debe quedar revocado igual")
                .isEqualTo(401);
    }

    @Test
    @DisplayName("Un usuario desactivado deja de poder usar su token")
    void usuarioInactivoNoEntra() throws Exception {
        Usuario usuario = crearUsuario();
        String token = tokenPara(usuario, 0);
        assertThat(cerrarSesion(token).statusCode()).isEqualTo(204);

        usuario.setActivo(false);
        usuarioRepositorio.save(usuario);

        // Token nuevo (con el contador al dia) pero cuenta desactivada: no debe entrar.
        Usuario recargado = usuarioRepositorio.findById(usuario.getUsuarioId()).orElseThrow();
        assertThat(cerrarSesion(tokenPara(recargado, recargado.getTokenVersion())).statusCode())
                .as("una cuenta desactivada no debe poder seguir usando sus tokens")
                .isEqualTo(401);
    }

    @Test
    @DisplayName("Sin token, cerrar sesion responde 401 (no hay sesion que cerrar)")
    void sinTokenNoSePuedeCerrarSesion() throws Exception {
        assertThat(cerrarSesion(null).statusCode()).isEqualTo(401);
    }
}
