package edu.msmk.clases;

import edu.msmk.clases.service.CoberturaServicio;
import edu.msmk.clases.service.TramoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@Slf4j
@SpringBootApplication
public class ClasesApplication implements CommandLineRunner {

    private final TramoService tramoService;
    private final CoberturaServicio coberturaServicio;

    public ClasesApplication(TramoService tramoService, CoberturaServicio coberturaServicio) {
        this.tramoService = tramoService;
        this.coberturaServicio = coberturaServicio;
    }

    public static void main(String[] args) {
        SpringApplication.run(ClasesApplication.class, args);
    }

    @Override
    public void run(String... args) {
        log.info("=== YO TE LO LLEVO: SISTEMA LOGÍSTICO 2026 ===");

        try {
            // CARGA INICIAL DE DATOS
            log.info("Cargando bases de datos de tramos...");

            // Cargamos BOE
            tramoService.leerTramos(this.coberturaServicio);

            // Cargamos Empresa (Archivo del profesor)
            tramoService.leerTramosEmpresa(this.coberturaServicio);

            log.info("Carga completada. El sistema está listo para recibir peticiones desde el Frontend.");
            log.info("Endpoints activos en: http://localhost:8080/api");

        } catch (Exception e) {
            log.error("ERROR CRÍTICO EN EL ARRANQUE: {}", e.getMessage());
        }
    }
}