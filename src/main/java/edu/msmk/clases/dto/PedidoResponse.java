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
    // Campos básicos de control
    private String pedidoId;
    private String estado;
    private String mensaje;
    private Boolean cobertura;
    private Integer ordenEntrega;
    private Double distanciaTotal;
    private String tiempoEstimado;
    private Object rutaGeoJson;   // JSON para el mapa de Mapbox

    // Referencias a los DTOs actualizados
    private CoordenadasDTO coordenadas;

    // CAMBIADO: Ahora usa el nuevo GraphDTO (el de los nombres en inglés)
    private GraphDTO grafoTeorico;

    private String tiempoProcesamiento;
}