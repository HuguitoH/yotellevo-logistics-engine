package edu.msmk.clases;

import edu.msmk.clases.service.cobertura.CoberturaServicio;
import edu.msmk.clases.service.cobertura.TramoLoader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@Slf4j
@SpringBootApplication
public class ClasesApplication implements CommandLineRunner {

    // Cambiamos TramoService por TramoLoader
    private final TramoLoader tramoLoader;
    private final CoberturaServicio coberturaServicio;


    @Value("${app.archivo.tramos:TRAM.EMPRESA}") // El segundo valor es por si olvidas ponerlo en el .properties
    private String nombreArchivoTramos;

    public ClasesApplication(TramoLoader tramoLoader, CoberturaServicio coberturaServicio) {
        this.tramoLoader = tramoLoader;
        this.coberturaServicio = coberturaServicio;

    }

    public static void main(String[] args) {
        SpringApplication.run(ClasesApplication.class, args);
    }

    @Override
    public void run(String... args) {
        log.info("=== YO TE LO LLEVO: SISTEMA LOGÍSTICO 2026 ===");

        try {
            log.info("Cargando bases de datos desde: {}", nombreArchivoTramos);

            // Usamos la variable inyectada desde application.properties
            tramoLoader.cargarTramosOptimizado(nombreArchivoTramos);



            log.info("Carga completada. El sistema está listo.");
            log.info("Endpoints activos en: http://localhost:8080/api");

        } catch (Exception e) {
            log.error("ERROR CRÍTICO EN EL ARRANQUE: {}", e.getMessage());
            e.printStackTrace();
        }
    }
}