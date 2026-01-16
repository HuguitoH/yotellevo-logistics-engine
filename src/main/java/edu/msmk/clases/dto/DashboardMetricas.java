package edu.msmk.clases.dto;

import lombok.Builder;
import lombok.Data;

/**
 * DTO para las métricas del dashboard administrativo
 */
@Data
@Builder
public class DashboardMetricas {

    // Métricas de pedidos
    private Integer pedidosHoy;
    private Integer pedidosPendientes;

    // Métricas de cobertura
    private Double porcentajeCobertura;

    // Estado del sistema
    private String estado; // "OPERATIVO", "MANTENIMIENTO", etc.

    // Métricas opcionales
    private Double latenciaPromedio;
    private Double throughput;
}