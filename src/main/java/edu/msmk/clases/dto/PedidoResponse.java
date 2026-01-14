package edu.msmk.clases.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PedidoResponse {
    private String pedidoId;
    private String estado;
    private Boolean cobertura;
    private String mensaje;
    private CoordenadasDTO coordenadas;
    private Integer ordenEntrega;
    private Double distanciaTotal;
    private String tiempoEstimado;

    @Data
    @Builder
    public static class CoordenadasDTO {
        private Double latitud;
        private Double longitud;
    }
}