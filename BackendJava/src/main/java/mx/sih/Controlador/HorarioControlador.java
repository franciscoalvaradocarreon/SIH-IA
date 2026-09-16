// mx.sih.controlador.HorarioControlador.java
package mx.sih.controlador;

import mx.sih.servicio.HorarioServicio;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import mx.sih.modelo.dto.HorarioDTO;
import mx.sih.modelo.dto.HorarioSolucionDTO;
import mx.sih.modelo.dto.HorarioSolucionMasivaDTO;

@RestController
@RequestMapping("/api/horarios")
@PreAuthorize("hasAnyRole('ADMIN', 'COORDINADOR')")
public class HorarioControlador {

    private final HorarioServicio horarioServicio;

    public HorarioControlador(HorarioServicio horarioServicio) {
        this.horarioServicio = horarioServicio;
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
            @RequestParam(required = false) Long semestreId) {     // 🔥 NUEVO
        return ResponseEntity.ok(horarioServicio.obtenerHorarioGrupo(grupoId, semestreId));
    }

    @GetMapping("/maestro/{maestroId}")
    public ResponseEntity<List<HorarioDTO>> obtenerHorarioMaestro(
            @PathVariable Long maestroId,
            @RequestParam(required = false) Long semestreId) {     // 🔥 NUEVO
        return ResponseEntity.ok(horarioServicio.obtenerHorarioMaestro(maestroId, semestreId));
    }

    @GetMapping("/aula/{aulaId}")
    public ResponseEntity<List<HorarioDTO>> obtenerHorarioAula(
            @PathVariable Long aulaId,
            @RequestParam(required = false) Long semestreId) {     // 🔥 NUEVO
        return ResponseEntity.ok(horarioServicio.obtenerHorarioAula(aulaId, semestreId));
    }
    @GetMapping("/todos")
    public ResponseEntity<List<HorarioDTO>> obtenerTodos(
            @RequestParam(required = false) Long semestreId) {     // 🔥 NUEVO
        return ResponseEntity.ok(horarioServicio.obtenerTodosLosHorarios(semestreId));
    }

    @PostMapping("/generar-todos")
    public ResponseEntity<HorarioSolucionMasivaDTO> generarTodos(
            @RequestParam(required = false) Long semestreId,
            @RequestParam(required = false) Long turnoId) {     // 🔥 NUEVO
        return ResponseEntity.ok(horarioServicio.generarTodos(semestreId, turnoId));
    }

    @GetMapping("/validar")
    public ResponseEntity<List<String>> validarFactibilidad(
            @RequestParam(required = false) Long semestreId,
            @RequestParam(required = false) Long turnoId) {     // 🔥 NUEVO
        return ResponseEntity.ok(horarioServicio.validarFactibilidad(semestreId, turnoId));
    }
}