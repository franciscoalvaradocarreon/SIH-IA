package mx.sih.controlador;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import mx.sih.modelo.dto.ClaimsUsuario;
import mx.sih.modelo.dto.RespuestaLogin;
import mx.sih.modelo.dto.SolicitudLogin;
import mx.sih.servicio.AutenticacionServicio;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AutenticacionControlador {

    private final AutenticacionServicio autenticacionServicio;

    public AutenticacionControlador(AutenticacionServicio autenticacionServicio) {
        this.autenticacionServicio = autenticacionServicio;
    }

    @PostMapping("/login")
    public RespuestaLogin iniciarSesion(@Valid @RequestBody SolicitudLogin solicitud,
                                        HttpServletRequest peticion) {
        return autenticacionServicio.autenticar(solicitud, ipDelCliente(peticion));
    }

    /**
     * Cierra la sesion de verdad.
     *
     * <p>Antes, cerrar sesion era solo cosa del navegador: borraba el token que guardaba. Si alguien
     * lo hubiera copiado, le seguia sirviendo hasta 24 horas. Este endpoint incrementa el contador de
     * sesiones del usuario, asi que el token que se acaba de usar -y cualquier otro suyo, en cualquier
     * dispositivo- queda revocado en la base al instante.
     *
     * <p>Exige estar autenticado (la ruta cae en la regla `/api/**`), porque no tiene sentido -ni es
     * posible- cerrar la sesion de otro.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> cerrarSesion(@AuthenticationPrincipal ClaimsUsuario claims) {
        autenticacionServicio.cerrarSesion(claims == null ? null : claims.usuarioId());
        return ResponseEntity.noContent().build();
    }

    /**
     * IP del cliente para el limitador de intentos.
     *
     * Se usa getRemoteAddr() (el par directo de la conexión) y NO la cabecera
     * X-Forwarded-For: esa cabecera la controla el cliente, así que confiar en ella
     * permitiría saltarse el límite rotándola. Si el backend se despliega detrás de
     * un proxy inverso, configurar en su lugar:
     *     server.forward-headers-strategy=framework
     * y el contenedor pasará a resolver la IP real de forma segura.
     */
    private String ipDelCliente(HttpServletRequest peticion) {
        return peticion.getRemoteAddr();
    }
}
