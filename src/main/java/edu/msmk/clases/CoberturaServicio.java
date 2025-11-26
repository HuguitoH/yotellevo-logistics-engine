package edu.msmk.clases;

import edu.msmk.clases.exchange.PeticionCliente;
import java.util.*;

/**
 * Servicio de validación de cobertura para entregas de paquetería
 * Usa HashMap con rangos de portales para validación eficiente
 * Complejidad búsqueda: O(1) + O(k) donde k = número de rangos por vía (típicamente 1-3)
 */
public class CoberturaServicio {

    /**
     * Clase interna que representa un rango de portales válidos en una vía
     */
    private static class RangoPortal {
        private final int extremoInferior;  // EIN
        private final int extremoSuperior;  // ESN
        private final int tipoNumeracion;   // TINUM: 1=impares, 2=pares, 0=todos

        public RangoPortal(int extremoInferior, int extremoSuperior, int tipoNumeracion) {
            this.extremoInferior = extremoInferior;
            this.extremoSuperior = extremoSuperior;
            this.tipoNumeracion = tipoNumeracion;
        }

        /**
         * Verifica si un número de portal está dentro de este rango
         * Complejidad: O(1)
         */
        public boolean contieneNumero(int numero) {
            // Verificar que esté dentro del rango
            if (numero < extremoInferior || numero > extremoSuperior) {
                return false;
            }

            // Verificar paridad según tipo de numeración
            switch (tipoNumeracion) {
                case 1: // Solo impares
                    return numero % 2 != 0;
                case 2: // Solo pares
                    return numero % 2 == 0;
                case 0: // Ambos (sin restricción)
                    return true;
                default:
                    return false;
            }
        }

        @Override
        public String toString() {
            String tipo = tipoNumeracion == 1 ? "impares" :
                    tipoNumeracion == 2 ? "pares" : "todos";
            return String.format("[%d-%d %s]", extremoInferior, extremoSuperior, tipo);
        }
    }

    // Clave: "PROV_MUN_CUN_VIA" -> Lista de rangos de portales
    private HashMap<String, List<RangoPortal>> tramosConRangos;

    public CoberturaServicio() {
        this.tramosConRangos = new HashMap<>();
    }

    /**
     * Añade un tramo completo con sus rangos de numeración según estructura INE
     * Complejidad: O(1) amortizado
     *
     * @param provincia Código provincia (CPRO)
     * @param municipio Código municipio (CMUM)
     * @param unidadPobl Código unidad poblacional (CUN)
     * @param via Código vía (CVIA)
     * @param numInf Extremo inferior numeración (EIN)
     * @param numSup Extremo superior numeración (ESN)
     * @param tipoNum Tipo de numeración (TINUM): 1=impares, 2=pares, 0=todos
     */
    public void addTramo(Integer provincia, Integer municipio, Integer unidadPobl,
                         Integer via, Integer numInf, Integer numSup, Integer tipoNum) {
        // Validar que tengamos todos los datos obligatorios
        if (provincia == null || municipio == null || via == null) {
            return;
        }

        // Si no hay rangos de numeración válidos, no podemos validar portales
        if (numInf == null || numSup == null || tipoNum == null) {
            return;
        }

        // Validar que el rango sea lógico
        if (numInf > numSup || numInf < 0) {
            return;
        }

        // Crear clave única: PROV_MUN_CUN_VIA
        String clave = String.format("%02d_%03d_%07d_%05d",
                provincia,
                municipio,
                unidadPobl != null ? unidadPobl : 0,
                via);

        // Crear el rango de portales
        RangoPortal rango = new RangoPortal(numInf, numSup, tipoNum);

        // Añadir a la lista de rangos de esta vía (puede haber múltiples rangos)
        tramosConRangos.computeIfAbsent(clave, k -> new ArrayList<>()).add(rango);
    }

    /**
     * Número de vías únicas cubiertas
     */
    public int numeroTramosCubiertos() {
        return this.tramosConRangos.size();
    }

    /**
     * Obtiene el número de provincias únicas cubiertas
     */
    public int numeroProvinciasCubiertas() {
        Set<Integer> provinciasUnicas = new HashSet<>();

        for (String tramo : tramosConRangos.keySet()) {
            String provinciaStr = tramo.substring(0, 2);
            provinciasUnicas.add(Integer.parseInt(provinciaStr));
        }

        return provinciasUnicas.size();
    }

    /**
     * Verifica si damos servicio a una dirección COMPLETA
     * Valida que el número de portal esté dentro de los rangos válidos de la vía
     * Complejidad: O(1) búsqueda HashMap + O(k) iteración rangos (k típicamente 1-3)
     *
     * @param peticion Petición con TODOS los campos obligatorios
     * @return true si cubrimos esa dirección exacta con ese número de portal
     * @throws IllegalArgumentException si la petición no está completa
     */
    public boolean damosServicio(PeticionCliente peticion) {
        if (peticion == null) {
            throw new IllegalArgumentException("La petición no puede ser null");
        }

        // Verificar que la petición esté completa
        if (!peticion.esValida()) {
            throw new IllegalArgumentException(
                    "Para validar entregas, la dirección debe estar COMPLETA. " +
                            "Faltan campos: " + obtenerCamposFaltantes(peticion)
            );
        }

        // Buscar la vía en el HashMap
        String clave = peticion.getClave();
        List<RangoPortal> rangos = tramosConRangos.get(clave);

        // Si la vía no existe en nuestra cobertura, no damos servicio
        if (rangos == null || rangos.isEmpty()) {
            return false;
        }

        // Verificar si el número de portal está en alguno de los rangos de la vía
        int numeroPortal = peticion.getNumero();
        for (RangoPortal rango : rangos) {
            if (rango.contieneNumero(numeroPortal)) {
                return true;
            }
        }

        // El número de portal no está en ningún rango válido
        return false;
    }

    /**
     * Verifica si existe una vía (sin validar número de portal)
     * @param clave Clave en formato "PROV_MUN_CUN_VIA"
     * @return true si la vía existe en nuestra cobertura
     */
    public boolean existeTramo(String clave) {
        return tramosConRangos.containsKey(clave);
    }

    /**
     * Obtiene todas las claves de vías cubiertas
     */
    public Set<String> getTramosCompletos() {
        return new HashSet<>(tramosConRangos.keySet());
    }

    /**
     * Obtiene el conjunto de provincias únicas cubiertas
     */
    public Set<Integer> getProvinciasCubiertas() {
        Set<Integer> provinciasUnicas = new HashSet<>();

        for (String tramo : tramosConRangos.keySet()) {
            String provinciaStr = tramo.substring(0, 2);
            provinciasUnicas.add(Integer.parseInt(provinciaStr));
        }

        return provinciasUnicas;
    }

    /**
     * Limpia todos los datos
     */
    public void limpiar() {
        tramosConRangos.clear();
    }

    /**
     * Devuelve una lista de campos faltantes en la petición
     */
    private String obtenerCamposFaltantes(PeticionCliente peticion) {
        StringBuilder faltantes = new StringBuilder();

        if (peticion.getProvincia() == null) {
            faltantes.append("provincia, ");
        }
        if (peticion.getMunicipio() == null) {
            faltantes.append("municipio, ");
        }
        if (peticion.getUnidadPoblacional() == null) {
            faltantes.append("unidadPoblacional, ");
        }
        if (peticion.getVia() == null) {
            faltantes.append("via, ");
        }
        if (peticion.getNumero() == null) {
            faltantes.append("numero");
        }

        String resultado = faltantes.toString();
        if (resultado.endsWith(", ")) {
            resultado = resultado.substring(0, resultado.length() - 2);
        }

        return resultado;
    }

    @Override
    public String toString() {
        return String.format("CoberturaServicio[vías=%d, provincias=%d]",
                tramosConRangos.size(), numeroProvinciasCubiertas());
    }
}