package edu.msmk.clases.service;

import edu.msmk.clases.dto.PedidoRequest;
import edu.msmk.clases.exchange.PeticionCliente;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class DireccionParserService {

    @Autowired
    private CoberturaServicio coberturaServicio;

    public PeticionCliente parsear(PedidoRequest.DireccionDTO dto) {
        log.info("🔍 Procesando: {} en {}", dto.getNombreVia(), dto.getMunicipio());

        // 1. Obtener Códigos de Provincia y Municipio
        Integer cpro = coberturaServicio.obtenerCodigoProvincia(dto.getProvincia());
        String muniNorm = normalizar(dto.getMunicipio());
        Integer cmum = coberturaServicio.obtenerCodigoMunicipio(cpro, muniNorm);

        if (cpro == null || cmum == null) {
            log.error("❌ No se encontró Provincia ({}) o Municipio ({})", cpro, cmum);
            return null;
        }

        // 2. Búsqueda de Calle
        String calleUsuario = normalizar(dto.getNombreVia());
        Integer cvia = coberturaServicio.obtenerCodigoVia(cpro, cmum, calleUsuario);

        // 3. Reintento con formato BOE (por si es "DE LAS FLORES" -> "FLORES (DE LAS)")
        if (cvia == null) {
            String formatoBoe = intentarFormatoBOE(calleUsuario);
            log.info("🔄 Reintentando con formato BOE: {}", formatoBoe);
            cvia = coberturaServicio.obtenerCodigoVia(cpro, cmum, formatoBoe);
        }

        if (cvia == null) {
            log.error("❌ Calle no encontrada: {} en municipio {}", calleUsuario, cmum);
            List<String> viasDisponibles = coberturaServicio.listarVias(cpro, cmum);
            log.info("💡 Sugerencias para el municipio {}: {}", cmum, viasDisponibles);
            return null;
        }

        log.info("✅ Match encontrado: CPRO={}, CMUM={}, CVIA={}", cpro, cmum, cvia);
        return new PeticionCliente(cpro, cmum, 0, cvia, parsearNumero(dto.getNumero()));
    } // <-- Aquí se cierra el método parsear correctamente

    private int parsearNumero(String numero) {
        if (numero == null) return 1;
        try {
            return Integer.parseInt(numero.replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            return 1;
        }
    }

    private String normalizar(String texto) {
        if (texto == null) return "";
        return texto.toUpperCase()
                .replace("Á", "A").replace("É", "E")
                .replace("Í", "I").replace("Ó", "O")
                .replace("Ú", "U").replace("Ñ", "N")
                .trim();
    }

    private String intentarFormatoBOE(String nombre) {
        if (nombre == null || !nombre.contains(" ")) return nombre;

        String[] partes = nombre.split(" ");
        if (partes.length >= 2 && (partes[0].equals("DE") || partes[0].equals("LA") ||
                partes[0].equals("EL") || partes[0].equals("LAS") || partes[0].equals("LOS"))) {

            StringBuilder nombrePrincipal = new StringBuilder();
            StringBuilder conectores = new StringBuilder();

            int puntoCorte = (partes.length > 2 && (partes[1].equals("LA") || partes[1].equals("LAS") ||
                    partes[1].equals("EL") || partes[1].equals("LOS"))) ? 2 : 1;

            conectores.append("(");
            for (int i = 0; i < puntoCorte; i++) {
                conectores.append(partes[i]).append(i == puntoCorte - 1 ? "" : " ");
            }
            conectores.append(")");

            for (int i = puntoCorte; i < partes.length; i++) {
                nombrePrincipal.append(partes[i]).append(i == partes.length - 1 ? "" : " ");
            }

            return (nombrePrincipal.toString() + " " + conectores.toString()).trim();
        }
        return nombre;
    }
} // <-- Aquí se cierra la clase