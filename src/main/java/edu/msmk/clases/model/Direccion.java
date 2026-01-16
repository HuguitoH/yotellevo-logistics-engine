package edu.msmk.clases.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Direccion {
    private String provincia;
    private String municipio;
    private String tipoVia;
    private String nombreVia;
    private String numero;
    private String codigoPostal;
    private String piso;      // Opcionales
    private String puerta;
    private String escalera;

    /**
     * Método de utilidad para obtener la dirección lista para Google Maps o Mapbox
     */
    public String getDireccionCompleta() {
        return String.format("%s %s, %s, %s %s",
                tipoVia, nombreVia, numero, codigoPostal, municipio);
    }
}
