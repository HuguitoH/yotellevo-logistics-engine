package edu.msmk.clases.service;

import edu.msmk.clases.dto.PedidoRequest;
import edu.msmk.clases.exchange.PeticionCliente;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class DireccionParserService {

    private static final Map<String, Integer> PROVINCIAS = new HashMap<>();
    private static final Map<String, DatosMunicipio> MUNICIPIOS = new HashMap<>();

    static {
        // IMPORTANTE: Asegúrate de que las provincias estén cargadas
        PROVINCIAS.put("ALAVA", 1);
        PROVINCIAS.put("MADRID", 28);

        // MUNICIPIOS
        MUNICIPIOS.put("01_ALEGRIA-DULANTZI", new DatosMunicipio(1, 1701, 1002));
        MUNICIPIOS.put("28_MADRID", new DatosMunicipio(79, 7901, 1234));
    }

    public PeticionCliente parsear(PedidoRequest.DireccionDTO dto) {
        try {
            log.info("Iniciando parseo de dirección: {}, {}", dto.getMunicipio(), dto.getProvincia());

            // 1. Traducir provincia (Ej: "ÁLAVA" -> 1)
            String provNormalizada = normalizar(dto.getProvincia());
            Integer cpro = PROVINCIAS.get(provNormalizada);

            if (cpro == null) {
                log.error("Fallo en Paso 1: Provincia '{}' no encontrada en el mapa. Claves disponibles: {}",
                        provNormalizada, PROVINCIAS.keySet());
                return null;
            }

            // 2. Buscar códigos por nombre de municipio
            String muniNormalizado = normalizar(dto.getMunicipio());
            String claveBusqueda = String.format("%02d_%s", cpro, muniNormalizado);

            log.info("Paso 2: Buscando municipio con clave generada: '{}'", claveBusqueda);

            DatosMunicipio datos = MUNICIPIOS.get(claveBusqueda);

            if (datos == null) {
                log.error("Fallo en Paso 2: Clave '{}' no existe en el mapa de MUNICIPIOS. Claves disponibles: {}",
                        claveBusqueda, MUNICIPIOS.keySet());
                return null;
            }

            log.info("Éxito: Municipio encontrado. IDs -> CMUM: {}, CUN: {}, CVIA: {}",
                    datos.codigoMunicipio, datos.unidadPoblacional, datos.codigoViaDefecto);

            // 3. Crear PeticionCliente
            int numeroPortal = parsearNumero(dto.getNumero());

            PeticionCliente peticion = new PeticionCliente(
                    cpro,                       // 1 -> Se convertirá en "01"
                    datos.codigoMunicipio,     // 1 -> Se convertirá en "001"
                    datos.unidadPoblacional,   // 1701 -> Se convertirá en "0001701"
                    datos.codigoViaDefecto,    // 1002 -> Se convertirá en "01002"
                    numeroPortal               // 8
            );

            // LOG CRÍTICO: Mira qué clave está generando el objeto antes de enviarlo a cobertura
            log.info("Clave técnica generada por PeticionCliente: '{}'", peticion.getClave());

            return peticion;

        } catch (Exception e) {
            log.error("Error inesperado en DireccionParserService: {}", e.getMessage(), e);
            return null;
        }
    }

    private int parsearNumero(String numero) {
        try {
            return Integer.parseInt(numero.replaceAll("[^0-9]", ""));
        } catch (Exception e) { return 1; }
    }

    private String normalizar(String texto) {
        if (texto == null) return "";
        return texto.toUpperCase()
                .replace("Á", "A").replace("É", "E")
                .replace("Í", "I").replace("Ó", "O")
                .replace("Ú", "U").replace("Ñ", "N")
                .trim();
    }

    private static class DatosMunicipio {
        int codigoMunicipio;
        int unidadPoblacional;
        int codigoViaDefecto;

        DatosMunicipio(int codigoMunicipio, int unidadPoblacional, int codigoViaDefecto) {
            this.codigoMunicipio = codigoMunicipio;
            this.unidadPoblacional = unidadPoblacional;
            this.codigoViaDefecto = codigoViaDefecto;
        }
    }
}
