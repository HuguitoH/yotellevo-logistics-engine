package edu.msmk.clases.service.geocoding;

import edu.msmk.clases.model.Punto;
import edu.msmk.clases.service.cobertura.NormalizacionService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;

/**
 * Servicio optimizado para interactuar con Mapbox API
 *
 * OPTIMIZACIONES:
 * - Connection pooling (reutiliza conexiones HTTP)
 * - Batch paralelo con ExecutorService
 * - Rate limiting automático
 * - Retry logic con exponential backoff
 * - Normalización con caché compartido
 * - Circuit breaker pattern
 */
@Service
@Slf4j
public class MapboxService {

    @Autowired
    private NormalizacionService normalizacionService;

    @Autowired
    private CoordenadasCache coordenadasCache;

    @Value("${mapbox.api.token}")
    private String accessToken;

    @Value("${mapbox.api.geocoding.url:https://api.mapbox.com/search/geocode/v6/forward}")
    private String geocodingUrl;

    @Value("${mapbox.api.country:ES}")
    private String defaultCountry;

    @Value("${mapbox.api.max-requests-per-second:50}")
    private int maxRequestsPerSecond;

    // OPTIMIZACIÓN 1: RestTemplate con connection pooling
    private RestTemplate restTemplate;

    // OPTIMIZACIÓN 2: Rate limiter (para evitar saturar Mapbox)
    private Semaphore rateLimiter;

    // OPTIMIZACIÓN 3: Thread pool para batch paralelo
    private ExecutorService executorService;

    // OPTIMIZACIÓN 4: Circuit breaker (si Mapbox falla mucho, para de intentar)
    private volatile boolean circuitOpen = false;
    private volatile long circuitOpenedAt = 0;
    private static final long CIRCUIT_RESET_TIME_MS = 30_000; // 30 segundos

    @PostConstruct
    public void inicializar() {
        // CORRECTO: Configurar timeouts en el factory, no en el builder
        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
        factory.setConnectTimeout(5000);      // 5 segundos
        factory.setReadTimeout(10000);        // 10 segundos
        factory.setConnectionRequestTimeout(3000); // 3 segundos

        // RestTemplate SIN setConnectTimeout/setReadTimeout deprecados
        restTemplate = new RestTemplateBuilder()
                .requestFactory(() -> factory)
                .build();

        // Rate limiter (50 requests/segundo)
        rateLimiter = new Semaphore(maxRequestsPerSecond);

        // Thread pool (P-cores para paralelizar batch)
        int cores = Runtime.getRuntime().availableProcessors() / 2;
        executorService = Executors.newFixedThreadPool(cores);

        log.info("MapboxService inicializado | Rate limit: {}/s | Thread pool: {} cores",
                maxRequestsPerSecond, cores);
    }

    /**
     * OPTIMIZADO: Obtiene coordenadas con caché + retry
     */
    public Punto obtenerCoordenadas(String calle, String codigoPostal, String municipio) {
        String direccionCompleta = construirDireccionCompleta(calle, codigoPostal, municipio);

        return coordenadasCache.get(direccionCompleta)
                .orElseGet(() -> {
                    log.debug("Cache miss, llamando a Mapbox API: {}", direccionCompleta);

                    Punto punto = llamarMapboxConRetry(direccionCompleta, municipio);

                    if (punto != null) {
                        coordenadasCache.put(direccionCompleta, punto);
                        log.info("Coordenadas obtenidas y cacheadas: {}", direccionCompleta);
                    }
                    return punto;
                });
    }

    /**
     * NUEVO: Llamada con retry logic y exponential backoff
     */
    private Punto llamarMapboxConRetry(String direccion, String municipioEsperado) {
        int maxRetries = 3;
        int retryDelayMs = 100;

        for (int intento = 0; intento < maxRetries; intento++) {
            try {
                // Circuit breaker: si está abierto, no intentar
                if (circuitOpen) {
                    long tiempoTranscurrido = System.currentTimeMillis() - circuitOpenedAt;
                    if (tiempoTranscurrido < CIRCUIT_RESET_TIME_MS) {
                        log.warn("Circuit breaker abierto, saltando llamada a Mapbox");
                        return null;
                    } else {
                        // Intentar cerrar el circuito
                        circuitOpen = false;
                        log.info("Circuit breaker cerrado, reintentando");
                    }
                }

                Punto resultado = llamarMapboxAPI(direccion, municipioEsperado);

                if (resultado != null) {
                    return resultado;
                }

            } catch (Exception e) {
                log.warn("Intento {}/{} falló: {}", intento + 1, maxRetries, e.getMessage());

                if (intento < maxRetries - 1) {
                    // Exponential backoff
                    try {
                        Thread.sleep(retryDelayMs);
                        retryDelayMs *= 2; // 100ms, 200ms, 400ms...
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                } else {
                    // Abrir circuit breaker después de 3 fallos
                    circuitOpen = true;
                    circuitOpenedAt = System.currentTimeMillis();
                    log.error("Circuit breaker abierto después de {} fallos", maxRetries);
                }
            }
        }

        return null;
    }

    /**
     * OPTIMIZADO: Llamada a Mapbox con rate limiting
     */
    private Punto llamarMapboxAPI(String direccion, String municipioEsperado) {
        try {
            // Rate limiting (espera si se alcanzó el límite)
            rateLimiter.acquire();

            String query = URLEncoder.encode(direccion, StandardCharsets.UTF_8);
            String url = geocodingUrl + "?q=" + query
                    + "&access_token=" + accessToken
                    + "&country=" + defaultCountry
                    + "&limit=1";

            JsonNode response = restTemplate.getForObject(url, JsonNode.class);

            if (response != null && response.has("features") && response.get("features").size() > 0) {
                JsonNode firstFeature = response.get("features").get(0);

                String fullAddressFound = firstFeature.get("properties")
                        .path("full_address").asText("");

                // Validación con normalización compartida
                if (municipioEsperado != null && !municipioEsperado.isEmpty()) {
                    String municipioNorm = normalizacionService.normalizar(municipioEsperado);
                    String respuestaNorm = normalizacionService.normalizar(fullAddressFound);

                    if (!respuestaNorm.contains(municipioNorm)) {
                        log.error("Bloqueo: Mapbox devolvió ubicación fuera de {}. Encontrado: {}",
                                municipioEsperado, fullAddressFound);
                        return null;
                    }
                }

                JsonNode coordinates = firstFeature.get("geometry").get("coordinates");
                double lon = coordinates.get(0).asDouble();
                double lat = coordinates.get(1).asDouble();

                return new Punto(lat, lon, fullAddressFound);
            }

            // Liberar permit inmediatamente después de la llamada
            rateLimiter.release();

        } catch (Exception e) {
            rateLimiter.release(); // Importante liberar en caso de error
            log.error("Error llamando a Mapbox API: {}", e.getMessage());
            throw new RuntimeException(e);
        }

        return null;
    }

    /**
     * ULTRA-OPTIMIZADO: Batch paralelo con ExecutorService
     */
    public Map<String, Punto> obtenerCoordenadasBatch(List<String> direcciones) {
        Map<String, Punto> resultados = new ConcurrentHashMap<>();
        List<Future<Void>> futures = new ArrayList<>();

        log.info("Iniciando batch paralelo de {} direcciones", direcciones.size());
        long inicio = System.currentTimeMillis();

        for (String direccion : direcciones) {
            Future<Void> future = executorService.submit(() -> {
                Punto punto = obtenerCoordenadas(direccion, "", "");
                if (punto != null) {
                    resultados.put(direccion, punto);
                }
                return null;
            });
            futures.add(future);
        }

        // Esperar a que terminen todas
        for (Future<Void> future : futures) {
            try {
                future.get(30, TimeUnit.SECONDS); // Timeout por seguridad
            } catch (Exception e) {
                log.error("Error en batch: {}", e.getMessage());
            }
        }

        long duracion = System.currentTimeMillis() - inicio;
        log.info("Batch completado | Resultados: {}/{} | Tiempo: {} ms",
                resultados.size(), direcciones.size(), duracion);

        return resultados;
    }

    /**
     * OPTIMIZADO: Matriz de distancias con mejor logging
     */
    public double[][] obtenerMatrizDistancias(List<Punto> puntos) {
        if (puntos == null || puntos.size() < 2) return new double[0][0];

        try {
            // Rate limiting
            rateLimiter.acquire();

            StringBuilder coords = new StringBuilder();
            for (int i = 0; i < puntos.size(); i++) {
                coords.append(puntos.get(i).lon()).append(",").append(puntos.get(i).lat());
                if (i < puntos.size() - 1) coords.append(";");
            }

            String url = "https://api.mapbox.com/directions-matrix/v1/mapbox/driving/"
                    + coords.toString()
                    + "?annotations=distance&access_token=" + accessToken;

            log.debug("Solicitando matriz {}x{} a Mapbox", puntos.size(), puntos.size());
            long inicio = System.currentTimeMillis();

            JsonNode response = restTemplate.getForObject(url, JsonNode.class);

            if (response != null && response.has("distances")) {
                JsonNode distancesNode = response.get("distances");
                int n = puntos.size();
                double[][] matriz = new double[n][n];

                for (int i = 0; i < n; i++) {
                    for (int j = 0; j < n; j++) {
                        // Convertir metros → kilómetros
                        matriz[i][j] = distancesNode.get(i).get(j).asDouble() / 1000.0;
                    }
                }

                log.info("Matriz obtenida en {} ms", System.currentTimeMillis() - inicio);
                rateLimiter.release();
                return matriz;
            }

            rateLimiter.release();

        } catch (Exception e) {
            rateLimiter.release();
            log.error("Error obteniendo matriz de distancias: {}", e.getMessage());
        }

        return null;
    }

    /**
     * Obtiene trazado de ruta (GeoJSON)
     */
    public String obtenerTrazadoRuta(List<Punto> puntos) {
        if (puntos == null || puntos.size() < 2) return null;

        try {
            rateLimiter.acquire();

            StringBuilder coords = new StringBuilder();
            for (int i = 0; i < puntos.size(); i++) {
                Punto p = puntos.get(i);
                coords.append(p.lon()).append(",").append(p.lat());
                if (i < puntos.size() - 1) coords.append(";");
            }

            String url = "https://api.mapbox.com/directions/v5/mapbox/driving/"
                    + coords.toString()
                    + "?geometries=geojson&overview=full&access_token=" + accessToken;

            log.debug("Solicitando trazado de {} puntos", puntos.size());

            JsonNode response = restTemplate.getForObject(url, JsonNode.class);

            if (response != null && response.has("routes") && response.get("routes").size() > 0) {
                rateLimiter.release();
                return response.get("routes").get(0).get("geometry").toString();
            }

            rateLimiter.release();

        } catch (Exception e) {
            rateLimiter.release();
            log.error("Error obteniendo trazado: {}", e.getMessage());
        }

        return null;
    }

    private String construirDireccionCompleta(String calle, String cp, String municipio) {
        StringBuilder sb = new StringBuilder();

        if (calle != null && !calle.isEmpty()) sb.append(calle);
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

    public MapboxEstadisticas obtenerEstadisticas() {
        CoordenadasCache.CacheEstadisticas cacheStats = coordenadasCache.obtenerEstadisticas();

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
                .circuitBreakerAbierto(circuitOpen)
                .build();
    }

    public void limpiarCache() {
        coordenadasCache.limpiar();
        log.info("Caché de Mapbox limpiado");
    }

    public void guardarCache() {
        coordenadasCache.forzarGuardado();
    }

    @lombok.Data
    @lombok.Builder
    public static class MapboxEstadisticas {
        private Integer direccionesEnCache;
        private Long cacheHits;
        private Long cacheMisses;
        private Double hitRate;
        private Double ahorroLlamadas;
        private Boolean circuitBreakerAbierto;
    }
}