package edu.msmk.clases.controller;

import edu.msmk.clases.dto.PilaVisualizacionDTO;
import edu.msmk.clases.service.routing.PilaService;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller para gestión y visualización de la pila de paquetes.
 *
 * Endpoints para:
 * - Visualizar estado de la pila
 * - Simular descarga de paquetes
 * - Ver siguiente paquete a descargar
 */
@RestController
@RequestMapping("/api/v1/pila")
@CrossOrigin(origins = "*")
@Slf4j
public class PilaController {

    @Autowired
    private PilaService pilaService;

    /**
     * GET /api/v1/pila/{furgonetaId}
     *
     * Obtiene la visualización completa de la pila de una furgoneta
     *
     * @param furgonetaId ID de la furgoneta
     * @return Datos de visualización de la pila
     */
    @GetMapping("/{furgonetaId}")
    public ResponseEntity<PilaVisualizacionDTO> obtenerPila(@PathVariable String furgonetaId) {
        log.info("Solicitando visualización de pila: {}", furgonetaId);

        PilaVisualizacionDTO pila = pilaService.obtenerVisualizacion(furgonetaId);

        if (pila.getPaquetes() == null || pila.getPaquetes().isEmpty()) {
            log.warn("Furgoneta {} vacía o no encontrada", furgonetaId);
        }

        return ResponseEntity.ok(pila);
    }

    /**
     * POST /api/v1/pila/{furgonetaId}/descargar
     *
     * Simula la descarga del siguiente paquete de la furgoneta
     *
     * @param furgonetaId ID de la furgoneta
     * @return Estado actualizado de la pila
     */
    @PostMapping("/{furgonetaId}/descargar")
    public ResponseEntity<PilaVisualizacionDTO> descargarSiguiente(@PathVariable String furgonetaId) {
        log.info("Descargando siguiente paquete de: {}", furgonetaId);

        try {
            pilaService.descargarSiguiente(furgonetaId);
            PilaVisualizacionDTO pilaActualizada = pilaService.obtenerVisualizacion(furgonetaId);

            log.info("Paquete descargado. Quedan: {} paquetes",
                    pilaActualizada.getPaquetesActuales());

            return ResponseEntity.ok(pilaActualizada);

        } catch (IllegalStateException e) {
            log.warn("Error al descargar: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(PilaVisualizacionDTO.builder()
                            .furgonetaId(furgonetaId)
                            .mensaje(e.getMessage())
                            .build());
        }
    }

    /**
     * GET /api/v1/pila/{furgonetaId}/siguiente
     *
     * Obtiene el siguiente paquete a descargar sin sacarlo de la pila
     *
     * @param furgonetaId ID de la furgoneta
     * @return Información del siguiente paquete
     */
    @GetMapping("/{furgonetaId}/siguiente")
    public ResponseEntity<SiguientePaqueteDTO> verSiguiente(@PathVariable String furgonetaId) {
        log.info("Consultando siguiente paquete de: {}", furgonetaId);

        try {
            var paquete = pilaService.verSiguiente(furgonetaId);

            if (paquete == null) {
                return ResponseEntity.ok(SiguientePaqueteDTO.builder()
                        .mensaje("La furgoneta está vacía")
                        .build());
            }

            SiguientePaqueteDTO response = SiguientePaqueteDTO.builder()
                    .paqueteId(paquete.getId())
                    .destinatario(paquete.getDestinatario())
                    .direccion(paquete.getDireccion().getDireccionCompleta())
                    .peso(paquete.getPeso())
                    .build();

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            log.warn("Furgoneta no encontrada: {}", furgonetaId);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * GET /api/v1/pila/furgonetas
     *
     * Obtiene lista de furgonetas activas
     *
     * @return Lista de IDs de furgonetas
     */
    @GetMapping("/furgonetas")
    public ResponseEntity<List<String>> obtenerFurgonetasActivas() {
        log.info("Consultando furgonetas activas");
        List<String> furgonetas = pilaService.obtenerFurgonetasActivas();
        return ResponseEntity.ok(furgonetas);
    }

    // ========== DTOs INTERNOS ==========

    @lombok.Data
    @lombok.Builder
    public static class SiguientePaqueteDTO {
        private String paqueteId;
        private String destinatario;
        private String direccion;
        private Double peso;
        private String mensaje;
    }
}