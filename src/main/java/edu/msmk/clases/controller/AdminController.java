package edu.msmk.clases.controller;


import edu.msmk.clases.dto.GraphDTO;
import edu.msmk.clases.service.GrafoService;
import edu.msmk.clases.service.PedidosService;
import edu.msmk.clases.service.TramoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@CrossOrigin(origins = "*")
public class AdminController {

    @Autowired
    private TramoService tramoService;

    @Autowired
    private PedidosService pedidosService;

    @Autowired // <--- ¡No olvides inyectar esto!
    private GrafoService grafoService;

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        return ResponseEntity.ok(tramoService.getStats());
    }

    @GetMapping("/grafo")
    public GraphDTO getGrafo() {
        return grafoService.obtenerGrafoActual();
    }
}