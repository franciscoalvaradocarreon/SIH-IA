// TurnoHorarioControlador.java - Versión SIN campo activo
package mx.sih.controlador;

import jakarta.validation.Valid;
import mx.sih.excepcion.NegocioExcepcion;
import mx.sih.modelo.dto.TurnoHorarioCrearDTO;
import mx.sih.modelo.dto.TurnoHorarioDTO;
import mx.sih.servicio.TurnoHorarioServicio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/turnos/{turnoId}/horarios")
@PreAuthorize("hasAnyRole('ADMIN', 'COORDINADOR')")
public class TurnoHorarioControlador {

    private static final Logger logger = LoggerFactory.getLogger(TurnoHorarioControlador.class);

    private final TurnoHorarioServicio turnoHorarioServicio;

    public TurnoHorarioControlador(TurnoHorarioServicio turnoHorarioServicio) {
        this.turnoHorarioServicio = turnoHorarioServicio;
    }

    // 🔥 Listar horarios (todos, sin filtro de activo en horario)
    @GetMapping
    public ResponseEntity<?> listarHorarios(@PathVariable Long turnoId, Long semestreId) {
        
        try {
            List<TurnoHorarioDTO> horarios = turnoHorarioServicio.findByTurnoIdAndSemestreId(turnoId, semestreId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", horarios);
            response.put("total", horarios.size());
            
            return ResponseEntity.ok(response);
            
        } catch (NegocioExcepcion e) {
            logger.warn("⚠️ Error de negocio: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(crearErrorResponse("error_negocio", e.getMessage()));
        } catch (Exception e) {
            logger.error("❌ Error interno: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(crearErrorResponse("error_interno", "Error al procesar la solicitud"));
        }
    }

    // 🔥 Listar solo clases
    @GetMapping("/clases")
    public ResponseEntity<?> listarClases(@PathVariable Long turnoId) {
        try {
            List<TurnoHorarioDTO> clases = turnoHorarioServicio.listarClasesPorTurno(turnoId);
            return ResponseEntity.ok(clases);
        } catch (NegocioExcepcion e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(crearErrorResponse("error_negocio", e.getMessage()));
        }
    }

    // 🔥 Listar solo descansos
    @GetMapping("/descansos")
    public ResponseEntity<?> listarDescansos(@PathVariable Long turnoId) {
        try {
            List<TurnoHorarioDTO> descansos = turnoHorarioServicio.listarDescansosPorTurno(turnoId);
            return ResponseEntity.ok(descansos);
        } catch (NegocioExcepcion e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(crearErrorResponse("error_negocio", e.getMessage()));
        }
    }

    // 🔥 Contar horarios
    @GetMapping("/contar")
    public ResponseEntity<?> contarHorarios(@PathVariable Long turnoId) {
        try {
            long count = turnoHorarioServicio.contarHorarios(turnoId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("turnoId", turnoId);
            response.put("total", count);
            
            return ResponseEntity.ok(response);
        } catch (NegocioExcepcion e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(crearErrorResponse("error_negocio", e.getMessage()));
        }
    }

    @PostMapping
    public ResponseEntity<?> crearHorario(@PathVariable Long turnoId,
                                          @Valid @RequestBody TurnoHorarioCrearDTO dto) {
        try {
            TurnoHorarioDTO creado = turnoHorarioServicio.crearHorario(turnoId, dto);
            return ResponseEntity.status(HttpStatus.CREATED).body(creado);
        } catch (NegocioExcepcion e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(crearErrorResponse("error_negocio", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> actualizarHorario(@PathVariable Long turnoId,
                                               @PathVariable Long id,
                                               @Valid @RequestBody TurnoHorarioCrearDTO dto) {
        try {
            TurnoHorarioDTO actualizado = turnoHorarioServicio.actualizarHorario(id, dto);
            return ResponseEntity.ok(actualizado);
        } catch (NegocioExcepcion e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(crearErrorResponse("error_negocio", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> eliminarHorario(@PathVariable Long turnoId,
                                             @PathVariable Long id) {
        try {
            turnoHorarioServicio.eliminarHorario(id);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Horario eliminado correctamente");
            
            return ResponseEntity.ok(response);
        } catch (NegocioExcepcion e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(crearErrorResponse("error_negocio", e.getMessage()));
        }
    }

    // MÉTODO PRIVADO - Crear respuesta de error
    private Map<String, Object> crearErrorResponse(String codigo, String mensaje) {
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("success", false);
        errorResponse.put("error", codigo);
        errorResponse.put("message", mensaje);
        return errorResponse;
    }
}