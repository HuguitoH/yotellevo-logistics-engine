package edu.msmk.clases.routing;

import edu.msmk.clases.model.Paquete;
import edu.msmk.clases.model.Punto;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * Representa un grafo de puntos de entrega con matriz de distancias
 * Estructura: Grafo completo ponderado (todos los nodos conectados)
 */
@Slf4j
@Getter
public class GrafoEntregas {

    private final Punto almacen;
    private final List<Paquete> paquetes;
    private final double[][] matrizDistancias;
    private final int numNodos;

    /**
     * Constructor del grafo
     * @param almacen Punto de inicio/fin (almacén)
     * @param paquetes Lista de paquetes a entregar
     */
    public GrafoEntregas(Punto almacen, List<Paquete> paquetes) {
        this.almacen = almacen;

        // 1. Filtramos la lista para eliminar cualquier elemento null
        // que haya podido llegar por error de la API o la Demo
        this.paquetes = new ArrayList<>();
        if (paquetes != null) {
            for (Paquete p : paquetes) {
                if (p != null) { // <--- VALIDACIÓN CRUCIAL
                    this.paquetes.add(p);
                }
            }
        }

        // 2. Validar que todos los paquetes filtrados tengan coordenadas
        for (Paquete p : this.paquetes) {
            if (p.getCoordenadas() == null) {
                throw new IllegalArgumentException(
                        "Paquete " + p.getId() + " no tiene coordenadas asignadas"
                );
            }
        }

        // 3. El grafo tiene N+1 nodos: almacén + N paquetes válidos
        this.numNodos = this.paquetes.size() + 1;
        this.matrizDistancias = new double[numNodos][numNodos];

        calcularDistancias();
    }

    /**
     * Calcula todas las distancias entre todos los puntos (O(n²))
     * Índice 0 = Almacén
     * Índices 1..N = Paquetes
     */
    private void calcularDistancias() {
        log.info("Calculando matriz de distancias para {} nodos...", numNodos);
        long inicio = System.currentTimeMillis();

        // Distancias del almacén a cada paquete
        for (int i = 0; i < paquetes.size(); i++) {
            double distancia = almacen.distanciaHaversine(paquetes.get(i).getCoordenadas());
            matrizDistancias[0][i + 1] = distancia;
            matrizDistancias[i + 1][0] = distancia;
        }

        // Distancias entre cada par de paquetes
        for (int i = 0; i < paquetes.size(); i++) {
            for (int j = i + 1; j < paquetes.size(); j++) {
                double distancia = paquetes.get(i).getCoordenadas()
                        .distanciaHaversine(paquetes.get(j).getCoordenadas());

                matrizDistancias[i + 1][j + 1] = distancia;
                matrizDistancias[j + 1][i + 1] = distancia;
            }
        }

        long tiempo = System.currentTimeMillis() - inicio;
        log.info("Matriz de distancias calculada en {} ms", tiempo);
    }

    /**
     * Obtiene la distancia entre dos nodos
     * @param i Índice nodo origen (0=almacén, 1..N=paquetes)
     * @param j Índice nodo destino
     * @return Distancia en kilómetros
     */
    public double getDistancia(int i, int j) {
        return matrizDistancias[i][j];
    }

    /**
     * Obtiene el paquete en el índice dado
     * @param indice Índice del paquete (1..N)
     * @return Paquete
     */
    public Paquete getPaquete(int indice) {
        if (indice == 0) {
            throw new IllegalArgumentException("Índice 0 es el almacén, no un paquete");
        }
        return paquetes.get(indice - 1);
    }

    /**
     * Calcula la distancia total de una ruta
     * @param ruta Lista de índices de nodos (debe empezar y terminar en 0)
     * @return Distancia total en kilómetros
     */
    public double calcularDistanciaTotal(List<Integer> ruta) {
        double distanciaTotal = 0.0;

        for (int i = 0; i < ruta.size() - 1; i++) {
            distanciaTotal += getDistancia(ruta.get(i), ruta.get(i + 1));
        }

        return distanciaTotal;
    }

    /**
     * Muestra la matriz de distancias (útil para debugging)
     */
    public void mostrarMatriz() {
        log.info("\n=== MATRIZ DE DISTANCIAS (km) ===");

        // Encabezado
        System.out.print("      ALM");
        for (int i = 0; i < paquetes.size(); i++) {
            System.out.printf("  P%02d", i + 1);
        }
        System.out.println();

        // Filas
        for (int i = 0; i < numNodos; i++) {
            if (i == 0) {
                System.out.print("ALM ");
            } else {
                System.out.printf("P%02d ", i);
            }

            for (int j = 0; j < numNodos; j++) {
                System.out.printf("%5.1f", matrizDistancias[i][j]);
            }
            System.out.println();
        }
    }
}