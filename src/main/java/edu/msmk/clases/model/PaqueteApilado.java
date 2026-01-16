package edu.msmk.clases.model;

import lombok.Data;

/**
 * Representa un paquete con información de su posición en la pila
 */
@Data
public class PaqueteApilado {

    private final Paquete paquete;
    private final int ordenCarga;      // Orden en que se cargó (1, 2, 3...)
    private final double posicionZ;     // Altura en la pila (cm)

    // Dimensiones estándar de caja (cm)
    public static final double ALTURA_CAJA = 30.0;
    public static final double ANCHO_CAJA = 40.0;
    public static final double PROFUNDIDAD_CAJA = 30.0;

    /**
     * Obtiene el color para visualización según prioridad
     */
    public String getColorHex() {
        return switch (paquete.getPrioridad()) {
            case 1 -> "#F44336"; // Rojo (urgente)
            case 2 -> "#4CAF50"; // Verde (normal)
            case 3 -> "#2196F3"; // Azul (económico)
            default -> "#9E9E9E"; // Gris
        };
    }

    /**
     * Genera etiqueta para visualización
     */
    public String getEtiqueta() {
        return String.format("#%d - %s", ordenCarga, paquete.getId());
    }

    /**
     * Obtiene la altura de la caja
     */
    public double getAltura() {
        return ALTURA_CAJA;
    }
}