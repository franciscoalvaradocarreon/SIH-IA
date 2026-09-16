/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package mx.sih.modelo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Credenciales de acceso.
 *
 * Antes era un record SIN ninguna validación y el controlador no usaba @Valid:
 * se aceptaba cualquier entrada, sin límite de longitud (un cuerpo enorme se
 * procesaba entero) y sin comprobar el formato.
 *
 * Nota: deliberadamente NO se valida @Email aquí. En el login la política debe
 * ser "no revelar nada": un formato inválido simplemente será credenciales
 * incorrectas, y así no se bloquea a usuarios antiguos con correos no estándar.
 */
public record SolicitudLogin(
    @NotBlank(message = "El correo es obligatorio")
    @Size(max = 100, message = "El correo no puede exceder 100 caracteres")
    String correo,

    @NotBlank(message = "La contraseña es obligatoria")
    @Size(max = 128, message = "La contraseña no puede exceder 128 caracteres")
    String contrasenia
) {}
