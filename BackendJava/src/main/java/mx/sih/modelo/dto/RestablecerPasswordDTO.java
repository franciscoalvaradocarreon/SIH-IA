/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package mx.sih.modelo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;


public record RestablecerPasswordDTO(
    @NotBlank String token,
    @NotBlank @Size(min = 8, max = 128) String nuevaPassword
) {}
