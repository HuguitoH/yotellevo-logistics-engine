package edu.msmk.clases.model;

import java.util.Objects;

/**
 * Representa un punto geográfico con coordenadas
 */
public record Punto(double lat, double lon, String descripcion) {

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
        double lat1Rad = Math.toRadians(this.lat);
        double lat2Rad = Math.toRadians(otro.lat);
        double deltaLatRad = Math.toRadians(otro.lat - this.lat);
        double deltaLonRad = Math.toRadians(otro.lon - this.lon);

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
        double deltaLat = otro.lat - this.lat;
        double deltaLon = otro.lon - this.lon;

        // Aproximación: 1 grado ≈ 111 km
        return Math.sqrt(deltaLat * deltaLat + deltaLon * deltaLon) * 111.0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Punto punto = (Punto) o;
        return Double.compare(punto.lat, lat) == 0 &&
                Double.compare(punto.lon, lon) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(lat, lon);
    }
}