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
import java.util.stream.Collectors;

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
    public PedidoResponse procesarPedido(PedidoRequest request) {
        long inicioTiempo = System.nanoTime();
        try {
            log.info("Procesando nuevo pedido para: {}", request.getDireccion().getMunicipio());

            // 1, 2 y 3. Parseo, Cobertura y Geocodificación
            PeticionCliente peticion = direccionParser.parsear(
                    request.getDireccion().getMunicipio(),
                    request.getDireccion().getProvincia(),
                    request.getDireccion().getNombreVia(),
                    Integer.parseInt(request.getDireccion().getNumero())
            );

            if (peticion == null) return rechazarPedido("Dirección no válida");

            boolean tieneCobertura = coberturaServicio.damosServicio(peticion);
            if (!tieneCobertura) return rechazarPedido("Sin servicio en esta zona");

            String direccionCompleta = construirDireccionCompleta(request.getDireccion());
            Punto coordenadas = obtenerCoordenadas(direccionCompleta, peticion.getCodigoPostalOficial(), request.getDireccion().getMunicipio());

            // 4. CREAR PAQUETE
            Paquete paquete = crearPaquete(request, coordenadas, peticion);

            synchronized (paquetesPendientes) {
                // A. Bloqueo si la furgoneta ya está en la calle (si hay algún ENTREGADO)
                boolean furgonetaYaEnMovimiento = paquetesPendientes.stream()
                        .anyMatch(p -> p.getEstado() == Paquete.EstadoPaquete.ENTREGADO);

                if (furgonetaYaEnMovimiento) {
                    paquete.setEstado(Paquete.EstadoPaquete.EN_ESPERA);
                    paquete.setOrdenEntrega(0);
                    guardarPaquete(paquete);
                    log.info("Furgoneta en reparto. Pedido {} en cola (EN_ESPERA)", paquete.getId());

                    return PedidoResponse.builder()
                            .pedidoId(paquete.getId())
                            .estado("EN_ESPERA")
                            .cobertura(true)
                            .mensaje("Pedido recibido. Se entregará en la próxima salida.")
                            .coordenadas(CoordenadasDTO.builder().lat(coordenadas.lat()).lon(coordenadas.lon()).build())
                            .build();
                }

                // B. Cargar como PENDIENTE
                paquete.setEstado(Paquete.EstadoPaquete.PENDIENTE);
                guardarPaquete(paquete);

                // --- NUEVA LÓGICA DE OPTIMIZACIÓN REAL (SIMPLIFICADA) ---

                // 1. El método construirGrafo() ya encapsula la llamada a Mapbox Matrix
                GrafoEntregas grafo = construirGrafo();

                // 2. Optimizamos la ruta con los datos reales
                OptimizadorRutas.ResultadoOptimizacion rutaOptimizada = optimizadorRutas.optimizarCompleto(grafo);

                // 3. Asignación de orden y estado (Usa tu método sincronizado)
                List<Paquete> listaOrdenada = rutaOptimizada.getRutaOptimizada();
                actualizarOrdenGlobal(listaOrdenada);

                // C. PLAN DE APILAMIENTO Y TRAZADO MAPBOX
                String furgonetaId = "FURG-001";
                double distanciaKM = rutaOptimizada.getDistanciaTotal();
                pilaService.crearPlanApilamiento(furgonetaId, listaOrdenada, distanciaKM);

                // Preparamos los puntos para que Mapbox dibuje la línea por la carretera
                List<Punto> puntosParaTrazado = new ArrayList<>();
                puntosParaTrazado.add(ALMACEN);
                listaOrdenada.forEach(p -> puntosParaTrazado.add(p.getCoordenadas()));
                puntosParaTrazado.add(ALMACEN);

                String trazadoGeoJson = mapboxService.obtenerTrazadoRuta(puntosParaTrazado);

                return construirRespuestaExitosa(paquete, rutaOptimizada, coordenadas, furgonetaId, inicioTiempo, trazadoGeoJson);
            }

        } catch (Exception e) {
            log.error("Error crítico: ", e);
            return PedidoResponse.builder().estado("ERROR").mensaje(e.getMessage()).build();
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
     * IMPORTANTE: El grafo para el Dashboard debe seguir viendo los nodos
     * aunque estén entregados para no romperse.
     */
    private GrafoEntregas construirGrafo() {
        List<Paquete> soloParaRutaActual;
        synchronized (paquetesPendientes) {
            soloParaRutaActual = paquetesPendientes.stream()
                    .filter(p -> p.getEstado() != Paquete.EstadoPaquete.ENTREGADO)
                    .filter(p -> p.getEstado() != Paquete.EstadoPaquete.EN_ESPERA)
                    .collect(Collectors.toList());
        }

        if (soloParaRutaActual.isEmpty()) {
            return new GrafoEntregas(ALMACEN, soloParaRutaActual, null);
        }

        // 1. Preparamos los puntos para la Matrix API
        List<Punto> puntosParaMatriz = new ArrayList<>();
        puntosParaMatriz.add(ALMACEN);
        soloParaRutaActual.forEach(p -> puntosParaMatriz.add(p.getCoordenadas()));

        // 2. Obtenemos la matriz real
        double[][] matrizReal = mapboxService.obtenerMatrizDistancias(puntosParaMatriz);

        // 3. Ahora pasamos los 3 argumentos: el tercero es la matriz (o null si falla)
        return new GrafoEntregas(ALMACEN, soloParaRutaActual, matrizReal);
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
     * Marca un paquete como entregado (BORRADO LÓGICO)
     * Ahora no lo borramos de las listas, solo cambiamos su estado
     */
    public void marcarComoEntregado(String paqueteId) {
        Paquete paquete = paquetesPorId.get(paqueteId);
        if (paquete != null) {
            paquete.setEstado(Paquete.EstadoPaquete.ENTREGADO);
            log.info("Entregado: {}. Manteniendo orden fijo hasta vaciar pila.", paqueteId);

            // SOLO comprobamos si la furgoneta ha terminado toda su tarea
            boolean rutaTerminada = paquetesPendientes.stream()
                    .noneMatch(p -> p.getEstado() == Paquete.EstadoPaquete.PENDIENTE ||
                            p.getEstado() == Paquete.EstadoPaquete.EN_RUTA);

            if (rutaTerminada) {
                // Solo cuando ya no queda NADA por entregar en la calle,
                // procedemos a la limpieza y carga de lo que estaba "en espera"
                finalizarRutaYPrepararSiguiente();
            }
        }
    }

    /**
     * Obtiene el número de pedidos pendientes filtrando por estado
     */
    public int contarPedidosPendientes() {
        // Usamos paquetesPendientes que es la lista que ya tienes declarada
        return (int) paquetesPendientes.stream()
                .filter(p -> p.getEstado() != Paquete.EstadoPaquete.ENTREGADO)
                .count();
    }



    /**
     * Obtiene el total de pedidos procesados
     */
    public int obtenerTotalPedidosProcesados() {
        return contadorPedidos.get();
    }

    public List<Paquete> obtenerTodosLosPaquetes() {
        synchronized (paquetesPendientes) {
            List<Paquete> copia = new ArrayList<>(paquetesPendientes);
            // Ordenamos por ID para que el Hash sea consistente siempre
            copia.sort(Comparator.comparing(Paquete::getId));
            return copia;
        }
    }

    /**
     * Limpia todos los paquetes pendientes (útil para testing)
     */
    public void limpiarPaquetesPendientes() {
        paquetesPendientes.clear();
        paquetesPorId.clear();
        log.info("Paquetes pendientes limpiados");
    }

    /**
     * Actualiza el orden de entrega garantizando coherencia geográfica.
     * Eliminamos el offset para que cada nueva ruta (o auto-carga)
     * comience una secuencia limpia desde el 1.
     */
    private void actualizarOrdenGlobal(List<Paquete> rutaNueva) {
        synchronized (paquetesPendientes) {
            // 1. Asignamos el orden empezando SIEMPRE desde 1
            // Esto evita saltos visuales en el trazado de Mapbox
            for (int i = 0; i < rutaNueva.size(); i++) {
                Paquete p = rutaNueva.get(i);
                p.setOrdenEntrega(i + 1);

                // Aprovechamos para asegurar que el estado sea EN_RUTA
                if (p.getEstado() == Paquete.EstadoPaquete.PENDIENTE) {
                    p.setEstado(Paquete.EstadoPaquete.EN_RUTA);
                }
            }

            log.info("Ruta optimizada: Orden de entrega reseteado para {} paquetes activos.", rutaNueva.size());
        }
    }

    public void finalizarRutaYPrepararSiguiente() {
        synchronized (paquetesPendientes) {
            // 1. Limpieza de los que ya se entregaron
            paquetesPendientes.removeIf(p -> p.getEstado() == Paquete.EstadoPaquete.ENTREGADO);

            // 2. Activamos los que estaban en espera (naranjas)
            paquetesPendientes.forEach(p -> {
                if (p.getEstado() == Paquete.EstadoPaquete.EN_ESPERA) {
                    p.setEstado(Paquete.EstadoPaquete.PENDIENTE);
                }
            });

            // 3. AUTO-CARGA: Si hay paquetes listos, optimizamos la nueva ruta
            if (!paquetesPendientes.isEmpty()) {
                // Usamos tu método estrella que ya trae la matriz real
                GrafoEntregas nuevoGrafo = construirGrafo();

                OptimizadorRutas.ResultadoOptimizacion nuevaRuta = optimizadorRutas.optimizarCompleto(nuevoGrafo);

                // 4. Sincronizamos órdenes, estados y creamos la pila LIFO
                actualizarOrdenGlobal(nuevaRuta.getRutaOptimizada());
                pilaService.crearPlanApilamiento("FURG-001", nuevaRuta.getRutaOptimizada(), nuevaRuta.getDistanciaTotal());

                log.info("Auto-carga completada: {} pedidos listos para nueva salida.", paquetesPendientes.size());
            }
        }
    }
}