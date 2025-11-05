package edu.msmk.clases.service;

import edu.msmk.clases.ExpansionServicio;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;


@Service
public class TramoService {


    public ExpansionServicio leerTramos() throws IOException {
        ExpansionServicio expansionServicio = new ExpansionServicio();

        ClassPathResource resource = new ClassPathResource("TRAM.D250630.G250702");
        
        if (!resource.exists()) {
            throw new IOException("El archivo TRAM.D250630.G250702 no existe en los recursos");
        }
        
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.ISO_8859_1))) {
            
            String linea;
            int numeroLinea = 1;
            boolean isEmpty = true;
            //guardar linea a linea y como quiera..
            while ((linea = reader.readLine()) != null) {
                if (!linea.trim().isEmpty()) {
                    isEmpty = false;
                    if (linea.length() >= 136) {
                        // Extraer del carácter 111 al 136 (0-indexed sería 10 a 135)
                        String pueblo = linea.substring(0,5).trim();
                        expansionServicio.addPueblo(pueblo);
                        //System.out.println("Línea " + numeroLinea + ": " + subcadena);
                    } else {
                        System.out.println("Línea " + numeroLinea + " es demasiado corta (longitud: " + linea.length() + ")");
                    }
                }
                numeroLinea++;
            }
            
            if (isEmpty) {
                throw new IOException("El archivo está vacío");
            }
        }
        return expansionServicio;
    }
}
