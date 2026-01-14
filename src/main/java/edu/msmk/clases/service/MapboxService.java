package edu.msmk.clases.service;

import edu.msmk.clases.model.Punto;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;

@Slf4j
@Service
public class MapboxService {

    // Inyecta el token de application.properties
    @Value("${mapbox.access_token}")
    private String accessToken;

    // Usamos RestTemplate para hacer las peticiones HTTP
    private final RestTemplate restTemplate = new RestTemplate();

    private final String GEOCODING_URL = "api.mapbox.com";
    private final String DIRECTIONS_URL = "api.mapbox.com";

    /**
     * Obtiene coordenadas reales de Mapbox para una dirección.
     */
    public Punto obtenerCoordenadas(String direccion) {
        log.info("Llamando a Mapbox Geocoding para: {}", direccion);
        try {
            String url = UriComponentsBuilder.fromHttpUrl(GEOCODING_URL + direccion + ".json")
                    .queryParam("access_token", accessToken)
                    .queryParam("limit", 1)
                    .queryParam("country", "ES") // Filtra solo por España
                    .build()
                    .toUriString();

            JsonNode response = restTemplate.getForObject(url, JsonNode.class);

            if (response != null && response.has("features") && response.get("features").size() > 0) {
                JsonNode coordinates = response.get("features").get(0).get("geometry").get("coordinates");
                double lon = coordinates.get(0).asDouble(); // Mapbox devuelve [lon, lat]
                double lat = coordinates.get(1).asDouble();
                log.info("Coordenadas reales obtenidas: {}, {}", lat, lon);
                return new Punto(lat, lon, direccion);
            }
        } catch (Exception e) {
            log.error("Error al obtener coordenadas de Mapbox: {}", e.getMessage());
        }
        // Devuelve null si falla, para que el servicio use la aproximación
        return null;
    }

    /**
     * Obtiene la ruta real de carretera entre puntos en formato GeoJSON.
     */
    public String obtenerRutaOptimizadaGeoJson(List<Punto> rutaOptimizada) {
        if (rutaOptimizada == null || rutaOptimizada.size() < 2) return null;
        log.info("Llamando a Mapbox Directions para {} puntos.", rutaOptimizada.size());

        try {
            // Formato de puntos: longitud,latitud;longitud,latitud;...
            StringBuilder puntosStr = new StringBuilder();
            for (Punto p : rutaOptimizada) {
                puntosStr.append(p.getLongitud()).append(",").append(p.getLatitud()).append(";");
            }
            puntosStr.deleteCharAt(puntosStr.length() - 1); // Elimina el último ";"

            String url = UriComponentsBuilder.fromHttpUrl(DIRECTIONS_URL + puntosStr.toString())
                    .queryParam("access_token", accessToken)
                    .queryParam("geometries", "geojson")
                    .queryParam("overview", "full")
                    .build()
                    .toUriString();

            JsonNode response = restTemplate.getForObject(url, JsonNode.class);

            if (response != null && response.has("routes")) {
                // Devuelve solo la geometría de la primera ruta como String
                return response.get("routes").get(0).get("geometry").toString();
            }
        } catch (Exception e) {
            log.error("Error al obtener ruta de Mapbox: {}", e.getMessage());
        }
        return null;
    }
}
