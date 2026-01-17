package edu.msmk.clases.service.geocoding;

import edu.msmk.clases.model.Punto;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Servicio para interactuar con la API de Mapbox.
 *
 * MEJORAS:
 * - Integración completa con CoordenadasCache persistente
 * - Manejo de errores mejorado
 * - Rate limiting awareness
 * - Logs detallados
 */
@Service
@Slf4j
public class MapboxService {

    @Value("${mapbox.api.token}")
    private String accessToken;

    @Value("${mapbox.api.geocoding.url:https://api.mapbox.com/search/geocode/v6/forward}")
    private String geocodingUrl;

    @Value("${mapbox.api.country:ES}")
    private String defaultCountry;

    @Autowired
    private CoordenadasCache coordenadasCache;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Obtiene coordenadas para una dirección.
     * Usa caché primero, si no lo encuentra llama a Mapbox.
     *
     * @param calle Nombre de la calle y número
     * @param codigoPostal Código postal
     * @param municipio Nombre del municipio
     * @return Punto con coordenadas, o null si no se encuentra
     */
    public Punto obtenerCoordenadas(String calle, String codigoPostal, String municipio) {
        String direccionCompleta = construirDireccionCompleta(calle, codigoPostal, municipio);

        return coordenadasCache.get(direccionCompleta)
                .orElseGet(() -> {
                    log.debug("No está en caché, llamando a Mapbox API");

                    // CAMBIO AQUÍ: Pasamos 'municipio' como segundo argumento
                    Punto punto = llamarMapboxAPI(direccionCompleta, municipio);

                    if (punto != null) {
                        coordenadasCache.put(direccionCompleta, punto);
                        log.info("Coordenadas obtenidas y guardadas en caché: {}", direccionCompleta);
                    }
                    return punto;
                });
    }

    /**
     * Llama a la API de Geocoding v6 de Mapbox.
     */
    // 1. Cambiamos la firma para recibir el municipio esperado
    private Punto llamarMapboxAPI(String direccion, String municipioEsperado) {
        try {
            String query = URLEncoder.encode(direccion, StandardCharsets.UTF_8);
            String url = geocodingUrl + "?q=" + query + "&access_token=" + accessToken + "&country=" + defaultCountry + "&limit=1";

            JsonNode response = restTemplate.getForObject(url, JsonNode.class);

            if (response != null && response.has("features") && response.get("features").size() > 0) {
                JsonNode firstFeature = response.get("features").get(0);

                String fullAddressFound = "";
                if (firstFeature.has("properties")) {
                    fullAddressFound = firstFeature.get("properties").path("full_address").asText();
                }

                // --- VALIDACIÓN FLEXIBLE ---
                if (municipioEsperado != null) {
                    String municipioNormalizado = normalizarTexto(municipioEsperado);
                    String respuestaNormalizada = normalizarTexto(fullAddressFound);

                    if (!respuestaNormalizada.contains(municipioNormalizado)) {
                        log.error("BLOQUEO DE SEGURIDAD: Mapbox devolvió una ubicación fuera de {}. Encontrado: {}",
                                municipioEsperado, fullAddressFound);
                        return null;
                    }
                }
                // ---------------------------

                JsonNode coordinates = firstFeature.get("geometry").get("coordinates");
                double lon = coordinates.get(0).asDouble();
                double lat = coordinates.get(1).asDouble();

                return new Punto(lat, lon, fullAddressFound);
            }
        } catch (Exception e) {
            log.error("Error llamando a Mapbox API: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Obtiene coordenadas para múltiples direcciones (batch).
     * Útil para optimizar llamadas.
     */
    public Map<String, Punto> obtenerCoordenadasBatch(List<String> direcciones) {
        Map<String, Punto> resultados = new HashMap<>();

        for (String direccion : direcciones) {
            Punto punto = obtenerCoordenadas(direccion, "", "");
            if (punto != null) {
                resultados.put(direccion, punto);
            }
        }

        return resultados;
    }

    /**
     * Construye una dirección completa para geocodificación.
     */
    private String construirDireccionCompleta(String calle, String cp, String municipio) {
        StringBuilder sb = new StringBuilder();

        if (calle != null && !calle.isEmpty()) {
            sb.append(calle);
        }

        if (cp != null && !cp.isEmpty()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(cp);
        }

        if (municipio != null && !municipio.isEmpty()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(municipio);
        }

        return sb.toString();
    }

    /**
     * Obtiene estadísticas del uso de la API y caché.
     */
    public MapboxEstadisticas obtenerEstadisticas() {
        CoordenadasCache.CacheEstadisticas cacheStats =
                coordenadasCache.obtenerEstadisticas();

        long totalPeticiones = cacheStats.getHits() + cacheStats.getMisses();
        double ahorroLlamadas = totalPeticiones > 0
                ? (cacheStats.getHits() * 100.0 / totalPeticiones)
                : 0.0;

        return MapboxEstadisticas.builder()
                .direccionesEnCache(cacheStats.getTamaño())
                .cacheHits(cacheStats.getHits())
                .cacheMisses(cacheStats.getMisses())
                .hitRate(cacheStats.getHitRate())
                .ahorroLlamadas(ahorroLlamadas)
                .build();
    }

    /**
     * Limpia el caché (útil para mantenimiento).
     */
    public void limpiarCache() {
        coordenadasCache.limpiar();
        log.info("Caché de Mapbox limpiado");
    }

    /**
     * Fuerza el guardado del caché.
     */
    public void guardarCache() {
        coordenadasCache.forzarGuardado();
    }

    /**
     * Obtiene el trazado de la ruta (GeoJSON) uniendo varios puntos por carretera.
     */
    public String obtenerTrazadoRuta(List<Punto> puntos) {
        if (puntos == null || puntos.size() < 2) return null;

        try {
            // 1. Formatear coordenadas: lon,lat;lon,lat;lon,lat
            StringBuilder coords = new StringBuilder();
            for (int i = 0; i < puntos.size(); i++) {
                Punto p = puntos.get(i);
                coords.append(p.lon()).append(",").append(p.lat());
                if (i < puntos.size() - 1) coords.append(";");
            }

            // 2. Construir URL de Directions API (v5)
            // Usamos overview=full y geometries=geojson para que el frontend pueda pintarlo
            String url = "https://api.mapbox.com/directions/v5/mapbox/driving/"
                    + coords.toString()
                    + "?geometries=geojson&overview=full&access_token=" + accessToken;

            log.debug("Solicitando trazado de ruta: {} puntos", puntos.size());

            JsonNode response = restTemplate.getForObject(url, JsonNode.class);

            if (response != null && response.has("routes") && response.get("routes").size() > 0) {
                // Extraemos la geometría de la primera ruta encontrada
                return response.get("routes").get(0).get("geometry").toString();
            }

        } catch (Exception e) {
            log.error("Error obteniendo trazado de ruta Mapbox: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Obtiene la matriz de distancias reales (en metros) entre una lista de puntos.
     * Útil para alimentar algoritmos de optimización (TSP).
     */
    public double[][] obtenerMatrizDistancias(List<Punto> puntos) {
        if (puntos == null || puntos.size() < 2) return new double[0][0];

        try {
            // 1. Formatear coordenadas: lon,lat;lon,lat...
            StringBuilder coords = new StringBuilder();
            for (int i = 0; i < puntos.size(); i++) {
                coords.append(puntos.get(i).lon()).append(",").append(puntos.get(i).lat());
                if (i < puntos.size() - 1) coords.append(";");
            }

            // 2. Llamar a Matrix API (v1)
            // Pedimos solo 'durations' o 'distances'. En este caso 'distances' (en metros).
            String url = "https://api.mapbox.com/directions-matrix/v1/mapbox/driving/"
                    + coords.toString()
                    + "?annotations=distance&access_token=" + accessToken;

            log.debug("Solicitando Matriz de Distancias Mapbox para {} puntos", puntos.size());
            JsonNode response = restTemplate.getForObject(url, JsonNode.class);

            if (response != null && response.has("distances")) {
                JsonNode distancesNode = response.get("distances");
                int n = puntos.size();
                double[][] matriz = new double[n][n];

                for (int i = 0; i < n; i++) {
                    for (int j = 0; j < n; j++) {
                        // Convertimos de metros (Mapbox) a kilómetros (tu sistema)
                        matriz[i][j] = distancesNode.get(i).get(j).asDouble() / 1000.0;
                    }
                }
                return matriz;
            }
        } catch (Exception e) {
            log.error("Error obteniendo matriz de distancias Mapbox: {}", e.getMessage());
        }
        return null;
    }

    private String normalizarTexto(String texto) {
        if (texto == null) return "";
        return java.text.Normalizer.normalize(texto, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .toLowerCase()
                .trim();
    }

    // ========== CLASES INTERNAS ==========

    @lombok.Data
    @lombok.Builder
    public static class MapboxEstadisticas {
        private Integer direccionesEnCache;
        private Long cacheHits;
        private Long cacheMisses;
        private Double hitRate;
        private Double ahorroLlamadas; // % de llamadas evitadas
    }
}