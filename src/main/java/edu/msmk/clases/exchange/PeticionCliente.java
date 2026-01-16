package edu.msmk.clases.exchange;

import lombok.Getter;
import lombok.Setter;

/**
 * Representa una petición de un cliente solicitando validar una dirección COMPLETA
 */
@Getter
@Setter
public class PeticionCliente {
    private Integer provincia;
    private Integer municipio;
    private String nombreMunicipio;
    private Integer unidadPoblacional;
    private Integer via;
    private Integer numero;
    private String codigoPostalOficial;

    public PeticionCliente() {
    }

    public PeticionCliente(Integer provincia, Integer municipio, Integer unidadPoblacional,
                           Integer via, Integer numero) {
        if (provincia == null || municipio == null || unidadPoblacional == null || via == null || numero == null) {
            throw new IllegalArgumentException("Todos los campos geográficos son obligatorios");
        }

        this.provincia = provincia;
        this.municipio = municipio;
        this.unidadPoblacional = unidadPoblacional;
        this.via = via;
        this.numero = numero;
    }

    // Constructor extendido para incluir el CP oficial tras validación
    public PeticionCliente(Integer provincia, Integer municipio, Integer unidadPoblacional,
                           Integer via, Integer numero, String codigoPostalOficial) {
        this(provincia, municipio, unidadPoblacional, via, numero);
        this.codigoPostalOficial = codigoPostalOficial;
    }

    public boolean esValida() {
        return provincia != null && municipio != null && unidadPoblacional != null
                && via != null && numero != null;
    }


    public String getClave() {
        // Genera: 28_115_0_2492
        return this.provincia + "_" + this.municipio + "_" +
                (this.unidadPoblacional != null ? this.unidadPoblacional : 0) + "_" +
                (this.via != null ? this.via : 0);
    }

    @Override
    public String toString() {
        return String.format("PeticionCliente[Prov=%02d, Mun=%03d, Via=%05d, Num=%d, CP_Oficial=%s]",
                provincia, municipio, via, numero, codigoPostalOficial);
    }
}
