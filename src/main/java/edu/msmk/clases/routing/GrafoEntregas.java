package edu.msmk.clases.routing;

import edu.msmk.clases.model.Paquete;
import edu.msmk.clases.model.Punto;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Representa un grafo de puntos de entrega con matriz de distancias precalculada.
 * Utiliza la fórmula Haversine para considerar la curvatura terrestre.
 */
@Slf4j
@Getter
public class GrafoEntregas {

    private final Punto almacen;
    private final List<Paquete> paquetes;
    private final double[][] matrizDistancias;
    private final int numNodos;

    public GrafoEntregas(Punto almacen, List<Paquete> paquetes) {
        if (almacen == null) {
            throw new IllegalArgumentException("El almacén de origen no puede ser nulo.");
        }

        this.almacen = almacen;

        // 1. Filtrado moderno (Java 8+) para eliminar nulos y paquetes sin coordenadas
        this.paquetes = (paquetes == null) ? new ArrayList<>() :
                paquetes.stream()
                        .filter(p -> p != null && p.getCoordenadas() != null)
                        .collect(Collectors.toList());

        // 2. Definición del tamaño del grafo (N+1: Almacén + Paquetes)
        this.numNodos = this.paquetes.size() + 1;
        this.matrizDistancias = new double[numNodos][numNodos];

        // 3. Solo calculamos si hay nodos que procesar
        if (numNodos > 1) {
            calcularDistancias();
        } else {
            log.warn("Grafo creado sin paquetes válidos. Solo se dispone del nodo Almacén.");
        }
    }

    /**
     * Calcula la matriz de adyacencia completa.
     * Complejidad O(n²) pero necesaria para algoritmos de optimización de rutas (TSP).
     */
    private void calcularDistancias() {
        long inicio = System.currentTimeMillis();

        // Distancias Almacén <-> Paquetes
        for (int i = 0; i < paquetes.size(); i++) {
            double dist = redondear(almacen.distanciaHaversine(paquetes.get(i).getCoordenadas()));
            matrizDistancias[0][i + 1] = dist;
            matrizDistancias[i + 1][0] = dist;
        }

        // Distancias Paquetes <-> Paquetes
        for (int i = 0; i < paquetes.size(); i++) {
            for (int j = i + 1; j < paquetes.size(); j++) {
                double dist = redondear(paquetes.get(i).getCoordenadas()
                        .distanciaHaversine(paquetes.get(j).getCoordenadas()));

                matrizDistancias[i + 1][j + 1] = dist;
                matrizDistancias[j + 1][i + 1] = dist;
            }
        }

        log.info("Matriz de {}x{} calculada en {} ms", numNodos, numNodos, System.currentTimeMillis() - inicio);
    }

    private double redondear(double valor) {
        // Redondeo a 3 decimales (precisión de 1 metro aprox)
        return Math.round(valor * 1000.0) / 1000.0;
    }

    public double getDistancia(int i, int j) {
        try {
            return matrizDistancias[i][j];
        } catch (ArrayIndexOutOfBoundsException e) {
            log.error("Error al acceder a la matriz: índice [{}][{}] fuera de rango.", i, j);
            return 0.0;
        }
    }

    public Paquete getPaquete(int indice) {
        if (indice <= 0 || indice > paquetes.size()) {
            return null; // Evitamos la excepción para que el optimizador sea más fluido
        }
        return paquetes.get(indice - 1);
    }

    /**
     * Suma las aristas de una secuencia de nodos.
     */
    public double calcularDistanciaTotal(List<Integer> ruta) {
        if (ruta == null || ruta.size() < 2) return 0.0;

        double suma = 0.0;
        for (int i = 0; i < ruta.size() - 1; i++) {
            suma += getDistancia(ruta.get(i), ruta.get(i + 1));
        }
        return redondear(suma);
    }


    public Map<String, Double> getMetricasGrafo() {
        if (numNodos <= 1) return Map.of("distanciaMedia", 0.0, "distanciaMaxima", 0.0);

        double suma = 0;
        int cont = 0;
        // Distancias desde el almacén (índice 0) a todos los paquetes
        for (int i = 1; i < numNodos; i++) {
            suma += matrizDistancias[0][i];
            cont++;
        }

        return Map.of(
                "distanciaMedia", redondear(suma / cont),
                "distanciaMaxima", redondear(Arrays.stream(matrizDistancias[0]).max().orElse(0.0))
        );
    }

    /**
     * Debugging elegante: imprime la matriz formateada en el log
     */
    public void mostrarMatriz() {
        StringBuilder sb = new StringBuilder("\nMATRIZ DE DISTANCIAS (km)\n     ");
        for (int i = 0; i < numNodos; i++) sb.append(String.format(" %-6s", i == 0 ? "ALM" : "P" + i));
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