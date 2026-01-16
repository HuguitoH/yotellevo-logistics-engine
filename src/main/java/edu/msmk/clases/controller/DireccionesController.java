package edu.msmk.clases.controller;

import edu.msmk.clases.service.cobertura.IndiceGeografico;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Controller para autocompletado y sugerencias de direcciones.
 *
 * Endpoints útiles para el frontend:
 * - Sugerencias de vías mientras el usuario escribe
 * - Validación en tiempo real
 */
@RestController
@RequestMapping("/api/v1/direcciones")
@CrossOrigin(origins = "*")
@Slf4j
public class DireccionesController {

    @Autowired
    private IndiceGeografico indiceGeografico;

    /**
     * GET /api/v1/direcciones/sugerencias
     *
     * Obtiene sugerencias de vías según lo que el usuario va escribiendo
     *
     * Ejemplo: GET /api/v1/direcciones/sugerencias?provincia=28&municipio=79&query=alcal
     *
     * @param provincia Código de provincia (ej: 28 para Madrid)
     * @param municipio Código de municipio (ej: 79 para Madrid capital)
     * @param query Texto que el usuario está escribiendo
     * @param limit Número máximo de sugerencias (default: 10)
     * @return Lista de sugerencias ordenadas por similitud
     */
    @GetMapping("/sugerencias")
    public ResponseEntity<SugerenciasResponse> obtenerSugerencias(
            @RequestParam Integer provincia,
            @RequestParam Integer municipio,
            @RequestParam String query,
            @RequestParam(defaultValue = "10") Integer limit) {

        log.debug("Buscando sugerencias: provincia={}, municipio={}, query='{}'",
                provincia, municipio, query);

        if (query == null || query.length() < 2) {
            return ResponseEntity.ok(SugerenciasResponse.builder()
                    .sugerencias(List.of())
                    .mensaje("Query debe tener al menos 2 caracteres")
                    .build());
        }

        List<IndiceGeografico.SugerenciaVia> sugerencias =
                indiceGeografico.obtenerSugerencias(provincia, municipio, query, limit);

        // Convertir a DTO
        List<SugerenciaDTO> sugerenciasDTO = sugerencias.stream()
                .map(s -> SugerenciaDTO.builder()
                        .nombreVia(s.getNombreVia())
                        .codigoVia(s.getCodigoVia())
                        .similitud(s.getSimilitud())
                        .build())
                .collect(Collectors.toList());

        log.debug("Encontradas {} sugerencias", sugerenciasDTO.size());

        return ResponseEntity.ok(SugerenciasResponse.builder()
                .sugerencias(sugerenciasDTO)
                .total(sugerenciasDTO.size())
                .build());
    }

    // ========== DTOs ==========

    @lombok.Data
    @lombok.Builder
    public static class SugerenciasResponse {
        private List<SugerenciaDTO> sugerencias;
        private Integer total;
        private String mensaje;
    }

    @lombok.Data
    @lombok.Builder
    public static class SugerenciaDTO {
        private String nombreVia;
        private Integer codigoVia;
        private Integer similitud; // Porcentaje 0-100
    }
}