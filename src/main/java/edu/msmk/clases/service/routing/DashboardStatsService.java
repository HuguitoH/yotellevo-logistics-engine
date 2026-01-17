package edu.msmk.clases.service.routing;

import edu.msmk.clases.dto.DashboardMetricas;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Service;
@Data
@Builder
@Service
@AllArgsConstructor // Genera el constructor de todos los campos (el que fallaba)
@NoArgsConstructor
public class DashboardStatsService {
    // Almacenamos el último DTO generado
    private DashboardMetricas metricasActuales = DashboardMetricas.builder()
            .empresasIndexadas(0L)
            .pedidosHoy(0)
            .pedidosPendientes(0)
            .porcentajeCobertura(0.0)
            .distanciaTotal(0.0)
            .ahorroOptimizado(0.0)
            .estado("ALMACEN")
            .latenciaPromedio(0.0)
            .throughput(0.0)
            .build();

    // Método para que los servicios actualicen datos
    public synchronized void actualizarRuta(Double distancia, Double ahorro) {
        metricasActuales.setDistanciaTotal(distancia);
        metricasActuales.setAhorroOptimizado(ahorro);
    }

    public synchronized void incrementarPedido(boolean esPendiente) {
        metricasActuales.setPedidosHoy(metricasActuales.getPedidosHoy() + 1);
        if (esPendiente) {
            metricasActuales.setPedidosPendientes(metricasActuales.getPedidosPendientes() + 1);
        }
        actualizarCobertura();
    }

    private void actualizarCobertura() {
        // Cálculo: (Pedidos Procesados / Pedidos Hoy) * 100
        if (metricasActuales.getPedidosHoy() > 0) {
            double cobertura = ((double) (metricasActuales.getPedidosHoy() - metricasActuales.getPedidosPendientes())
                    / metricasActuales.getPedidosHoy()) * 100;
            metricasActuales.setPorcentajeCobertura(cobertura);
        }
    }

    public DashboardMetricas getMetricas() {
        return metricasActuales;
    }
}
