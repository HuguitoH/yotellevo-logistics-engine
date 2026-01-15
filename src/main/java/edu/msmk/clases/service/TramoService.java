package edu.msmk.clases.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@Slf4j
public class TramoService {

    // Nuestra "fuente de verdad" cargada desde el CSV
    private final Map<String, String> maestroMunicipios = new ConcurrentHashMap<>();

    private final Set<String> nombresMunicipios = Collections.synchronizedSet(new HashSet<>());
    private final Map<String, Set<String>> callesPorMunicipio = new ConcurrentHashMap<>();

    private long tiempoCargaBoe = 0;
    private int registrosBoe = 0;
    private int registrosEmpresa = 0;

    /**
     * Se ejecuta automáticamente al arrancar la aplicación.
     * Carga el CSV que generaste con Python.
     */
    @PostConstruct
    public void cargarMaestroMunicipios() {
        try {
            ClassPathResource resource = new ClassPathResource("municipios_maestro.csv");
            if (!resource.exists()) {
                log.error("Archivo municipios_maestro.csv no encontrado en resources");
                return;
            }

            try (BufferedReader br = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                br.lines().forEach(linea -> {
                    String[] partes = linea.split(";");
                    if (partes.length == 2) {
                        maestroMunicipios.put(partes[0].trim(), partes[1].trim());
                    }
                });
            }
            log.info("Maestro cargado: {} municipios listos para usar.", maestroMunicipios.size());
        } catch (IOException e) {
            log.error("Error cargando maestro de municipios", e);
        }
    }

    public void leerTramos(CoberturaServicio coberturaServicio) throws IOException {
        coberturaServicio.limpiar();
        this.nombresMunicipios.clear();
        this.callesPorMunicipio.clear();
        // NO limpiamos maestroMunicipios aquí, lo necesitamos cargado siempre

        long inicio = System.currentTimeMillis();
        this.registrosBoe = cargarDesdeFichero("TRAM.D250630.G250702", coberturaServicio);
        this.tiempoCargaBoe = System.currentTimeMillis() - inicio;

        log.info("BOE cargado: {} registros en {} ms", registrosBoe, tiempoCargaBoe);
    }

    public void leerTramosEmpresa(CoberturaServicio coberturaServicio) throws IOException {
        long inicio = System.currentTimeMillis();
        this.registrosEmpresa = cargarDesdeFichero("TRAM.EMPRESA", coberturaServicio);
        log.info("Archivo Empresa cargado: {} registros", registrosEmpresa);
    }

    private int cargarDesdeFichero(String path, CoberturaServicio coberturaServicio) throws IOException {
        ClassPathResource resource = new ClassPathResource(path);
        if (!resource.exists()) return 0;

        java.util.concurrent.atomic.LongAdder erroresParsing = new java.util.concurrent.atomic.LongAdder();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.ISO_8859_1))) {

            int total = reader.lines()
                    .parallel()
                    .filter(linea -> linea != null && linea.length() >= 180)
                    .mapToInt(linea -> {
                        try {
                            // 1. Extraer IDs básicos primero
                            int cpro = parseLenient(linea, 0, 2);
                            int cmum = parseLenient(linea, 2, 5);
                            int cvia = parseLenient(linea, 140, 145);

                            String nombreViaRaw = linea.substring(145, 185).trim();

                            // 2. Extraer el nombre de la vía (posiciones 145 a 185 según tu TRAM)
                            String nombreVia = nombreViaRaw.replaceAll("^[0-9]+", "").trim();

                            // 3. Resolver el nombre del municipio usando tu maestro de Python
                            String municipioId = String.format("%02d%03d", cpro, cmum);
                            String nombreMuniOficial = maestroMunicipios.getOrDefault(municipioId, "MUNICIPIO DESCONOCIDO");

                            if (nombreMuniOficial.equals("MUNICIPIO DESCONOCIDO")) {
                                nombreMuniOficial = extraerMunicipioRealmenteLimpio(linea);
                                maestroMunicipios.put(municipioId, nombreMuniOficial);
                            }

                            // 4. Extraer el resto de datos necesarios para la cobertura
                            String cp = linea.substring(42, 47).trim();
                            int ein   = parseLenient(linea, 48, 52);
                            int esn   = parseLenient(linea, 53, 57);
                            int tinum = parseLenient(linea, 47, 48);
                            String nombreProv = extraerCampoLimpio(linea, 70, 100);

                            // 5. Alimentar los Sets de búsqueda (para el autocompletado de la tienda)
                            nombresMunicipios.add(nombreMuniOficial);
                            if (!nombreVia.isEmpty()) {
                                callesPorMunicipio
                                        .computeIfAbsent(nombreMuniOficial, k -> Collections.synchronizedSet(new HashSet<>()))
                                        .add(nombreVia);
                            }

                            // 6. ÚNICO Registro final en el servicio de cobertura
                            coberturaServicio.registrarTodo(
                                    cpro, nombreProv, cmum, nombreMuniOficial,
                                    nombreVia, cvia, 0, ein, esn, tinum, cp
                            );

                            return 1;
                        } catch (Exception e) {
                            erroresParsing.increment();
                            return 0;
                        }
                    }).sum();

            log.info("RESULTADO CARGA [{}]: Válidos: {} | Errores: {}", path, total, erroresParsing.sum());
            return total;
        }
    }

    private String extraerMunicipioRealmenteLimpio(String linea) {
        String bloque = linea.substring(100, 140).trim();
        return bloque.split("\\s{2,}")[0].trim().toUpperCase();
    }

    private String extraerCampoLimpio(String linea, int inicio, int fin) {
        if (linea.length() < fin) return "";
        String trozo = linea.substring(inicio, fin).trim();
        return trozo.split("\\s{2,}")[0].replace("*", "").trim().toUpperCase();
    }

    private int parseLenient(String linea, int inicio, int fin) {
        try {
            String trozo = linea.substring(inicio, fin).trim().replaceAll("[^0-9]", "");
            return trozo.isEmpty() ? 0 : Integer.parseInt(trozo);
        } catch (Exception e) { return 0; }
    }

    private String normalizar(String texto) {
        if (texto == null) return "";
        return Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase()
                .trim();
    }

    public List<String> buscarMunicipios(String query) {
        String q = normalizar(query);
        return nombresMunicipios.stream()
                .filter(m -> normalizar(m).startsWith(q))
                .limit(10)
                .collect(Collectors.toList());
    }

    public List<String> buscarCalles(String municipio, String query) {
        if (municipio == null || query == null) return Collections.emptyList();

        // 1. Obtenemos el Set de calles
        Set<String> calles = callesPorMunicipio.get(municipio);
        if (calles == null || calles.isEmpty()) return Collections.emptyList();

        String q = normalizar(query);

        // 2. IMPORTANTE: Sincronizamos sobre el objeto 'calles'
        // mientras el Stream lo recorre para evitar el error de hilos
        synchronized (calles) {
            return calles.stream()
                    .filter(c -> normalizar(c).contains(q))
                    .limit(10)
                    .collect(Collectors.toList());
        }
    }

    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalMunicipios", nombresMunicipios.size());
        stats.put("registrosBoe", registrosBoe);
        stats.put("registrosEmpresa", registrosEmpresa);
        stats.put("tiempoCargaMs", tiempoCargaBoe);
        return stats;
    }
}