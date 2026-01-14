package edu.msmk.clases.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Response para validacion de cobertura
 */
@Data
@Builder
public class ValidacionResponse {
    private Boolean cubierta;
    private String clave;
    private String mensaje;
    private String provincia;
    private String municipio;
}