package mx.sih.controlador;

import mx.sih.excepcion.NegocioExcepcion;
import mx.sih.ia.DatosIA;
import mx.sih.ia.IntentoIA;
import mx.sih.modelo.dto.ClaimsUsuario;
import mx.sih.modelo.dto.CorridaIADTO;
import mx.sih.modelo.dto.SolicitudGuardarCorridaDTO;
import mx.sih.modelo.dto.SolicitudIAInicioDTO;
import mx.sih.modelo.dto.TrabajoIADTO;
import mx.sih.modelo.dto.ValidacionIADTO;
import mx.sih.seguridad.contexto.EscuelaContexto;
import mx.sih.servicio.CorridaIAServicio;
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
 *   <li>{@code POST /trabajo/{id}/intento/{n}/guardar}: guarda ese intento con un nombre, SIN tocar el
 *       horario. Es lo que permite seguir generando para comparar opciones.</li>
 *   <li>{@code GET  /corridas?semestreId=N}: corridas guardadas de ese semestre, con sus métricas.</li>
 *   <li>{@code POST /corridas/{id}/aplicar}: aplica una corrida guardada al horario vigente.</li>
 *   <li>{@code DELETE /corridas/{id}}: quita una corrida de la lista (NO toca el horario).</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/horario-ia")
@PreAuthorize("hasAnyRole('ADMIN', 'COORDINADOR')")
public class HorarioIAControlador {

    private final HorarioIAServicio servicio;
    private final HorarioIATrabajoServicio trabajos;
    private final CorridaIAServicio corridas;

    public HorarioIAControlador(HorarioIAServicio servicio, HorarioIATrabajoServicio trabajos,
                                CorridaIAServicio corridas) {
        this.servicio = servicio;
        this.trabajos = trabajos;
        this.corridas = corridas;
    }

    /** Ajustes con los que se lanzará la generación: la interfaz los muestra y los deja cambiar. */
    @GetMapping("/config")
    public ResponseEntity<ConfigIA> config() {
        return ResponseEntity.ok(new ConfigIA(
                trabajos.getIntentosPorDefecto(),
                trabajos.getSegundosPorIntentoDefecto(),
                trabajos.getMaxPasosDefecto(),
                trabajos.llmConfigurado(),
                trabajos.getModeloPorDefecto(),
                trabajos.getHilos()));
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
                s.getApiKey(), s.getUrl(), s.getModelo(),
                s.getAsignarMaestros(), s.getAsignarAulas(), usuario(autenticacion));
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

    // ============================================================
    // CORRIDAS GUARDADAS
    // ============================================================
    // Guardar es inerte: escribe en sih.corrida_ia y NO toca el horario que ve la escuela. Asi se
    // pueden lanzar varias generaciones y quedarse con la mejor. Aplicar es lo unico destructivo, y
    // reutiliza registrar() a traves de CorridaIAServicio en vez de duplicar esa logica.

    /**
     * Guarda un intento del trabajo con un nombre. El alcance (semestre y turno) sale del propio
     * trabajo y no del cliente: es el mismo con el que se genero, asi que no se puede guardar una
     * corrida "en" un semestre que no le corresponde.
     */
    @PostMapping("/trabajo/{trabajoId}/intento/{numero}/guardar")
    public ResponseEntity<CorridaIADTO> guardar(@PathVariable String trabajoId,
                                                @PathVariable int numero,
                                                @RequestBody(required = false) SolicitudGuardarCorridaDTO solicitud,
                                                Authentication autenticacion) {
        Long escuelaId = EscuelaContexto.getEscuelaId();
        if (escuelaId == null) {
            throw new NegocioExcepcion("sin_escuela_activa", "No se ha seleccionado una escuela activa");
        }
        TrabajoIADTO trabajo = trabajos.consultar(trabajoId)
                .orElseThrow(() -> new NegocioExcepcion("trabajo_no_encontrado",
                        "El trabajo de generación IA ya no existe. Vuelve a generarlo."));
        IntentoIA intento = trabajos.intento(trabajoId, numero);
        SolicitudGuardarCorridaDTO s = solicitud != null ? solicitud : new SolicitudGuardarCorridaDTO();

        return ResponseEntity.ok(corridas.guardar(escuelaId, trabajo.getSemestreId(), trabajo.getTurnoId(),
                trabajo.isAsignarMaestros(), trabajo.isAsignarAulas(),
                intento, s.getNombre(), s.getNotas(), usuario(autenticacion)));
    }

    /** Corridas guardadas de un semestre, con sus métricas, para poder compararlas. */
    @GetMapping("/corridas")
    public ResponseEntity<List<CorridaIADTO>> corridasGuardadas(
            @RequestParam(required = false) Long semestreId) {
        return ResponseEntity.ok(corridas.listar(EscuelaContexto.getEscuelaId(), semestreId));
    }

    /**
     * Aplica una corrida guardada al horario VIGENTE. Reemplaza el horario de los grupos del alcance:
     * es la operación destructiva, aunque se puede corregir aplicando otra corrida o restaurando la
     * copia de seguridad diaria.
     */
    @PostMapping("/corridas/{id}/aplicar")
    public ResponseEntity<HorarioIAServicio.RegistroIA> aplicar(@PathVariable Long id) {
        return ResponseEntity.ok(corridas.aplicar(EscuelaContexto.getEscuelaId(), id));
    }

    /**
     * Borra una corrida de la lista de opciones. NO toca el horario vigente: si esa corrida ya se
     * aplico, el horario que quedo sigue igual. Solo la quita de la lista.
     */
    @DeleteMapping("/corridas/{id}")
    public ResponseEntity<Void> eliminarCorrida(@PathVariable Long id) {
        corridas.eliminar(EscuelaContexto.getEscuelaId(), id);
        return ResponseEntity.noContent().build();
    }

    private String usuario(Authentication autenticacion) {
        if (autenticacion != null && autenticacion.getPrincipal() instanceof ClaimsUsuario claims) {
            return claims.correo();
        }
        return "desconocido";
    }

    /** Ajustes por defecto del generador IA. */
    public record ConfigIA(int intentos, int segundosPorIntento, int maxPasos, boolean llmConfigurado,
                           String modeloPorDefecto, int hilos) {
    }
}
