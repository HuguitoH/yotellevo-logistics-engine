package edu.msmk.clases.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;
import edu.msmk.clases.model.Paquete;

@Data
@Builder
public class DashboardResponse {
    private DashboardMetricas metricas;
    private GraphDTO grafo;
    private List<Paquete> listaCarga; // Para la "Pila de carga" visual
    private String trazadoMapa; // El GeoJSON de Mapbox
}