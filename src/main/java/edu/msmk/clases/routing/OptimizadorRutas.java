package edu.msmk.clases.routing;

import edu.msmk.clases.model.Paquete;
import edu.msmk.clases.model.Punto;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * Optimizador de rutas de entrega usando algoritmo Nearest Neighbor (Vecino más cercano)
 *
 * Complejidad: O(n²)
 * Calidad: Aproximadamente 120% de la solución óptima
 *
 * Algoritmo greedy:
 * 1. Empezar en el almacén
 * 2. Ir siempre al paquete más cercano no visitado
 * 3. Repetir hasta entregar todos
 * 4. Volver al almacén
 */
@Slf4j
public class OptimizadorRutas {

    /**
     * Resultado de la optimización con métricas
     */
    @Getter
    public static class ResultadoOptimizacion {
        private final List<Paquete> rutaOptimizada;
        private final List<Integer> indicesRuta;
        private final double distanciaTotal;
        private final long tiempoCalculo;
        private final String algoritmo;

        public ResultadoOptimizacion(List<Paquete> rutaOptimizada,
                                     List<Integer> indicesRuta,
                                     double distanciaTotal,
                                     long tiempoCalculo,
                                     String algoritmo) {
            this.rutaOptimizada = rutaOptimizada;
            this.indicesRuta = indicesRuta;
            this.distanciaTotal = distanciaTotal;
            this.tiempoCalculo = tiempoCalculo;
            this.algoritmo = algoritmo;
        }

        @Override
        public String toString() {
            return String.format(
                    "Resultado[algoritmo=%s, distancia=%.2f km, tiempo=%d ms, paquetes=%d]",
                    algoritmo, distanciaTotal, tiempoCalculo, rutaOptimizada.size()
            );
        }
    }

    /**
     * Optimiza la ruta usando Nearest Neighbor
     *
     * @param grafo Grafo con distancias precalculadas
     * @return Resultado con la ruta optimizada
     */
    public ResultadoOptimizacion optimizarNearestNeighbor(GrafoEntregas grafo) {
        log.info("Iniciando optimizacion Nearest Neighbor...");
        long inicio = System.currentTimeMillis();

        int numPaquetes = grafo.getPaquetes().size();
        List<Integer> indicesRuta = new ArrayList<>();
        Set<Integer> visitados = new HashSet<>();

        // Empezar en el almacén (índice 0)
        int nodoActual = 0;
        indicesRuta.add(nodoActual);
        visitados.add(nodoActual);

        log.info("Ruta: Almacen");

        // Mientras haya paquetes por visitar
        while (visitados.size() <= numPaquetes) {
            int siguienteNodo = encontrarMasCercano(grafo, nodoActual, visitados);

            if (siguienteNodo == -1) {
                // No hay más nodos por visitar
                break;
            }

            indicesRuta.add(siguienteNodo);
            visitados.add(siguienteNodo);

            Paquete paquete = grafo.getPaquete(siguienteNodo);
            double distancia = grafo.getDistancia(nodoActual, siguienteNodo);

            log.info("  -> Paquete {} ({}km)", paquete.getId(),
                    String.format("%.2f", distancia));

            nodoActual = siguienteNodo;
        }

        // Volver al almacén
        indicesRuta.add(0);
        double distanciaVuelta = grafo.getDistancia(nodoActual, 0);
        log.info("  -> Almacen ({}km)", String.format("%.2f", distanciaVuelta));

        // Calcular distancia total
        double distanciaTotal = grafo.calcularDistanciaTotal(indicesRuta);

        // Convertir índices a paquetes
        List<Paquete> rutaPaquetes = new ArrayList<>();
        for (int i = 1; i < indicesRuta.size() - 1; i++) {
            rutaPaquetes.add(grafo.getPaquete(indicesRuta.get(i)));
        }

        // Asignar orden de entrega
        for (int i = 0; i < rutaPaquetes.size(); i++) {
            rutaPaquetes.get(i).setOrdenEntrega(i + 1);
        }

        long tiempoCalculo = System.currentTimeMillis() - inicio;

        log.info("Optimizacion completada:");
        log.info("  Distancia total: {} km", String.format("%.2f", distanciaTotal));
        log.info("  Tiempo calculo: {} ms", tiempoCalculo);

        return new ResultadoOptimizacion(
                rutaPaquetes,
                indicesRuta,
                distanciaTotal,
                tiempoCalculo,
                "Nearest Neighbor"
        );
    }

    /**
     * Encuentra el nodo más cercano no visitado
     *
     * @param grafo Grafo con distancias
     * @param nodoActual Nodo desde el que buscar
     * @param visitados Set de nodos ya visitados
     * @return Índice del nodo más cercano, o -1 si todos están visitados
     */
    private int encontrarMasCercano(GrafoEntregas grafo, int nodoActual, Set<Integer> visitados) {
        double distanciaMinima = Double.MAX_VALUE;
        int nodoMasCercano = -1;

        // Buscar entre todos los nodos no visitados
        for (int i = 1; i < grafo.getNumNodos(); i++) {
            if (!visitados.contains(i)) {
                double distancia = grafo.getDistancia(nodoActual, i);

                if (distancia < distanciaMinima) {
                    distanciaMinima = distancia;
                    nodoMasCercano = i;
                }
            }
        }

        return nodoMasCercano;
    }

    /**
     * Optimiza una ruta usando 2-opt
     * Toma una ruta inicial y la mejora intercambiando pares de aristas
     *
     * Algoritmo:
     * 1. Tomar ruta inicial (puede ser de Nearest Neighbor)
     * 2. Para cada par de aristas, intentar invertir el segmento entre ellas
     * 3. Si la inversión reduce la distancia, aplicarla
     * 4. Repetir hasta que no haya mejoras
     *
     * Complejidad: O(n²) por iteración, típicamente 2-5 iteraciones
     *
     * @param grafo Grafo con distancias
     * @param rutaInicial Ruta inicial a mejorar
     * @return Resultado con la ruta mejorada
     */
    public ResultadoOptimizacion optimizar2Opt(GrafoEntregas grafo,
                                               ResultadoOptimizacion rutaInicial) {
        log.info("Iniciando optimizacion 2-opt...");
        log.info("Ruta inicial: {} km", String.format("%.2f", rutaInicial.getDistanciaTotal()));

        long inicio = System.currentTimeMillis();

        // Convertir ruta de paquetes a índices (incluye almacén al inicio y final)
        List<Integer> ruta = new ArrayList<>(rutaInicial.getIndicesRuta());

        boolean mejora = true;
        int iteraciones = 0;
        double distanciaActual = grafo.calcularDistanciaTotal(ruta);

        while (mejora) {
            mejora = false;
            iteraciones++;

            // Probar todos los pares de aristas
            for (int i = 1; i < ruta.size() - 2; i++) {
                for (int j = i + 1; j < ruta.size() - 1; j++) {

                    // Calcular distancia actual de las dos aristas
                    double distanciaAntes =
                            grafo.getDistancia(ruta.get(i - 1), ruta.get(i)) +
                                    grafo.getDistancia(ruta.get(j), ruta.get(j + 1));

                    // Calcular distancia después de invertir el segmento
                    double distanciaDespues =
                            grafo.getDistancia(ruta.get(i - 1), ruta.get(j)) +
                                    grafo.getDistancia(ruta.get(i), ruta.get(j + 1));

                    // Si mejora, invertir el segmento
                    if (distanciaDespues < distanciaAntes) {
                        invertirSegmento(ruta, i, j);
                        mejora = true;

                        double nuevaDistancia = grafo.calcularDistanciaTotal(ruta);
                        double ahorro = distanciaActual - nuevaDistancia;

                        log.info("  Iteracion {}: Mejora encontrada (ahorro: {} km)",
                                iteraciones,
                                String.format("%.3f", ahorro));

                        distanciaActual = nuevaDistancia;
                    }
                }
            }

            if (!mejora) {
                log.info("  Iteracion {}: No se encontraron mas mejoras", iteraciones);
            }
        }

        double distanciaFinal = grafo.calcularDistanciaTotal(ruta);
        long tiempoCalculo = System.currentTimeMillis() - inicio;

        // Convertir índices a paquetes
        List<Paquete> rutaPaquetes = new ArrayList<>();
        for (int i = 1; i < ruta.size() - 1; i++) {
            Paquete paquete = grafo.getPaquete(ruta.get(i));
            paquete.setOrdenEntrega(i);
            rutaPaquetes.add(paquete);
        }

        double mejoraPorcentaje = ((rutaInicial.getDistanciaTotal() - distanciaFinal) /
                rutaInicial.getDistanciaTotal()) * 100;

        log.info("Optimizacion 2-opt completada:");
        log.info("  Distancia inicial: {} km",
                String.format("%.2f", rutaInicial.getDistanciaTotal()));
        log.info("  Distancia final: {} km", String.format("%.2f", distanciaFinal));
        log.info("  Mejora: {} % en {} iteraciones",
                String.format("%.2f", mejoraPorcentaje), iteraciones);
        log.info("  Tiempo calculo: {} ms", tiempoCalculo);

        return new ResultadoOptimizacion(
                rutaPaquetes,
                ruta,
                distanciaFinal,
                tiempoCalculo,
                "2-opt"
        );
    }

    /**
     * Invierte un segmento de la ruta
     * Ejemplo: [0, 1, 2, 3, 4, 5] con i=2, j=4 -> [0, 1, 4, 3, 2, 5]
     *
     * @param ruta Lista de índices de la ruta
     * @param i Inicio del segmento a invertir
     * @param j Fin del segmento a invertir
     */
    private void invertirSegmento(List<Integer> ruta, int i, int j) {
        while (i < j) {
            // Intercambiar elementos
            int temp = ruta.get(i);
            ruta.set(i, ruta.get(j));
            ruta.set(j, temp);
            i++;
            j--;
        }
    }

    /**
     * Optimiza usando Nearest Neighbor + 2-opt (combinado)
     * Este es el método recomendado para producción
     *
     * @param grafo Grafo con distancias
     * @return Mejor resultado obtenido
     */
    public ResultadoOptimizacion optimizarCompleto(GrafoEntregas grafo) {
        log.info("\n");
        log.info("OPTIMIZACION COMPLETA (Nearest Neighbor + 2-opt)");
        log.info("=================================================");

        // Paso 1: Generar ruta inicial con Nearest Neighbor
        log.info("\nPaso 1: Generar ruta inicial con Nearest Neighbor");
        ResultadoOptimizacion rutaNN = optimizarNearestNeighbor(grafo);

        // Paso 2: Mejorar con 2-opt
        log.info("\nPaso 2: Mejorar con 2-opt");
        ResultadoOptimizacion ruta2Opt = optimizar2Opt(grafo, rutaNN);

        // Crear resultado combinado
        long tiempoTotal = rutaNN.getTiempoCalculo() + ruta2Opt.getTiempoCalculo();

        log.info("\nRESULTADO FINAL:");
        log.info("  Distancia final: {} km", String.format("%.2f", ruta2Opt.getDistanciaTotal()));
        log.info("  Tiempo total: {} ms", tiempoTotal);

        return new ResultadoOptimizacion(
                ruta2Opt.getRutaOptimizada(),
                ruta2Opt.getIndicesRuta(),
                ruta2Opt.getDistanciaTotal(),
                tiempoTotal,
                "Nearest Neighbor + 2-opt"
        );
    }

    /**
     * Genera una ruta sin optimizar (orden original)
     * Útil para comparar con la ruta optimizada
     */
    public ResultadoOptimizacion rutaSinOptimizar(GrafoEntregas grafo) {
        log.info("Generando ruta sin optimizar (orden original)...");
        long inicio = System.currentTimeMillis();

        List<Paquete> paquetes = new ArrayList<>(grafo.getPaquetes());
        List<Integer> indicesRuta = new ArrayList<>();

        // Almacén -> Paquetes en orden original -> Almacén
        indicesRuta.add(0);
        for (int i = 0; i < paquetes.size(); i++) {
            indicesRuta.add(i + 1);
            paquetes.get(i).setOrdenEntrega(i + 1);
        }
        indicesRuta.add(0);

        double distanciaTotal = grafo.calcularDistanciaTotal(indicesRuta);
        long tiempoCalculo = System.currentTimeMillis() - inicio;

        log.info("Ruta sin optimizar:");
        log.info("  Distancia total: {} km", String.format("%.2f", distanciaTotal));

        return new ResultadoOptimizacion(
                paquetes,
                indicesRuta,
                distanciaTotal,
                tiempoCalculo,
                "Sin optimizar"
        );
    }

    /**
     * Compara dos resultados de optimización
     */
    public void compararResultados(ResultadoOptimizacion resultado1,
                                   ResultadoOptimizacion resultado2) {
        log.info("\n");
        log.info("COMPARATIVA DE RUTAS");
        log.info("====================");

        log.info("\n{}: {} km en {} ms",
                resultado1.getAlgoritmo(),
                String.format("%.2f", resultado1.getDistanciaTotal()),
                resultado1.getTiempoCalculo());

        log.info("{}: {} km en {} ms",
                resultado2.getAlgoritmo(),
                String.format("%.2f", resultado2.getDistanciaTotal()),
                resultado2.getTiempoCalculo());

        double ahorro = resultado1.getDistanciaTotal() - resultado2.getDistanciaTotal();
        double porcentajeAhorro = (ahorro / resultado1.getDistanciaTotal()) * 100;

        log.info("\nAHORRO:");
        log.info("  Distancia: {} km ({} %)",
                String.format("%.2f", ahorro),
                String.format("%.1f", porcentajeAhorro));

        if (porcentajeAhorro > 0) {
            log.info("  La ruta optimizada es {} % mas eficiente",
                    String.format("%.1f", porcentajeAhorro));
        } else {
            log.info("  La ruta original es mejor (caso raro con pocos paquetes)");
        }
    }
}