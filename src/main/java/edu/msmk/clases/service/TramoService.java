package edu.msmk.clases.service;

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

    /**
     * Rellena la instancia compartida de CoberturaServicio con los datos del BOE
     */
    public void leerTramos(CoberturaServicio coberturaServicio) throws IOException {

        ClassPathResource resource = new ClassPathResource("TRAM.D250630.G250702");

        if (!resource.exists()) {
            throw new IOException("El archivo TRAM.D250630.G250702 no existe en los recursos");
        }

        log.info("Iniciando carga del archivo de tramos (estructura BOE) en instancia compartida...");
        long inicio = System.currentTimeMillis();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.ISO_8859_1))) {

            String linea;
            int numeroLinea = 0;
            int lineasValidas = 0;
            int lineasDescartadas = 0;

            while ((linea = reader.readLine()) != null) {
                numeroLinea++;

                if (linea.trim().isEmpty() || linea.length() < 58) {
                    lineasDescartadas++;
                    continue;
                }

                try {
                    String cpro = extraerCampo(linea, 0, 2);
                    String cmum = extraerCampo(linea, 2, 5);
                    String cun = extraerCampo(linea, 13, 20);
                    String cvia = extraerCampo(linea, 20, 25);
                    String ein = extraerCampo(linea, 48, 52);
                    String esn = extraerCampo(linea, 53, 57);
                    String tinum = extraerCampo(linea, 47, 48);

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

                    // Se añade directamente a la instancia inyectada por Spring
                    coberturaServicio.addTramo(provincia, municipio, unidadPobl, via, numInf, numSup, tipoNum);
                    lineasValidas++;

                } catch (Exception e) {
                    lineasDescartadas++;
                }

                if (numeroLinea % 100000 == 0) {
                    log.info("Procesadas {} líneas...", numeroLinea);
                }
            }

            long tiempoTotal = System.currentTimeMillis() - inicio;

            log.info("CARGA COMPLETADA EN INSTANCIA GLOBAL");
            log.info("Líneas válidas: {}", lineasValidas);
            log.info("Provincias cubiertas: {}", coberturaServicio.numeroProvinciasCubiertas());
            log.info("Tramos cubiertos: {}", coberturaServicio.numeroTramosCubiertos());
            log.info("Tiempo total: {} ms", tiempoTotal);
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
