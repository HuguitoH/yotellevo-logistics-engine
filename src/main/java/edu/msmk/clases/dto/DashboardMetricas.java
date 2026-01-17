package edu.msmk.clases.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para las métricas del dashboard administrativo
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DashboardMetricas {

    private Long empresasIndexadas;

    // Métricas de pedidos
    private Integer pedidosHoy;
    private Integer pedidosPendientes;

    // Métricas de cobertura y eficiencia (AÑADIDOS PARA EL ALGORITMO)
    private Double porcentajeCobertura;
    private Double distanciaTotal;      // Nuevo: km totales de la ruta
    private Double ahorroOptimizado;    // Nuevo: % ahorro respecto a ruta original

    // Estado del sistema
    private String estado; // "OPERATIVO", "MANTENIMIENTO", etc.

    // Métricas técnicas
    private Double latenciaPromedio;
    private Double throughput;
}