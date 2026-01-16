package edu.msmk.clases.service.cobertura;

import edu.msmk.clases.exchange.PeticionCliente;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class CoberturaServicio {

    @Value("#{${config.provincias}}")
    private Map<Integer, String> maestroProvincias;

    private final Map<String, Integer> mapaNombresAMunicipios = new ConcurrentHashMap<>(20000);

    // CAMBIO: Estructura jerárquica para búsquedas rápidas de vías
    // ProvinciaID -> MunicipioID -> (NombreNormalizado -> ViaID)
    private final Map<Integer, Map<Integer, Map<String, Integer>>> buscadorViasEstructurado = new ConcurrentHashMap<>();

    // Clave: CPRO_CMUM_CVIA -> Lista de tramos numéricos
    private final Map<String, List<RangoPortal>> tramosConRangos = new ConcurrentHashMap<>(100000);

    /**
     * Registra una vía en el buscador estructurado.
     * Usado por el TramoLoader.
     */
    public void registrarVia(int cpro, int cmum, String nomVia, int cvia) {
        if (nomVia == null || nomVia.isEmpty()) return;

        String nombreLimpio = normalizar(nomVia);

        buscadorViasEstructurado
                .computeIfAbsent(cpro, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(cmum, k -> new ConcurrentHashMap<>())
                .put(nombreLimpio, cvia);
    }

    public void addTramo(Integer provincia, Integer municipio, Integer via, Integer numInf, Integer numSup, Integer tipoNum, String cp) {
        // Sincronizamos la clave con PeticionCliente: PROV_MUN_0_VIA
        String clave = provincia + "_" + municipio + "_0_" + via;
        RangoPortal nuevoRango = new RangoPortal(numInf, numSup, tipoNum, cp);

        tramosConRangos.computeIfAbsent(clave, k -> Collections.synchronizedList(new ArrayList<>(2)))
                .add(nuevoRango);

        log.debug("Tramo registrado: {}", clave);
    }

    public boolean damosServicio(PeticionCliente peticion) {
        if (peticion == null) return false;

        // Usamos el método getClave() propio de la petición para asegurar consistencia
        String clave = peticion.getClave();
        List<RangoPortal> rangos = tramosConRangos.get(clave);

        if (rangos != null) {
            synchronized (rangos) {
                int numeroPortal = peticion.getNumero();
                for (RangoPortal rango : rangos) {
                    if (rango.contieneNumero(numeroPortal)) {
                        peticion.setCodigoPostalOficial(rango.getCodigoPostal());
                        return true;
                    }
                }
            }
        }
        log.warn("Sin cobertura para la clave: {}", clave);
        return false;
    }

    public Integer obtenerCodigoVia(int cpro, int cmum, String nombreBuscado) {
        if (nombreBuscado == null) return null;

        // 1. Limpiamos lo que viene de la API (ej: "Calle de las Flores")
        String busquedaLimpia = limpiarParaMatch(nombreBuscado);

        // 2. Obtenemos el sub-mapa de ese municipio
        Map<String, Integer> viasMunicipio = buscadorViasEstructurado
                .getOrDefault(cpro, Collections.emptyMap())
                .getOrDefault(cmum, Collections.emptyMap());

        // 3. Búsqueda inteligente: ¿El nombre del BOE contiene mi búsqueda?
        return viasMunicipio.entrySet().stream()
                .filter(e -> {
                    String nombreBOE = limpiarParaMatch(e.getKey()); // "FLORES (DE LAS)" -> "FLORES"
                    return nombreBOE.contains(busquedaLimpia) || busquedaLimpia.contains(nombreBOE);
                })
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    private String limpiarParaMatch(String texto) {
        if (texto == null) return "";
        return normalizar(texto)
                .replaceAll("^(CALLE|AVENIDA|PLAZA|PASEO|AV\\.|C/|CL|AVDA)\\s+", "")
                .replaceAll("\\(.*?\\)", "") // Quita (DE LAS)
                .replaceAll("\\s+(DE|LA|EL|LAS|LOS)\\s+", " ") // Quita artículos
                .trim();
    }

    // --- Resto de métodos de apoyo ---

    public void registrarNombreMunicipio(int cpro, String nombre, int cmum) {
        if (nombre == null) return;
        mapaNombresAMunicipios.put(cpro + "_" + normalizar(nombre), cmum);
    }

    public Integer obtenerCodigoMunicipio(int cpro, String nombreBusqueda) {
        String busqueda = normalizar(nombreBusqueda);
        return mapaNombresAMunicipios.entrySet().stream()
                .filter(e -> e.getKey().startsWith(cpro + "_"))
                .filter(e -> e.getKey().contains(busqueda))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    public Integer obtenerCodigoProvincia(String entrada) {
        if (entrada == null) return null;
        String busqueda = normalizar(entrada);
        try {
            return Integer.parseInt(busqueda);
        } catch (NumberFormatException e) {
            return maestroProvincias.entrySet().stream()
                    .filter(entry -> normalizar(entry.getValue()).contains(busqueda))
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElse(null);
        }
    }

    private String normalizar(String texto) {
        if (texto == null) return "";
        return java.text.Normalizer.normalize(texto, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase()
                .trim();
    }

    // Clase interna para los rangos
    private static class RangoPortal {
        private final int extremoInferior;
        private final int extremoSuperior;
        private final int tipoNumeracion;
        @Getter private final String codigoPostal;

        public RangoPortal(int ein, int esn, int tipo, String cp) {
            this.extremoInferior = ein;
            this.extremoSuperior = esn;
            this.tipoNumeracion = tipo;
            this.codigoPostal = cp;
        }

        public boolean contieneNumero(int numero) {
            if (numero < extremoInferior || numero > extremoSuperior) return false;
            if (tipoNumeracion == 1) return numero % 2 != 0; // Impares
            if (tipoNumeracion == 2) return numero % 2 == 0; // Pares
            return true; // Ambos
        }
    }
}