// mx.sih.controlador.HorarioControlador.java
package mx.sih.controlador;

import mx.sih.excepcion.NegocioExcepcion;
import mx.sih.modelo.dto.AnalisisCuelloBotellaDTO;
import mx.sih.modelo.dto.ClaimsUsuario;
import mx.sih.modelo.dto.HorarioDTO;
import mx.sih.modelo.dto.HorarioSolucionDTO;
import mx.sih.modelo.dto.HorarioSolucionMasivaDTO;
import mx.sih.modelo.dto.ResultadoValidacionDTO;
import mx.sih.modelo.dto.TrabajoGeneracionDTO;
import mx.sih.modelo.dto.SolicitudManualDTO;
import mx.sih.modelo.dto.ResultadoManualDTO;
import mx.sih.seguridad.contexto.EscuelaContexto;
import mx.sih.servicio.GeneracionHorarioTrabajoServicio;
import mx.sih.servicio.HorarioServicio;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/horarios")
@PreAuthorize("hasAnyRole('ADMIN', 'COORDINADOR')")
public class HorarioControlador {

    private final HorarioServicio horarioServicio;
    private final GeneracionHorarioTrabajoServicio trabajosGeneracion;

    public HorarioControlador(HorarioServicio horarioServicio,
                              GeneracionHorarioTrabajoServicio trabajosGeneracion) {
        this.horarioServicio = horarioServicio;
        this.trabajosGeneracion = trabajosGeneracion;
    }

    @PostMapping("/generar/{grupoId}")
    public ResponseEntity<HorarioSolucionDTO> generarHorario(
            @PathVariable Long grupoId,
            @RequestParam(required = false) Long semestreId) {
        HorarioSolucionDTO resultado = horarioServicio.generarHorario(grupoId, semestreId);
        return ResponseEntity.ok(resultado);
    }

    @GetMapping("/grupo/{grupoId}")
    public ResponseEntity<List<HorarioDTO>> obtenerHorarioGrupo(
            @PathVariable Long grupoId,
            @RequestParam(required = false) Long semestreId) {
        return ResponseEntity.ok(horarioServicio.obtenerHorarioGrupo(grupoId, semestreId));
    }

    @GetMapping("/maestro/{maestroId}")
    public ResponseEntity<List<HorarioDTO>> obtenerHorarioMaestro(
            @PathVariable Long maestroId,
            @RequestParam(required = false) Long semestreId) {
        return ResponseEntity.ok(horarioServicio.obtenerHorarioMaestro(maestroId, semestreId));
    }

    @GetMapping("/aula/{aulaId}")
    public ResponseEntity<List<HorarioDTO>> obtenerHorarioAula(
            @PathVariable Long aulaId,
            @RequestParam(required = false) Long semestreId) {
        return ResponseEntity.ok(horarioServicio.obtenerHorarioAula(aulaId, semestreId));
    }

    @GetMapping("/todos")
    public ResponseEntity<List<HorarioDTO>> obtenerTodos(
            @RequestParam(required = false) Long semestreId) {
        return ResponseEntity.ok(horarioServicio.obtenerTodosLosHorarios(semestreId));
    }

    /**
     * Generación masiva ASÍNCRONA.
     *
     * Responde 202 Accepted de inmediato con un trabajo en estado EN_COLA: el solver
     * tarda hasta 300 s y antes este endpoint se quedaba bloqueado todo ese tiempo en
     * el hilo HTTP (con riesgo de timeout en navegador o proxy). El resultado se
     * consulta en {@link #consultarGeneracion(String)}.
     *
     * Si ya hay una generación en curso con el mismo alcance, devuelve ESA (idempotente
     * ante dobles clics).
     */
    @PostMapping("/generar-todos")
    public ResponseEntity<TrabajoGeneracionDTO> generarTodos(
            @RequestParam(required = false) Long semestreId,
            @RequestParam(required = false) Long turnoId,
            Authentication autenticacion) {

        Long escuelaId = EscuelaContexto.getEscuelaId();
        if (escuelaId == null) {
            throw new NegocioExcepcion("sin_escuela_activa",
                    "No se ha seleccionado una escuela activa");
        }

        TrabajoGeneracionDTO trabajo = trabajosGeneracion.iniciar(
                escuelaId, semestreId, turnoId, usuarioAutenticado(autenticacion));

        return ResponseEntity.accepted().body(trabajo);
    }

    /** Estado del trabajo de generación. Incluye el resultado completo al COMPLETARSE. */
    @GetMapping("/generar-todos/{trabajoId}")
    public ResponseEntity<TrabajoGeneracionDTO> consultarGeneracion(@PathVariable String trabajoId) {
        return trabajosGeneracion.consultar(trabajoId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Termina la generación en curso.
     *
     * <p>No se pierde el trabajo hecho: el solver devuelve lo mejor que haya encontrado hasta ese
     * momento y ESA solución es la que se guarda, así que se puede cortar una generación larga en
     * cuanto aparezca un resultado que sirva.
     */
    @PostMapping("/generar-todos/{trabajoId}/terminar")
    public ResponseEntity<TrabajoGeneracionDTO> terminarGeneracion(@PathVariable String trabajoId) {
        return trabajosGeneracion.terminar(trabajoId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Valida la factibilidad de generar horarios.
     *
     * Devuelve TODAS las validaciones realizadas (no solo las que fallan),
     * cada una con estado OK / ADVERTENCIA / ERROR, para que el frontend
     * muestre la lista completa con iconos.
     */
    @GetMapping("/validar")
    public ResponseEntity<ResultadoValidacionDTO> validarFactibilidad(
            @RequestParam(required = false) Long semestreId,
            @RequestParam(required = false) Long turnoId) {
        return ResponseEntity.ok(horarioServicio.validarFactibilidad(semestreId, turnoId));
    }

    /**
    * Análisis informativo de cuellos de botella por (grupo, bloque).
    * Muestra cuántos maestros del grupo están disponibles en cada bloque.
    */
    @GetMapping("/analisis-cuellos")
    public ResponseEntity<AnalisisCuelloBotellaDTO> analizarCuellos(
            @RequestParam(required = false) Long semestreId,
            @RequestParam(required = false) Long turnoId) {
        return ResponseEntity.ok(horarioServicio.analizarCuellosBotella(semestreId, turnoId));
    }

    /**
     * Guarda (o solo valida) una tanda de cambios del tablero manual de pines.
     *
     * El tablero trabaja con un borrador local: al pulsar Guardar manda todos los cambios
     * juntos y aqui se validan como conjunto. Si algo choca (grupo, maestro, aula,
     * disponibilidad o un bloque de otro turno) NO se aplica ninguno y se devuelven los
     * problemas numerados para que el tablero marque los pines.
     */
    @PostMapping("/manual")
    public ResponseEntity<ResultadoManualDTO> aplicarCambiosManuales(
            @RequestBody SolicitudManualDTO solicitud) {
        return ResponseEntity.ok(horarioServicio.aplicarCambiosManuales(solicitud));
    }

    private String usuarioAutenticado(Authentication autenticacion) {
        if (autenticacion != null && autenticacion.getPrincipal() instanceof ClaimsUsuario claims) {
            return claims.correo();
        }
        return "desconocido";
    }
}
