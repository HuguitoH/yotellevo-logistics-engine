package edu.msmk.clases.controller;

import edu.msmk.clases.dto.PedidoRequest;
import edu.msmk.clases.exchange.PeticionCliente;
import edu.msmk.clases.service.DireccionParserService;
import edu.msmk.clases.service.CoberturaServicio; // Importante
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/tienda")
@CrossOrigin(origins = "*")
public class TiendaController {

    @Autowired
    private DireccionParserService parserService;

    @Autowired
    private CoberturaServicio coberturaServicio; // Inyectamos el motor de tramos

    @PostMapping("/finalizar-y-validar")
    public ResponseEntity<?> validarYProcesar(@RequestBody PedidoRequest.DireccionDTO dto) {
        log.info("🛒 Tienda: Validando {} en {}", dto.getNombreVia(), dto.getMunicipio());

        // 1. Intentar encontrar los códigos (CPRO, CMUM, CVIA)
        PeticionCliente peticion = parserService.parsear(dto);

        if (peticion == null || peticion.getVia() == null) {
            log.warn("Calle no encontrada: {}", dto.getNombreVia());
            return generarError("Dirección no encontrada", "La calle no existe en nuestra base de datos.");
        }

        // 2. Validar el NÚMERO (Rango y Paridad) y obtener el Código Postal Oficial
        boolean tieneCobertura = coberturaServicio.damosServicio(peticion);

        if (!tieneCobertura) {
            log.warn("Número fuera de rango o paridad incorrecta: {}", peticion.getNumero());
            return generarError("Sin cobertura", "El número indicado no tiene servicio en esta vía.");
        }

        // 3. Respuesta de éxito con el CP recuperado del TRAM
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("pedidoId", "PED-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        response.put("cpro", peticion.getProvincia());
        response.put("cmum", peticion.getMunicipio());
        response.put("cvia", peticion.getVia());
        response.put("cp", peticion.getCodigoPostalOficial()); // Viene del tramo validado
        response.put("mensaje", "Pedido validado correctamente.");

        log.info("Pedido OK. CP asignado: {}", peticion.getCodigoPostalOficial());
        return ResponseEntity.ok(response);
    }

    // Método auxiliar para no repetir código de error
    private ResponseEntity<?> generarError(String error, String mensaje) {
        Map<String, String> body = new HashMap<>();
        body.put("error", error);
        body.put("mensaje", mensaje);
        return ResponseEntity.status(404).body(body);
    }
}