package edu.msmk.clases.controller;

import edu.msmk.clases.service.geocoding.MapboxService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/mantenimiento")
@CrossOrigin(origins = "http://localhost:5173")
public class MantenimientoController {

    @Autowired
    private MapboxService mapboxService;

    @DeleteMapping("/cache-mapa")
    public ResponseEntity<?> borrarCacheMapa() {
        try {
            mapboxService.limpiarCache();
            return ResponseEntity.ok(Map.of(
                    "mensaje", "Caché de geocodificación eliminada correctamente",
                    "estado", "CLEANED"
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(e.getMessage());
        }
    }
}