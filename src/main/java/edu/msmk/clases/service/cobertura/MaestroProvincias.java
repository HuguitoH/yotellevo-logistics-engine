package edu.msmk.clases.service.cobertura;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

/**
 * Servicio para gestionar el maestro de provincias españolas.
 * Lee las 52 provincias desde application.properties
 */
@Service
@Slf4j
public class MaestroProvincias {

    @Value("#{${config.provincias}}")
    private Map<Integer, String> provincias;

    // Índice inverso: nombre → código
    private final Map<String, Integer> indicePorNombre = new HashMap<>();

    @PostConstruct
    public void inicializar() {
        // Crear índice inverso para búsquedas rápidas
        provincias.forEach((codigo, nombre) -> {
            String nombreNormalizado = normalizar(nombre);
            indicePorNombre.put(nombreNormalizado, codigo);
        });

        log.info("✅ Maestro de provincias inicializado: {} provincias", provincias.size());
    }

    /**
     * Obtiene el código de provincia a partir del nombre
     *
     * @param nombreProvincia Nombre de la provincia (ej: "Madrid", "Barcelona")
     * @return Código de provincia (ej: 28, 8) o null si no se encuentra
     */
    public Integer obtenerCodigo(String nombreProvincia) {
        if (nombreProvincia == null) {
            return null;
        }

        // Intentar como número primero
        try {
            int codigo = Integer.parseInt(nombreProvincia.trim());
            if (provincias.containsKey(codigo)) {
                return codigo;
            }
        } catch (NumberFormatException e) {
            // No es número, continuar
        }

        // Buscar por nombre normalizado
        String nombreNormalizado = normalizar(nombreProvincia);
        Integer codigo = indicePorNombre.get(nombreNormalizado);

        if (codigo != null) {
            return codigo;
        }

        // Búsqueda difusa (por si hay typos)
        for (Map.Entry<String, Integer> entry : indicePorNombre.entrySet()) {
            if (entry.getKey().contains(nombreNormalizado) ||
                    nombreNormalizado.contains(entry.getKey())) {
                log.debug("🔍 Provincia encontrada por similitud: {} → {}",
                        nombreProvincia, provincias.get(entry.getValue()));
                return entry.getValue();
            }
        }

        log.warn("⚠️ Provincia no encontrada: {}", nombreProvincia);
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
     * Busca provincias que coincidan con el query
     */
    public Map<Integer, String> buscarProvincias(String query) {
        String queryNormalizado = normalizar(query);
        Map<Integer, String> resultados = new HashMap<>();

        provincias.forEach((codigo, nombre) -> {
            if (normalizar(nombre).contains(queryNormalizado)) {
                resultados.put(codigo, nombre);
            }
        });

        return resultados;
    }

    /**
     * Normaliza texto para comparaciones
     */
    private String normalizar(String texto) {
        if (texto == null) return "";
        return texto.toUpperCase()
                .trim()
                .replaceAll("\\s+", " ");
    }
}