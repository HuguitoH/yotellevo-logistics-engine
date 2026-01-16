package edu.msmk.clases.dto;

import lombok.Data;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@Data
public class PedidoRequest {

    @Valid
    @NotNull(message = "Los datos del contacto son obligatorios")
    private ContactoDTO contacto; // Cambiado de destinatario a contacto para coincidir con el controller

    @Valid
    @NotNull(message = "La dirección es obligatoria")
    private DireccionDTO direccion;

    private Double peso;
    private Integer prioridad;

    @Data
    public static class ContactoDTO {
        private String nombre;
        private String apellidos;
        private String correo;
        private String telefono;
    }

    @Data
    public static class DireccionDTO {
        private String provincia;
        private String municipio;
        private String tipoVia;
        private String nombreVia;
        private String numero;
        private String codigoPostal;
        private String piso;
        private String puerta;
        private String escalera;
    }
}