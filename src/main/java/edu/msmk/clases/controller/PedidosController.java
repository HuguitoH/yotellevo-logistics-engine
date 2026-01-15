package edu.msmk.clases.controller;

import edu.msmk.clases.dto.PedidoRequest;
import edu.msmk.clases.dto.PedidoResponse;
import edu.msmk.clases.service.PedidosService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller REST para gestión de pedidos
 */
@Slf4j
@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "*")  // Permitir CORS por ahora
public class PedidosController {

    @Autowired
    private PedidosService pedidosService;

    /**
     * Endpoint para crear un nuevo pedido
     *
     * POST /api/v1/pedidos
     */
    @PostMapping("/pedidos")
    public ResponseEntity<PedidoResponse> crearPedido(@RequestBody PedidoRequest request) {
        log.info("Procesando pedido para: {}", request.getDireccion()); // Mejor loguear dirección que destinatario para tracking de rutas

        PedidoResponse response = pedidosService.procesarPedido(request);

        // Si hay cobertura, devolvemos 200 OK.
        // Si NO hay cobertura, devolvemos 200 OK igualmente pero con el flag cobertura:false
        // ¿Por qué? Porque el sistema funcionó bien, solo que el negocio dice que no llegamos.
        return ResponseEntity.ok(response);
    }

    /**
     * Endpoint de prueba
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("API funcionando correctamente");
    }
}