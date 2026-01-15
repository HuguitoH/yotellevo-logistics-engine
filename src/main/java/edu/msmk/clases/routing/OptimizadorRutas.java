package edu.msmk.clases.routing;

import edu.msmk.clases.model.Paquete;
import edu.msmk.clases.model.Punto;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

@Slf4j
public class OptimizadorRutas {

    @Getter
    public static class ResultadoOptimizacion {
        private final List<Paquete> rutaOptimizada;
        private final List<Integer> indicesRuta;
        private final double distanciaTotal;
        private final double ahorroPorcentaje;     // Nuevo: Para el gráfico de eficiencia
        private final double combustibleEstimado; // Nuevo: Cálculo basado en 8L/100km
        private final long tiempoCalculo;
        private final String algoritmo;

        public ResultadoOptimizacion(List<Paquete> rutaOptimizada,
                                     List<Integer> indicesRuta,
                                     double distanciaTotal,
                                     double ahorroPorcentaje,
                                     long tiempoCalculo,
                                     String algoritmo) {
            this.rutaOptimizada = rutaOptimizada;
            this.indicesRuta = indicesRuta;
            // Redondeamos a 2 decimales para que el JSON sea limpio
            this.distanciaTotal = Math.round(distanciaTotal * 100.0) / 100.0;
            this.ahorroPorcentaje = Math.round(ahorroPorcentaje * 100.0) / 100.0;
            // Consumo estimado: 8 litros cada 100 km
            this.combustibleEstimado = Math.round((distanciaTotal * 0.08) * 100.0) / 100.0;
            this.tiempoCalculo = tiempoCalculo;
            this.algoritmo = algoritmo;
        }
    }

    /**
     * Ejecuta el flujo completo: Compara ruta base vs optimizada
     */
    public ResultadoOptimizacion optimizarCompleto(GrafoEntregas grafo) {
        long inicioGlobal = System.nanoTime();

        // 1. Generar ruta de referencia (sin optimizar)
        ResultadoOptimizacion rutaBase = rutaSinOptimizar(grafo);

        // 2. Aplicar Fase 1: Vecino más cercano
        ResultadoOptimizacion rutaNN = optimizarNearestNeighbor(grafo);

        // 3. Aplicar Fase 2: 2-Opt (Refinamiento)
        ResultadoOptimizacion resultadoFinal = optimizar2Opt(grafo, rutaNN);

        // 4. Calcular métricas de éxito para el Dashboard
        double ahorro = 0;
        if (rutaBase.getDistanciaTotal() > 0) {
            ahorro = ((rutaBase.getDistanciaTotal() - resultadoFinal.getDistanciaTotal())
                    / rutaBase.getDistanciaTotal()) * 100;
        }

        long finGlobal = System.nanoTime();
        long tiempoTotalMs = (finGlobal - inicioGlobal) / 1_000_000;

        log.info("Optimización finalizada. Ahorro: {}%, Distancia: {} km",
                String.format("%.2f", ahorro), resultadoFinal.getDistanciaTotal());

        return new ResultadoOptimizacion(
                resultadoFinal.getRutaOptimizada(),
                resultadoFinal.getIndicesRuta(),
                resultadoFinal.getDistanciaTotal(),
                ahorro,
                tiempoTotalMs,
                "Híbrido (NN + 2-Opt)"
        );
    }

    public ResultadoOptimizacion optimizarNearestNeighbor(GrafoEntregas grafo) {
        long inicio = System.currentTimeMillis();
        int numPaquetes = grafo.getPaquetes().size();
        List<Integer> indicesRuta = new ArrayList<>();
        Set<Integer> visitados = new HashSet<>();

        int nodoActual = 0;
        indicesRuta.add(nodoActual);
        visitados.add(nodoActual);

        while (visitados.size() <= numPaquetes) {
            int siguienteNodo = encontrarMasCercano(grafo, nodoActual, visitados);
            if (siguienteNodo == -1) break;

            indicesRuta.add(siguienteNodo);
            visitados.add(siguienteNodo);
            nodoActual = siguienteNodo;
        }

        indicesRuta.add(0); // Volver al almacén
        double distanciaTotal = grafo.calcularDistanciaTotal(indicesRuta);

        List<Paquete> rutaPaquetes = new ArrayList<>();
        for (int i = 1; i < indicesRuta.size() - 1; i++) {
            rutaPaquetes.add(grafo.getPaquete(indicesRuta.get(i)));
        }

        return new ResultadoOptimizacion(rutaPaquetes, indicesRuta, distanciaTotal, 0,
                System.currentTimeMillis() - inicio, "Nearest Neighbor");
    }

    public ResultadoOptimizacion optimizar2Opt(GrafoEntregas grafo, ResultadoOptimizacion rutaInicial) {
        long inicio = System.currentTimeMillis();
        List<Integer> ruta = new ArrayList<>(rutaInicial.getIndicesRuta());
        boolean mejora = true;
        double distanciaActual = grafo.calcularDistanciaTotal(ruta);

        while (mejora) {
            mejora = false;
            for (int i = 1; i < ruta.size() - 2; i++) {
                for (int j = i + 1; j < ruta.size() - 1; j++) {
                    double distAntes = grafo.getDistancia(ruta.get(i - 1), ruta.get(i)) +
                            grafo.getDistancia(ruta.get(j), ruta.get(j + 1));
                    double distDespues = grafo.getDistancia(ruta.get(i - 1), ruta.get(j)) +
                            grafo.getDistancia(ruta.get(i), ruta.get(j + 1));

                    if (distDespues < distAntes) {
                        invertirSegmento(ruta, i, j);
                        distanciaActual = grafo.calcularDistanciaTotal(ruta);
                        mejora = true;
                        log.debug("Mejora 2-opt: Nueva distancia {} km", distanciaActual);
                    }
                }
            }
        }

        List<Paquete> rutaPaquetes = new ArrayList<>();
        for (int i = 1; i < ruta.size() - 1; i++) {
            Paquete p = grafo.getPaquete(ruta.get(i));
            p.setOrdenEntrega(i);
            rutaPaquetes.add(p);
        }

        return new ResultadoOptimizacion(rutaPaquetes, ruta, distanciaActual, 0,
                System.currentTimeMillis() - inicio, "2-opt");
    }

    private void invertirSegmento(List<Integer> ruta, int i, int j) {
        while (i < j) {
            int temp = ruta.get(i);
            ruta.set(i, ruta.get(j));
            ruta.set(j, temp);
            i++; j--;
        }
    }

    private int encontrarMasCercano(GrafoEntregas grafo, int nodoActual, Set<Integer> visitados) {
        double distMin = Double.MAX_VALUE;
        int masCercano = -1;
        for (int i = 1; i < grafo.getNumNodos(); i++) {
            if (!visitados.contains(i)) {
                double dist = grafo.getDistancia(nodoActual, i);
                if (dist < distMin) {
                    distMin = dist;
                    masCercano = i;
                }
            }
        }
        return masCercano;
    }

    public ResultadoOptimizacion rutaSinOptimizar(GrafoEntregas grafo) {
        List<Paquete> paquetes = new ArrayList<>(grafo.getPaquetes());
        List<Integer> indices = new ArrayList<>();
        indices.add(0);
        for (int i = 0; i < paquetes.size(); i++) indices.add(i + 1);
        indices.add(0);
        return new ResultadoOptimizacion(paquetes, indices, grafo.calcularDistanciaTotal(indices),
                0, 0, "Original");
    }
}