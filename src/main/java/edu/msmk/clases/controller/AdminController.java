package edu.msmk.clases.controller;

import edu.msmk.clases.dto.DashboardMetricas;
import edu.msmk.clases.dto.GraphDTO;
import edu.msmk.clases.model.Paquete;
import edu.msmk.clases.model.Punto;
import edu.msmk.clases.service.PedidoOrquestador;
import edu.msmk.clases.service.routing.GrafoEntregas;
import edu.msmk.clases.service.routing.OptimizadorRutas;
import edu.msmk.clases.service.geocoding.MapboxService;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
@CrossOrigin(origins = "*")
@Slf4j
public class AdminController {

    @Autowired
    private PedidoOrquestador pedidoOrquestador;

    @Autowired
    private OptimizadorRutas optimizadorRutas;

    @Autowired
    private MapboxService mapboxService;

    // Uso de los métodos lat() y lon() del record Punto
    private static final Punto ALMACEN = new Punto(40.4168, -3.7038, "Almacén Central");

    @GetMapping("/metricas")
    public ResponseEntity<DashboardMetricas> obtenerMetricas() {
        log.info("Solicitando métricas del sistema");
        int pedidosPendientes = pedidoOrquestador.contarPedidosPendientes();
        int totalProcesados = pedidoOrquestador.obtenerTotalPedidosProcesados();

        DashboardMetricas metricas = DashboardMetricas.builder()
                .pedidosHoy(totalProcesados)
                .pedidosPendientes(pedidosPendientes)
                .porcentajeCobertura(75.0)
                .estado("OPERATIVO")
                .build();

        return ResponseEntity.ok(metricas);
    }

    @GetMapping("/grafo")
    public ResponseEntity<GraphDTO> obtenerGrafo() {
        log.info("Solicitando grafo de entregas");
        List<Paquete> paquetesPendientes = pedidoOrquestador.obtenerPaquetesPendientes();

        if (paquetesPendientes.isEmpty()) {
            return ResponseEntity.ok(GraphDTO.builder()
                    .nodes(new ArrayList<>())
                    .links(new ArrayList<>())
                    .mensaje("No hay paquetes pendientes")
                    .build());
        }

        // Constructor con 2 argumentos
        GrafoEntregas grafo = new GrafoEntregas(ALMACEN, paquetesPendientes);
        return ResponseEntity.ok(convertirGrafoADTO(grafo, paquetesPendientes));
    }

    @GetMapping("/ruta-optimizada")
    public ResponseEntity<RutaOptimizadaDTO> obtenerRutaOptimizada() {
        log.info("Solicitando ruta optimizada con trazado real");
        List<Paquete> paquetesPendientes = pedidoOrquestador.obtenerPaquetesPendientes();

        if (paquetesPendientes.isEmpty()) {
            return ResponseEntity.ok(RutaOptimizadaDTO.builder()
                    .rutaOptimizada(new ArrayList<>())
                    .distanciaTotal(0.0)
                    .mensaje("No hay paquetes para optimizar")
                    .build());
        }

        // 1. Calcular la ruta óptima (el orden de las paradas)
        GrafoEntregas grafo = new GrafoEntregas(ALMACEN, paquetesPendientes);
        OptimizadorRutas.ResultadoOptimizacion resultado = optimizadorRutas.optimizarCompleto(grafo);

        // 2. Preparar lista de puntos para Mapbox (Almacén -> Paradas -> Almacén)
        List<Punto> puntosParaTrazado = new ArrayList<>();
        puntosParaTrazado.add(ALMACEN);
        for (Paquete p : resultado.getRutaOptimizada()) {
            puntosParaTrazado.add(p.getCoordenadas());
        }
        puntosParaTrazado.add(ALMACEN); // Volver al almacén

        // 3. Obtener el trazado REAL por carretera desde Mapbox
        String geoJsonReal = mapboxService.obtenerTrazadoRuta(puntosParaTrazado);

        // 4. Construir la respuesta para el Frontend
        List<String> rutaIds = resultado.getRutaOptimizada().stream()
                .map(Paquete::getId)
                .toList();

        RutaOptimizadaDTO rutaDTO = RutaOptimizadaDTO.builder()
                .rutaOptimizada(rutaIds)
                .distanciaTotal(resultado.getDistanciaTotal())
                .tiempoEstimado((int) (resultado.getDistanciaTotal() / 30.0 * 60))
                .ahorroPorcentaje(resultado.getAhorroPorcentaje())
                .rutaGeoJson(geoJsonReal) // Cambiamos el DTO manual por el String de Mapbox
                .build();

        return ResponseEntity.ok(rutaDTO);
    }

    @DeleteMapping("/paquetes")
    public ResponseEntity<String> limpiarPaquetes() {
        pedidoOrquestador.limpiarPaquetesPendientes();
        return ResponseEntity.ok("Paquetes pendientes eliminados");
    }

    // ========== MÉTODOS AUXILIARES CORREGIDOS ==========

    private GraphDTO convertirGrafoADTO(GrafoEntregas grafo, List<Paquete> paquetes) {
        List<GraphDTO.NodoDTO> nodes = new ArrayList<>();
        List<GraphDTO.LinkDTO> links = new ArrayList<>();

        // 1. Nodo del almacén
        nodes.add(GraphDTO.NodoDTO.builder()
                .id("ALMACEN")
                .tipo("ALMACEN")
                .lat(ALMACEN.lat())
                .lon(ALMACEN.lon())
                .etiqueta("Almacén Central")
                .build());

        // 2. Nodos de entregas y conexiones con el almacén
        for (Paquete paquete : paquetes) {
            // CORRECCIÓN: getCoordenadas() en lugar de getDestino()
            nodes.add(GraphDTO.NodoDTO.builder()
                    .id(paquete.getId())
                    .tipo("ENTREGA")
                    .lat(paquete.getCoordenadas().lat())
                    .lon(paquete.getCoordenadas().lon())
                    .etiqueta(paquete.getDestinatario())
                    .build());

            // Conexión Almacén -> Paquete
            double distancia = ALMACEN.distanciaHaversine(paquete.getCoordenadas());
            links.add(GraphDTO.LinkDTO.builder()
                    .source("ALMACEN")
                    .target(paquete.getId())
                    .distancia(distancia)
                    .build());
        }

        // 3. Conexiones entre paquetes (Opcional, para visualizar la malla del grafo)
        for (int i = 0; i < paquetes.size(); i++) {
            for (int j = i + 1; j < paquetes.size(); j++) {
                Paquete p1 = paquetes.get(i);
                Paquete p2 = paquetes.get(j);

                double dist = p1.getCoordenadas().distanciaHaversine(p2.getCoordenadas());
                links.add(GraphDTO.LinkDTO.builder()
                        .source(p1.getId())
                        .target(p2.getId())
                        .distancia(dist)
                        .build());
            }
        }

        return GraphDTO.builder()
                .nodes(nodes)
                .links(links)
                .build();
    }

    // ========== DTOs INTERNOS ==========

    @lombok.Data
    @lombok.Builder
    public static class RutaOptimizadaDTO {
        private List<String> rutaOptimizada;
        private double distanciaTotal;
        private int tiempoEstimado;
        private double ahorroPorcentaje;
        private String rutaGeoJson; // Ahora es el String que devuelve Mapbox
        private String mensaje;
    }

    @lombok.Data
    @lombok.Builder
    public static class GeoJsonDTO {
        private String type;
        private List<List<Double>> coordinates;
    }
}