package edu.msmk.clases.service;

import edu.msmk.clases.exchange.PeticionCliente;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap; // IMPORTANTE

@Service
@Slf4j
public class CoberturaServicio {

    @Value("#{${config.provincias}}")
    private Map<Integer, String> maestroProvincias;

    // CAMBIO: Todos los mapas ahora son ConcurrentHashMap para soportar carga en paralelo
    private final Map<String, Integer> mapaProvincias = new ConcurrentHashMap<>(100);
    private final Map<String, Integer> mapaNombresAMunicipios = new ConcurrentHashMap<>(20000);
    private final Map<String, Integer> buscadorVias = new ConcurrentHashMap<>(200000);
    private final Map<String, List<RangoPortal>> tramosConRangos = new ConcurrentHashMap<>(2000000);

    public void registrarTodo(int cpro, String nomProv, int cmum, String nomMun,
                              String nomVia, int cvia, int cpun,
                              int numInf, int numSup, int tipoNum, String cp) {

        String nombreProvReal = (nomProv != null && !nomProv.trim().isEmpty())
                ? nomProv.toUpperCase().trim()
                : maestroProvincias.getOrDefault(cpro, "PROVINCIA " + cpro);

        mapaProvincias.put(nombreProvReal, cpro);
        registrarNombreMunicipio(cpro, nomMun, cmum);
        registrarVia(cpro, cmum, nomVia, cvia);
        addTramo(cpro, cmum, cpun, cvia, numInf, numSup, tipoNum, cp);
    }

    public void addTramo(Integer provincia, Integer municipio, Integer unidadPobl,
                         Integer via, Integer numInf, Integer numSup, Integer tipoNum, String cp) {

        String clave = provincia + "_" + municipio + "_" + (unidadPobl != null ? unidadPobl : 0) + "_" + (via != null ? via : 0);
        RangoPortal nuevoRango = new RangoPortal(numInf, numSup, tipoNum, cp);

        // OPTIMIZACIÓN: computeIfAbsent en ConcurrentHashMap es atómico.
        // Usamos CopyOnWriteArrayList o sincronizamos la lista para evitar errores en la lista misma.
        tramosConRangos.computeIfAbsent(clave, k -> Collections.synchronizedList(new ArrayList<>(2)))
                .add(nuevoRango);
    }

    public void registrarVia(int cpro, int cmum, String nomVia, int cvia) {
        if (nomVia == null || nomVia.isEmpty()) return;

        // 1. Limpieza radical:
        // Quitamos los números (el cvia que se cuela) y espacios sobrantes
        String nombreLimpio = nomVia.replaceAll("[0-9]", "") // Quita los números (00383...)
                .replaceAll("\\s+", " ") // Convierte múltiples espacios en uno solo
                .trim();

        // 2. Si el nombre empieza por el nombre de una provincia o río (como EBRO),
        // es que el substring está mal, pero con esta limpieza el 'contains' funcionará.

        String claveVia = cpro + "_" + cmum + "_" + nombreLimpio;

        if (cmum == 115) {
            log.info("Clave GENERADA Y LIMPIA: {}", claveVia);
        }

        buscadorVias.put(claveVia, cvia);
    }

    private String normalizarNombreVia(String nombre) {
        String nom = nombre.toUpperCase().trim();
        if (nom.contains("(") && nom.contains(")")) {
            try {
                int open = nom.indexOf("(");
                int close = nom.indexOf(")");
                String dentro = nom.substring(open + 1, close);
                String fuera = nom.substring(0, open).trim();
                return (dentro + " " + fuera).trim();
            } catch (Exception e) { return nom; }
        }
        return nom;
    }

    public boolean damosServicio(PeticionCliente peticion) {
        if (peticion == null) return false;
        String clave = peticion.getClave();
        List<RangoPortal> rangos = tramosConRangos.get(clave);

        if (rangos != null) {
            // Sincronizamos la lectura para evitar colisiones si se consulta mientras se carga
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
        return false;
    }

    // Busca la provincia aunque el usuario escriba "Alava" o "01"
    public Integer obtenerCodigoProvincia(String entrada) {
        if (entrada == null) return null;
        String busqueda = normalizar(entrada);

        // 1. ¿Es un número? (El usuario mandó "01")
        try {
            return Integer.parseInt(busqueda);
        } catch (NumberFormatException e) {
            // 2. Si es texto, buscamos en el mapa de propiedades
            return maestroProvincias.entrySet().stream()
                    .filter(entry -> normalizar(entry.getValue()).contains(busqueda))
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElse(null);
        }
    }

    // Busca el municipio ignorando si el usuario puso solo una parte del nombre
    public Integer obtenerCodigoMunicipio(int cpro, String nombreBusqueda) {
        String busqueda = normalizar(nombreBusqueda);
        String prefijo = String.format("%02d", cpro); // Convertir 1 en "01"

        return mapaNombresAMunicipios.entrySet().stream()
                .filter(e -> e.getKey().startsWith(prefijo)) // Que sea de esa provincia
                .filter(e -> normalizar(e.getKey()).contains(busqueda)) // Que contenga el nombre
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    public Integer obtenerCodigoVia(int cpro, int cmum, String nombreBuscado) {
        if (nombreBuscado == null) return null;

        String busqueda = normalizar(nombreBuscado);
        String prefijo = cpro + "_" + cmum + "_";

        return buscadorVias.entrySet().stream()
                .filter(e -> e.getKey().startsWith(prefijo))
                .filter(e -> {
                    String nombreOficial = e.getKey().replace(prefijo, "");
                    // Esto permite que "EBRO ALBERT CAMUS" sea encontrado buscando "ALBERT CAMUS"
                    return nombreOficial.contains(busqueda) || busqueda.contains(nombreOficial);
                })
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }
    // Método clave para que el match funcione sin importar artículos o tipos de vía
    private String limpiarParaMatch(String texto) {
        return texto.toUpperCase()
                .replaceAll("^(CALLE|AVENIDA|PLAZA|PASEO|AV\\.|C/|CL)\\s+", "")
                .replaceAll("\\(.*?\\)", "") // Quita lo que hay entre paréntesis (DE LAS)
                .replaceAll("\\s+(DE|LA|EL|LAS|LOS)\\s+", " ")
                .replaceAll("[^A-Z0-9]", "") // Deja solo letras y números
                .trim();
    }


    public void registrarNombreMunicipio(int cpro, String nombre, int cmum) {
        if (nombre == null || nombre.isEmpty()) return;
        mapaNombresAMunicipios.put(cpro + "_" + nombre.toUpperCase().trim(), cmum);
    }

    public void registrarProvincia(int cpro, String nombreProv) {
        if (nombreProv == null || nombreProv.isEmpty()) {
            nombreProv = maestroProvincias.getOrDefault(cpro, "PROVINCIA " + cpro);
        }
        mapaProvincias.put(nombreProv.toUpperCase().trim(), cpro);
    }

    public void limpiar() {
        tramosConRangos.clear();
        mapaNombresAMunicipios.clear();
        mapaProvincias.clear();
        buscadorVias.clear();
        log.info("Memoria de Cobertura liberada.");
    }

    public int numeroProvinciasCubiertas() {
        // Usamos stream para contar de forma segura
        return (int) tramosConRangos.keySet().stream()
                .map(clave -> clave.split("_")[0])
                .distinct()
                .count();
    }

    private static class RangoPortal {
        private final int extremoInferior;
        private final int extremoSuperior;
        private final int tipoNumeracion;
        @Getter private final String codigoPostal;

        public RangoPortal(int extremoInferior, int extremoSuperior, int tipoNumeracion, String codigoPostal) {
            this.extremoInferior = extremoInferior;
            this.extremoSuperior = extremoSuperior;
            this.tipoNumeracion = tipoNumeracion;
            this.codigoPostal = codigoPostal;
        }

        public boolean contieneNumero(int numero) {
            if (numero < extremoInferior || numero > extremoSuperior) return false;
            if (tipoNumeracion == 1) return numero % 2 != 0;
            if (tipoNumeracion == 2) return numero % 2 == 0;
            return true;
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

    public List<String> listarVias(int cpro, int cmum) {
        String prefijo = cpro + "_" + cmum + "_";
        return buscadorVias.keySet().stream()
                .filter(clave -> clave.startsWith(prefijo))
                .map(clave -> clave.replace(prefijo, "")) // Quitamos los códigos para ver solo el nombre
                .sorted()
                .limit(20) // Limitamos a 20 para no inundar el log
                .toList();
    }


    public int numeroTramosCubiertos() { return this.tramosConRangos.size(); }
    public int numeroViasIndexadas() { return this.buscadorVias.size(); }
}