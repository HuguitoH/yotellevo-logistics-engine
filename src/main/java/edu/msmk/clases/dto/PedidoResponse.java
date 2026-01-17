package edu.msmk.clases.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PedidoResponse {
    private String pedidoId;
    private String estado;
    private String mensaje;

    // CAMBIO: De Boolean (Objeto) a boolean (primitivo) para evitar nulls
    @Builder.Default
    private boolean cobertura = false;

    private Integer ordenEntrega;
    private Double distanciaTotal;
    private String tiempoEstimado;
    private Object rutaGeoJson;
    private String furgonetaId;
    private CoordenadasDTO coordenadas;
    private GraphDTO grafoTeorico;
    private String tiempoProcesamiento;
}