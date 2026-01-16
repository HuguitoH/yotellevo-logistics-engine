package edu.msmk.clases.service;

import edu.msmk.clases.dto.PedidoRequest;
import edu.msmk.clases.dto.PedidoResponse;
import edu.msmk.clases.dto.CoordenadasDTO;
import edu.msmk.clases.exchange.PeticionCliente;
import edu.msmk.clases.model.Paquete;
import edu.msmk.clases.model.Direccion;
import edu.msmk.clases.model.Punto;
import edu.msmk.clases.service.cobertura.CoberturaServicio;
import edu.msmk.clases.service.cobertura.DireccionParser;
import edu.msmk.clases.service.geocoding.MapboxService;
import edu.msmk.clases.service.geocoding.CoordenadasCache;
import edu.msmk.clases.service.routing.GrafoEntregas;
import edu.msmk.clases.service.routing.OptimizadorRutas;
import edu.msmk.clases.service.routing.PilaService;
import edu.msmk.clases.service.routing.PilaFurgoneta;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Orquestador principal del flujo de pedidos.
 * Coordina todos los servicios necesarios para procesar un pedido de principio a fin.
 *
 * Flujo:
 * 1. Parsear dirección del cliente
 * 2. Validar cobertura en la zona
 * 3. Obtener coordenadas (Mapbox)
 * 4. Crear paquete
 * 5. Optimizar ruta con todos los paquetes pendientes
 * 6. Generar respuesta con toda la información
 */
@Service
@Slf4j
public class PedidoOrquestador {

    @Autowired
    private DireccionParser direccionParser;

    @Autowired
    private CoberturaServicio coberturaServicio;

    @Autowired
    private MapboxService mapboxService;

    @Autowired
    private CoordenadasCache coordenadasCache;

    @Autowired
    private OptimizadorRutas optimizadorRutas;

    @Autowired
    private PilaService pilaService;

    // Repositorio en memoria de paquetes pendientes
    private final Map<String, Paquete> paquetesPorId = new ConcurrentHashMap<>();
    private final List<Paquete> paquetesPendientes = Collections.synchronizedList(new ArrayList<>());
    private final AtomicInteger contadorPedidos = new AtomicInteger(0);

    // Punto del almacén (Madrid - ajusta según tu caso)
    private static final Punto ALMACEN = new Punto(40.4168, -3.7038, "Almacén Central");

    /**
     * Procesa un nuevo pedido de principio a fin
     */
    /**
     * Procesa un nuevo pedido de principio a fin
     */
    public PedidoResponse procesarPedido(PedidoRequest request) {
        long inicioTiempo = System.nanoTime(); // Para medir rendimiento real
        try {
            log.info("Procesando nuevo pedido para: {}", request.getDireccion().getMunicipio());

            // 1. PARSEO (Dinámico con IDs numéricos)
            PeticionCliente peticion = direccionParser.parsear(
                    request.getDireccion().getMunicipio(),
                    request.getDireccion().getProvincia(),
                    request.getDireccion().getNombreVia(),
                    Integer.parseInt(request.getDireccion().getNumero())
            );

            if (peticion == null) {
                log.warn("Dirección no encontrada tras normalización");
                return rechazarPedido("Dirección no válida o no encontrada en el sistema");
            }

            // 2. COBERTURA (Basada en tramos del archivo CSI)
            boolean tieneCobertura = coberturaServicio.damosServicio(peticion);
            if (!tieneCobertura) {
                return rechazarPedido("Lo sentimos, aún no damos servicio en esta zona");
            }

            // 3. GEOCODIFICACIÓN
            String direccionCompleta = construirDireccionCompleta(request.getDireccion());
            Punto coordenadas = obtenerCoordenadas(
                    direccionCompleta,
                    peticion.getCodigoPostalOficial(),
                    request.getDireccion().getMunicipio()
            );

            if (coordenadas == null) return rechazarPedido("No se pudo geocodificar la dirección");

            // 4. CREAR PAQUETE
            Paquete paquete = crearPaquete(request, coordenadas, peticion);
            guardarPaquete(paquete);

            // 5. OPTIMIZACIÓN DE RUTA
            GrafoEntregas grafo = construirGrafo();
            OptimizadorRutas.ResultadoOptimizacion rutaOptimizada = optimizadorRutas.optimizarCompleto(grafo);

            // 6. PLAN DE APILAMIENTO
            String furgonetaId = "FURG-001";
            pilaService.crearPlanApilamiento(furgonetaId, rutaOptimizada.getRutaOptimizada());

            // --- NUEVO: OBTENER TRAZADO PARA EL FRONTEND ---
            List<Punto> puntosParaTrazado = new ArrayList<>();
            puntosParaTrazado.add(ALMACEN); // La ruta empieza en el almacén
            for (Paquete p : rutaOptimizada.getRutaOptimizada()) {
                puntosParaTrazado.add(p.getCoordenadas());
            }
            // Llamamos a la API de Directions de Mapbox
            String trazadoGeoJson = mapboxService.obtenerTrazadoRuta(puntosParaTrazado);

            // 7. RESPUESTA (Pasamos el trazadoGeoJson)
            return construirRespuestaExitosa(paquete, rutaOptimizada, coordenadas, furgonetaId, inicioTiempo, trazadoGeoJson);

        } catch (Exception e) {
            log.error(" Error crítico en el orquestador: ", e);
            return PedidoResponse.builder()
                    .cobertura(false)
                    .estado("ERROR")
                    .mensaje("Error interno: " + e.getMessage())
                    .build();
        }
    }

    /**
     * Obtiene coordenadas con caché
     */
    private Punto obtenerCoordenadas(String direccion, String cp, String municipio) {
        // Intentar primero desde la caché
        Optional<Punto> cached = coordenadasCache.get(direccion);
        if (cached.isPresent()) {
            log.debug("Coordenadas obtenidas de caché");
            return cached.get();
        }

        // Si no está en caché, llamar a Mapbox
        Punto punto = mapboxService.obtenerCoordenadas(direccion, cp, municipio);

        if (punto != null) {
            coordenadasCache.put(direccion, punto);
        }

        return punto;
    }

    /**
     * Crea un objeto Paquete con toda la información consolidada.
     */
    private Paquete crearPaquete(PedidoRequest request, Punto coordenadas, PeticionCliente peticion) {
        String pedidoId = generarIdPedido();

        // 1. Crear el objeto Direccion
        Direccion direccion = Direccion.builder()
                .nombreVia(request.getDireccion().getNombreVia())
                .numero(request.getDireccion().getNumero())
                .municipio(request.getDireccion().getMunicipio())
                .codigoPostal(peticion.getCodigoPostalOficial())
                .provincia(request.getDireccion().getProvincia())
                .tipoVia(request.getDireccion().getTipoVia())
                .build();

        // 2. Extraer datos del Contacto (Antes era Destinatario)
        // Cambiamos getDestinatario() -> getContacto()
        String nombre = request.getContacto().getNombre() != null ? request.getContacto().getNombre() : "";
        String apellidos = request.getContacto().getApellidos() != null ? request.getContacto().getApellidos() : "";

        String nombreCompleto = (nombre + " " + apellidos).trim();

        // 3. Validar peso y prioridad
        double peso = (request.getPeso() != null) ? request.getPeso() : 1.0;
        int prioridad = (request.getPrioridad() != null) ? request.getPrioridad() : 2;

        // 4. Crear el paquete
        return new Paquete(
                pedidoId,
                nombreCompleto,
                direccion,
                coordenadas,
                peso,
                prioridad
        );
    }

    /**
     * Construye el grafo con todos los paquetes pendientes
     */
    private GrafoEntregas construirGrafo() {
        List<Paquete> listaParaGrafo;

        synchronized (paquetesPendientes) {
            // Creamos una copia de la lista actual para que el Grafo trabaje tranquilo
            listaParaGrafo = new ArrayList<>(paquetesPendientes);
        }

        // Ahora pasamos los DOS argumentos que pide tu constructor
        return new GrafoEntregas(ALMACEN, listaParaGrafo);
    }

    /**
     * Actualizamos la firma del método para aceptar el GeoJSON
     */
    private PedidoResponse construirRespuestaExitosa(Paquete paquete,
                                                     OptimizadorRutas.ResultadoOptimizacion ruta,
                                                     Punto coordenadas,
                                                     String furgonetaId,
                                                     long inicioTiempo,
                                                     String geoJson) {

        int ordenEntrega = calcularOrdenEntrega(paquete, ruta);
        int tiempoEstimadoMinutos = (int) (ruta.getDistanciaTotal() / 30.0 * 60);

        return PedidoResponse.builder()
                .pedidoId(paquete.getId())
                .estado("ACEPTADO")
                .cobertura(true)
                .coordenadas(CoordenadasDTO.builder()
                        .lat(coordenadas.lat())
                        .lon(coordenadas.lon())
                        .build())
                .rutaGeoJson(geoJson) // <--- ¡AQUÍ SE ENVÍA EL DIBUJO AL FRONTEND!
                .tiempoProcesamiento(String.valueOf((System.nanoTime() - inicioTiempo) / 1000))
                .mensaje("Pedido aceptado y ruta actualizada")
                .distanciaTotal(ruta.getDistanciaTotal())
                .tiempoEstimado(tiempoEstimadoMinutos + " min")
                .ordenEntrega(ordenEntrega)
                .furgonetaId(furgonetaId)
                .build();
    }

    /**
     * Construye respuesta de rechazo
     */
    private PedidoResponse rechazarPedido(String motivo) {
        return PedidoResponse.builder()
                .pedidoId(null)
                .estado("RECHAZADO")
                .cobertura(false)
                .mensaje(motivo)
                .tiempoProcesamiento("0")
                .build();
    }

    /**
     * Calcula el orden de entrega del paquete en la ruta optimizada
     */
    private int calcularOrdenEntrega(Paquete paquete, OptimizadorRutas.ResultadoOptimizacion ruta) {
        List<Paquete> rutaOptimizada = ruta.getRutaOptimizada();
        for (int i = 0; i < rutaOptimizada.size(); i++) {
            if (rutaOptimizada.get(i).getId().equals(paquete.getId())) {
                return i + 1; // +1 porque empezamos desde 1, no desde 0
            }
        }
        return rutaOptimizada.size(); // Por defecto, al final
    }

    /**
     * Guarda el paquete en el repositorio en memoria
     */
    private void guardarPaquete(Paquete paquete) {
        paquetesPorId.put(paquete.getId(), paquete);
        paquetesPendientes.add(paquete);
    }

    /**
     * Genera ID único para pedido
     */
    private String generarIdPedido() {
        return String.format("PKG-%05d", contadorPedidos.incrementAndGet());
    }

    /**
     * Construye dirección completa para geocodificación
     */
    private String construirDireccionCompleta(PedidoRequest.DireccionDTO direccion) {
        return String.format("%s %s, %s",
                direccion.getNombreVia(),
                direccion.getNumero(),
                direccion.getMunicipio());
    }

    // ========== MÉTODOS AUXILIARES PARA ADMIN/ESTADÍSTICAS ==========

    /**
     * Obtiene todos los paquetes pendientes
     */
    public List<Paquete> obtenerPaquetesPendientes() {
        synchronized (paquetesPendientes) {
            return new ArrayList<>(paquetesPendientes);
        }
    }

    /**
     * Marca un paquete como entregado
     */
    public void marcarComoEntregado(String paqueteId) {
        Paquete paquete = paquetesPorId.get(paqueteId);
        if (paquete != null) {
            paquetesPendientes.remove(paquete);
            log.info("Paquete {} marcado como entregado", paqueteId);
        }
    }

    /**
     * Obtiene el número de pedidos pendientes
     */
    public int contarPedidosPendientes() {
        return paquetesPendientes.size();
    }

    /**
     * Obtiene el total de pedidos procesados
     */
    public int obtenerTotalPedidosProcesados() {
        return contadorPedidos.get();
    }

    /**
     * Limpia todos los paquetes pendientes (útil para testing)
     */
    public void limpiarPaquetesPendientes() {
        paquetesPendientes.clear();
        paquetesPorId.clear();
        log.info("Paquetes pendientes limpiados");
    }
}