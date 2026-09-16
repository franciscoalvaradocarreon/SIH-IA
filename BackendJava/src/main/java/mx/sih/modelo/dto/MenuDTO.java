/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package mx.sih.modelo.dto;

import java.util.List;

public record MenuDTO(
    Long id,
    String label,
    String path,
    String icono,
    List<MenuDTO> hijos
) {
    // 🔥 Constructor para crear un MenuDTO sin hijos (para listados planos)
    public MenuDTO(Long id, String label, String path, String icono) {
        this(id, label, path, icono, List.of());
    }
}