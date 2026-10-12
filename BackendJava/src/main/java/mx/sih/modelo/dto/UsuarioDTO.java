package mx.sih.modelo.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UsuarioDTO {
    private Long id;
    private String usuario;
    private String nombreCompleto;
    private String email;
    private String fotoUrl;
    private Boolean activo;
    private LocalDateTime ultimoAcceso;
    private List<String> roles;
    private List<String> escuelas;

    /**
     * Aviso NO bloqueante para el usuario final. Hoy se usa cuando la foto no se pudo procesar: el
     * usuario se guarda igual, sin foto, y la pantalla muestra este texto. null = todo salió bien.
     */
    private String aviso;
}