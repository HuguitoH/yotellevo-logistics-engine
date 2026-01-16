package edu.msmk.clases.service.cobertura;

import edu.msmk.clases.exchange.PeticionCliente;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class DireccionParser {

    @Autowired
    private CoberturaServicio coberturaServicio;

    /**
     * Convierte texto del frontend en IDs numéricos del BOE/Empresa.
     */
    public PeticionCliente parsear(String municipio, String provincia, String nombreVia, int numero) {
        log.info(" Parseando dirección: {} en {}, {}", nombreVia, municipio, provincia);

        try {
            // 1. Resolvemos Provincia (Ej: "Madrid" -> 28)
            Integer cpro = coberturaServicio.obtenerCodigoProvincia(provincia);
            if (cpro == null) {
                log.warn(" Provincia no reconocida: {}", provincia);
                return null;
            }

            // 2. Resolvemos Municipio (Ej: "Pozuelo" -> 115)
            Integer cmum = coberturaServicio.obtenerCodigoMunicipio(cpro, municipio);
            if (cmum == null) {
                log.warn(" Municipio no encontrado en la provincia {}: {}", cpro, municipio);
                return null;
            }

            // 3. Resolvemos Vía (Ej: "Calle Flores" -> 2492)
            // Usamos el buscador estructurado que creamos en CoberturaServicio
            Integer cvia = coberturaServicio.obtenerCodigoVia(cpro, cmum, nombreVia);

            if (cvia == null) {
                log.warn(" Vía no encontrada en el sistema: {}", nombreVia);
                return null;
            }

            log.info("Match exitoso: CPRO={}, CMUM={}, CVIA={}, NUM={}", cpro, cmum, cvia, numero);

            // 4. Creamos la petición con los 5 parámetros que pide tu constructor:
            // (provincia, municipio, unidadPoblacional, via, numero)
            return new PeticionCliente(cpro, cmum, 0, cvia, numero);

        } catch (Exception e) {
            log.error("Error en el proceso de parseo: {}", e.getMessage());
            return null;
        }
    }
}