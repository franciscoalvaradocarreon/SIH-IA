/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package mx.sih.modelo.dto;

import java.util.List;

public record ClaimsUsuario(
    Long usuarioId,
    String correo,
    List<String> roles,
    Long escuelaId,
    List<Long> escuelaIds
){
    /** Constructor de retrocompatibilidad con tokens viejos. */
    public ClaimsUsuario(Long usuarioId, String correo, List<String> roles, Long escuelaId) {
        this(usuarioId, correo, roles, escuelaId,
             escuelaId != null ? List.of(escuelaId) : List.of());
    }

    public boolean tieneAccesoA(Long otraEscuelaId) {
        return otraEscuelaId != null && escuelaIds.contains(otraEscuelaId);
    }
}
