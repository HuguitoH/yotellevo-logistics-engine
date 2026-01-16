package edu.msmk.clases.service.cobertura;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.similarity.LevenshteinDistance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Índice geográfico optimizado para búsquedas logísticas.
 * * MEJORAS:
 * - Estructura de datos optimizada para evitar split() masivos.
 * - Limpieza de caracteres especiales avanzada.
 * - Estadísticas corregidas.
 */
@Service
@Slf4j
public class IndiceGeografico {

    @Autowired
    private MaestroProvincias maestroProvincias;

    // Mapa: "CPRO_NOMBRE" -> CMUM (Para búsqueda rápida por nombre)
    private final Map<String, Integer> indiceMunicipios = new ConcurrentHashMap<>();

    // Mapa: "CPRO_CMUM" -> "NOMBRE ORIGINAL" (Para autocompletado)
    private final Map<String, String> nombresMunicipiosVisualizacion = new ConcurrentHashMap<>();

    // Mapa: "CPRO_CMUM" -> { "NOMBRE_VIA" -> CVIA }
    private final Map<String, Map<String, Integer>> indiceVias = new ConcurrentHashMap<>();

    private final LevenshteinDistance levenshtein = new LevenshteinDistance(3);

    /**
     * Indexa un tramo completo (Municipio y Vía)
     */
    public void indexarTramo(int cpro, int cmum, String nombreMunicipio, int cvia, String nombreVia) {
        indexarMunicipio(cpro, cmum, nombreMunicipio);
        indexarVia(cpro, cmum, cvia, nombreVia);
    }

    public void indexarMunicipio(Integer cpro, Integer cmum, String nombreMunicipio) {
        if (nombreMunicipio == null) return;
        // Forzamos limpieza total al guardar
        String nombreNormalizado = normalizar(nombreMunicipio);

        // Guardamos: "28_POZUELO DE ALARCON"
        indiceMunicipios.put(cpro + "_" + nombreNormalizado, cmum);

        // Guardamos para el autocompletado del front
        nombresMunicipiosVisualizacion.put(cpro + "_" + cmum, nombreNormalizado);
    }



    public void indexarVia(Integer cpro, Integer cmum, Integer cvia, String nombreVia) {
        if (nombreVia == null) return;
        String claveMunicipio = cpro + "_" + cmum;
        String nombreNormalizado = normalizar(nombreVia);

        indiceVias.computeIfAbsent(claveMunicipio, k -> new ConcurrentHashMap<>())
                .put(nombreNormalizado, cvia);
    }

    /**
     * Resuelve código de municipio con 3 estrategias (Exacta, Contiene, Difusa)
     */

    public Integer resolverCodigoMunicipio(Integer cpro, String municipio) {
        if (cpro == null || municipio == null) return null;

        String municipioNormalizado = normalizar(municipio);
        String claveBusqueda = cpro + "_" + municipioNormalizado;

        // 1. Intento exacto
        Integer cmum = indiceMunicipios.get(claveBusqueda);
        if (cmum != null) return cmum;

        // 2. Si falla, buscar por "Contiene" (Muy útil para Pozuelo de Alarcón vs Pozuelo)
        return indiceMunicipios.entrySet().stream()
                .filter(e -> e.getKey().startsWith(cpro + "_"))
                .filter(e -> e.getKey().contains(municipioNormalizado) || municipioNormalizado.contains(e.getKey().substring(e.getKey().indexOf("_")+1)))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }



    public Integer buscarMunicipioSimilar(Integer cpro, String municipioNormalizado) {
        Integer mejor = null;
        int mejorDist = Integer.MAX_VALUE;
        String prefijo = cpro + "_";

        for (Map.Entry<String, Integer> entry : indiceMunicipios.entrySet()) {
            if (entry.getKey().startsWith(prefijo)) {
                String nombreIndexado = entry.getKey().substring(prefijo.length());

                // Probar si uno contiene al otro (Ej: "Pozuelo" en "Pozuelo de Alarcón")
                if (nombreIndexado.contains(municipioNormalizado) || municipioNormalizado.contains(nombreIndexado)) {
                    return entry.getValue();
                }

                // Probar Levenshtein
                Integer dist = levenshtein.apply(municipioNormalizado, nombreIndexado);
                if (dist != null && dist < mejorDist && dist <= 3) {
                    mejorDist = dist;
                    mejor = entry.getValue();
                }
            }
        }
        return mejor;
    }

    public List<String> buscarMunicipiosPorQuery(String query, int limit) {
        String busqueda = normalizar(query);
        return nombresMunicipiosVisualizacion.values().stream()
                .filter(nombre -> normalizar(nombre).contains(busqueda))
                .distinct()
                .sorted()
                .limit(limit)
                .collect(Collectors.toList());
    }

    public Integer resolverCodigoProvincia(String provincia) {
        return maestroProvincias.obtenerCodigo(provincia);
    }

    // Solo necesitamos este para que el Parser haga su magia
    public Integer buscarViaExacta(Integer cpro, Integer cmum, String nombreVia) {
        String claveMunicipio = cpro + "_" + cmum;
        Map<String, Integer> vias = indiceVias.get(claveMunicipio);
        if (vias == null) return null;

        // IMPORTANTE: Aquí normalizamos la entrada para comparar con lo indexado
        return vias.get(normalizar(nombreVia));
    }

    public List<SugerenciaVia> obtenerSugerencias(Integer cpro, Integer cmum, String query, int limit) {
        Map<String, Integer> vias = indiceVias.get(cpro + "_" + cmum);
        if (vias == null) return Collections.emptyList();

        String queryNorm = normalizar(query);

        return vias.entrySet().stream()
                .filter(e -> e.getKey().contains(queryNorm) ||
                        (levenshtein.apply(queryNorm, e.getKey()) != null &&
                                levenshtein.apply(queryNorm, e.getKey()) <= 3))
                .map(e -> {
                    Integer dist = levenshtein.apply(queryNorm, e.getKey());
                    int d = (dist == null) ? 99 : dist;

                    // Calcular similitud porcentual
                    int maxLen = Math.max(queryNorm.length(), e.getKey().length());
                    int sim = (maxLen == 0) ? 100 : (int) (((maxLen - d) / (double) maxLen) * 100);

                    return new SugerenciaVia(e.getKey(), e.getValue(), sim, d);
                })
                .sorted(Comparator.comparingInt(SugerenciaVia::getDistancia))
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * Normalización avanzada para evitar fallos por tildes o caracteres especiales
     */
    private String normalizar(String texto) {
        if (texto == null) return "";
        return java.text.Normalizer.normalize(texto, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "") // Quita acentos
                .toUpperCase()
                .replaceAll("[^A-Z0-9 ]", "") // Quita TODO lo que no sea letra, número o espacio
                .trim()
                .replaceAll("\\s+", " "); // Colapsa múltiples espacios en uno solo
    }

    public Map<String, Object> obtenerEstadisticas() {
        return Map.of(
                "municipios", indiceMunicipios.size(),
                "vias", indiceVias.size(),
                "visualizaciones", nombresMunicipiosVisualizacion.size()
        );
    }



    @lombok.Data
    @lombok.AllArgsConstructor
    public static class SugerenciaVia {
        private String nombreVia;
        private Integer codigoVia;
        private Integer similitud;
        private Integer distancia;
    }
}