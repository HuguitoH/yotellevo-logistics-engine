package edu.msmk.clases.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO para visualización de la pila de paquetes en una furgoneta.
 *
 * Este DTO se usa para:
 * - Visualización 2D (CSS Grid tipo Tetris)
 * - Visualización 3D (Three.js)
 * - Dashboard administrativo
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PilaVisualizacionDTO {

    // Información de la furgoneta
    private String furgonetaId;
    private Integer capacidadMaxima;
    private Integer paquetesActuales;
    private Double pesoTotal;
    private Double porcentajeOcupacion;
    private Double distanciaTotal;

    // Lista de paquetes (ordenados de arriba a abajo de la pila)
    private List<PaqueteVisualDTO> paquetes;

    // Orden de descarga/entrega
    private List<String> ordenDescarga;

    // Mensaje opcional
    private String mensaje;

    /**
     * DTO de un paquete individual para visualización
     */
    @Data
    @Builder
    public static class PaqueteVisualDTO {

        // Información del paquete
        private String id;
        private String destinatario;
        private String direccion;
        private Double peso;
        private Integer prioridad;

        // Información de apilamiento
        private Integer ordenCarga;      // Orden en que se cargó (1, 2, 3...)
        private Double posicionZ;        // Altura en la pila (cm)

        // Información de visualización
        private String color;            // Color hex para el paquete
        private String etiqueta;         // Texto para mostrar en la caja
    }
}