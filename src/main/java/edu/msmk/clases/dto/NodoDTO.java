package edu.msmk.clases.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class NodoDTO {
    private String id;
    private String tipo; // "ALMACEN", "URGENTE", "ESTANDAR"
    private double x;    // Coordenada relativa para el dibujo D3.js
    private double y;
}
