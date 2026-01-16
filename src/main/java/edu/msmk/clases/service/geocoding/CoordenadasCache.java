package edu.msmk.clases.service.geocoding;

import edu.msmk.clases.model.Punto;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Caché persistente de coordenadas de Mapbox.
 *
 * CARACTERÍSTICAS:
 * - Persistencia en archivo JSON
 * - Se carga automáticamente al iniciar (@PostConstruct)
 * - Se guarda automáticamente al cerrar (@PreDestroy)
 * - Thread-safe con ConcurrentHashMap
 * - Estadísticas de hits/misses
 * - Auto-guardado periódico (cada X inserciones)
 */
@Component
@Slf4j
public class CoordenadasCache {

    // Caché en memoria
    private final Map<String, Punto> cache = new ConcurrentHashMap<>();

    // ObjectMapper para serialización JSON
    private final ObjectMapper objectMapper;

    // Ruta del archivo de caché
    private final Path cacheFile;

    // Estadísticas
    private final AtomicLong hits = new AtomicLong(0);
    private final AtomicLong misses = new AtomicLong(0);
    private final AtomicLong inserciones = new AtomicLong(0);

    // Auto-guardado cada N inserciones
    private static final long AUTO_SAVE_INTERVAL = 100;

    public CoordenadasCache() {
        this.objectMapper = new ObjectMapper();

        // Ruta del archivo de caché (en el directorio de trabajo)
        this.cacheFile = Paths.get("data", "mapbox-cache.json");

        // Crear directorio si no existe
        try {
            Files.createDirectories(cacheFile.getParent());
        } catch (IOException e) {
            log.warn("No se pudo crear directorio de caché: {}", e.getMessage());
        }
    }

    /**
     * Se ejecuta al iniciar la aplicación.
     * Carga el caché desde el archivo JSON.
     */
    @PostConstruct
    public void cargar() {
        if (!Files.exists(cacheFile)) {
            log.info("Archivo de caché no existe. Se creará al guardar.");
            return;
        }

        long inicio = System.currentTimeMillis();

        try {
            File file = cacheFile.toFile();
            TypeReference<Map<String, Punto>> typeRef = new TypeReference<>() {};
            Map<String, Punto> data = objectMapper.readValue(file, typeRef);

            cache.putAll(data);

            long duracion = System.currentTimeMillis() - inicio;
            log.info("Caché de coordenadas cargado: {} direcciones en {} ms",
                    cache.size(), duracion);

        } catch (IOException e) {
            log.error("Error cargando caché: {}", e.getMessage());
        }
    }

    /**
     * Se ejecuta al cerrar la aplicación.
     * Guarda el caché en el archivo JSON.
     */
    @PreDestroy
    public void guardar() {
        if (cache.isEmpty()) {
            log.info("Caché vacío, no hay nada que guardar");
            return;
        }

        long inicio = System.currentTimeMillis();

        try {
            File file = cacheFile.toFile();
            objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValue(file, cache);

            long duracion = System.currentTimeMillis() - inicio;
            log.info("Caché guardado: {} direcciones en {} ms",
                    cache.size(), duracion);

        } catch (IOException e) {
            log.error("Error guardando caché: {}", e.getMessage());
        }
    }

    /**
     * Obtiene coordenadas del caché.
     *
     * @param direccion Dirección completa (normalizada)
     * @return Optional con el Punto, o empty si no está en caché
     */
    public Optional<Punto> get(String direccion) {
        String key = normalizar(direccion);
        Punto punto = cache.get(key);

        if (punto != null) {
            hits.incrementAndGet();
            log.debug("Cache HIT: {}", direccion);
        } else {
            misses.incrementAndGet();
            log.debug("Cache MISS: {}", direccion);
        }

        return Optional.ofNullable(punto);
    }

    /**
     * Guarda coordenadas en el caché.
     *
     * @param direccion Dirección completa
     * @param punto Punto con coordenadas
     */
    public void put(String direccion, Punto punto) {
        String key = normalizar(direccion);
        cache.put(key, punto);

        long count = inserciones.incrementAndGet();
        log.debug("Guardado en caché: {} → ({}, {})",
                direccion, punto.lat(), punto.lon());

        // Auto-guardado periódico
        if (count % AUTO_SAVE_INTERVAL == 0) {
            log.debug("Auto-guardado de caché (cada {} inserciones)", AUTO_SAVE_INTERVAL);
            guardarAsync();
        }
    }

    /**
     * Verifica si una dirección está en el caché.
     */
    public boolean contains(String direccion) {
        String key = normalizar(direccion);
        return cache.containsKey(key);
    }

    /**
     * Elimina una entrada del caché.
     */
    public void remove(String direccion) {
        String key = normalizar(direccion);
        cache.remove(key);
        log.debug("🗑️ Eliminado del caché: {}", direccion);
    }

    /**
     * Limpia todo el caché.
     */
    public void limpiar() {
        cache.clear();
        hits.set(0);
        misses.set(0);
        inserciones.set(0);
        log.info("Caché de coordenadas limpiado");
    }

    /**
     * Guarda el caché de forma asíncrona (sin bloquear).
     */
    private void guardarAsync() {
        new Thread(() -> {
            try {
                guardar();
            } catch (Exception e) {
                log.warn("Error en auto-guardado asíncrono: {}", e.getMessage());
            }
        }).start();
    }

    /**
     * Normaliza una dirección para usarla como clave.
     * Convierte a mayúsculas y elimina espacios extras.
     */
    private String normalizar(String direccion) {
        if (direccion == null) return "";
        return direccion.toUpperCase()
                .trim()
                .replaceAll("\\s+", " ");
    }

    /**
     * Obtiene el tamaño actual del caché.
     */
    public int size() {
        return cache.size();
    }

    /**
     * Obtiene estadísticas del caché.
     */
    public CacheEstadisticas obtenerEstadisticas() {
        long totalAccesos = hits.get() + misses.get();
        double hitRate = totalAccesos > 0
                ? (hits.get() * 100.0 / totalAccesos)
                : 0.0;

        return CacheEstadisticas.builder()
                .tamaño(cache.size())
                .hits(hits.get())
                .misses(misses.get())
                .hitRate(hitRate)
                .inserciones(inserciones.get())
                .archivoCache(cacheFile.toString())
                .build();
    }

    /**
     * Fuerza el guardado del caché (útil para testing o mantenimiento).
     */
    public void forzarGuardado() {
        log.info("Forzando guardado manual del caché");
        guardar();
    }

    // ========== CLASE INTERNA ==========

    /**
     * DTO para estadísticas del caché
     */
    @lombok.Data
    @lombok.Builder
    public static class CacheEstadisticas {
        private Integer tamaño;
        private Long hits;
        private Long misses;
        private Double hitRate; // Porcentaje de aciertos
        private Long inserciones;
        private String archivoCache;
    }
}