package edu.msmk.clases.routing;

import edu.msmk.clases.model.Paquete;
import edu.msmk.clases.model.Punto;
import lombok.extern.slf4j.Slf4j;
import org.graphstream.graph.Graph;
import org.graphstream.graph.Node;
import org.graphstream.graph.Edge;
import org.graphstream.graph.implementations.SingleGraph;
import org.graphstream.ui.swing_viewer.SwingViewer;
import org.graphstream.ui.view.Viewer;

import java.util.List;

/**
 * Visualizador de grafos de rutas usando GraphStream
 */
@Slf4j
public class VisualizadorGrafos {

    public VisualizadorGrafos() {
        // Forzar modo grafico
        System.setProperty("org.graphstream.ui", "swing");
        System.setProperty("java.awt.headless", "false");
    }

    /**
     * Muestra el grafo completo con todas las conexiones
     */
    public void mostrarGrafoCompleto(GrafoEntregas grafoEntregas) {
        log.info("Generando visualizacion del grafo completo...");

        Graph grafo = new SingleGraph("Grafo Completo de Entregas");
        configurarEstilo(grafo, false);

        // Añadir nodos
        añadirNodos(grafo, grafoEntregas);

        // Añadir todas las aristas
        int numNodos = grafoEntregas.getNumNodos();
        int edgeCount = 0;

        for (int i = 0; i < numNodos; i++) {
            for (int j = i + 1; j < numNodos; j++) {
                String nodoOrigen = i == 0 ? "ALM" : "P" + i;
                String nodoDestino = j == 0 ? "ALM" : "P" + j;

                String edgeId = "E" + edgeCount++;
                Edge arista = grafo.addEdge(edgeId, nodoOrigen, nodoDestino, false);

                double distancia = grafoEntregas.getDistancia(i, j);
                arista.setAttribute("ui.label", String.format("%.1f", distancia));
            }
        }

        log.info("Grafo creado: {} nodos, {} aristas",
                grafo.getNodeCount(), grafo.getEdgeCount());

        mostrarGrafo(grafo);
    }

    /**
     * Muestra la ruta optimizada
     */
    public void mostrarRutaOptimizada(GrafoEntregas grafoEntregas,
                                      OptimizadorRutas.ResultadoOptimizacion resultado) {
        log.info("Generando visualizacion de la ruta optimizada...");

        Graph grafo = new SingleGraph("Ruta: " + resultado.getAlgoritmo());
        configurarEstilo(grafo, true);

        // Añadir nodos
        añadirNodos(grafo, grafoEntregas);

        // Añadir solo aristas de la ruta
        List<Integer> rutaIndices = resultado.getIndicesRuta();

        for (int i = 0; i < rutaIndices.size() - 1; i++) {
            int idxOrigen = rutaIndices.get(i);
            int idxDestino = rutaIndices.get(i + 1);

            String nodoOrigen = idxOrigen == 0 ? "ALM" : "P" + idxOrigen;
            String nodoDestino = idxDestino == 0 ? "ALM" : "P" + idxDestino;

            String edgeId = "R" + i;
            Edge arista = grafo.addEdge(edgeId, nodoOrigen, nodoDestino, true);

            double distancia = grafoEntregas.getDistancia(idxOrigen, idxDestino);
            arista.setAttribute("ui.label",
                    String.format("%d: %.1f km", i + 1, distancia));
        }

        log.info("Ruta: {} pasos, {} km total",
                rutaIndices.size() - 1,
                String.format("%.2f", resultado.getDistanciaTotal()));

        mostrarGrafo(grafo);
    }

    /**
     * Configura el estilo CSS del grafo (sin guiones)
     */
    private void configurarEstilo(Graph grafo, boolean esRuta) {
        String colorArista = "#000000";
        String anchoArista = "1px";

        String estiloCSS =
                "graph { " +
                        "    fill-color: #fafafa; " +
                        "    padding: 80px; " +
                        "}" +
                        "node { " +
                        "    size: 25px; " +
                        "    fill-color: #F0754F; " +
                        "    shape: circle; " +
                        "    text-size: 18px; " +
                        "    text-style: bold; " +
                        "    text-color: #fff; " +
                        "    text-alignment: center; " +
                        "    text-offset: 0px, 0px; " +
                        "    stroke-mode: plain; " +
                        "    stroke-color: #F57C00; " +
                        "    stroke-width: 0px; " +
                        "}" +
                        "node.almacen { " +
                        "    fill-color: #F5A94C; " +
                        "    size: 50px; " +
                        "    shape: diamond; " +
                        "    stroke-color: #F5A94C; " +
                        "    stroke-width: 0px; " +
                        "    text-size: 22px; " +
                        "    text-style: bold; " +
                        "    text-color: #fff; " +
                        "    text-alignment: center; " +
                        "    text-offset: 0px, 0px; " +
                        "}" +
                        "node.urgente { " +
                        "    fill-color: #F44336; " +
                        "    stroke-color: #C62828; " +
                        "}" +
                        "node.estandar { " +
                        "    fill-color: #4CAF50; " +
                        "    stroke-color: #2E7D32; " +
                        "}" +
                        "node.economico { " +
                        "    fill-color: #2196F3; " +
                        "    stroke-color: #1565C0; " +
                        "}" +
                        "edge { " +
                        "    fill-color: " + colorArista + "; " +
                        "    size: " + anchoArista + "; " +
                        "    arrow-size: 8px, 8px; " +
                        "    text-size: 12px; " +
                        "    text-style: bold; " +
                        "    text-color: #333; " +
                        "}";

        grafo.setAttribute("ui.stylesheet", estiloCSS);
        grafo.setAttribute("ui.quality");
        grafo.setAttribute("ui.antialias");
    }

    /**
     * Añade nodos al grafo con estilos según prioridad
     */
    private void añadirNodos(Graph grafo, GrafoEntregas grafoEntregas) {
        // Almacen - Diamante rojo en el centro
        Node nodoAlmacen = grafo.addNode("ALM");
        nodoAlmacen.setAttribute("ui.label", "A");
        nodoAlmacen.setAttribute("ui.class", "almacen");
        nodoAlmacen.setAttribute("xyz", 0.0, 0.0, 0.0);

        // Paquetes - Círculos de colores según prioridad
        List<Paquete> paquetes = grafoEntregas.getPaquetes();
        for (int i = 0; i < paquetes.size(); i++) {
            Paquete paquete = paquetes.get(i);
            String nodeId = "P" + (i + 1);

            Node nodo = grafo.addNode(nodeId);

            // Posición
            Punto coords = paquete.getCoordenadas();
            double x = (coords.getLongitud() + 3.7038) * 80;
            double y = (coords.getLatitud() - 40.4168) * 80;

            nodo.setAttribute("xyz", x, y, 0.0);
        }
    }

    /**
     * Muestra el grafo en una ventana
     */
    private void mostrarGrafo(Graph grafo) {
        try {
            // Crear viewer
            SwingViewer viewer = new SwingViewer(grafo, Viewer.ThreadingModel.GRAPH_IN_ANOTHER_THREAD);
            viewer.enableAutoLayout();

            // Abrir ventana
            viewer.addDefaultView(true);

            log.info("Ventana grafica abierta. Cierra la ventana manualmente cuando termines.");

        } catch (Exception e) {
            log.error("Error al mostrar grafo: {}", e.getMessage());
            log.error("Tu sistema no soporta interfaz grafica.");
            log.error("Ejecuta desde IntelliJ o en un sistema con display grafico.");

            // Alternativa: mostrar en consola
            mostrarGrafoEnConsola(grafo);
        }
    }

    /**
     * Muestra representación del grafo en consola (fallback)
     */
    private void mostrarGrafoEnConsola(Graph grafo) {
        log.info("\n========================================");
        log.info("REPRESENTACION DEL GRAFO (Texto)");
        log.info("========================================\n");

        log.info("NODOS ({}):", grafo.getNodeCount());
        grafo.nodes().forEach(nodo -> {
            String label = nodo.getAttribute("ui.label").toString();
            log.info("  - {}", label);
        });

        log.info("\nARISTAS ({}):", grafo.getEdgeCount());
        grafo.edges().forEach(arista -> {
            String label = arista.hasAttribute("ui.label") ?
                    (String) arista.getAttribute("ui.label") : "";
            log.info("  - {} -> {} [{}]",
                    arista.getSourceNode().getAttribute("ui.label"),
                    arista.getTargetNode().getAttribute("ui.label"),
                    label);
        });

        log.info("\n========================================\n");
    }
}