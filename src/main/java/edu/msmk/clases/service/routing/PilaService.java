package edu.msmk.clases.service.routing;

import edu.msmk.clases.dto.PilaVisualizacionDTO;
import edu.msmk.clases.model.Paquete;
import edu.msmk.clases.model.PaqueteApilado;
import edu.msmk.clases.model.Punto;
import edu.msmk.clases.service.geocoding.MapboxService;
import edu.msmk.clases.service.routing.PilaFurgoneta;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Servicio para gestionar la pila de paquetes en furgonetas.
 * FUNCIONALIDAD CLAVE:
 * - Crea planes de apilamiento basados en la ruta optimizada
 * - Invierte el orden de la ruta para LIFO
 * - Gestiona múltiples furgonetas
 * - Genera DTO para visualización
 */
@Service
@Slf4j
public class PilaService {

    @Autowired
    private MapboxService mapboxService;

    // Repositorio de furgonetas en memoria
    private final Map<String, PilaFurgoneta> furgonetas = new HashMap<>();

    // Configuración por defecto
    private static final int CAPACIDAD_DEFAULT = 50;
    private static final double PESO_MAXIMO_DEFAULT = 500.0; // kg
    private static final Punto ALMACEN = new Punto(40.4168, -3.7038, "Almacén Central");


    /**
     * Crea un plan de apilamiento optimizado basado en la ruta.
     * IMPORTANTE: Los paquetes se apilan en ORDEN INVERSO a la ruta
     * para que el primero que saquemos sea el primero que entreguemos.
     *
     * @param furgonetaId ID de la furgoneta
     * @param rutaOptimizada Lista de paquetes en orden de entrega
     * @return PilaFurgoneta con los paquetes apilados
     */
    public PilaFurgoneta crearPlanApilamiento(String furgonetaId, List<Paquete> rutaOptimizada, double distanciaCalculada) {
        log.info("Creando plan de apilamiento para furgoneta: {}", furgonetaId);

        // 1. Recuperar o crear furgoneta
        PilaFurgoneta furgoneta = furgonetas.computeIfAbsent(
                furgonetaId,
                id -> new PilaFurgoneta(id, CAPACIDAD_DEFAULT, PESO_MAXIMO_DEFAULT)
        );

        // !!! IMPORTANTE: LIMPIAR LA FURGONETA ANTES DE RE-APILAR
        // Si tu clase PilaFurgoneta no tiene un método vaciar, puedes
        // simplemente forzar una furgoneta nueva cada vez:
        furgoneta = new PilaFurgoneta(furgonetaId, CAPACIDAD_DEFAULT, PESO_MAXIMO_DEFAULT);
        furgonetas.put(furgonetaId, furgoneta);

        // 2. Invertir el orden (CLAVE para LIFO)
        List<Paquete> ordenApilamiento = new ArrayList<>(rutaOptimizada);
        Collections.reverse(ordenApilamiento);

        log.debug("Orden de ruta: {}",
                rutaOptimizada.stream().map(Paquete::getId).collect(Collectors.toList()));
        log.debug("Orden de apilamiento (inverso): {}",
                ordenApilamiento.stream().map(Paquete::getId).collect(Collectors.toList()));

        // 3. Apilar en orden inverso
        int paquetesApilados = 0;
        for (Paquete paquete : ordenApilamiento) {
            try {
                furgoneta.apilar(paquete);
                paquetesApilados++;
                log.debug("Apilado: {} en posición {}", paquete.getId(), paquetesApilados);
            } catch (IllegalStateException e) {
                log.warn("No se pudo apilar {}: {}", paquete.getId(), e.getMessage());
                break;
            }
        }

        log.info("Plan de apilamiento creado: {} paquetes, {}",
                paquetesApilados, furgoneta.obtenerResumen());

        furgoneta.setDistanciaRuta(distanciaCalculada);

        return furgoneta;
    }

    /**
     * Simula la descarga del siguiente paquete
     *
     * @param furgonetaId ID de la furgoneta
     * @return El paquete descargado
     */
    public Paquete descargarSiguiente(String furgonetaId) {
        PilaFurgoneta furgoneta = obtenerFurgoneta(furgonetaId);

        // 1. PRIMERO: Desapilamos y marcamos como entregado
        PaqueteApilado entregadoApilado = furgoneta.desapilar();
        Paquete paqueteEntregado = entregadoApilado.getPaquete();
        paqueteEntregado.setEstado(Paquete.EstadoPaquete.ENTREGADO);

        // 2. SEGUNDO: Obtenemos lo que queda pendiente
        List<Paquete> pendientes = furgoneta.obtenerVista().stream()
                .map(PaqueteApilado::getPaquete)
                .filter(p -> p.getEstado() != Paquete.EstadoPaquete.ENTREGADO)
                .collect(Collectors.toList());

        // Invertimos para el orden de conducción (Mapbox)
        Collections.reverse(pendientes);

        // 3. TERCERO: Calculamos la distancia si hay paquetes pendientes
        if (pendientes.isEmpty()) {
            furgoneta.setDistanciaRuta(0.0);
        } else {
            List<Punto> puntosRestantes = new ArrayList<>();

            // Añadimos el punto actual (el paquete que acabamos de entregar)
            if (paqueteEntregado.getDireccion() != null && paqueteEntregado.getDireccion().getPunto() != null) {
                puntosRestantes.add(paqueteEntregado.getDireccion().getPunto());
            }

            // Añadimos los puntos de los paquetes que faltan
            for (Paquete p : pendientes) {
                if (p.getDireccion() != null && p.getDireccion().getPunto() != null) {
                    puntosRestantes.add(p.getDireccion().getPunto());
                }
            }

            // Calculamos solo si tenemos al menos 2 puntos (Origen + 1 Destino)
            if (puntosRestantes.size() >= 2) {
                double kmRestantes = calcularDistanciaEntrePuntos(puntosRestantes);
                furgoneta.setDistanciaRuta(kmRestantes);
            } else {
                // Si solo queda un punto, podemos poner una distancia mínima o dejar la que estaba
                furgoneta.setDistanciaRuta(0.0);
            }
        }

        return paqueteEntregado;
    }

    // Método auxiliar para sumar distancias de una lista de puntos
    private double calcularDistanciaEntrePuntos(List<Punto> puntos) {
        // 1. Limpieza de seguridad: quitamos cualquier nulo que se haya colado
        List<Punto> puntosValidos = puntos.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (puntosValidos.size() < 2) {
            log.warn("No hay suficientes puntos válidos para calcular distancia");
            return 0.0;
        }

        // 2. Log seguro (ahora 'p' nunca será null aquí)
        puntosValidos.forEach(p -> log.debug("Punto para matriz: {}, {}", p.lat(), p.lon()));

        // 3. Llamada a Mapbox
        double[][] matriz = mapboxService.obtenerMatrizDistancias(puntosValidos);

        if (matriz == null) {
            log.error("Mapbox service devolvió una matriz nula");
            return 0.0;
        }

        double suma = 0.0;
        for (int i = 0; i < puntosValidos.size() - 1; i++) {
            // Validación adicional de dimensiones de la matriz
            if (i + 1 < matriz.length && i + 1 < matriz[i].length) {
                suma += matriz[i][i+1];
            }
        }
        return suma;
    }

    /**
     * Obtiene el siguiente paquete a descargar sin sacarlo
     */
    public Paquete verSiguiente(String furgonetaId) {
        PilaFurgoneta furgoneta = obtenerFurgoneta(furgonetaId);
        var siguiente = furgoneta.verSiguiente();
        return siguiente != null ? siguiente.getPaquete() : null;
    }

    /**
     * Genera DTO para visualización de la pila
     */

    public PilaVisualizacionDTO obtenerVisualizacion(String furgonetaId) {
        PilaFurgoneta furgoneta = furgonetas.get(furgonetaId);

        if (furgoneta == null) {
            return crearDTORespaldo(furgonetaId, "Esperando ruta...");
        }

        // 1. Filtramos los paquetes que aún no se han entregado para la vista
        List<PilaVisualizacionDTO.PaqueteVisualDTO> paquetesVisual = furgoneta.obtenerVista().stream()
                .filter(p -> p.getPaquete().getEstado() != Paquete.EstadoPaquete.ENTREGADO)
                .map(this::convertirAPaqueteVisualDTO)
                .collect(Collectors.toList());

        // 2. LÓGICA DE DISTANCIA (ESTÁTICA HASTA EL FINAL)
        double distanciaAMostrar;

        if (paquetesVisual.isEmpty()) {
            // Si ya no hay paquetes en la lista visual, ponemos el contador a 0
            distanciaAMostrar = 0.0;
        } else {
            // Mientras haya paquetes, mostramos la distancia total que se calculó al crear la ruta
            // furgoneta.getDistanciaRuta() ya tiene los 36.64 km (o lo que diera la optimización)
            distanciaAMostrar = furgoneta.getDistanciaRuta();

            // Si por alguna razón es 0 pero hay paquetes, intentamos un recalculo rápido
            if (distanciaAMostrar <= 0) {
                distanciaAMostrar = calcularRutaCompleta(furgoneta);
            }
        }

        return PilaVisualizacionDTO.builder()
                .furgonetaId(furgonetaId)
                .capacidadMaxima(furgoneta.getCapacidadMaxima())
                .paquetesActuales(paquetesVisual.size())
                .pesoTotal(furgoneta.getPesoTotal())
                .distanciaTotal(distanciaAMostrar) // <--- El número de la ruta optimizada
                .porcentajeOcupacion((paquetesVisual.size() * 100.0) / furgoneta.getCapacidadMaxima())
                .paquetes(paquetesVisual)
                .ordenDescarga(paquetesVisual.stream()
                        .map(p -> p.getId() + " → " + p.getDireccion())
                        .collect(Collectors.toList()))
                .build();
    }

    /**
     * Convierte un PaqueteApilado a DTO para visualización
     */
    private PilaVisualizacionDTO.PaqueteVisualDTO convertirAPaqueteVisualDTO(
            PaqueteApilado apilado) {

        Paquete paquete = apilado.getPaquete();

        return PilaVisualizacionDTO.PaqueteVisualDTO.builder()
                .id(paquete.getId())
                .destinatario(paquete.getDestinatario())
                .direccion(paquete.getDireccion().getNombreVia() + " " +
                        paquete.getDireccion().getNumero())
                .peso(paquete.getPeso())
                .prioridad(paquete.getPrioridad())
                .ordenCarga(apilado.getOrdenCarga())
                .posicionZ(apilado.getPosicionZ())
                .color(apilado.getColorHex())
                .etiqueta(apilado.getEtiqueta())
                .build();
    }

    /**
     * Obtiene una furgoneta o lanza excepción si no existe
     */
    private PilaFurgoneta obtenerFurgoneta(String furgonetaId) {
        PilaFurgoneta furgoneta = furgonetas.get(furgonetaId);
        if (furgoneta == null) {
            throw new IllegalArgumentException("Furgoneta no encontrada: " + furgonetaId);
        }
        return furgoneta;
    }

    /**
     * Limpia todas las furgonetas (útil para testing)
     */
    public void limpiarFurgonetas() {
        furgonetas.clear();
        log.info("Todas las furgonetas limpiadas");
    }

    /**
     * Obtiene el listado de todas las furgonetas activas
     */
    public List<String> obtenerFurgonetasActivas() {
        return new ArrayList<>(furgonetas.keySet());
    }

    /**
     * Verifica si una furgoneta existe
     */
    public boolean existeFurgoneta(String furgonetaId) {
        return furgonetas.containsKey(furgonetaId);
    }

    private double calcularDistanciaRutaReal(List<Paquete> ruta) {
        // Si no hay nada en la ruta, efectivamente es 0
        if (ruta == null || ruta.isEmpty()) return 0.0;

        // 1. Extraemos los puntos (coordenadas)
        List<Punto> puntos = ruta.stream()
                .map(p -> p.getDireccion().getPunto())
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        // SI SOLO QUEDA UN PUNTO:
        // Significa que estamos en camino al último paquete.
        // Podríamos devolver una distancia mínima estimada o
        // calcularla desde el punto anterior si lo tuviéramos.
        if (puntos.size() < 2) {
            // Si hay paquetes pero no puntos suficientes para una matriz,
            // devolvemos un valor simbólico o 0.0 si ya se considera entregado.
            return ruta.size() > 0 ? 0.5 : 0.0;
        }

        // 2. Pedimos la matriz a Mapbox
        double[][] matriz = mapboxService.obtenerMatrizDistancias(puntos);

        if (matriz == null) return 0.0;

        // 3. Suma de distancias
        double sumaKM = 0.0;
        for (int i = 0; i < puntos.size() - 1; i++) {
            // Evitamos errores de índice si la matriz es más pequeña que los puntos
            if (i + 1 < matriz.length && i + 1 < matriz[i].length) {
                sumaKM += matriz[i][i+1];
            }
        }

        return sumaKM;
    }
    /**
     * Crea un DTO de respuesta básico cuando no hay datos o hay errores
     */
    private PilaVisualizacionDTO crearDTORespaldo(String furgonetaId, String mensaje) {
        return PilaVisualizacionDTO.builder()
                .furgonetaId(furgonetaId)
                .paquetes(new ArrayList<>())
                .ordenDescarga(new ArrayList<>())
                .distanciaTotal(0.0)
                .mensaje(mensaje)
                .build();
    }

    /**
     * Recalcula la ruta completa desde el almacén pasando por todos los paquetes
     * que no han sido entregados.
     */
    private double calcularRutaCompleta(PilaFurgoneta furgoneta) {
        List<Paquete> pendientes = furgoneta.obtenerVista().stream()
                .map(PaqueteApilado::getPaquete)
                .filter(p -> p.getEstado() != Paquete.EstadoPaquete.ENTREGADO)
                .collect(Collectors.toList());

        // Invertimos para que el orden sea el de entrega
        Collections.reverse(pendientes);

        if (pendientes.isEmpty()) return 0.0;

        List<Punto> puntos = new ArrayList<>();
        puntos.add(ALMACEN); // Salida desde almacén

        for (Paquete p : pendientes) {
            if (p.getDireccion() != null && p.getDireccion().getPunto() != null) {
                puntos.add(p.getDireccion().getPunto());
            }
        }

        return calcularDistanciaEntrePuntos(puntos);
    }
}