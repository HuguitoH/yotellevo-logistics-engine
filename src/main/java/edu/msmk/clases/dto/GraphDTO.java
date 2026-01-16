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

    /**
     * Nodo del grafo (almacén o punto de entrega)
     */
    @Data
    @Builder
    public static class NodoDTO {
        private String id;
        private String tipo;        // "ALMACEN" o "ENTREGA"
        private Double lat;
        private Double lon;
        private String etiqueta;    // Para mostrar en el grafo
    }

    /**
     * Enlace entre dos nodos
     */
    @Data
    @Builder
    public static class LinkDTO {
        private String source;      // ID del nodo origen
        private String target;      // ID del nodo destino
        private Double distancia;   // En kilómetros
    }
}