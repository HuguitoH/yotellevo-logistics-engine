package edu.msmk.clases.service.cobertura;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.LongAdder;

@Service
@Slf4j
public class TramoLoader {

    public static double ultimaLatenciaMs = 0;
    public static long totalRegistrosIndexados = 0;
    public static double ultimoThroughput = 0;

    private final CoberturaServicio coberturaServicio;
    private final IndiceGeografico indiceGeografico;
    private final NormalizacionService normalizacionService;

    public TramoLoader(CoberturaServicio coberturaServicio,
                       IndiceGeografico indiceGeografico,
                       NormalizacionService normalizacionService) {
        this.coberturaServicio = coberturaServicio;
        this.indiceGeografico = indiceGeografico;
        this.normalizacionService = normalizacionService;
    }

    /**
     * ⚡⚡⚡ VERSIÓN ULTRA-OPTIMIZADA (Todas las mejoras juntas)
     */
    public void cargarTramosOptimizado(String nombreArchivoManual) {
        long tiempoInicio = System.nanoTime();
        log.info("Iniciando ingesta OPTIMIZADA desde: {}", nombreArchivoManual);

        LongAdder procesados = new LongAdder();
        LongAdder errores = new LongAdder();

        try {
            // OPTIMIZACIÓN 1: Memory-mapped I/O (leer todo de golpe)
            InputStream is = getClass().getClassLoader()
                    .getResourceAsStream(nombreArchivoManual);

            if (is == null) {
                log.error("No se encontró el archivo: {}", nombreArchivoManual);
                return;
            }

            log.debug("Leyendo archivo completo a memoria...");
            byte[] allBytes = is.readAllBytes();
            String contenidoCompleto = new String(allBytes, StandardCharsets.ISO_8859_1);
            String[] lineas = contenidoCompleto.split("\n");
            log.debug("Cargadas {} líneas en memoria", lineas.length);

            // OPTIMIZACIÓN 2: Particionamiento por provincia
            log.debug("Agrupando por provincia...");
            Map<Integer, List<String>> porProvincia = new ConcurrentHashMap<>();

            for (String linea : lineas) {
                if (linea.length() >= 180) {
                    try {
                        int cpro = Integer.parseInt(linea.substring(0, 2));
                        porProvincia.computeIfAbsent(cpro, k -> new ArrayList<>())
                                .add(linea);
                    } catch (Exception e) {
                        errores.increment();
                    }
                }
            }
            log.debug("Datos agrupados en {} provincias", porProvincia.size());

            // Procesar en paralelo
            // Usa solo P-cores para trabajo CPU-intensivo
            int pCores = Runtime.getRuntime().availableProcessors() / 2;
            ExecutorService executor = Executors.newFixedThreadPool(pCores);
            log.debug("Procesando con {} threads...", pCores);

            List<Future<?>> futures = new ArrayList<>();

            for (Map.Entry<Integer, List<String>> entry : porProvincia.entrySet()) {
                Future<?> future = executor.submit(() -> {
                    for (String linea : entry.getValue()) {
                        if (procesarLinea(linea)) {
                            procesados.increment();
                        } else {
                            errores.increment();
                        }
                    }
                });
                futures.add(future);
            }

            for (Future<?> future : futures) {
                future.get();
            }

            executor.shutdown();
            executor.awaitTermination(5, TimeUnit.MINUTES);

            long tiempoFin = System.nanoTime();
            generarMetricas(tiempoInicio, tiempoFin, procesados, errores, "ULTRA-OPT");

            // Estadísticas del caché compartido
            log.info("Caché de normalización: {} entradas",
                    normalizacionService.getCacheSize());

        } catch (Exception e) {
            log.error("Error crítico: {}", e.getMessage());
        }
    }

    private boolean procesarLinea(String linea) {
        try {
            int cpro = Integer.parseInt(linea.substring(0, 2));
            int cmum = Integer.parseInt(linea.substring(2, 5));
            int cvia = Integer.parseInt(linea.substring(20, 25));
            String cp = linea.substring(42, 47).trim();

            int tinum = Integer.parseInt(linea.substring(47, 48));
            int ein = Integer.parseInt(linea.substring(48, 52));
            int esn = Integer.parseInt(linea.substring(53, 57));

            String munRaw = linea.substring(94, 134).trim();
            String patronVia = linea.substring(20, 25);
            int indexNombre = linea.indexOf(patronVia, 60) + 5;
            String viaRaw = linea.substring(indexNombre, indexNombre + 25).trim();

            // OPTIMIZACIÓN 3: Usar normalización con caché compartido
            String municipioNorm = normalizacionService.normalizar(munRaw);
            String viaNorm = normalizacionService.normalizar(viaRaw);

            coberturaServicio.registrarNombreMunicipio(cpro, municipioNorm, cmum);
            coberturaServicio.registrarVia(cpro, cmum, viaNorm, cvia);
            coberturaServicio.addTramo(cpro, cmum, cvia, ein, esn, tinum, cp);
            indiceGeografico.indexarTramo(cpro, cmum, municipioNorm, cvia, viaNorm);

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void generarMetricas(long inicio, long fin, LongAdder procesados,
                                 LongAdder errores, String version) {
        long duracionNanos = fin - inicio;
        ultimaLatenciaMs = duracionNanos / 1_000_000.0;
        totalRegistrosIndexados = procesados.sum();

        if (ultimaLatenciaMs > 0) {
            ultimoThroughput = (totalRegistrosIndexados / (ultimaLatenciaMs / 1000.0));
        }

        log.info("LOAD_DONE | MODE: {} | SUCCESS: {} | ERRORS: {} | LATENCY: {}ms | THROUGHPUT: {}reg/s | STATUS: READY",
                version,
                String.format("%,d", totalRegistrosIndexados),
                errores.sum(),
                String.format("%.2f", ultimaLatenciaMs),
                String.format("%,.0f", ultimoThroughput));
    }
}