package edu.msmk.clases.controller;

import edu.msmk.clases.service.cobertura.IndiceGeografico;
import edu.msmk.clases.service.cobertura.MaestroProvincias;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Controller para búsquedas y sugerencias geográficas.
 *
 * Endpoints útiles para autocompletado en el frontend.
 */
@RestController
@RequestMapping("/api/geo")
@CrossOrigin(origins = "*")
@Slf4j
public class GeoController {

    @Autowired
    private IndiceGeografico indiceGeografico;

    @Autowired
    private MaestroProvincias maestroProvincias;

    /**
     * GET /api/geo/municipios?query=Mad
     *
     * Obtiene sugerencias de municipios según lo que el usuario escribe
     *
     * @param query Texto que el usuario está escribiendo (ej: "Mad", "Pozue")
     * @param limit Número máximo de sugerencias (default: 10)
     * @return Lista de nombres de municipios
     */
    @GetMapping("/municipios")
    public ResponseEntity<List<String>> buscarMunicipios(
            @RequestParam String query,
            @RequestParam(defaultValue = "10") int limit) {

        log.debug("Buscando municipios: query='{}', limit={}", query, limit);

        if (query == null || query.length() < 2) {
            return ResponseEntity.ok(List.of());
        }

        // Buscar municipios que contengan el query (case insensitive)
        List<String> sugerencias = indiceGeografico.buscarMunicipiosPorQuery(query, limit);

        log.debug("Encontradas {} sugerencias", sugerencias.size());

        return ResponseEntity.ok(sugerencias);
    }

    /**
     * GET /api/geo/provincias
     *
     * Obtiene la lista completa de las 52 provincias españolas
     *
     * @return Mapa con código y nombre de todas las provincias
     */
    @GetMapping("/provincias")
    public ResponseEntity<Map<Integer, String>> obtenerProvincias() {
        log.debug("Solicitando lista de provincias");
        Map<Integer, String> provincias = maestroProvincias.obtenerTodas();
        return ResponseEntity.ok(provincias);
    }

    /**
     * GET /api/geo/provincias/buscar?query=Mad
     *
     * Busca provincias que coincidan con el query
     *
     * @param query Texto de búsqueda
     * @return Mapa con provincias que coinciden
     */
    @GetMapping("/provincias/buscar")
    public ResponseEntity<Map<Integer, String>> buscarProvincias(
            @RequestParam String query) {
        log.debug("Buscando provincias: query='{}'", query);
        Map<Integer, String> resultados = maestroProvincias.buscarProvincias(query);
        return ResponseEntity.ok(resultados);
    }

    /**
     * GET /api/geo/vias?provincia=28&municipio=79&query=Alc
     *
     * Obtiene sugerencias de vías según provincia, municipio y query
     *
     * @param provincia Código o nombre de provincia
     * @param municipio Código o nombre de municipio
     * @param query Texto que el usuario está escribiendo
     * @param limit Número máximo de sugerencias
     * @return Lista de nombres de vías
     */
    @GetMapping("/vias")
    public ResponseEntity<List<ViaSugerenciaDTO>> buscarVias(
            @RequestParam String provincia,
            @RequestParam String municipio,
            @RequestParam String query,
            @RequestParam(defaultValue = "10") int limit) {

        log.debug("Buscando vías: prov={}, mun={}, query={}", provincia, municipio, query);

        if (query == null || query.length() < 2) {
            return ResponseEntity.ok(List.of());
        }

        try {
            // Resolver códigos usando MaestroProvincias
            Integer cpro = maestroProvincias.obtenerCodigo(provincia);
            Integer cmum = indiceGeografico.resolverCodigoMunicipio(cpro, municipio);

            if (cpro == null || cmum == null) {
                log.warn("No se pudo resolver provincia/municipio: {}/{}", provincia, municipio);
                return ResponseEntity.ok(List.of());
            }

            // Buscar vías con sugerencias
            var sugerencias = indiceGeografico.obtenerSugerencias(cpro, cmum, query, limit);

            List<ViaSugerenciaDTO> resultado = sugerencias.stream()
                    .map(s -> new ViaSugerenciaDTO(
                            s.getNombreVia(),
                            s.getCodigoVia(),
                            s.getSimilitud()
                    ))
                    .collect(Collectors.toList());

            log.debug("Encontradas {} vías", resultado.size());

            return ResponseEntity.ok(resultado);

        } catch (Exception e) {
            log.error("Error buscando vías: {}", e.getMessage());
            return ResponseEntity.ok(List.of());
        }
    }

    // DTO para sugerencias de vías
    @lombok.Data
    @lombok.AllArgsConstructor
    public static class ViaSugerenciaDTO {
        private String nombreVia;
        private Integer codigoVia;
        private Integer similitud; // Porcentaje 0-100
    }
}