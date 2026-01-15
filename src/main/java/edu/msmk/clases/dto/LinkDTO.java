package edu.msmk.clases.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LinkDTO {
    private String source; // Antes "desde"
    private String target; // Antes "hasta"
    private String label;  // Antes "etiqueta" (opcional para mostrar la distancia)
}