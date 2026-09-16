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
}