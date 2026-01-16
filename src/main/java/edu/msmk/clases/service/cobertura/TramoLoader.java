package edu.msmk.clases.service.cobertura;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.concurrent.atomic.LongAdder;

@Service
@Slf4j
public class TramoLoader {

    private final CoberturaServicio coberturaServicio;
    private final IndiceGeografico indiceGeografico;

    public TramoLoader(CoberturaServicio coberturaServicio, IndiceGeografico indiceGeografico) {
        this.coberturaServicio = coberturaServicio;
        this.indiceGeografico = indiceGeografico;
    }

    /**
     * Método que recibe el nombre del archivo desde ClasesApplication
     */
    public void cargarTramosDesdeArchivo(String nombreArchivoManual) {
        log.info("📂 Iniciando ingesta de datos desde: {}", nombreArchivoManual);

        // Necesitamos estas variables para contar el progreso
        LongAdder procesados = new LongAdder();
        LongAdder errores = new LongAdder();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                getClass().getClassLoader().getResourceAsStream(nombreArchivoManual),
                StandardCharsets.ISO_8859_1))) {

            if (reader == null) {
                log.error("❌ No se encontró el archivo: {}", nombreArchivoManual);
                return;
            }

            reader.lines()
                    .parallel() // Carga ultra rápida usando todos los núcleos del procesador
                    .forEach(linea -> {
                        if (linea.length() >= 180) {
                            if (procesarLinea(linea)) {
                                procesados.increment();
                            } else {
                                errores.increment();
                            }
                        }
                    });

            log.info("✅ Proceso finalizado. Exitosos: {} | Errores/Ignorados: {}",
                    procesados.sum(), errores.sum());

        } catch (Exception e) {
            log.error("❌ Error crítico en el acceso al archivo: {}", e.getMessage());
        }
    }

    private boolean procesarLinea(String linea) {
        try {
            // 1. Extracción posicional según el formato CSI
            int cpro = Integer.parseInt(linea.substring(0, 2));
            int cmum = Integer.parseInt(linea.substring(2, 5));
            int cvia = Integer.parseInt(linea.substring(20, 25));
            String cp = linea.substring(42, 47).trim();

            int tinum = Integer.parseInt(linea.substring(47, 48));
            int ein = Integer.parseInt(linea.substring(48, 52));
            int esn = Integer.parseInt(linea.substring(53, 57));

            // 2. Extracción de nombres
            String munRaw = linea.substring(94, 134).trim();
            String patronVia = linea.substring(20, 25);
            int indexNombre = linea.indexOf(patronVia, 60) + 5;
            String viaRaw = linea.substring(indexNombre, indexNombre + 25).trim();

            String municipioNorm = normalizar(munRaw);
            String viaNorm = normalizar(viaRaw);

            // 3. REGISTRO EN MEMORIA (Crucial para el All-In)
            coberturaServicio.registrarNombreMunicipio(cpro, municipioNorm, cmum);
            coberturaServicio.registrarVia(cpro, cmum, viaNorm, cvia);
            coberturaServicio.addTramo(cpro, cmum, cvia, ein, esn, tinum, cp);

            // Opcional: registrar en el índice antiguo si aún lo usas
            indiceGeografico.indexarTramo(cpro, cmum, municipioNorm, cvia, viaNorm);

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private String normalizar(String texto) {
        if (texto == null || texto.isEmpty()) return "";
        return Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase()
                .trim()
                .replaceAll("\\s+", " ");
    }
}