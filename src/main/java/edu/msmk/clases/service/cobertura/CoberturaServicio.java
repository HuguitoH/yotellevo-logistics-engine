package edu.msmk.clases.service.cobertura;

import edu.msmk.clases.exchange.PeticionCliente;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Service
@Slf4j
public class CoberturaServicio {

    @Autowired
    private NormalizacionService normalizacionService;

    @Value("#{${config.provincias}}")
    private Map<Integer, String> maestroProvincias;

    private final Map<String, Integer> mapaNombresAMunicipios = new ConcurrentHashMap<>(20000);
    private final Map<Integer, Map<Integer, Map<String, Integer>>> buscadorViasEstructurado = new ConcurrentHashMap<>();
    private final Map<String, List<RangoPortal>> tramosConRangos = new ConcurrentHashMap<>(100000);

    // OPTIMIZACIÓN: Patterns pre-compilados
    private static final Pattern PATTERN_PREFIJOS = Pattern.compile("^(CALLE|AVENIDA|PLAZA|PASEO|AV\\.|C/|CL|AVDA)\\s+");
    private static final Pattern PATTERN_PARENTESIS = Pattern.compile("\\(.*?\\)");
    private static final Pattern PATTERN_ARTICULOS = Pattern.compile("\\s+(DE|LA|EL|LAS|LOS)\\s+");

    /**
     * Registra una vía en el buscador estructurado.
     */
    public void registrarVia(int cpro, int cmum, String nomVia, int cvia) {
        if (nomVia == null || nomVia.isEmpty()) return;

        String nombreLimpio = normalizacionService.normalizar(nomVia);

        buscadorViasEstructurado
                .computeIfAbsent(cpro, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(cmum, k -> new ConcurrentHashMap<>())
                .put(nombreLimpio, cvia);
    }

    public void addTramo(Integer provincia, Integer municipio, Integer via,
                         Integer numInf, Integer numSup, Integer tipoNum, String cp) {
        String clave = provincia + "_" + municipio + "_0_" + via;
        RangoPortal nuevoRango = new RangoPortal(numInf, numSup, tipoNum, cp);

        tramosConRangos.computeIfAbsent(clave, k -> Collections.synchronizedList(new ArrayList<>(2)))
                .add(nuevoRango);

        log.debug("Tramo registrado: {}", clave);
    }

    public boolean damosServicio(PeticionCliente peticion) {
        if (peticion == null) return false;

        String clave = peticion.getClave();
        List<RangoPortal> rangos = tramosConRangos.get(clave);

        if (rangos != null) {
            // OPTIMIZACIÓN: Evitar synchronized si solo hay 1 rango (caso común)
            if (rangos.size() == 1) {
                RangoPortal rango = rangos.get(0);
                if (rango.contieneNumero(peticion.getNumero())) {
                    peticion.setCodigoPostalOficial(rango.getCodigoPostal());
                    return true;
                }
            } else {
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
        }
        log.warn("Sin cobertura para la clave: {}", clave);
        return false;
    }

    public Integer obtenerCodigoVia(int cpro, int cmum, String nombreBuscado) {
        if (nombreBuscado == null) return null;

        String busquedaLimpia = limpiarParaMatch(nombreBuscado);

        Map<String, Integer> viasMunicipio = buscadorViasEstructurado
                .getOrDefault(cpro, Collections.emptyMap())
                .getOrDefault(cmum, Collections.emptyMap());

        // OPTIMIZACIÓN: Búsqueda directa primero (O(1))
        Integer codigoDirecto = viasMunicipio.get(busquedaLimpia);
        if (codigoDirecto != null) return codigoDirecto;

        // OPTIMIZACIÓN: Solo si no hay match exacto, hacer búsqueda fuzzy
        for (Map.Entry<String, Integer> entry : viasMunicipio.entrySet()) {
            String nombreBOE = limpiarParaMatch(entry.getKey());
            if (nombreBOE.contains(busquedaLimpia) || busquedaLimpia.contains(nombreBOE)) {
                return entry.getValue();
            }
        }

        return null;
    }

    private String limpiarParaMatch(String texto) {
        if (texto == null) return "";

        String resultado = normalizacionService.normalizar(texto);
        resultado = PATTERN_PREFIJOS.matcher(resultado).replaceAll("");
        resultado = PATTERN_PARENTESIS.matcher(resultado).replaceAll("");
        resultado = PATTERN_ARTICULOS.matcher(resultado).replaceAll(" ");

        return resultado.trim();
    }

    public void registrarNombreMunicipio(int cpro, String nombre, int cmum) {
        if (nombre == null) return;
        mapaNombresAMunicipios.put(cpro + "_" + normalizacionService.normalizar(nombre), cmum);
    }

    public Integer obtenerCodigoMunicipio(int cpro, String nombreBusqueda) {
        String busqueda = normalizacionService.normalizar(nombreBusqueda);
        String prefijo = cpro + "_";

        for (Map.Entry<String, Integer> entry : mapaNombresAMunicipios.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith(prefijo) && key.contains(busqueda)) {
                return entry.getValue();
            }
        }
        return null;
    }

    public Integer obtenerCodigoProvincia(String entrada) {
        if (entrada == null) return null;
        String busqueda = normalizacionService.normalizar(entrada);

        try {
            return Integer.parseInt(busqueda);
        } catch (NumberFormatException e) {
            for (Map.Entry<Integer, String> entry : maestroProvincias.entrySet()) {
                if (normalizacionService.normalizar(entry.getValue()).contains(busqueda)) {
                    return entry.getKey();
                }
            }
            return null;
        }
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
            if (tipoNumeracion == 1) return numero % 2 != 0;
            if (tipoNumeracion == 2) return numero % 2 == 0;
            return true;
        }
    }
}