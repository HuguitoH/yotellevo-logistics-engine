package edu.msmk.clases.service;

import edu.msmk.clases.dto.PedidoRequest;
import edu.msmk.clases.dto.PedidoResponse;
import edu.msmk.clases.exchange.PeticionCliente;
import edu.msmk.clases.model.Direccion;
import edu.msmk.clases.model.Paquete;
import edu.msmk.clases.model.Punto;
import edu.msmk.clases.routing.GrafoEntregas;
import edu.msmk.clases.routing.OptimizadorRutas;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Servicio que orquesta todo el flujo de pedidos
 * Usa todos los componentes que ya existen
 */
@Slf4j
@Service
public class PedidosService {

    @Autowired
    private CoberturaServicio coberturaServicio;  // YA EXISTE

    @Autowired
    private MapboxService mapboxService;  // Lo crearemos después

    @Autowired
    private DireccionParserService direccionParser;

    private final List<Paquete> paquetesPendientes = new ArrayList<>();
    private final AtomicInteger contadorPedidos = new AtomicInteger(0);
    private Punto almacen = new Punto(40.4168, -3.7038, "Almacen Central");

    /**
     * Procesa un nuevo pedido
     */
    public PedidoResponse procesarPedido(PedidoRequest request) {
        // 1. PARSEAR DIRECCIÓN
        PeticionCliente peticion = direccionParser.parsear(request.getDireccion());

        if (peticion == null) {
            log.error("Fallo al parsear: Municipio o Provincia no encontrados");
            return PedidoResponse.builder()
                    .estado("RECHAZADO")
                    .cobertura(false) // <--- ESTO ES LO QUE FALTA Y EVITA EL ERROR 500
                    .mensaje("No se pudo procesar la dirección proporcionada")
                    .build();
        }

        // 2. VALIDAR COBERTURA
        boolean cubierta = coberturaServicio.damosServicio(peticion);

        if (!cubierta) {
            return PedidoResponse.builder()
                    .estado("RECHAZADO")
                    .cobertura(false) // <--- ASEGÚRATE DE QUE ESTÉ AQUÍ TAMBIÉN
                    .mensaje("Lo sentimos, aún no llegamos a esa dirección")
                    .build();
        }

        // 3. OBTENER COORDENADAS DE MAPBOX
        String dirString = request.getDireccion().getNombreVia() + " " + request.getDireccion().getNumero();
        Punto coordenadas = mapboxService.obtenerCoordenadas(dirString);

        if (coordenadas == null) {
            log.warn("Usando coordenadas aproximadas para: {}", dirString);
            coordenadas = generarCoordenadasAproximadas(peticion);
        }

        // 4. CREAR EL MODELO DE DIRECCIÓN (Mapeo del DTO al Modelo)
        PedidoRequest.DireccionDTO dDTO = request.getDireccion();
        Direccion direccionModelo = new Direccion(
                dDTO.getProvincia(),
                dDTO.getMunicipio(),
                dDTO.getTipoVia(),
                dDTO.getNombreVia(),
                dDTO.getNumero(),
                dDTO.getCodigoPostal(),
                dDTO.getPiso(),
                dDTO.getPuerta(),
                dDTO.getEscalera()
        );

        // 5. CREAR PAQUETE (Ya con todos los datos calculados arriba)
        String pedidoId = "PKG-" + String.format("%04d", contadorPedidos.incrementAndGet());
        String nombreCompleto = request.getDestinatario().getNombre() + " " + request.getDestinatario().getApellidos();

        Paquete paquete = new Paquete(
                pedidoId,
                nombreCompleto,
                direccionModelo,
                coordenadas,
                request.getPeso(),
                request.getPrioridad()
        );

        // 6. AÑADIR A LISTA Y OPTIMIZAR
        synchronized (paquetesPendientes) {
            paquetesPendientes.add(paquete);
        }

        OptimizadorRutas.ResultadoOptimizacion resultado = optimizarRutaActual();

        return PedidoResponse.builder()
                .pedidoId(pedidoId)
                .estado("ACEPTADO")
                .cobertura(true)
                .mensaje("Pedido recibido correctamente")
                .coordenadas(PedidoResponse.CoordenadasDTO.builder()
                        .latitud(coordenadas.getLatitud())
                        .longitud(coordenadas.getLongitud())
                        .build())
                .ordenEntrega(paquete.getOrdenEntrega())
                .distanciaTotal(resultado != null ? resultado.getDistanciaTotal() : 0.0)
                .tiempoEstimado(calcularTiempoEstimado(resultado))
                .build();
    }

    /**
     * Optimiza la ruta actual con todos los paquetes pendientes
     */
    private OptimizadorRutas.ResultadoOptimizacion optimizarRutaActual() {
        synchronized (paquetesPendientes) {
            if (paquetesPendientes.isEmpty()) {
                return null;
            }

            try {
                // Crear grafo (USA TU CÓDIGO)
                GrafoEntregas grafo = new GrafoEntregas(almacen, paquetesPendientes);

                // Optimizar (USA TU ALGORITMO)
                OptimizadorRutas optimizador = new OptimizadorRutas();
                OptimizadorRutas.ResultadoOptimizacion resultadoNN =
                        optimizador.optimizarNearestNeighbor(grafo);

                OptimizadorRutas.ResultadoOptimizacion resultado2Opt =
                        optimizador.optimizar2Opt(grafo, resultadoNN);

                return resultado2Opt;

            } catch (Exception e) {
                log.error("Error al optimizar ruta: {}", e.getMessage());
                return null;
            }
        }
    }

    /**
     * Parsea una dirección de texto a PeticionCliente
     * TODO: Implementar parser real según el formato que uses
     */
    private PeticionCliente parsearDireccion(String direccion) {
        try {
            // Por ahora, usar valores de prueba
            // En producción, deberías parsear la dirección real
            // Ejemplo: "AÑUA BIDEA 8, ALEGRIA-DULANTZI" → provincia:1, municipio:1, etc.

            return new PeticionCliente(1, 1, 1701, 1002, 8);

        } catch (Exception e) {
            log.error("Error al parsear dirección: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Genera coordenadas aproximadas si Mapbox falla
     */
    private Punto generarCoordenadasAproximadas(PeticionCliente peticion) {
        // Generar coordenadas aleatorias cercanas al almacén
        double lat = almacen.getLatitud() + (Math.random() - 0.5) * 0.1;
        double lon = almacen.getLongitud() + (Math.random() - 0.5) * 0.1;
        return new Punto(lat, lon);
    }

    /**
     * Calcula tiempo estimado de entrega
     */
    private String calcularTiempoEstimado(OptimizadorRutas.ResultadoOptimizacion resultado) {
        if (resultado == null) {
            return "Calculando...";
        }

        // Asumir velocidad promedio de 30 km/h
        double horas = resultado.getDistanciaTotal() / 30.0;
        int minutos = (int) (horas * 60);

        if (minutos < 60) {
            return minutos + " minutos";
        } else {
            int h = minutos / 60;
            int m = minutos % 60;
            return h + "h " + m + "min";
        }
    }

    /**
     * Obtiene lista de paquetes pendientes
     */
    public List<Paquete> getPaquetesPendientes() {
        synchronized (paquetesPendientes) {
            return new ArrayList<>(paquetesPendientes);
        }
    }

    /**
     * Obtiene resultado de optimización actual
     */
    public OptimizadorRutas.ResultadoOptimizacion getRutaOptimizada() {
        return optimizarRutaActual();
    }
}