// mx.sih.controlador.HorarioControlador.java
package mx.sih.controlador;

import mx.sih.modelo.dto.AnalisisCuelloBotellaDTO;
import mx.sih.modelo.dto.HorarioDTO;
import mx.sih.modelo.dto.ResultadoValidacionDTO;
import mx.sih.modelo.dto.SolicitudManualDTO;
import mx.sih.modelo.dto.ResultadoManualDTO;
import mx.sih.servicio.HorarioServicio;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * HORARIOS: consulta, validacion previa y tablero manual.
 *
 * <p>Aquí ya NO hay generación automática. El solver de Timefold se retiró entero: el horario se arma
 * con el motor propio del módulo IA ({@code /api/horario-ia}) o a mano con el tablero de pines
 * ({@code POST /manual}). Lo que queda en este controlador es leer el horario, validar si los datos
 * dan para generarlo y aplicar cambios manuales.
 */
@RestController
@RequestMapping("/api/horarios")
@PreAuthorize("hasAnyRole('ADMIN', 'COORDINADOR')")
public class HorarioControlador {

    private final HorarioServicio horarioServicio;

    public HorarioControlador(HorarioServicio horarioServicio) {
        this.horarioServicio = horarioServicio;
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
     * Valida la factibilidad de generar horarios.
     *
     * <p>Devuelve TODAS las validaciones realizadas (no solo las que fallan), cada una con estado
     * OK / ADVERTENCIA / ERROR, para que el frontend muestre la lista completa con iconos.
     *
     * <p>Sigue siendo necesario aunque el solver se haya ido: el módulo IA lo usa para su
     * pre-validación (ver {@code HorarioIAServicio.validar}).
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
}
