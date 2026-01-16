package edu.msmk.clases.service.routing;

import edu.msmk.clases.dto.PilaVisualizacionDTO;
import edu.msmk.clases.model.Paquete;
import edu.msmk.clases.model.PaqueteApilado;
import edu.msmk.clases.service.routing.PilaFurgoneta;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Servicio para gestionar la pila de paquetes en furgonetas.
 *
 * FUNCIONALIDAD CLAVE:
 * - Crea planes de apilamiento basados en la ruta optimizada
 * - Invierte el orden de la ruta para LIFO
 * - Gestiona múltiples furgonetas
 * - Genera DTOs para visualización
 */
@Service
@Slf4j
public class PilaService {

    // Repositorio de furgonetas en memoria
    private final Map<String, PilaFurgoneta> furgonetas = new HashMap<>();

    // Configuración por defecto
    private static final int CAPACIDAD_DEFAULT = 50;
    private static final double PESO_MAXIMO_DEFAULT = 500.0; // kg

    /**
     * Crea un plan de apilamiento optimizado basado en la ruta.
     *
     * IMPORTANTE: Los paquetes se apilan en ORDEN INVERSO a la ruta
     * para que el primero que saquemos sea el primero que entreguemos.
     *
     * @param furgonetaId ID de la furgoneta
     * @param rutaOptimizada Lista de paquetes en orden de entrega
     * @return PilaFurgoneta con los paquetes apilados
     */
    public PilaFurgoneta crearPlanApilamiento(String furgonetaId, List<Paquete> rutaOptimizada) {
        log.info("Creando plan de apilamiento para furgoneta: {}", furgonetaId);

        // 1. Crear o recuperar furgoneta
        PilaFurgoneta furgoneta = furgonetas.computeIfAbsent(
                furgonetaId,
                id -> new PilaFurgoneta(id, CAPACIDAD_DEFAULT, PESO_MAXIMO_DEFAULT)
        );

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

        var paqueteApilado = furgoneta.desapilar();
        Paquete paquete = paqueteApilado.getPaquete();

        log.info("Paquete {} descargado de {}. Quedan: {} paquetes",
                paquete.getId(),
                furgonetaId,
                furgoneta.getPaquetesActuales());

        return paquete;
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
            log.warn("Furgoneta {} no encontrada", furgonetaId);
            return PilaVisualizacionDTO.builder()
                    .furgonetaId(furgonetaId)
                    .paquetes(Collections.emptyList())
                    .ordenDescarga(Collections.emptyList())
                    .mensaje("Furgoneta no encontrada")
                    .build();
        }

        // Convertir paquetes apilados a DTOs
        List<PilaVisualizacionDTO.PaqueteVisualDTO> paquetesVisual =
                furgoneta.obtenerVista().stream()
                        .map(this::convertirAPaqueteVisualDTO)
                        .collect(Collectors.toList());

        // Generar orden de descarga
        List<String> ordenDescarga = furgoneta.obtenerVista().stream()
                .map(p -> String.format("%s → %s",
                        p.getPaquete().getId(),
                        p.getPaquete().getDireccion().getNombreVia()))
                .collect(Collectors.toList());

        return PilaVisualizacionDTO.builder()
                .furgonetaId(furgonetaId)
                .capacidadMaxima(furgoneta.getCapacidadMaxima())
                .paquetesActuales(furgoneta.getPaquetesActuales())
                .pesoTotal(furgoneta.getPesoTotal())
                .porcentajeOcupacion(furgoneta.getPorcentajeOcupacion())
                .paquetes(paquetesVisual)
                .ordenDescarga(ordenDescarga)
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
}