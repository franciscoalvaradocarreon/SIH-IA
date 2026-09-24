package mx.sih.modelo.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Cuerpo de la peticion para guardar una corrida del generador IA.
 *
 * <p>El nombre es obligatorio: la lista de opciones guardadas se elige por nombre, asi que una corrida
 * sin nombre es una corrida que no se puede encontrar despues.
 *
 * <p>Es una clase con Lombok y no un record, igual que {@link SolicitudIAInicioDTO}: es el tipo que
 * este proyecto ya usa para los {@code @RequestBody}, y no merece la pena arriesgar la
 * deserializacion por ahorrar unas lineas.
 */
@Data
@NoArgsConstructor
public class SolicitudGuardarCorridaDTO {

    private String nombre;
    private String notas;
}
