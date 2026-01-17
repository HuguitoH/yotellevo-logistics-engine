package edu.msmk.clases.controller;

import edu.msmk.clases.dto.DashboardMetricas;
import edu.msmk.clases.dto.DashboardResponse;
import edu.msmk.clases.dto.GraphDTO;
import edu.msmk.clases.model.Paquete;
import edu.msmk.clases.model.Punto;
import edu.msmk.clases.service.PedidoOrquestador;
import edu.msmk.clases.service.cobertura.TramoLoader;
import edu.msmk.clases.service.routing.DashboardStatsService;
import edu.msmk.clases.service.routing.GrafoEntregas;
import edu.msmk.clases.service.routing.OptimizadorRutas;
import edu.msmk.clases.service.geocoding.MapboxService;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.Comparator;

@RestController
@RequestMapping("/api/v1/admin")
@CrossOrigin(origins = "*")
@Slf4j
public class AdminController {

    @Autowired
    private DashboardStatsService statsService;

    @Autowired
    private PedidoOrquestador pedidoOrquestador;

    @Autowired
    private OptimizadorRutas optimizadorRutas;

    @Autowired
    private MapboxService mapboxService;

    private static final Punto ALMACEN = new Punto(40.4168, -3.7038, "Almacén Central");

    private DashboardResponse ultimaRespuestaCacheada;
    private int ultimoHashCalculado = 0;

    // --- ENDPOINTS PRINCIPALES ---

    @GetMapping("/dashboard-completo")
    public ResponseEntity<DashboardResponse> obtenerTodo() {
        List<Paquete> todosLosPaquetes = pedidoOrquestador.obtenerTodosLosPaquetes();
        int hashActual = Arrays.hashCode(todosLosPaquetes.toArray());

        if (ultimaRespuestaCacheada != null && hashActual == ultimoHashCalculado) {
            return ResponseEntity.ok(ultimaRespuestaCacheada);
        }

        // 1. Para la LISTA DE CARGA (Derecha del dashboard) sí filtramos lo que falta
        List<Paquete> pendientesParaLista = todosLosPaquetes.stream()
                .filter(p -> p.getEstado() != Paquete.EstadoPaquete.ENTREGADO)
                .filter(p -> p.getEstado() != Paquete.EstadoPaquete.EN_ESPERA)
                .sorted(Comparator.comparing(Paquete::getOrdenEntrega))
                .collect(Collectors.toList());

        // 2. Para el MAPA: Usamos todos los que NO estén en espera para mantener el trazado completo
        List<Paquete> rutaActivaCompleta = todosLosPaquetes.stream()
                .filter(p -> p.getEstado() != Paquete.EstadoPaquete.EN_ESPERA)
                .sorted(Comparator.comparing(Paquete::getOrdenEntrega))
                .collect(Collectors.toList());

        OptimizadorRutas.ResultadoOptimizacion resultado;
        String geoJson = null;

        if (!rutaActivaCompleta.isEmpty()) {
            resultado = OptimizadorRutas.ResultadoOptimizacion.builder()
                    .rutaOptimizada(pendientesParaLista)
                    .distanciaTotal(0.0)
                    .build();

            // 3. Trazado Mapbox: Siempre desde el Almacén pasando por toda la ruta histórica + pendiente
            List<Punto> puntosMapa = new ArrayList<>();
            puntosMapa.add(ALMACEN);
            rutaActivaCompleta.forEach(p -> puntosMapa.add(p.getCoordenadas()));
            puntosMapa.add(ALMACEN);

            geoJson = mapboxService.obtenerTrazadoRuta(puntosMapa);
        } else {
            resultado = OptimizadorRutas.ResultadoOptimizacion.vacio();
        }

        GraphDTO grafoFinal = convertirRutaAGrafoDTO(todosLosPaquetes);

        this.ultimaRespuestaCacheada = DashboardResponse.builder()
                .metricas(generarMetricas(todosLosPaquetes, resultado))
                .grafo(grafoFinal)
                .listaCarga(pendientesParaLista) // Mostramos solo lo que falta por repartir
                .trazadoMapa(geoJson)
                .build();

        this.ultimoHashCalculado = hashActual;
        return ResponseEntity.ok(ultimaRespuestaCacheada);
    }

    /**
     * Dibuja el grafo uniendo los puntos según su orden histórico (1 -> 2 -> 3...)
     */
    private GraphDTO convertirRutaAGrafoDTO(List<Paquete> todosLosPaquetes) {
        List<GraphDTO.NodoDTO> nodes = new ArrayList<>();
        List<GraphDTO.LinkDTO> links = new ArrayList<>();

        // 1. Añadir Almacén
        nodes.add(GraphDTO.NodoDTO.builder().id("ALMACEN").tipo("ALMACEN")
                .lat(ALMACEN.lat()).lon(ALMACEN.lon()).etiqueta("Almacén Central").build());

        // 2. Añadir todos los paquetes como nodos
        for (Paquete p : todosLosPaquetes) {
            nodes.add(GraphDTO.NodoDTO.builder()
                    .id(p.getId()).tipo("ENTREGA").lat(p.getCoordenadas().lat()).lon(p.getCoordenadas().lon())
                    .etiqueta(p.getDestinatario()).estado(p.getEstado().toString()).build());
        }

        // 3. FILTRAR: Solo creamos enlaces para la ruta activa (los que NO están en espera)
        List<Paquete> rutaActiva = todosLosPaquetes.stream()
                .filter(p -> p.getEstado() != Paquete.EstadoPaquete.EN_ESPERA) // <--- CRUCIAL
                .sorted(Comparator.comparing(Paquete::getOrdenEntrega))
                .collect(Collectors.toList());

        if (!rutaActiva.isEmpty()) {
            // Almacén -> Primer Paquete real
            links.add(GraphDTO.LinkDTO.builder().source("ALMACEN").target(rutaActiva.get(0).getId()).build());

            // Cadena consecutiva de la ruta real
            for (int i = 0; i < rutaActiva.size() - 1; i++) {
                links.add(GraphDTO.LinkDTO.builder()
                        .source(rutaActiva.get(i).getId())
                        .target(rutaActiva.get(i + 1).getId())
                        .build());
            }

            // Último Paquete real -> Almacén
            links.add(GraphDTO.LinkDTO.builder()
                    .source(rutaActiva.get(rutaActiva.size() - 1).getId())
                    .target("ALMACEN")
                    .build());
        }

        return GraphDTO.builder().nodes(nodes).links(links).mensaje("Ruta sincronizada").build();
    }

    @DeleteMapping("/paquetes")
    public ResponseEntity<String> limpiarPaquetes() {
        // 1. Limpia las listas en el servicio
        pedidoOrquestador.limpiarPaquetesPendientes();

        // 2. IMPORTANTE: Reseteamos la caché del controlador
        // Si no hacemos esto, el frontend seguirá viendo los datos viejos
        // hasta que el hash cambie.
        this.ultimaRespuestaCacheada = null;
        this.ultimoHashCalculado = 0;

        log.info("Sistema reseteado: Listas vacías y caché de Dashboard eliminada");
        return ResponseEntity.ok("Sistema reseteado completamente");
    }

    // --- MÉTODOS DE APOYO (SIN DUPLICADOS) ---

    private DashboardMetricas generarMetricas(List<Paquete> todos, OptimizadorRutas.ResultadoOptimizacion res) {
        // 1. Contamos basándonos en la lista real en memoria
        int totalHoy = todos.size();

        // Contamos como pendientes los que NO están entregados ni en espera
        long pendientes = todos.stream()
                .filter(p -> p.getEstado() == Paquete.EstadoPaquete.PENDIENTE ||
                        p.getEstado() == Paquete.EstadoPaquete.EN_RUTA)
                .count();

        // 2. Cálculo de Cobertura
        // Si tenemos 10 paquetes y 2 pendientes, cobertura es 80%
        double cobertura = totalHoy > 0
                ? ((double) (totalHoy - pendientes) / totalHoy) * 100
                : 0.0;

        // 3. Estado dinámico
        String estadoSistema = "ALMACEN";
        if (pendientes > 0) {
            estadoSistema = "EN RUTA";
        } else if (totalHoy > 0) {
            estadoSistema = "FINALIZADO";
        }

        // 4. Construcción del DTO
        return DashboardMetricas.builder()
                .empresasIndexadas(TramoLoader.totalRegistrosIndexados) // <--- Aquí pasamos el dato del Loader
                .pedidosHoy(totalHoy)
                .pedidosPendientes((int) pendientes)
                .porcentajeCobertura(totalHoy > 0 ? ((double)(totalHoy - pendientes)/totalHoy)*100 : 0.0)
                .distanciaTotal(res != null ? res.getDistanciaTotal() : 0.0)
                .ahorroOptimizado(res != null ? res.getAhorroPorcentaje() : 0.0)
                .estado(pendientes > 0 ? "EN RUTA" : "ALMACEN")
                .latenciaPromedio(TramoLoader.ultimaLatenciaMs)
                .throughput(TramoLoader.ultimoThroughput)
                .build(); // Esto crea el objeto correctamente sin importar el orden
    }

    private GraphDTO convertirRutaAGrafoDTO(GrafoEntregas grafo, OptimizadorRutas.ResultadoOptimizacion resultado) {
        List<GraphDTO.NodoDTO> nodes = new ArrayList<>();
        List<GraphDTO.LinkDTO> links = new ArrayList<>();

        // 1. Siempre añadimos el Almacén
        nodes.add(GraphDTO.NodoDTO.builder()
                .id("ALMACEN").tipo("ALMACEN").lat(ALMACEN.lat()).lon(ALMACEN.lon())
                .etiqueta("Almacén Central").build());

        // 2. Añadimos TODOS los paquetes
        for (Paquete p : grafo.getPaquetes()) {
            nodes.add(GraphDTO.NodoDTO.builder()
                    .id(p.getId())
                    .tipo("ENTREGA")
                    .lat(p.getCoordenadas().lat())
                    .lon(p.getCoordenadas().lon())
                    .etiqueta(p.getDestinatario())
                    .estado(p.getEstado().toString())
                    .build());
        }

        // 3. CREAR LOS ENLACES (Corregido para usar IDs de Paquete reales)
        // El 'resultado' viene de un grafo que solo tiene pendientes.
        // Necesitamos mapear esos índices a los Paquetes reales.
        List<Integer> rutaIndices = resultado.getIndicesRuta();

        if (rutaIndices != null && rutaIndices.size() > 1) {
            for (int i = 0; i < rutaIndices.size() - 1; i++) {
                int actualIdx = rutaIndices.get(i);
                int siguienteIdx = rutaIndices.get(i + 1);

                // IMPORTANTE: Obtenemos el ID del paquete desde la ruta optimizada, no por índice del grafo visual
                String sourceId = (actualIdx == 0) ? "ALMACEN" : resultado.getRutaOptimizada().get(actualIdx - 1).getId();

                // Manejo especial para el último link que vuelve al almacén (índice 0)
                String targetId;
                if (siguienteIdx == 0) {
                    targetId = "ALMACEN";
                } else {
                    targetId = resultado.getRutaOptimizada().get(siguienteIdx - 1).getId();
                }

                links.add(GraphDTO.LinkDTO.builder()
                        .source(sourceId)
                        .target(targetId)
                        .build());
            }
        }

        // 4. PARCHE PARA HUÉRFANOS (Nodos que no están en la ruta actual pero deben verse)
        // Si un paquete es nuevo y aún no entró en la 'rutaIndices', lo conectamos al almacén
        for (Paquete p : grafo.getPaquetes()) {
            boolean estaEnLinks = links.stream().anyMatch(l -> l.getSource().equals(p.getId()) || l.getTarget().equals(p.getId()));
            if (!estaEnLinks) {
                links.add(GraphDTO.LinkDTO.builder()
                        .source("ALMACEN")
                        .target(p.getId())
                        .build());
            }
        }

        return GraphDTO.builder()
                .nodes(nodes)
                .links(links)
                .mensaje("Grafo actualizado con " + links.size() + " conexiones")
                .build();


    }

    @PostMapping("/finalizar-ruta")
    public ResponseEntity<String> finalizarRuta() {
        // Esto llamará a la lógica de auto-carga que pusimos en el Orquestador
        pedidoOrquestador.finalizarRutaYPrepararSiguiente();

        // Reset de caché para que el grafo se dibuje con la nueva ruta inmediatamente
        this.ultimaRespuestaCacheada = null;
        this.ultimoHashCalculado = 0;

        return ResponseEntity.ok("Furgoneta de vuelta. Pedidos en espera activados.");
    }


    // --- DTOs INTERNOS ---

    @lombok.Data
    @lombok.Builder
    public static class RutaOptimizadaDTO {
        private List<String> rutaOptimizada;
        private double distanciaTotal;
        private int tiempoEstimado;
        private double ahorroPorcentaje;
        private String rutaGeoJson;
        private String mensaje;
    }
}