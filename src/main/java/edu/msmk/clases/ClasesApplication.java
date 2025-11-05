package edu.msmk.clases;

import ch.qos.logback.classic.Logger;
import edu.msmk.clases.service.TramoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.IOException;
import java.sql.Timestamp;

@Slf4j
@SpringBootApplication

public class ClasesApplication implements CommandLineRunner {

    private final TramoService tramoService;



    public ClasesApplication(TramoService tramoService) {
        this.tramoService = tramoService;
    }

    public static void main(String[] args) {
        SpringApplication.run(ClasesApplication.class, args);
    }

    @Override
    public void run(String... args) {
        try {
            ExpansionServicio expansionServicio = tramoService.leerTramos();
            String puebloRecibido = "SIGUENZA";
            Timestamp inicio = new Timestamp(System.currentTimeMillis());
            log.info("Damos Servicio en {}? : {}", puebloRecibido,expansionServicio.DamoServicio(puebloRecibido));
            Timestamp fin = new Timestamp(System.currentTimeMillis());
            log.info("Numero de pueblos cubiertos: {}", expansionServicio.numeroPueblos());
            log.info("Tiempo de ejecución:{}", fin.getTime() - inicio.getTime());

        } catch (IOException e) {
            System.err.println("Error al procesar el archivo: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Error inesperado: " + e.getMessage());
        }
    }
}
