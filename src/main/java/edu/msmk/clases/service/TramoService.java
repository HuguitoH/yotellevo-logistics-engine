package edu.msmk.clases.service;

import edu.msmk.clases.CoberturaServicio;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

@Service
@Slf4j
public class TramoService {

    /**
     * Lee el archivo de tramos y carga la cobertura según estructura BOE
     *
     * ESTRUCTURA BOE (posiciones):
     * - CPRO (0-2): Código provincia (2 dígitos)
     * - CMUM (2-5): Código municipio (3 dígitos)
     * - DIST (5-7): Distrito (2 dígitos)
     * - SECC (7-10): Sección (3 dígitos)
     * - LSECC (10-11): Letra sección (1 char)
     * - SUBSC (11-13): Subsección (2 chars)
     * - CUN (13-20): Código unidad poblacional (7 dígitos)
     * - CVIA (20-25): Código vía (5 dígitos)
     * - CPSVIA (25-30): Código pseudovía (5 dígitos)
     * - MANZ (30-42): Manzana (12 chars)
     * - CPOS (42-47): Código postal (5 dígitos)
     * - TINUM (47-48): Tipo numeración (1 dígito)
     * - EIN (48-52): Extremo inferior numeración (4 dígitos)
     * - CEIN (52-53): Calificador EIN (1 char)
     * - ESN (53-57): Extremo superior numeración (4 dígitos)
     * - CESN (57-58): Calificador ESN (1 char)
     */
    public CoberturaServicio leerTramos() throws IOException {
        CoberturaServicio miCobertura = new CoberturaServicio();

        ClassPathResource resource = new ClassPathResource("TRAM.EMPRESA");

        if (!resource.exists()) {
            throw new IOException("El archivo TRAM.D250630.G250702 no existe en los recursos");
        }

        log.info("Iniciando carga del archivo de tramos (estructura BOE)...");
        long inicio = System.currentTimeMillis();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.ISO_8859_1))) {

            String linea;
            int numeroLinea = 0;
            int lineasValidas = 0;
            int lineasDescartadas = 0;

            while ((linea = reader.readLine()) != null) {
                numeroLinea++;

                // La línea debe tener al menos 58 caracteres para contener todos los campos básicos
                if (linea.trim().isEmpty() || linea.length() < 58) {
                    lineasDescartadas++;
                    continue;
                }

                try {
                    // Extraer campos según posiciones BOE
                    String cpro = extraerCampo(linea, 0, 2);        // Provincia
                    String cmum = extraerCampo(linea, 2, 5);        // Municipio
                    String cun = extraerCampo(linea, 13, 20);       // Unidad poblacional
                    String cvia = extraerCampo(linea, 20, 25);      // Código vía
                    String ein = extraerCampo(linea, 48, 52);       // Extremo inferior numeración
                    String esn = extraerCampo(linea, 53, 57);       // Extremo superior numeración
                    String tinum = extraerCampo(linea, 47, 48);     // Tipo de numeración

                    // Validar que al menos tengamos provincia
                    if (cpro.isEmpty()) {
                        lineasDescartadas++;
                        continue;
                    }

                    // Parsear a integers
                    Integer provincia = parseIntSafe(cpro);
                    Integer municipio = parseIntSafe(cmum);
                    Integer unidadPobl = parseIntSafe(cun);
                    Integer via = parseIntSafe(cvia);
                    Integer numInf = parseIntSafe(ein);
                    Integer numSup = parseIntSafe(esn);
                    Integer tipoNum = parseIntSafe(tinum);

                    if (provincia == null) {
                        lineasDescartadas++;
                        continue;
                    }

                    // Añadir al servicio de cobertura
                    miCobertura.addTramo(provincia, municipio, unidadPobl, via, numInf, numSup,tipoNum);
                    lineasValidas++;

                } catch (Exception e) {
                    log.debug("Error parseando línea {}: {}", numeroLinea, e.getMessage());
                    lineasDescartadas++;
                }

                // Log de progreso cada 100k líneas
                if (numeroLinea % 100000 == 0) {
                    log.info("Procesadas {} líneas... (válidas: {}, descartadas: {})",
                            numeroLinea, lineasValidas, lineasDescartadas);
                }
            }

            if (lineasValidas == 0) {
                throw new IOException("No se pudieron parsear líneas válidas del archivo");
            }

            long tiempoTotal = System.currentTimeMillis() - inicio;

            log.info("\n");
            log.info("CARGA COMPLETADA");
            log.info("Líneas procesadas: {}", numeroLinea);
            log.info("Líneas válidas: {} ({:.2f}%)", lineasValidas,
                    (lineasValidas * 100.0 / numeroLinea));
            log.info("Líneas descartadas: {} ({:.2f}%)", lineasDescartadas,
                    (lineasDescartadas * 100.0 / numeroLinea));
            log.info("Provincias cubiertas: {}", miCobertura.numeroProvinciasCubiertas());
            log.info("Tramos cubiertos: {}", miCobertura.numeroTramosCubiertos());
            log.info("Tiempo total: {} ms ({} seg)", tiempoTotal, tiempoTotal / 1000.0);

            return miCobertura;
        }
    }

    /**
     * Extrae un campo de la línea en las posiciones especificadas
     */
    private String extraerCampo(String linea, int inicio, int fin) {
        if (linea.length() < fin) {
            return "";
        }
        return linea.substring(inicio, fin).trim();
    }

    /**
     * Parsea un string a Integer de forma segura
     * Retorna null si el string está vacío o no es numérico
     */
    private Integer parseIntSafe(String valor) {
        if (valor == null || valor.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(valor);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}