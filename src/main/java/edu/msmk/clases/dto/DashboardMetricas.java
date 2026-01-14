package edu.msmk.clases.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Metricas para el dashboard
 */
@Data
@Builder
public class DashboardMetricas {
    private Long throughput;  // Peticiones/segundo
    private Integer pedidosHoy;
    private Integer pedidosPendientes;
    private Double porcentajeCobertura;
    private Double latenciaPromedio;  // microsegundos

    private RutaOptimizadaDTO rutaOptimizada;
    private List<PedidoDTO> pedidosPendientesList;

    @Data
    @Builder
    public static class RutaOptimizadaDTO {
        private Double distanciaTotal;
        private Integer numeroPaquetes;
        private String algoritmo;
        private Long tiempoCalculo;
        private List<PuntoRutaDTO> puntos;
    }

    @Data
    @Builder
    public static class PuntoRutaDTO {
        private Integer orden;
        private String paqueteId;
        private Double latitud;
        private Double longitud;
        private Double distancia;
    }

    @Data
    @Builder
    public static class PedidoDTO {
        private String id;
        private String destinatario;
        private Integer prioridad;
        private Integer ordenEntrega;
        private String estado;
    }
}