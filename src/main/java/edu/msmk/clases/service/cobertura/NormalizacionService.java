package edu.msmk.clases.service.cobertura;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Service
public class NormalizacionService {

    // ConcurrentHashMap (sin lock global)
    private final Map<String, String> cacheNormalizacion = new ConcurrentHashMap<>(5000);

    // Control de tamaño aproximado (no exacto, pero evita lock)
    private static final int MAX_CACHE_SIZE = 5000;

    private static final Pattern PATTERN_ACENTOS = Pattern.compile("\\p{M}");
    private static final Pattern PATTERN_NO_ALFANUM = Pattern.compile("[^A-Z0-9 ]");
    private static final Pattern PATTERN_ESPACIOS = Pattern.compile("\\s+");

    /**
     * Normalización con caché compartido SIN contención
     */
    public String normalizar(String texto) {
        if (texto == null || texto.isEmpty()) return "";

        // Buscar en caché (lock-free read)
        String cached = cacheNormalizacion.get(texto);
        if (cached != null) return cached;

        // Normalizar (costoso)
        String resultado = java.text.Normalizer.normalize(texto, java.text.Normalizer.Form.NFD);
        resultado = PATTERN_ACENTOS.matcher(resultado).replaceAll("");
        resultado = resultado.toUpperCase().trim();
        resultado = PATTERN_NO_ALFANUM.matcher(resultado).replaceAll("");
        resultado = PATTERN_ESPACIOS.matcher(resultado).replaceAll(" ");

        // Guardar en caché (putIfAbsent para evitar sobrescribir)
        // Solo limitamos tamaño de forma aproximada para evitar locks
        if (cacheNormalizacion.size() < MAX_CACHE_SIZE) {
            cacheNormalizacion.putIfAbsent(texto, resultado);
        }

        return resultado;
    }

    /**
     * Estadísticas del caché
     */
    public int getCacheSize() {
        return cacheNormalizacion.size();
    }
}