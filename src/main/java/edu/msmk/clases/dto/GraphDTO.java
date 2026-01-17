package edu.msmk.clases.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

/**
 * DTO para representación del grafo de entregas
 * Compatible con librerías de visualización como D3.js, Cytoscape, etc.
 */
@Data
@Builder
public class GraphDTO {
    private List<NodoDTO> nodes;
    private List<LinkDTO> links;
    private String mensaje;

    @Data
    @Builder
    public static class NodoDTO {
        private String id;
        private String tipo;
        private Double lat;
        private Double lon;
        // Crucial para la estabilidad de la simulación en el Frontend
        private Double x;
        private Double y;
        private String etiqueta;
        private String estado;
    }

    @Data
    @Builder
    public static class LinkDTO {
        private String source;
        private String target;
        private Double distancia;
        private String label; // Para mostrar "2.5 km" sobre la línea
    }
}