/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package mx.sih.servicio;

import mx.sih.modelo.dto.EscuelaDTO;
import mx.sih.modelo.entidad.Escuela;
import mx.sih.repositorio.UsuarioEscuelaRolRepositorio;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class UsuarioEscuelaServicio {

    private final UsuarioEscuelaRolRepositorio usuarioEscuelaRolRepositorio;

    public UsuarioEscuelaServicio(UsuarioEscuelaRolRepositorio usuarioEscuelaRolRepositorio) {
        this.usuarioEscuelaRolRepositorio = usuarioEscuelaRolRepositorio;
    }

    public List<EscuelaDTO> obtenerEscuelasPorUsuario(Long usuarioId) {
        List<Escuela> escuelas = usuarioEscuelaRolRepositorio.findEscuelasByUsuarioId(usuarioId);
        return escuelas.stream()
                .map(this::convertirADTO)
                .collect(Collectors.toList());
    }

    private EscuelaDTO convertirADTO(Escuela escuela) {
        return new EscuelaDTO(
                escuela.getEscuelaId(),
                escuela.getNombre(),
                escuela.getNombreLargo(),
                escuela.getDireccion(),
                escuela.getTelefono(),
                escuela.getLogoUrl(),
                escuela.getClave(),
                escuela.getActivo()
        );
    }
}