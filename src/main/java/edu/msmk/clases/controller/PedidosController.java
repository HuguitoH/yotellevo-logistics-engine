package edu.msmk.clases.controller;

import edu.msmk.clases.dto.PedidoRequest;
import edu.msmk.clases.dto.PedidoResponse;
import edu.msmk.clases.service.PedidoOrquestador;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

/**
 * Controller simplificado para gestión de pedidos.
 *
 * CORREGIDO: Usa request.getContacto() en lugar de getDestinatario()
 */
@RestController
@RequestMapping("/api/v1/pedidos")
@CrossOrigin(origins = "*")
@Slf4j
public class PedidosController {

    @Autowired
    private PedidoOrquestador pedidoOrquestador;

    /**
     * POST /api/v1/pedidos
     *
     * Crea un nuevo pedido
     */
    @PostMapping
    public ResponseEntity<PedidoResponse> crearPedido(@Valid @RequestBody PedidoRequest request) {
        log.info("📦 Nuevo pedido recibido: {} {}",
                request.getContacto().getNombre(),
                request.getContacto().getApellidos());

        PedidoResponse response = pedidoOrquestador.procesarPedido(request);

        if (response != null && Boolean.TRUE.equals(response.getCobertura())) {
            log.info("Pedido {} aceptado", response.getPedidoId());
        } else {
            // Si response es null o cobertura es null/false
            log.warn("Pedido rechazado o error en proceso: {}",
                    (response != null) ? response.getMensaje() : "Respuesta nula");
        }

        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/v1/pedidos/estado
     */
    @GetMapping("/estado")
    public ResponseEntity<EstadoSistema> obtenerEstado() {
        int pendientes = pedidoOrquestador.contarPedidosPendientes();

        return ResponseEntity.ok(EstadoSistema.builder()
                .pedidosPendientes(pendientes)
                .estado("OPERATIVO")
                .mensaje("Sistema funcionando correctamente")
                .build());
    }

    @lombok.Data
    @lombok.Builder
    public static class EstadoSistema {
        private Integer pedidosPendientes;
        private String estado;
        private String mensaje;
    }
}