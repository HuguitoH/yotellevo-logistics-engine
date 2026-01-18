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
    public PeticionCliente parsear(String municipio, String provincia, String nombreVia, int numero, String cp) {
        log.info("--- Iniciando Validación Inteligente ---");
        log.info("Entrada: CP {}, {} en {}, {}", cp, nombreVia, municipio, provincia);

        try {
            // 1. Extraer CPRO del Código Postal (primeros 2 dígitos)
            // Ejemplo: "28023" -> 28
            if (cp == null || cp.length() < 2) {
                log.warn("Código Postal inválido: {}", cp);
                return null;
            }
            int cproDesdeCP = Integer.parseInt(cp.substring(0, 2));

            // 2. Resolvemos Provincia y comparamos con el CP
            Integer cproBaseDatos = coberturaServicio.obtenerCodigoProvincia(provincia);

            if (cproBaseDatos != null && cproBaseDatos != cproDesdeCP) {
                log.error("INCONSISTENCIA: El CP {} es de la provincia {}, pero has escrito {}",
                        cp, cproDesdeCP, provincia);
                return null; // Bloqueamos el pedido por seguridad
            }

            // Si el usuario no puso provincia, usamos la del CP
            int cproFinal = (cproBaseDatos != null) ? cproBaseDatos : cproDesdeCP;

            // 3. Resolvemos Municipio dentro de esa provincia
            Integer cmum = coberturaServicio.obtenerCodigoMunicipio(cproFinal, municipio);
            if (cmum == null) {
                log.warn("Municipio {} no existe en la provincia {}", municipio, cproFinal);
                return null;
            }

            // 4. Limpieza de Vía (para que "De las flores" sea "FLORES")
            String viaLimpia = nombreVia.toUpperCase()
                    .replace("CALLE ", "")
                    .replace("DE LAS ", "")
                    .replace("DE LOS ", "")
                    .replace("DE LA ", "")
                    .replace("DEL ", "")
                    .replace("DE ", "")
                    .trim();

            Integer cvia = coberturaServicio.obtenerCodigoVia(cproFinal, cmum, viaLimpia);

            if (cvia == null) {
                log.warn("Vía no encontrada: '{}' (Buscado como: '{}')", nombreVia, viaLimpia);
                return null;
            }

            log.info("VALIDACIÓN TOTAL: CPRO={}, CMUM={}, CVIA={}, NUM={} (CP {})",
                    cproFinal, cmum, cvia, numero, cp);

            return new PeticionCliente(cproFinal, cmum, 0, cvia, numero);

        } catch (Exception e) {
            log.error("Error crítico en el parseo: {}", e.getMessage());
            return null;
        }
    }
}