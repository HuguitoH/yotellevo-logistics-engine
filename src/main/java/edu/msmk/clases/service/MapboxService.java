package edu.msmk.clases.service;

import edu.msmk.clases.model.Punto;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class MapboxService {

    @Value("${mapbox.api.token}")
    private String accessToken;

    @Value("${mapbox.api.geocoding.url}")
    private String geocodingUrl;

    @Value("${mapbox.api.directions.url}")
    private String directionsUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    // --- NUEVA CACHÉ EN MEMORIA ---
    // Guardamos la dirección completa como clave y el objeto Punto como valor
    private final Map<String, Punto> cacheCoordenadas = new ConcurrentHashMap<>();

    /**
     * Obtiene coordenadas con lógica de caché
     */
    public Punto obtenerCoordenadas(String calleConNumero, String cp, String municipio) {
        String direccionCompleta = String.format("%s, %s, %s", calleConNumero, cp, municipio).toUpperCase();

        // 1. Intentar recuperar de la caché
        if (cacheCoordenadas.containsKey(direccionCompleta)) {
            log.info("HIT DE CACHÉ: Recuperando coordenadas para {}", direccionCompleta);
            return cacheCoordenadas.get(direccionCompleta);
        }

        log.info("LLAMADA API: Buscando en Mapbox para {}", direccionCompleta);
        try {
            String url = UriComponentsBuilder.fromHttpUrl(geocodingUrl)
                    .queryParam("q", direccionCompleta)
                    .queryParam("access_token", accessToken)
                    .queryParam("types", "address")
                    .queryParam("limit", 1)
                    .queryParam("country", "ES")
                    .queryParam("language", "es")
                    .build()
                    .toUriString();

            JsonNode response = restTemplate.getForObject(url, JsonNode.class);

            if (response != null && response.has("features") && !response.get("features").isEmpty()) {
                JsonNode feature = response.get("features").get(0);
                JsonNode coordinates = feature.get("geometry").get("coordinates");

                double lon = coordinates.get(0).asDouble();
                double lat = coordinates.get(1).asDouble();

                Punto punto = new Punto(lat, lon, direccionCompleta);

                // 2. Guardar en la caché para la próxima vez
                cacheCoordenadas.put(direccionCompleta, punto);

                return punto;
            }
        } catch (Exception e) {
            log.error("Error al obtener coordenadas de Mapbox: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Obtiene la ruta real (Directions)
     */
    public JsonNode obtenerRutaOptimizadaGeoJson(List<Punto> rutaOptimizada) {
        // Podríamos cachear rutas también, pero como cambian cada vez que
        // añades un paquete a la furgoneta, es mejor dejarlo dinámico.
        if (rutaOptimizada == null || rutaOptimizada.size() < 2) return null;

        try {
            StringBuilder puntosStr = new StringBuilder();
            for (Punto p : rutaOptimizada) {
                puntosStr.append(p.getLongitud()).append(",").append(p.getLatitud()).append(";");
            }
            puntosStr.deleteCharAt(puntosStr.length() - 1);

            String url = UriComponentsBuilder.fromHttpUrl(directionsUrl)
                    .pathSegment(puntosStr.toString())
                    .queryParam("access_token", accessToken)
                    .queryParam("geometries", "geojson")
                    .queryParam("overview", "full")
                    .build()
                    .toUriString();

            JsonNode response = restTemplate.getForObject(url, JsonNode.class);
            if (response != null && response.has("routes") && !response.get("routes").isEmpty()) {
                return response.get("routes").get(0).get("geometry");
            }

        } catch (Exception e) {
            log.error("Error al obtener ruta de Mapbox: {}", e.getMessage());
        }
        return null;
    }

    // Método extra para el Dashboard: ver cuántas direcciones tenemos cache Merged
    public int getTamanoCache() {
        return cacheCoordenadas.size();
    }
}