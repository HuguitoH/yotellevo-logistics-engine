package edu.msmk.clases.controller;

import edu.msmk.clases.service.TramoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/geo")
@CrossOrigin(origins = "*") // Crucial para que React (puerto 5173) hable con Java (8080)
public class GeoController {

    @Autowired
    private TramoService tramoService;

    /**
     * Endpoint para el autocompletado de municipios en el Frontend
     */
    @GetMapping("/municipios")
    public ResponseEntity<List<String>> sugerirMunicipios(@RequestParam String query) {
        // Llamamos al método de búsqueda que añadimos al TramoService
        return ResponseEntity.ok(tramoService.buscarMunicipios(query));
    }

    /**
     * Endpoint para el autocompletado de calles basado en un municipio
     */
    @GetMapping("/calles")
    public ResponseEntity<List<String>> sugerirCalles(
            @RequestParam String municipio,
            @RequestParam String query) {
        return ResponseEntity.ok(tramoService.buscarCalles(municipio, query));
    }

    /**
     * Endpoint de salud para verificar que el controlador responde
     */
    @GetMapping("/check")
    public ResponseEntity<String> check() {
        return ResponseEntity.ok("GeoController activo");
    }
}