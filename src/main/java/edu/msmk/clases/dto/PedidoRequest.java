package edu.msmk.clases.dto;

import lombok.Data;

@Data
public class PedidoRequest {
    // DATOS PERSONALES
    private DestinatarioDTO destinatario;

    // DIRECCIÓN COMPLETA
    private DireccionDTO direccion;

    // DATOS DEL ENVÍO
    private Double peso;
    private Integer prioridad;  // 1=urgente, 2=normal, 3=economico

    @Data
    public static class DestinatarioDTO {
        private String nombre;
        private String apellidos;
        private String correo;
        private String telefono;
    }

    @Data
    public static class DireccionDTO {
        private String provincia;      // "Álava" o "01"
        private String municipio;      // "ALEGRIA-DULANTZI" o "001"
        private String tipoVia;        // "CALLE", "AVENIDA", etc.
        private String nombreVia;      // "AÑUA BIDEA"
        private String numero;         // "8"
        private String codigoPostal;   // "01002"

        // Opcional (si el usuario añade más detalles)
        private String piso;
        private String puerta;
        private String escalera;
    }
}