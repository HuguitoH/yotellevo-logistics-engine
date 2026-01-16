package edu.msmk.clases.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
public class Paquete {
    // Getters
    private final String id;
    private final String destinatario; // Cambiado de DTO a String
    private final Direccion direccion;  // Cambiado de PeticionCliente a tu nueva clase Direccion
    @Setter
    private Punto coordenadas;
    private final double peso;
    // Setters
    @Setter
    private EstadoPaquete estado;
    private final LocalDateTime fechaCreacion;
    @Setter
    private int ordenEntrega;
    @Setter
    private int prioridad;

    public enum EstadoPaquete {
        PENDIENTE, EN_ALMACEN, EN_FURGONETA, EN_RUTA, ENTREGADO, DEVUELTO
    }

    // Constructor corregido para coincidir con PedidosService
    public Paquete(String id, String destinatario, Direccion direccion,
                   Punto coordenadas, double peso, int prioridad) {
        this.id = id;
        this.destinatario = destinatario;
        this.direccion = direccion;
        this.coordenadas = coordenadas;
        this.peso = peso;
        this.estado = EstadoPaquete.PENDIENTE;
        this.fechaCreacion = LocalDateTime.now();
        this.prioridad = prioridad;
    }

    @Override
    public String toString() {
        String coords = (coordenadas != null) ?
                String.format("(%.4f, %.4f)", coordenadas.lat(), coordenadas.lon()) : // lat() y lon()
                "sin coords";

        return String.format("Paquete[id=%s, dest=%s, coords=%s, peso=%.2fkg, estado=%s, prioridad=%d]",
                id, destinatario, coords, peso, estado, prioridad);
    }
}
