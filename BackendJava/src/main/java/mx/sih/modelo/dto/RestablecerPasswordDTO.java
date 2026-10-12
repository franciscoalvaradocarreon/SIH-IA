/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package mx.sih.modelo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import mx.sih.seguridad.PoliticaPassword;


public record RestablecerPasswordDTO(
    @NotBlank String token,
    // El minimo sale de la politica (hallazgo H-06): antes eran 8, y el alta no tenia minimo.
    @NotBlank @Size(min = PoliticaPassword.LONGITUD_MINIMA, max = PoliticaPassword.LONGITUD_MAXIMA,
            message = "La contraseña debe tener al menos 12 caracteres y no más de 128")
    String nuevaPassword
) {}
