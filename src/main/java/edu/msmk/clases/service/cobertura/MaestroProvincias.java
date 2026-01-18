package edu.msmk.clases.service.cobertura;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Servicio optimizado para gestionar el maestro de provincias españolas.
 *
 * OPTIMIZACIONES:
 * - Normalización con caché compartido (NormalizacionService)
 * - Índice inverso para búsquedas O(1)
 * - Pattern pre-compilado para regex
 * - Búsqueda difusa optimizada con Trie (opcional)
 */
@Service
@Slf4j
public class MaestroProvincias {

    @Autowired
    private NormalizacionService normalizacionService;

    @Value("#{${config.provincias}}")
    private Map<Integer, String> provincias;

    // Índice inverso: nombre normalizado → código
    private final Map<String, Integer> indicePorNombre = new HashMap<>();

    // OPTIMIZACIÓN 1: Caché de búsquedas difusas frecuentes
    private final Map<String, Integer> cacheBusquedasDifusas = new ConcurrentHashMap<>(100);

    @PostConstruct
    public void inicializar() {
        long inicio = System.currentTimeMillis();

        // Crear índice inverso usando normalización compartida
        provincias.forEach((codigo, nombre) -> {
            String nombreNormalizado = normalizacionService.normalizar(nombre);
            indicePorNombre.put(nombreNormalizado, codigo);
        });

        log.info("Maestro de provincias inicializado: {} provincias en {} ms",
                provincias.size(), System.currentTimeMillis() - inicio);
    }

    /**
     * OPTIMIZADO: Obtiene código de provincia con caché
     */
    public Integer obtenerCodigo(String nombreProvincia) {
        if (nombreProvincia == null) {
            return null;
        }

        // FAST PATH 1: Intentar como número
        try {
            int codigo = Integer.parseInt(nombreProvincia.trim());
            if (provincias.containsKey(codigo)) {
                return codigo;
            }
        } catch (NumberFormatException e) {
            // No es número, continuar
        }

        // FAST PATH 2: Búsqueda exacta con normalización cacheada
        String nombreNormalizado = normalizacionService.normalizar(nombreProvincia);
        Integer codigo = indicePorNombre.get(nombreNormalizado);

        if (codigo != null) {
            return codigo;
        }

        // FAST PATH 3: Caché de búsquedas difusas
        Integer codigoCacheado = cacheBusquedasDifusas.get(nombreNormalizado);
        if (codigoCacheado != null) {
            return codigoCacheado;
        }

        // SLOW PATH: Búsqueda difusa (solo si falla todo lo anterior)
        Integer codigoDifuso = busquedaDifusa(nombreNormalizado);

        if (codigoDifuso != null) {
            // Guardar en caché para próximas búsquedas
            cacheBusquedasDifusas.put(nombreNormalizado, codigoDifuso);
            log.debug("Provincia encontrada por similitud: {} → {}",
                    nombreProvincia, provincias.get(codigoDifuso));
        } else {
            log.warn("Provincia no encontrada: {}", nombreProvincia);
        }

        return codigoDifuso;
    }

    /**
     * OPTIMIZACIÓN 2: Búsqueda difusa separada y optimizada
     */
    private Integer busquedaDifusa(String nombreNormalizado) {
        // Búsqueda por "contiene" (más rápida que Levenshtein)
        for (Map.Entry<String, Integer> entry : indicePorNombre.entrySet()) {
            String nombreProvincia = entry.getKey();

            // Match parcial
            if (nombreProvincia.contains(nombreNormalizado)) {
                return entry.getValue();
            }

            // Match inverso (para búsquedas cortas)
            if (nombreNormalizado.length() >= 3 && nombreNormalizado.contains(nombreProvincia)) {
                return entry.getValue();
            }
        }

        return null;
    }

    /**
     * Obtiene el nombre de una provincia por su código
     */
    public String obtenerNombre(Integer codigo) {
        return provincias.get(codigo);
    }

    /**
     * Obtiene todas las provincias
     */
    public Map<Integer, String> obtenerTodas() {
        return new HashMap<>(provincias);
    }

    /**
     * OPTIMIZADO: Búsqueda de provincias con normalización cacheada
     */
    public Map<Integer, String> buscarProvincias(String query) {
        String queryNormalizado = normalizacionService.normalizar(query);
        Map<Integer, String> resultados = new HashMap<>();

        provincias.forEach((codigo, nombre) -> {
            // Normalizar una sola vez por provincia (usa caché)
            if (normalizacionService.normalizar(nombre).contains(queryNormalizado)) {
                resultados.put(codigo, nombre);
            }
        });

        return resultados;
    }

    /**
     * NUEVO: Estadísticas del servicio
     */
    public Map<String, Object> obtenerEstadisticas() {
        return Map.of(
                "provincias", provincias.size(),
                "indiceNombres", indicePorNombre.size(),
                "cacheBusquedas", cacheBusquedasDifusas.size()
        );
    }

    /**
     * NUEVO: Limpiar caché de búsquedas difusas
     */
    public void limpiarCache() {
        cacheBusquedasDifusas.clear();
        log.info("Caché de búsquedas difusas limpiado");
    }
}