package mx.sih.controlador;

import mx.sih.excepcion.NegocioExcepcion;
import mx.sih.ia.DatosIA;
import mx.sih.ia.IntentoIA;
import mx.sih.modelo.dto.ClaimsUsuario;
import mx.sih.modelo.dto.SolicitudIAInicioDTO;
import mx.sih.modelo.dto.TrabajoIADTO;
import mx.sih.modelo.dto.ValidacionIADTO;
import mx.sih.seguridad.contexto.EscuelaContexto;
import mx.sih.servicio.HorarioIAServicio;
import mx.sih.servicio.HorarioIATrabajoServicio;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * GENERADOR DE HORARIOS IA (sin Timefold).
 *
 * <p>Rutas, todas bajo {@code /api/horario-ia}:
 *
 * <ul>
 *   <li>{@code GET  /validar}: pre-validación (la del backend + los chequeos del motor IA).</li>
 *   <li>{@code POST /generar}: lanza N intentos en segundo plano (202 + id del trabajo).</li>
 *   <li>{@code GET  /trabajo/{id}}: estado, intentos y mejor intento.</li>
 *   <li>{@code POST /trabajo/{id}/terminar}: no lanza más intentos y conserva lo hecho.</li>
 *   <li>{@code POST /trabajo/{id}/registrar?numero=N}: escribe ese intento en el horario real.</li>
 *   <li>{@code GET  /trabajo/{id}/intento/{n}/pendientes}: horas que quedaron sin colocar y por qué.</li>
 *   <li>{@code GET  /config}: valores por defecto y si hay LLM configurado.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/horario-ia")
@PreAuthorize("hasAnyRole('ADMIN', 'COORDINADOR')")
public class HorarioIAControlador {

    private final HorarioIAServicio servicio;
    private final HorarioIATrabajoServicio trabajos;

    public HorarioIAControlador(HorarioIAServicio servicio, HorarioIATrabajoServicio trabajos) {
        this.servicio = servicio;
        this.trabajos = trabajos;
    }

    /** Ajustes con los que se lanzará la generación: la interfaz los muestra y los deja cambiar. */
    @GetMapping("/config")
    public ResponseEntity<ConfigIA> config() {
        return ResponseEntity.ok(new ConfigIA(
                trabajos.getIntentosPorDefecto(),
                trabajos.getSegundosPorIntentoDefecto(),
                trabajos.getMaxPasosDefecto(),
                trabajos.llmConfigurado(),
                trabajos.getModeloPorDefecto()));
    }

    /** Pre-validación: se puede llamar sin lanzar nada, para ver si los datos están consistentes. */
    @GetMapping("/validar")
    public ResponseEntity<ValidacionIADTO> validar(
            @RequestParam(required = false) Long semestreId,
            @RequestParam(required = false) Long turnoId) {
        Long escuelaId = EscuelaContexto.getEscuelaId();
        if (escuelaId == null) {
            throw new NegocioExcepcion("sin_escuela_activa", "No se ha seleccionado una escuela activa");
        }
        DatosIA datos = servicio.cargarDatos(semestreId, turnoId);
        return ResponseEntity.ok(servicio.validar(escuelaId, semestreId, turnoId, datos));
    }

    /** Lanza la generación en segundo plano. */
    @PostMapping("/generar")
    public ResponseEntity<TrabajoIADTO> generar(@RequestBody(required = false) SolicitudIAInicioDTO solicitud,
                                                Authentication autenticacion) {
        Long escuelaId = EscuelaContexto.getEscuelaId();
        if (escuelaId == null) {
            throw new NegocioExcepcion("sin_escuela_activa", "No se ha seleccionado una escuela activa");
        }
        SolicitudIAInicioDTO s = solicitud != null ? solicitud : new SolicitudIAInicioDTO();
        TrabajoIADTO trabajo = trabajos.iniciar(escuelaId, s.getSemestreId(), s.getTurnoId(), s.getModo(),
                s.getIntentos(), s.getSegundosPorIntento(), s.getMaxPasos(),
                s.getApiKey(), s.getUrl(), s.getModelo(), s.getAsignarMaestros(), usuario(autenticacion));
        return ResponseEntity.accepted().body(trabajo);
    }

    @GetMapping("/trabajo/{trabajoId}")
    public ResponseEntity<TrabajoIADTO> consultar(@PathVariable String trabajoId) {
        return trabajos.consultar(trabajoId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/trabajo/{trabajoId}/terminar")
    public ResponseEntity<TrabajoIADTO> terminar(@PathVariable String trabajoId) {
        return trabajos.terminar(trabajoId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/trabajo/{trabajoId}/registrar")
    public ResponseEntity<TrabajoIADTO> registrar(@PathVariable String trabajoId,
                                                  @RequestParam int numero) {
        return ResponseEntity.ok(trabajos.registrar(trabajoId, numero));
    }

    /** Igual que el anterior, pero colgado del intento (es como se usa desde la lista de intentos). */
    @PostMapping("/trabajo/{trabajoId}/intento/{numero}/registrar")
    public ResponseEntity<TrabajoIADTO> registrarIntento(@PathVariable String trabajoId,
                                                         @PathVariable int numero) {
        return ResponseEntity.ok(trabajos.registrar(trabajoId, numero));
    }

    /** Horas que quedaron pendientes en un intento, con el motivo y las ventanas que están ocupadas. */
    @GetMapping("/trabajo/{trabajoId}/intento/{numero}/pendientes")
    public ResponseEntity<List<IntentoIA.Pendiente>> pendientes(@PathVariable String trabajoId,
                                                                @PathVariable int numero) {
        return ResponseEntity.ok(trabajos.intento(trabajoId, numero).getPendientes());
    }

    /** Valor por defecto del trabajo: el mejor intento conseguido. */
    @GetMapping("/trabajo/{trabajoId}/mejor")
    public ResponseEntity<IntentoIA> mejor(@PathVariable String trabajoId) {
        IntentoIA mejor = trabajos.mejor(trabajoId);
        return mejor == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(mejor);
    }

    private String usuario(Authentication autenticacion) {
        if (autenticacion != null && autenticacion.getPrincipal() instanceof ClaimsUsuario claims) {
            return claims.correo();
        }
        return "desconocido";
    }

    /** Ajustes por defecto del generador IA. */
    public record ConfigIA(int intentos, int segundosPorIntento, int maxPasos, boolean llmConfigurado,
                           String modeloPorDefecto) {
    }
}
