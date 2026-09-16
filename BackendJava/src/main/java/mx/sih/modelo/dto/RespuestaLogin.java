/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package mx.sih.modelo.dto;

import java.util.List;

public record RespuestaLogin(
    String tokenJwt,
    String nombreCompleto,
    String correo,
    Long escuelaActivaId,
    List<String> roles
) {}