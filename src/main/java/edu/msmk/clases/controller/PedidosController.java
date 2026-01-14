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
        log.info("Nueva petición de pedido: {}", request.getDestinatario());

        PedidoResponse response = pedidosService.procesarPedido(request);

        if (Boolean.TRUE.equals(response.getCobertura())) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }

    /**
     * Endpoint de prueba
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("API funcionando correctamente");
    }
}