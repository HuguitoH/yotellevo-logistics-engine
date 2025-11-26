package edu.msmk.clases.exchange;

/**
 * Representa una petición de un cliente solicitando validar una dirección COMPLETA
 * TODOS los campos son OBLIGATORIOS para entregas de paquetería
 */
public class PeticionCliente {
    private Integer provincia;           // CPRO: Código provincia (obligatorio)
    private Integer municipio;           // CMUM: Código municipio (obligatorio)
    private Integer unidadPoblacional;   // CUN: Código unidad poblacional (obligatorio)
    private Integer via;                 // CVIA: Código vía (obligatorio)
    private Integer numero;              // Número del portal (obligatorio)

    // Constructor vacío (necesario para Spring)
    public PeticionCliente() {
    }

    /**
     * Constructor con validación de campos obligatorios
     * @throws IllegalArgumentException si algún campo es null
     */
    public PeticionCliente(Integer provincia, Integer municipio, Integer unidadPoblacional,
                           Integer via, Integer numero) {
        // Validar que TODOS los campos estén presentes
        if (provincia == null) {
            throw new IllegalArgumentException("El código de provincia es obligatorio");
        }
        if (municipio == null) {
            throw new IllegalArgumentException("El código de municipio es obligatorio");
        }
        if (unidadPoblacional == null) {
            throw new IllegalArgumentException("El código de unidad poblacional es obligatorio");
        }
        if (via == null) {
            throw new IllegalArgumentException("El código de vía es obligatorio");
        }
        if (numero == null) {
            throw new IllegalArgumentException("El número de portal es obligatorio");
        }

        this.provincia = provincia;
        this.municipio = municipio;
        this.unidadPoblacional = unidadPoblacional;
        this.via = via;
        this.numero = numero;
    }

    /**
     * Valida que la petición esté completa
     * @return true si todos los campos están presentes
     */
    public boolean esValida() {
        return provincia != null
                && municipio != null
                && unidadPoblacional != null
                && via != null
                && numero != null;
    }

    // Getters
    public Integer getProvincia() {
        return provincia;
    }

    public Integer getMunicipio() {
        return municipio;
    }

    public Integer getUnidadPoblacional() {
        return unidadPoblacional;
    }

    public Integer getVia() {
        return via;
    }

    public Integer getNumero() {
        return numero;
    }

    // Setters (necesarios para Spring)
    public void setProvincia(Integer provincia) {
        this.provincia = provincia;
    }

    public void setMunicipio(Integer municipio) {
        this.municipio = municipio;
    }

    public void setUnidadPoblacional(Integer unidadPoblacional) {
        this.unidadPoblacional = unidadPoblacional;
    }

    public void setVia(Integer via) {
        this.via = via;
    }

    public void setNumero(Integer numero) {
        this.numero = numero;
    }

    /**
     * Genera una clave única para buscar en el HashSet
     * Formato: "PROV_MUN_CUN_VIA"
     * El número no se incluye en la clave porque representa un rango
     */
    public String getClave() {
        return String.format("%02d_%03d_%07d_%05d",
                provincia != null ? provincia : 0,
                municipio != null ? municipio : 0,
                unidadPoblacional != null ? unidadPoblacional : 0,
                via != null ? via : 0);
    }

    /**
     * Genera la dirección legible
     */
    public String getDireccionLegible() {
        return String.format("Provincia=%02d, Municipio=%03d, UnidadPobl=%07d, Via=%05d, Num=%d",
                provincia, municipio, unidadPoblacional, via, numero);
    }

    @Override
    public String toString() {
        return String.format("PeticionCliente[%s]", getDireccionLegible());
    }
}