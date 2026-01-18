package edu.msmk.clases.service.cobertura;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.similarity.LevenshteinDistance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@Slf4j
public class IndiceGeografico {

    @Autowired
    private NormalizacionService normalizacionService;

    @Autowired
    private MaestroProvincias maestroProvincias;

    private final Map<String, Integer> indiceMunicipios = new ConcurrentHashMap<>();
    private final Map<String, String> nombresMunicipiosVisualizacion = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Integer>> indiceVias = new ConcurrentHashMap<>();

    private final LevenshteinDistance levenshtein = new LevenshteinDistance(3);

    /**
     * Indexa un tramo completo
     */
    public void indexarTramo(int cpro, int cmum, String nombreMunicipio, int cvia, String nombreVia) {
        indexarMunicipio(cpro, cmum, nombreMunicipio);
        indexarVia(cpro, cmum, cvia, nombreVia);
    }

    public void indexarMunicipio(Integer cpro, Integer cmum, String nombreMunicipio) {
        if (nombreMunicipio == null) return;

        String nombreNormalizado = normalizacionService.normalizar(nombreMunicipio);
        indiceMunicipios.put(cpro + "_" + nombreNormalizado, cmum);
        nombresMunicipiosVisualizacion.put(cpro + "_" + cmum, nombreNormalizado);
    }

    public void indexarVia(Integer cpro, Integer cmum, Integer cvia, String nombreVia) {
        if (nombreVia == null) return;

        String claveMunicipio = cpro + "_" + cmum;
        String nombreNormalizado = normalizacionService.normalizar(nombreVia);

        indiceVias.computeIfAbsent(claveMunicipio, k -> new ConcurrentHashMap<>())
                .put(nombreNormalizado, cvia);
    }

    /**
     * Resuelve código de municipio (3 estrategias)
     */
    public Integer resolverCodigoMunicipio(Integer cpro, String municipio) {
        if (cpro == null || municipio == null) return null;

        String municipioNormalizado = normalizacionService.normalizar(municipio);
        String claveBusqueda = cpro + "_" + municipioNormalizado;

        // 1. Match exacto O(1)
        Integer cmum = indiceMunicipios.get(claveBusqueda);
        if (cmum != null) return cmum;

        // 2. Búsqueda por contenido (optimizada con for loop)
        String prefijo = cpro + "_";
        for (Map.Entry<String, Integer> entry : indiceMunicipios.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith(prefijo)) {
                String nombreIndexado = key.substring(prefijo.length());
                if (nombreIndexado.contains(municipioNormalizado) ||
                        municipioNormalizado.contains(nombreIndexado)) {
                    return entry.getValue();
                }
            }
        }

        return null;
    }

    public Integer buscarMunicipioSimilar(Integer cpro, String municipioNormalizado) {
        Integer mejor = null;
        int mejorDist = Integer.MAX_VALUE;
        String prefijo = cpro + "_";

        String municipioNorm = normalizacionService.normalizar(municipioNormalizado);

        for (Map.Entry<String, Integer> entry : indiceMunicipios.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith(prefijo)) {
                String nombreIndexado = key.substring(prefijo.length());

                // Fast path: contenido
                if (nombreIndexado.contains(municipioNorm) ||
                        municipioNorm.contains(nombreIndexado)) {
                    return entry.getValue();
                }

                // Slow path: Levenshtein (solo si no hay match)
                Integer dist = levenshtein.apply(municipioNorm, nombreIndexado);
                if (dist != null && dist < mejorDist && dist <= 3) {
                    mejorDist = dist;
                    mejor = entry.getValue();
                }
            }
        }
        return mejor;
    }

    public List<String> buscarMunicipiosPorQuery(String query, int limit) {
        String busqueda = normalizacionService.normalizar(query);

        // OPTIMIZACIÓN: Los nombres YA están normalizados, no normalizar de nuevo
        return nombresMunicipiosVisualizacion.values().stream()
                .filter(nombre -> nombre.contains(busqueda))
                .distinct()
                .sorted()
                .limit(limit)
                .collect(Collectors.toList());
    }

    public Integer resolverCodigoProvincia(String provincia) {
        return maestroProvincias.obtenerCodigo(provincia);
    }

    public Integer buscarViaExacta(Integer cpro, Integer cmum, String nombreVia) {
        String claveMunicipio = cpro + "_" + cmum;
        Map<String, Integer> vias = indiceVias.get(claveMunicipio);
        if (vias == null) return null;

        return vias.get(normalizacionService.normalizar(nombreVia));
    }

    public List<SugerenciaVia> obtenerSugerencias(Integer cpro, Integer cmum, String query, int limit) {
        Map<String, Integer> vias = indiceVias.get(cpro + "_" + cmum);
        if (vias == null) return Collections.emptyList();

        String queryNorm = normalizacionService.normalizar(query);
        List<SugerenciaVia> resultados = new ArrayList<>();

        // OPTIMIZACIÓN: Calcular Levenshtein UNA SOLA VEZ
        for (Map.Entry<String, Integer> entry : vias.entrySet()) {
            String nombreVia = entry.getKey();

            // Fast path: contiene
            if (nombreVia.contains(queryNorm)) {
                int maxLen = Math.max(queryNorm.length(), nombreVia.length());
                int sim = 100; // Match perfecto por contenido
                resultados.add(new SugerenciaVia(nombreVia, entry.getValue(), sim, 0));
                continue;
            }

            // Slow path: Levenshtein (UNA sola llamada)
            Integer dist = levenshtein.apply(queryNorm, nombreVia);
            if (dist != null && dist <= 3) {
                int maxLen = Math.max(queryNorm.length(), nombreVia.length());
                int sim = (maxLen == 0) ? 100 : (int) (((maxLen - dist) / (double) maxLen) * 100);
                resultados.add(new SugerenciaVia(nombreVia, entry.getValue(), sim, dist));
            }
        }

        // Ordenar y limitar
        return resultados.stream()
                .sorted(Comparator.comparingInt(SugerenciaVia::getDistancia))
                .limit(limit)
                .collect(Collectors.toList());
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