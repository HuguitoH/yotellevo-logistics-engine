package edu.msmk.clases.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@lombok.EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Paquete {

    @lombok.EqualsAndHashCode.Include
    private final String id;

    private final String destinatario;
    private final Direccion direccion;

    @Setter
    private Punto coordenadas;
    private final double peso;

    @lombok.EqualsAndHashCode.Include
    @Setter
    private EstadoPaquete estado;

    private final LocalDateTime fechaCreacion;

    @Setter
    private int ordenEntrega;

    @lombok.EqualsAndHashCode.Include
    @Setter
    private int prioridad;



    public enum EstadoPaquete {
        PENDIENTE, EN_ALMACEN, EN_FURGONETA, EN_RUTA, ENTREGADO, DEVUELTO, EN_ESPERA
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
        this.ordenEntrega = 0;
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
