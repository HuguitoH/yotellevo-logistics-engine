package edu.msmk.clases.model;

import lombok.Getter;
import lombok.ToString;

/**
 * Representa un punto geográfico con coordenadas
 */
@Getter
@ToString
public class Punto {
    private final double latitud;
    private final double longitud;
    private final String descripcion;

    public Punto(double latitud, double longitud, String descripcion) {
        this.latitud = latitud;
        this.longitud = longitud;
        this.descripcion = descripcion;
    }

    public Punto(double latitud, double longitud) {
        this(latitud, longitud, "");
    }

    /**
     * Calcula la distancia en kilómetros a otro punto usando la fórmula de Haversine
     * Esta fórmula calcula la distancia más corta entre dos puntos en una esfera
     *
     * @param otro Punto destino
     * @return Distancia en kilómetros
     */
    public double distanciaHaversine(Punto otro) {
        final double RADIO_TIERRA_KM = 6371.0;

        // Convertir grados a radianes
        double lat1Rad = Math.toRadians(this.latitud);
        double lat2Rad = Math.toRadians(otro.latitud);
        double deltaLatRad = Math.toRadians(otro.latitud - this.latitud);
        double deltaLonRad = Math.toRadians(otro.longitud - this.longitud);

        // Fórmula de Haversine
        double a = Math.sin(deltaLatRad / 2) * Math.sin(deltaLatRad / 2) +
                Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                        Math.sin(deltaLonRad / 2) * Math.sin(deltaLonRad / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return RADIO_TIERRA_KM * c;
    }

    /**
     * Calcula la distancia euclidiana aproximada (más rápida pero menos precisa)
     * Útil para distancias cortas (<100km)
     */
    public double distanciaEuclidiana(Punto otro) {
        double deltaLat = otro.latitud - this.latitud;
        double deltaLon = otro.longitud - this.longitud;

        // Aproximación: 1 grado ≈ 111 km
        double distanciaKm = Math.sqrt(deltaLat * deltaLat + deltaLon * deltaLon) * 111.0;
        return distanciaKm;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Punto punto = (Punto) o;
        return Double.compare(punto.latitud, latitud) == 0 &&
                Double.compare(punto.longitud, longitud) == 0;
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(latitud, longitud);
    }
}