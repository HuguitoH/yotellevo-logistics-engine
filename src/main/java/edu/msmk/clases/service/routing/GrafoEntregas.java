package edu.msmk.clases.service.routing;

import edu.msmk.clases.model.Paquete;
import edu.msmk.clases.model.Punto;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * Representa un grafo de puntos de entrega con matriz de distancias precalculada.
 * Utiliza la fórmula Haversine para considerar la curvatura terrestre.
 *
 * OPTIMIZACIONES:
 * - Matriz de distancias precalculada O(1) lookup
 * - Validación eficiente sin exceptions
 * - Acceso directo a matriz para algoritmos
 */
@Slf4j
@Getter
public class GrafoEntregas {
    private final Punto almacen;
    private final List<Paquete> paquetes;
    private final double[][] matrizDistancias;
    private final int numNodos;

    /**
     * Constructor que acepta matriz externa (Mapbox) o calcula fallback
     */
    public GrafoEntregas(Punto almacen, List<Paquete> paquetes, double[][] matrizMapbox) {
        this.almacen = almacen;
        this.paquetes = (paquetes == null) ? Collections.emptyList() :
                Collections.unmodifiableList(paquetes);
        this.numNodos = this.paquetes.size() + 1;

        if (matrizMapbox != null) {
            this.matrizDistancias = matrizMapbox;
            log.info("Grafo creado usando Matriz Real de Mapbox ({} nodos).", numNodos);
        } else {
            this.matrizDistancias = new double[numNodos][numNodos];
            calcularDistancias();
            log.warn("Mapbox Matrix falló. Usando distancias Haversine (línea recta).");
        }
    }

    /**
     * OPTIMIZADO: Calcula matriz de adyacencia completa
     * Complejidad O(n²) pero solo se ejecuta UNA vez
     */
    private void calcularDistancias() {
        long inicio = System.currentTimeMillis();

        // Distancias Almacén <-> Paquetes
        for (int i = 0; i < paquetes.size(); i++) {
            double dist = redondear(almacen.distanciaHaversine(paquetes.get(i).getCoordenadas()));
            matrizDistancias[0][i + 1] = dist;
            matrizDistancias[i + 1][0] = dist;
        }

        // Distancias Paquetes <-> Paquetes (solo triángulo superior)
        for (int i = 0; i < paquetes.size(); i++) {
            for (int j = i + 1; j < paquetes.size(); j++) {
                double dist = redondear(paquetes.get(i).getCoordenadas()
                        .distanciaHaversine(paquetes.get(j).getCoordenadas()));

                matrizDistancias[i + 1][j + 1] = dist;
                matrizDistancias[j + 1][i + 1] = dist; // Simétrica
            }
        }

        log.info("Matriz {}x{} calculada en {} ms", numNodos, numNodos,
                System.currentTimeMillis() - inicio);
    }

    /**
     * OPTIMIZADO: Acceso a distancia SIN try-catch (elimina overhead)
     * Los algoritmos deben acceder directamente a la matriz cuando sea posible
     */
    public double getDistancia(int i, int j) {
        // Validación simple (más rápida que exceptions)
        if (i < 0 || i >= numNodos || j < 0 || j >= numNodos) {
            log.error("Índice fuera de rango: [{},{}] (max: {})", i, j, numNodos - 1);
            return 0.0;
        }
        return matrizDistancias[i][j];
    }

    /**
     * ✅ OPTIMIZADO: Cálculo de distancia total con acceso directo
     */
    public double calcularDistanciaTotal(List<Integer> ruta) {
        if (ruta == null || ruta.size() < 2) return 0.0;

        double suma = 0.0;
        for (int i = 0; i < ruta.size() - 1; i++) {
            int from = ruta.get(i);
            int to = ruta.get(i + 1);

            // Validación inline para evitar exceptions
            if (from >= 0 && from < numNodos && to >= 0 && to < numNodos) {
                suma += matrizDistancias[from][to];
            }
        }
        return redondear(suma);
    }

    public Paquete getPaquete(int indice) {
        if (indice <= 0 || indice > paquetes.size()) {
            log.warn("Índice de paquete fuera de rango: {} (0=almacén)", indice);
            return null;
        }
        return paquetes.get(indice - 1);
    }

    private double redondear(double valor) {
        // Precisión de 1 metro (3 decimales en km)
        return Math.round(valor * 1000.0) / 1000.0;
    }

    public Map<String, Double> getMetricasGrafo() {
        if (numNodos <= 1) return Map.of("distanciaMedia", 0.0, "distanciaMaxima", 0.0);

        double suma = 0;
        double max = 0;

        // Distancias desde almacén (índice 0) a todos los paquetes
        for (int i = 1; i < numNodos; i++) {
            double dist = matrizDistancias[0][i];
            suma += dist;
            if (dist > max) max = dist;
        }

        return Map.of(
                "distanciaMedia", redondear(suma / (numNodos - 1)),
                "distanciaMaxima", redondear(max)
        );
    }

    /**
     * Debugging: imprime la matriz formateada
     */
    public void mostrarMatriz() {
        if (numNodos > 20) {
            log.info("Matriz demasiado grande para mostrar ({} nodos)", numNodos);
            return;
        }

        StringBuilder sb = new StringBuilder("\nMATRIZ DE DISTANCIAS (km)\n     ");
        for (int i = 0; i < numNodos; i++) {
            sb.append(String.format(" %-6s", i == 0 ? "ALM" : "P" + i));
        }
        sb.append("\n");

        for (int i = 0; i < numNodos; i++) {
            sb.append(String.format("%-4s ", i == 0 ? "ALM" : "P" + i));
            for (int j = 0; j < numNodos; j++) {
                sb.append(String.format("[%5.2f] ", matrizDistancias[i][j]));
            }
            sb.append("\n");
        }
        log.info(sb.toString());
    }
}