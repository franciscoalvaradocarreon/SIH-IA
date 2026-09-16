package mx.sih.controlador;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import mx.sih.modelo.dto.RestablecerPasswordDTO;
import mx.sih.modelo.dto.SolicitudRecuperacionDTO;
import mx.sih.servicio.RecuperacionPasswordServicio;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class RecuperacionPasswordControlador {

    private final RecuperacionPasswordServicio recuperacionServicio;

    public RecuperacionPasswordControlador(RecuperacionPasswordServicio recuperacionServicio) {
        this.recuperacionServicio = recuperacionServicio;
    }

    @PostMapping("/recuperar-password")
    public ResponseEntity<Void> solicitar(@Valid @RequestBody SolicitudRecuperacionDTO dto,
                                          HttpServletRequest request) {
        recuperacionServicio.solicitarRecuperacion(dto.correo(), request.getRemoteAddr());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/restablecer-password")
    public ResponseEntity<Void> restablecer(@Valid @RequestBody RestablecerPasswordDTO dto) {
        recuperacionServicio.restablecerPassword(dto.token(), dto.nuevaPassword());
        return ResponseEntity.ok().build();
    }
}