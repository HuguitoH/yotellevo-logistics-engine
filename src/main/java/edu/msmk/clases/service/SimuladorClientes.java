package edu.msmk.clases.service;

import edu.msmk.clases.exchange.PeticionCliente;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Service
@Slf4j
public class SimuladorClientes {

    /**
     * Simula múltiples clientes haciendo peticiones concurrentes al sistema
     *
     * @param cobertura Servicio de cobertura a probar
     * @param numClientes Número de hilos (clientes) simultáneos
     * @param peticionesPorCliente Peticiones que hace cada cliente
     * @return Estadísticas de la simulación
     */
    public EstadisticasSimulacion simularCargaConcurrente(
            CoberturaServicio cobertura,
            int numClientes,
            int peticionesPorCliente) {

        log.info("\n");
        log.info("  SIMULADOR DE CLIENTES CONCURRENTES");
        log.info("\n");
        log.info("Configuración:");
        log.info("  → Clientes simultáneos: {}", numClientes);
        log.info("  → Peticiones por cliente: {}", peticionesPorCliente);
        log.info("  → Total de peticiones: {}", numClientes * peticionesPorCliente);

        // Contadores atómicos (thread-safe)
        AtomicInteger peticionesExitosas = new AtomicInteger(0);
        AtomicInteger peticionesFallidas = new AtomicInteger(0);
        AtomicLong tiempoTotalNanos = new AtomicLong(0);

        // Pool de hilos para simular clientes concurrentes
        ExecutorService executor = Executors.newFixedThreadPool(numClientes);
        CountDownLatch latch = new CountDownLatch(numClientes);

        // Generar peticiones de prueba variadas
        List<PeticionCliente> peticionesPrueba = generarPeticionesPrueba(100);

        log.info("\nIniciando simulación...\n");
        long inicioSimulacion = System.nanoTime();

        // Lanzar clientes concurrentes
        for (int i = 0; i < numClientes; i++) {
            final int clienteId = i + 1;

            executor.submit(() -> {
                try {
                    Random random = new Random();

                    // Cada cliente hace N peticiones
                    for (int j = 0; j < peticionesPorCliente; j++) {
                        try {
                            // Seleccionar petición aleatoria
                            PeticionCliente peticion = peticionesPrueba.get(
                                    random.nextInt(peticionesPrueba.size())
                            );

                            // Medir tiempo de la petición
                            long inicio = System.nanoTime();
                            boolean resultado = cobertura.damosServicio(peticion);
                            long tiempo = System.nanoTime() - inicio;

                            // Actualizar contadores
                            tiempoTotalNanos.addAndGet(tiempo);
                            if (resultado) {
                                peticionesExitosas.incrementAndGet();
                            } else {
                                peticionesFallidas.incrementAndGet();
                            }

                            // Log cada 1000 peticiones
                            int totalProcesadas = peticionesExitosas.get() + peticionesFallidas.get();
                            if (totalProcesadas % 10000 == 0) {
                                log.info("[Progreso] {} peticiones procesadas...", totalProcesadas);
                            }

                        } catch (Exception e) {
                            peticionesFallidas.incrementAndGet();
                            log.error("[Cliente-{}] Error en petición: {}", clienteId, e.getMessage());
                        }
                    }

                } finally {
                    latch.countDown();
                }
            });
        }

        // Esperar a que todos los clientes terminen
        try {
            latch.await();
        } catch (InterruptedException e) {
            log.error("Simulación interrumpida: {}", e.getMessage());
            Thread.currentThread().interrupt();
        }

        long tiempoSimulacion = System.nanoTime() - inicioSimulacion;
        executor.shutdown();

        // Calcular estadísticas
        int totalPeticiones = peticionesExitosas.get() + peticionesFallidas.get();
        double tiempoPromedioNs = tiempoTotalNanos.get() / (double) totalPeticiones;
        double tiempoPromedioUs = tiempoPromedioNs / 1000.0;
        double tiempoSimulacionMs = tiempoSimulacion / 1_000_000.0;
        double throughput = (totalPeticiones * 1_000_000_000.0) / tiempoSimulacion;

        // Crear objeto con estadísticas
        EstadisticasSimulacion stats = new EstadisticasSimulacion(
                numClientes,
                peticionesPorCliente,
                totalPeticiones,
                peticionesExitosas.get(),
                peticionesFallidas.get(),
                tiempoPromedioNs,
                tiempoPromedioUs,
                tiempoSimulacionMs,
                throughput
        );

        // Mostrar resultados
        mostrarResultados(stats);

        return stats;
    }

    /**
     * Genera un conjunto de peticiones de prueba variadas
     */
    private List<PeticionCliente> generarPeticionesPrueba(int cantidad) {
        List<PeticionCliente> peticiones = new ArrayList<>();
        Random random = new Random();

        // Peticiones variadas para probar diferentes casos
        for (int i = 0; i < cantidad; i++) {
            try {
                // Generar códigos aleatorios dentro de rangos razonables
                int provincia = 1 + random.nextInt(52);  // 1-52 (provincias España)
                int municipio = 1 + random.nextInt(999); // 1-999
                int unidadPobl = 1000 + random.nextInt(9000); // 1000-9999
                int via = 1000 + random.nextInt(9000); // 1000-9999
                int numero = 1 + random.nextInt(200); // 1-200

                peticiones.add(new PeticionCliente(
                        provincia, municipio, unidadPobl, via, numero
                ));
            } catch (Exception e) {
                // Ignorar peticiones inválidas
            }
        }

        // Añadir algunas peticiones conocidas si están cubiertas
        try {
            peticiones.add(new PeticionCliente(1, 1, 1701, 1001, 15));
            peticiones.add(new PeticionCliente(1, 1, 1701, 1002, 8));
            peticiones.add(new PeticionCliente(28, 115, 4301, 2492, 5));
        } catch (Exception e) {
            // Ignorar
        }

        log.info("Generadas {} peticiones de prueba", peticiones.size());
        return peticiones;
    }

    /**
     * Muestra los resultados de la simulación
     */
    private void mostrarResultados(EstadisticasSimulacion stats) {
        double porcentajeExitosas = (stats.exitosas * 100.0 / stats.totalPeticiones);
        double porcentajeFallidas = (stats.fallidas * 100.0 / stats.totalPeticiones);

        log.info("\n");
        log.info("  Restultados de la Simulación");
        log.info("\n");
        log.info("\nPETICIONES:");
        log.info("  → Total procesadas: {}", stats.totalPeticiones);
        log.info("  → Exitosas: {} ({}%)",
                stats.exitosas,
                String.format("%.2f", porcentajeExitosas));
        log.info("  → Fallidas: {} ({}%)",
                stats.fallidas,
                String.format("%.2f", porcentajeFallidas));

        log.info("\nRENDIMIENTO:");
        log.info("  → Tiempo promedio por petición: {} ns ({} μs)",
                String.format("%.2f", stats.tiempoPromedioNs),
                String.format("%.3f", stats.tiempoPromedioUs));
        log.info("  → Tiempo total simulación: {} ms ({} seg)",
                String.format("%.2f", stats.tiempoSimulacionMs),
                String.format("%.2f", stats.tiempoSimulacionMs / 1000.0));

        log.info("\nTHROUGHPUT:");
        log.info("  → Peticiones/segundo: {}", String.format("%,.0f", stats.throughput));
        log.info("  → Peticiones/minuto: {}", String.format("%,.0f", stats.throughput * 60));

        log.info("\nANÁLISIS:");
        if (stats.tiempoPromedioUs < 1.0) {
            log.info("EXCELENTE: Tiempo de respuesta < 1 microsegundo");
        } else if (stats.tiempoPromedioUs < 10.0) {
            log.info("MUY BUENO: Tiempo de respuesta < 10 microsegundos");
        } else if (stats.tiempoPromedioUs < 100.0) {
            log.info("ACEPTABLE: Tiempo de respuesta < 100 microsegundos");
        } else {
            log.info("MEJORABLE: Tiempo de respuesta > 100 microsegundos");
        }

        if (stats.throughput > 1_000_000) {
            log.info("CAPACIDAD: Más de 1 millón de peticiones/segundo");
        } else if (stats.throughput > 100_000) {
            log.info("CAPACIDAD: Más de 100k peticiones/segundo");
        } else if (stats.throughput > 10_000) {
            log.info("CAPACIDAD: Más de 10k peticiones/segundo");
        }

    }

    /**
     * Clase para almacenar estadísticas de la simulación
     */
    public static class EstadisticasSimulacion {
        public final int numClientes;
        public final int peticionesPorCliente;
        public final int totalPeticiones;
        public final int exitosas;
        public final int fallidas;
        public final double tiempoPromedioNs;
        public final double tiempoPromedioUs;
        public final double tiempoSimulacionMs;
        public final double throughput;

        public EstadisticasSimulacion(int numClientes, int peticionesPorCliente,
                                      int totalPeticiones, int exitosas, int fallidas,
                                      double tiempoPromedioNs, double tiempoPromedioUs,
                                      double tiempoSimulacionMs, double throughput) {
            this.numClientes = numClientes;
            this.peticionesPorCliente = peticionesPorCliente;
            this.totalPeticiones = totalPeticiones;
            this.exitosas = exitosas;
            this.fallidas = fallidas;
            this.tiempoPromedioNs = tiempoPromedioNs;
            this.tiempoPromedioUs = tiempoPromedioUs;
            this.tiempoSimulacionMs = tiempoSimulacionMs;
            this.throughput = throughput;
        }
    }
}