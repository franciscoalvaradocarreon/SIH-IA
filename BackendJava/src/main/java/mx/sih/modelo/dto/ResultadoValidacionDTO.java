package mx.sih.modelo.dto;

import java.util.List;

public record ResultadoValidacionDTO(
    List<ValidacionDTO> validaciones,
    boolean aptoParaGenerar,
    int totalErrores,
    int totalAdvertencias
) {}