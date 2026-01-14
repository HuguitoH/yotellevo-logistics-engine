package edu.msmk.clases.model;

import java.time.LocalDateTime;

public class Paquete {
    private String id;
    private String destinatario; // Cambiado de DTO a String
    private Direccion direccion;  // Cambiado de PeticionCliente a tu nueva clase Direccion
    private Punto coordenadas;
    private double peso;
    private EstadoPaquete estado;
    private LocalDateTime fechaCreacion;
    private int ordenEntrega;
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

    // Getters
    public String getId() { return id; }
    public String getDestinatario() { return destinatario; }
    public Direccion getDireccion() { return direccion; }
    public Punto getCoordenadas() { return coordenadas; }
    public double getPeso() { return peso; }
    public EstadoPaquete getEstado() { return estado; }
    public int getOrdenEntrega() { return ordenEntrega; }
    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public int getPrioridad() { return prioridad; }

    // Setters
    public void setEstado(EstadoPaquete estado) { this.estado = estado; }
    public void setOrdenEntrega(int orden) { this.ordenEntrega = orden; }
    public void setCoordenadas(Punto coordenadas) { this.coordenadas = coordenadas; }
    public void setPrioridad(int prioridad) { this.prioridad = prioridad; }

    @Override
    public String toString() {
        String coords = (coordenadas != null) ?
                String.format("(%.4f, %.4f)", coordenadas.getLatitud(), coordenadas.getLongitud()) :
                "sin coords";

        return String.format("Paquete[id=%s, dest=%s, coords=%s, peso=%.2fkg, estado=%s, prioridad=%d]",
                id, destinatario, coords, peso, estado, prioridad);
    }
}
