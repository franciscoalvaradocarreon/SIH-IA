package mx.sih.servicio;

import mx.sih.excepcion.NegocioExcepcion;
import mx.sih.modelo.dto.ClaimsUsuario;
import mx.sih.modelo.dto.RespuestaLogin;
import mx.sih.modelo.dto.SolicitudLogin;
import mx.sih.repositorio.UsuarioRepositorio;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * Autenticación por correo + contraseña.
 *
 * CAMBIOS DE SEGURIDAD
 *  · Límite de intentos (IntentoLoginServicio): antes se podía hacer fuerza
 *    bruta indefinida sobre POST /api/auth/login.
 *  · Orden de comprobaciones: primero la CONTRASEÑA y después el estado de la
 *    cuenta. Antes se respondía "usuario_inactivo" sin necesidad de acertar la
 *    contraseña, lo que permitía enumerar correos registrados.
 *  · Si el correo no existe se ejecuta igualmente una verificación BCrypt de
 *    descarte para que el tiempo de respuesta no delate qué correos existen.
 *
 * AVISO AL USUARIO
 *  · Cuando a un usuario legítimo le queda 1 intento antes del bloqueo, el
 *    mensaje incluye un aviso explícito. Los correos inexistentes siguen
 *    recibiendo el mensaje genérico para no filtrar su existencia.
 */
@Service
public class AutenticacionServicio {

    private static final String MENSAJE_CREDENCIALES_INVALIDAS =
            "Correo o contraseña incorrectos";

    private final UsuarioRepositorio usuarioRepositorio;
    private final PasswordEncoder codificadorPassword;
    private final TokenServicio tokenServicio;
    private final IntentoLoginServicio intentoLoginServicio;

    /** Hash de descarte (se genera al arrancar) para igualar tiempos de respuesta. */
    private final String hashDescarte;

    public AutenticacionServicio(UsuarioRepositorio usuarioRepositorio,
                                 PasswordEncoder codificadorPassword,
                                 TokenServicio tokenServicio,
                                 IntentoLoginServicio intentoLoginServicio) {
        this.usuarioRepositorio = usuarioRepositorio;
        this.codificadorPassword = codificadorPassword;
        this.tokenServicio = tokenServicio;
        this.intentoLoginServicio = intentoLoginServicio;
        this.hashDescarte = codificadorPassword.encode("hash-de-descarte-para-igualar-tiempos");
    }

    /** Compatibilidad: sin IP no se aplica la cuota por IP. */
    @Transactional(readOnly = true)
    public RespuestaLogin autenticar(SolicitudLogin solicitud) {
        return autenticar(solicitud, null);
    }

    @Transactional(readOnly = true)
    public RespuestaLogin autenticar(SolicitudLogin solicitud, String ipCliente) {

        String correo = solicitud.correo() == null ? "" : solicitud.correo().trim();
        String claveIntentos = correo.toLowerCase(Locale.ROOT);

        // 1) ¿Está bloqueado por intentos fallidos?
        intentoLoginServicio.verificarPermitido(claveIntentos, ipCliente);

        // 2) Buscar al usuario (con sus relaciones para armar los claims)
        var usuarioOpt = usuarioRepositorio.findByEmailConRelaciones(correo);
        if (usuarioOpt.isEmpty()) {
            // Verificación de descarte: mismo costo que un login real.
            // NO se agrega aviso de "intentos restantes": el correo no existe
            // y un aviso distinto permitiría enumerar correos registrados.
            codificadorPassword.matches(solicitud.contrasenia(), hashDescarte);
            intentoLoginServicio.registrarFallo(claveIntentos, ipCliente);
            throw new NegocioExcepcion("credenciales_invalidas", MENSAJE_CREDENCIALES_INVALIDAS);
        }
        var usuario = usuarioOpt.get();

        // 3) VERIFICAR CONTRASEÑA ANTES DE CUALQUIER OTRA COSA
        if (!codificadorPassword.matches(solicitud.contrasenia(), usuario.getPasswordHash())) {
            intentoLoginServicio.registrarFallo(claveIntentos, ipCliente);

            // 🔥 Aviso si le queda 1 intento. Solo en este punto podemos
            // afirmar que el usuario existe (la contraseña fue lo que falló),
            // así que mostrar "te queda 1 intento" no filtra información.
            int restantes = intentoLoginServicio.intentosRestantes(claveIntentos, ipCliente);
            String mensaje = MENSAJE_CREDENCIALES_INVALIDAS;
            if (restantes == 1) {
                mensaje += ". Te queda 1 intento antes de bloquear tu cuenta por 15 minutos.";
            }
            throw new NegocioExcepcion("credenciales_invalidas", mensaje);
        }

        // 4) La contraseña ya es correcta: ahora sí puede informarse del estado de la cuenta
        if (!Boolean.TRUE.equals(usuario.getActivo())) {
            throw new NegocioExcepcion("usuario_inactivo",
                "La cuenta está desactivada. Contacta al administrador.");
        }

        // 5) Relaciones activas (usuario activo + escuela activa)
        var relacionesActivas = usuario.getRelacionesEscuelaRol().stream()
            .filter(r -> Boolean.TRUE.equals(r.getActivo()))
            .filter(r -> r.getEscuela() != null && Boolean.TRUE.equals(r.getEscuela().getActivo()))
            .toList();

        if (relacionesActivas.isEmpty()) {
            throw new NegocioExcepcion("sin_escuela",
                "No tienes acceso a ninguna escuela activa. Contacta al administrador.");
        }

        // 6) Recolectar TODAS las escuelas y TODOS los roles
        var escuelaIds = relacionesActivas.stream()
            .map(r -> r.getEscuela().getEscuelaId())
            .distinct()
            .toList();

        var roles = relacionesActivas.stream()
            .map(r -> r.getRol().getNombre())
            .distinct()
            .toList();

        // Escuela "activa" al momento del login: la primera (el frontend puede cambiarla después)
        Long escuelaInicial = escuelaIds.get(0);

        var claims = new ClaimsUsuario(
            usuario.getUsuarioId(),
            usuario.getEmail(),
            roles,
            escuelaInicial,
            escuelaIds
        );

        var token = tokenServicio.generarToken(claims);

        // 7) Actualizar último acceso
        usuario.setUltimoAcceso(java.time.LocalDateTime.now());
        usuarioRepositorio.save(usuario);

        // 8) Login correcto: limpiar el contador de fallos
        intentoLoginServicio.registrarExito(claveIntentos, ipCliente);

        return new RespuestaLogin(
            token,
            usuario.getNombreCompleto(),
            usuario.getEmail(),
            escuelaInicial,
            roles
        );
    }
}