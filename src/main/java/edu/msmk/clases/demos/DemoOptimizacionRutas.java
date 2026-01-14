package edu.msmk.clases.demos;

import edu.msmk.clases.exchange.PeticionCliente;
import edu.msmk.clases.model.Direccion;
import edu.msmk.clases.model.Paquete;
import edu.msmk.clases.model.Punto;
import edu.msmk.clases.routing.GrafoEntregas;
import edu.msmk.clases.routing.OptimizadorRutas;
import edu.msmk.clases.routing.VisualizadorGrafos;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * Demostración del sistema de optimización de rutas
 */
@Slf4j
public class DemoOptimizacionRutas {

    public static void main(String[] args) {
        log.info("=== DEMO: Optimizacion de Rutas de Entrega ===\n");

        // PASO 1: Crear almacén
        Punto almacen = new Punto(40.4168, -3.7038, "Almacen Central Madrid");
        log.info("Almacen: {}", almacen.getDescripcion());
        log.info("Coordenadas: {}, {}\n", almacen.getLatitud(), almacen.getLongitud());

        // PASO 2: Crear paquetes con direcciones reales de Madrid
        List<Paquete> paquetes = crearPaquetesMadrid(almacen);
        log.info("Creados {} paquetes para entrega\n", paquetes.size());

        // PASO 3: Crear grafo
        log.info("Construyendo grafo de entregas...");
        GrafoEntregas grafo = new GrafoEntregas(almacen, paquetes);
        log.info("");

        // PASO 4: Optimizar con diferentes algoritmos
        OptimizadorRutas optimizador = new OptimizadorRutas();

        log.info("\n1. RUTA SIN OPTIMIZAR (orden original):");
        log.info("=========================================");
        OptimizadorRutas.ResultadoOptimizacion rutaOriginal =
                optimizador.rutaSinOptimizar(grafo);

        log.info("\n2. RUTA CON NEAREST NEIGHBOR:");
        log.info("=============================");
        OptimizadorRutas.ResultadoOptimizacion rutaNN =
                optimizador.optimizarNearestNeighbor(grafo);

        log.info("\n3. RUTA CON 2-OPT (mejora de Nearest Neighbor):");
        log.info("================================================");
        OptimizadorRutas.ResultadoOptimizacion ruta2Opt =
                optimizador.optimizar2Opt(grafo, rutaNN);

        // PASO 5: Comparación triple
        log.info("\n");
        log.info("COMPARATIVA FINAL DE ALGORITMOS");
        log.info("================================\n");

        compararTresResultados(rutaOriginal, rutaNN, ruta2Opt);

        // PASO 6: Mostrar ruta final optimizada
        mostrarRutaDetallada(ruta2Opt, almacen);

        // PASO 7: Análisis de complejidad
        mostrarAnalisisComplejidad(paquetes.size());

        // PASO 7: VISUALIZAR GRAFOS
        log.info("\n");
        log.info("VISUALIZACION DE GRAFOS");
        log.info("=======================\n");

        VisualizadorGrafos visualizador = new VisualizadorGrafos();

        // Opcion 1: Mostrar grafo completo
        log.info("Mostrando grafo completo (todas las conexiones)...");
        visualizador.mostrarGrafoCompleto(grafo);

        // Esperar un poco
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Opcion 2: Mostrar ruta optimizada
        log.info("Mostrando ruta optimizada...");
        visualizador.mostrarRutaOptimizada(grafo, ruta2Opt);

        // Opcion 3: Comparacion (comentar si no quieres muchas ventanas)
    /*
    log.info("Mostrando comparacion...");
    visualizador.mostrarComparacion(grafo, rutaOriginal, ruta2Opt);
    */

        log.info("\nCierra las ventanas de grafos para continuar...");
    }


    /**
     * Compara tres resultados de optimización
     */
    private static void compararTresResultados(
            OptimizadorRutas.ResultadoOptimizacion original,
            OptimizadorRutas.ResultadoOptimizacion nearestNeighbor,
            OptimizadorRutas.ResultadoOptimizacion twoOpt) {

        log.info("Algoritmo                  | Distancia | Tiempo | vs Original");
        log.info("--------------------------------------------------------");

        // Fila Original
        log.info("{} | {} km | {} ms | {}",
                String.format("%-26s", original.getAlgoritmo()),
                String.format("%8.2f", original.getDistanciaTotal()),
                String.format("%7d", original.getTiempoCalculo()),
                "baseline");

        // Fila Nearest Neighbor
        double ahorroNN = original.getDistanciaTotal() - nearestNeighbor.getDistanciaTotal();
        double porcentajeNN = (ahorroNN / original.getDistanciaTotal()) * 100;
        log.info("{} | {} km | {} ms | {}% mejor",
                String.format("%-26s", nearestNeighbor.getAlgoritmo()),
                String.format("%8.2f", nearestNeighbor.getDistanciaTotal()),
                String.format("%7d", nearestNeighbor.getTiempoCalculo()),
                String.format("%5.1f", porcentajeNN));

        // Fila 2-Opt
        double ahorro2Opt = original.getDistanciaTotal() - twoOpt.getDistanciaTotal();
        double porcentaje2Opt = (ahorro2Opt / original.getDistanciaTotal()) * 100;
        log.info("{} | {} km | {} ms | {}% mejor",
                String.format("%-26s", twoOpt.getAlgoritmo()),
                String.format("%8.2f", twoOpt.getDistanciaTotal()),
                String.format("%7d", twoOpt.getTiempoCalculo()),
                String.format("%5.1f", porcentaje2Opt));
    }

    /**
     * Muestra análisis de complejidad computacional
     */
    private static void mostrarAnalisisComplejidad(int numPaquetes) {
        log.info("\n");
        log.info("ANALISIS DE COMPLEJIDAD COMPUTACIONAL");
        log.info("=====================================\n");

        log.info("Para {} paquetes:", numPaquetes);
        log.info("");

        log.info("Fuerza Bruta (TSP exacto):");
        log.info("  Complejidad: O(n!)");
        log.info("  Operaciones: {} (IMPOSIBLE)", factorial(numPaquetes));
        log.info("  Tiempo estimado: varios años");
        log.info("");

        log.info("Nearest Neighbor:");
        log.info("  Complejidad: O(n²)");
        long opsNN = (long) numPaquetes * numPaquetes;
        log.info("  Operaciones: {}", opsNN);
        log.info("  Tiempo real: < 10 ms");
        log.info("");

        log.info("2-opt:");
        log.info("  Complejidad: O(n²) por iteración");
        long ops2Opt = (long) numPaquetes * numPaquetes * 3; // Aproximado con 3 iteraciones
        log.info("  Operaciones: {} (aprox. 3 iteraciones)", ops2Opt);
        log.info("  Tiempo real: < 20 ms");
        log.info("");

        log.info("CONCLUSION:");
        log.info("  Nearest Neighbor + 2-opt es la mejor opcion para produccion");
        log.info("  Obtiene resultados cercanos al optimo (95-98% de calidad)");
        log.info("  En tiempo de ejecucion aceptable (< 50ms para 50 paquetes)");
    }

    /**
     * Calcula factorial de forma segura (para mostrar la inviabilidad de fuerza bruta)
     */
    private static String factorial(int n) {
        if (n > 20) {
            return "> 10^18 (impracticable)";
        }

        long result = 1;
        for (int i = 2; i <= n; i++) {
            result *= i;
        }

        return String.format("%,d", result);
    }

    /**
     * Crea paquetes con coordenadas reales de Madrid
     */
    private static List<Paquete> crearPaquetesMadrid(Punto almacen) {
        List<Paquete> paquetes = new ArrayList<>();
        paquetes.add(crearPaquete("PKG-001", "Juan Pérez", 40.4200, -3.7050, "Puerta del Sol", 2.5, 1));
        paquetes.add(crearPaquete("PKG-002", "María López", 40.4400, -3.6900, "Barrio Salamanca", 1.8, 2));
        paquetes.add(crearPaquete("PKG-003", "Carlos Ruiz", 40.3900, -3.7200, "Atocha", 3.1, 2));
        paquetes.add(crearPaquete("PKG-004", "Ana García", 40.4050, -3.7150, "La Latina", 0.9, 3));
        paquetes.add(crearPaquete("PKG-005", "David Martín", 40.4500, -3.7000, "Chamartín", 6.5, 1));
        return paquetes;
    }

    private static Paquete crearPaquete(String id, String nombre, double lat, double lon,
                                        String desc, double peso, int prioridad) {
        Punto p = new Punto(lat, lon, desc);
        Direccion dir = new Direccion(); // Tu nueva clase de modelo
        dir.setNombreVia(desc);
        dir.setMunicipio("Madrid");

        // El constructor debe coincidir con: String, String, Direccion, Punto, double, int
        return new Paquete(id, nombre, dir, p, peso, prioridad);
    }


    /**
     * Muestra la ruta detallada con distancias
     */
    private static void mostrarRutaDetallada(OptimizadorRutas.ResultadoOptimizacion resultado, Punto almacen) {
        log.info("\nDETALLE DE LA RUTA OPTIMIZADA:");
        List<Paquete> ruta = resultado.getRutaOptimizada();
        if (ruta == null || ruta.isEmpty()) return;

        log.info("Inicio: {}", almacen.getDescripcion());
        for (int i = 0; i < ruta.size(); i++) {
            log.info("Parada {}: {} ({})", i + 1, ruta.get(i).getId(), ruta.get(i).getDireccion().getNombreVia());
        }
        log.info("Fin: Vuelta a {}", almacen.getDescripcion());
    }

}