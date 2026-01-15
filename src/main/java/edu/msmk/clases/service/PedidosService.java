package edu.msmk.clases.service;

import edu.msmk.clases.dto.*;
import edu.msmk.clases.exchange.PeticionCliente;
import edu.msmk.clases.model.Direccion;
import edu.msmk.clases.model.Paquete;
import edu.msmk.clases.model.Punto;
import edu.msmk.clases.routing.GrafoEntregas;
import edu.msmk.clases.routing.OptimizadorRutas;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Slf4j
@Service
public class PedidosService {

    @Autowired
    private CoberturaServicio coberturaServicio;
    @Autowired
    private MapboxService mapboxService;
    @Autowired
    private DireccionParserService direccionParser;

    private final Set<String> nombresMunicipios = Collections.synchronizedSet(new HashSet<>());
    private final Map<String, Set<String>> callesPorMunicipio = new ConcurrentHashMap<>();

    private final List<Paquete> paquetesPendientes = Collections.synchronizedList(new ArrayList<>());
    private final AtomicInteger contadorPedidos = new AtomicInteger(0);
    private final Punto almacen = new Punto(40.4168, -3.7038, "Almacen Central");

    // volatile asegura que el cambio sea visible instantáneamente entre hilos
    private volatile GraphDTO ultimoGrafoActual = inicializarGrafoVacio();

    private static final double VELOCIDAD_PROMEDIO_KMH = 80.0;
    private static final int ZOOM_GRAFO = 15000; // Ajustado para que los puntos no se amontonen

    public GraphDTO obtenerUltimoGrafo() {
        return ultimoGrafoActual;
    }

    public PedidoResponse procesarPedido(PedidoRequest request) {
        long tiempoInicioNano = System.nanoTime();

        // 1. PARSEAR Y VALIDAR COBERTURA (Rápido)
        PeticionCliente peticion = direccionParser.parsear(request.getDireccion());
        if (peticion == null || !coberturaServicio.damosServicio(peticion)) {
            return rechazarPedido(request, peticion);
        }

        // 2. OBTENER COORDENADAS (Lento - FUERA de synchronized para no bloquear el sistema)
        String cpOficial = peticion.getCodigoPostalOficial();
        String calleNum = request.getDireccion().getNombreVia() + " " + request.getDireccion().getNumero();

        Punto coordenadas = mapboxService.obtenerCoordenadas(calleNum, cpOficial, peticion.getNombreMunicipio());
        if (coordenadas == null) coordenadas = generarCoordenadasAproximadas(peticion);

        // 3. PREPARAR MODELOS
        String pedidoId = "PKG-" + String.format("%04d", contadorPedidos.incrementAndGet());
        Paquete nuevoPaquete = crearPaquete(pedidoId, request, coordenadas, cpOficial);

        // 4. AÑADIR Y OPTIMIZAR RUTA (Bloqueo corto solo para cálculo)
        OptimizadorRutas.ResultadoOptimizacion resultado;
        Object geoJson;

        synchronized (paquetesPendientes) {
            paquetesPendientes.add(nuevoPaquete);
            resultado = calcularRutaOptima();
            this.ultimoGrafoActual = generarGraphDTO(resultado);
            geoJson = mapboxService.obtenerRutaOptimizadaGeoJson(extraerPuntosRuta(resultado));
        }

        return construirRespuestaExitosa(pedidoId, coordenadas, resultado, geoJson, tiempoInicioNano);
    }

    private OptimizadorRutas.ResultadoOptimizacion calcularRutaOptima() {
        if (paquetesPendientes.isEmpty()) return null;
        GrafoEntregas grafo = new GrafoEntregas(almacen, paquetesPendientes);
        OptimizadorRutas optimizador = new OptimizadorRutas();
        return optimizador.optimizar2Opt(grafo, optimizador.optimizarNearestNeighbor(grafo));
    }

    private GraphDTO generarGraphDTO(OptimizadorRutas.ResultadoOptimizacion res) {
        List<NodoDTO> nodes = new ArrayList<>();
        List<LinkDTO> links = new ArrayList<>();
        GrafoEntregas grafoAux = new GrafoEntregas(almacen, paquetesPendientes);

        // Nodo Almacén fijo
        nodes.add(NodoDTO.builder().id("ALM").tipo("ALMACEN").x(400.0).y(200.0).build());

        // Nodos Dinámicos
        for (int i = 0; i < paquetesPendientes.size(); i++) {
            Paquete p = paquetesPendientes.get(i);
            nodes.add(NodoDTO.builder()
                    .id("P" + (i + 1))
                    .tipo(p.getPrioridad() == 1 ? "URGENTE" : "ESTANDAR")
                    .x(400 + (p.getCoordenadas().getLongitud() - almacen.getLongitud()) * ZOOM_GRAFO)
                    .y(200 - (p.getCoordenadas().getLatitud() - almacen.getLatitud()) * ZOOM_GRAFO)
                    .build());
        }

        // Enlaces de la ruta optimizada
        List<Integer> idx = res.getIndicesRuta();
        for (int i = 0; i < idx.size() - 1; i++) {
            links.add(LinkDTO.builder()
                    .source(idx.get(i) == 0 ? "ALM" : "P" + idx.get(i))
                    .target(idx.get(i+1) == 0 ? "ALM" : "P" + idx.get(i+1))
                    .label(String.format("%.1f km", grafoAux.getDistancia(idx.get(i), idx.get(i+1))))
                    .build());
        }

        return GraphDTO.builder().nodes(nodes).links(links).build();
    }

    // --- MÉTODOS AUXILIARES ---

    private List<Punto> extraerPuntosRuta(OptimizadorRutas.ResultadoOptimizacion res) {
        List<Punto> puntos = new ArrayList<>();
        puntos.add(almacen);
        res.getRutaOptimizada().forEach(p -> puntos.add(p.getCoordenadas()));
        puntos.add(almacen);
        return puntos;
    }

    private Paquete crearPaquete(String id, PedidoRequest req, Punto coord, String cp) {
        Direccion dir = new Direccion(req.getDireccion().getProvincia(), req.getDireccion().getMunicipio(),
                req.getDireccion().getTipoVia(), req.getDireccion().getNombreVia(), req.getDireccion().getNumero(),
                cp, req.getDireccion().getPiso(), req.getDireccion().getPuerta(), req.getDireccion().getEscalera());

        return new Paquete(id, req.getDestinatario().getNombre() + " " + req.getDestinatario().getApellidos(),
                dir, coord, req.getPeso(), req.getPrioridad());
    }

    private PedidoResponse rechazarPedido(PedidoRequest req, PeticionCliente pet) {
        String msg = (pet == null) ? "Dirección no válida" : "Sin cobertura en esta zona exacta";
        return PedidoResponse.builder().estado("RECHAZADO").cobertura(false).mensaje(msg).build();
    }

    private PedidoResponse construirRespuestaExitosa(String id, Punto coord, OptimizadorRutas.ResultadoOptimizacion res, Object geo, long inicio) {
        double dist = Math.round(res.getDistanciaTotal() * 100.0) / 100.0;
        String perf = String.format("%.2f μs", (System.nanoTime() - inicio) / 1000.0);

        return PedidoResponse.builder()
                .pedidoId(id).estado("ACEPTADO").cobertura(true)
                .coordenadas(CoordenadasDTO.builder().latitud(coord.getLatitud()).longitud(coord.getLongitud()).build())
                .distanciaTotal(dist).tiempoEstimado(calcularTiempoEstimado(res))
                .tiempoProcesamiento(perf).rutaGeoJson(geo).grafoTeorico(ultimoGrafoActual).build();
    }

    private GraphDTO inicializarGrafoVacio() {
        return GraphDTO.builder().nodes(new ArrayList<>(List.of(NodoDTO.builder().id("ALM").tipo("ALMACEN").x(400.0).y(200.0).build()))).links(new ArrayList<>()).build();
    }

    private Punto generarCoordenadasAproximadas(PeticionCliente pet) {
        return new Punto(almacen.getLatitud() + (Math.random()-0.5)*0.05, almacen.getLongitud() + (Math.random()-0.5)*0.05, "Aprox");
    }

    private String calcularTiempoEstimado(OptimizadorRutas.ResultadoOptimizacion res) {
        return (int)((res.getDistanciaTotal() / VELOCIDAD_PROMEDIO_KMH) * 60) + " min";
    }

}